package edu.cit.audioscholar.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.TagException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import com.google.cloud.Timestamp;
import edu.cit.audioscholar.config.RabbitMQConfig;
import edu.cit.audioscholar.dto.AudioProcessingMessage;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.ProcessingStatus;
import edu.cit.audioscholar.model.Recording;

@Service
public class AudioTranscriptionListenerService {
    private static final Logger log =
            LoggerFactory.getLogger(AudioTranscriptionListenerService.class);
    private static final String CACHE_METADATA_BY_USER = "audioMetadataByUser";

    private final FirebaseService firebaseService;
    private final NhostStorageService nhostStorageService;
    private final GeminiService geminiService;
    private final RecordingService recordingService;
    private final CacheManager cacheManager;
    private final Path tempFileDir;
    private final RabbitTemplate rabbitTemplate;
    private final Map<String, ReentrantLock> metadataLocks = new ConcurrentHashMap<>();

    public AudioTranscriptionListenerService(FirebaseService firebaseService,
            NhostStorageService nhostStorageService, GeminiService geminiService,
            @Lazy RecordingService recordingService, CacheManager cacheManager,
            @Value("${app.temp-file-dir}") String tempFileDirStr, RabbitTemplate rabbitTemplate) {
        this.firebaseService = firebaseService;
        this.nhostStorageService = nhostStorageService;
        this.geminiService = geminiService;
        this.recordingService = recordingService;
        this.cacheManager = cacheManager;
        this.tempFileDir = Paths.get(tempFileDirStr);
        this.rabbitTemplate = rabbitTemplate;
        try {
            Files.createDirectories(this.tempFileDir);
        } catch (IOException e) {
            log.error("Could not create temporary directory for listener: {}",
                    this.tempFileDir.toAbsolutePath(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.TRANSCRIPTION_QUEUE_NAME)
    public void handleAudioTranscriptionRequest(AudioProcessingMessage message) {
        String metadataId = message.getMetadataId();
        String userId = message.getUserId();

        log.info("[AMQP Listener - Transcription] Received request for metadataId: {}", metadataId);

        if (metadataId == null) {
            log.error("Received transcription request with null metadataId. Ignoring.");
            return;
        }

        Lock metadataLock = metadataLocks.computeIfAbsent(metadataId, id -> new ReentrantLock());
        if (!metadataLock.tryLock()) {
            log.info(
                    "[{}] Another thread is already processing this metadata ID. Skipping duplicate processing.",
                    metadataId);
            return;
        }

        try {
            log.debug("[{}] Fetching AudioMetadata document...", metadataId);
            Map<String, Object> metadataMap;
            try {
                metadataMap = firebaseService
                        .getData(firebaseService.getAudioMetadataCollectionName(), metadataId);
            } catch (Exception e) {
                log.error("[{}] Failed to fetch metadata: {}", metadataId, e.getMessage());
                return;
            }

            AudioMetadata metadata = AudioMetadata.fromMap(metadataMap);
            log.info("[{}] Found metadata. Current status: {}, User: {}", metadataId,
                    metadata.getStatus(), userId);

            ProcessingStatus currentStatus = metadata.getStatus();
            if (currentStatus != ProcessingStatus.UPLOAD_IN_PROGRESS
                    && currentStatus != ProcessingStatus.PROCESSING_QUEUED) {
                log.info(
                        "[{}] Skipping transcription as metadata is already in status: {}. Transcription has likely been processed already.",
                        metadataId, currentStatus);
                return;
            }

            if (metadata.isTranscriptionComplete()) {
                log.info("[{}] Skipping transcription as it is already marked as complete.",
                        metadataId);

                checkCompletionAndTriggerSummarization(metadataId, userId);
                return;
            }

            updateMetadataStatus(metadataId, userId, ProcessingStatus.TRANSCRIBING, null);

            try {
                log.debug("[{}] Fetching Recording document {}...", metadataId, metadataId);
                Recording recording = recordingService.getRecordingById(metadataId);
                log.info("[{}] Found recording {}.", metadataId, metadataId);

                String audioUrl = recording.getAudioUrl();
                if (audioUrl == null || audioUrl.isBlank()) {
                    updateMetadataStatusToFailed(metadataId, userId,
                            "Audio URL not found in recording");
                    return;
                }

                String nhostFileId = extractNhostIdFromUrl(audioUrl);
                if (nhostFileId == null) {
                    updateMetadataStatusToFailed(metadataId, userId,
                            "Could not extract Nhost file ID from URL");
                    return;
                }

                String originalFileName =
                        metadata.getFileName() != null ? metadata.getFileName() : "audio.aac";
                Path tempFilePath = downloadAudioToFile(recording, metadataId);
                if (tempFilePath == null) {
                    return;
                }

                Integer durationSeconds = metadata.getDurationSeconds();
                if (durationSeconds == null || durationSeconds <= 0) {
                    durationSeconds = calculateAudioDuration(tempFilePath, metadataId);
                    if (durationSeconds != null && durationSeconds > 0) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("durationSeconds", durationSeconds);
                        updates.put("lastUpdated", Timestamp.now());
                        firebaseService.updateDataWithMap(
                                firebaseService.getAudioMetadataCollectionName(), metadataId,
                                updates);
                        log.info("[{}] Successfully updated durationSeconds ({}) in metadata.",
                                metadataId, durationSeconds);
                    }
                }

                metadataMap = firebaseService
                        .getData(firebaseService.getAudioMetadataCollectionName(), metadataId);
                metadata = AudioMetadata.fromMap(metadataMap);
                if (metadata.isTranscriptionComplete()) {
                    log.info(
                            "[{}] Transcription was completed by another process while we were preparing. Skipping API call.",
                            metadataId);
                    checkCompletionAndTriggerSummarization(metadataId, userId);
                    return;
                }

                log.info(
                        "[{}] Calling Gemini API for transcription... Original Filename: {}, ContentType: {}",
                        metadataId, originalFileName, metadata.getContentType());
                String transcript =
                        geminiService.callGeminiTranscriptionAPI(tempFilePath, originalFileName);

                metadataMap = firebaseService
                        .getData(firebaseService.getAudioMetadataCollectionName(), metadataId);
                metadata = AudioMetadata.fromMap(metadataMap);
                if (metadata.isTranscriptionComplete()) {
                    log.info(
                            "[{}] Transcription was completed by another process while we were transcribing. Skipping update.",
                            metadataId);
                    return;
                }

                log.info(
                        "[{}] Transcription completed successfully. Saving transcript and updating status.",
                        metadataId);
                Map<String, Object> updates = new HashMap<>();
                updates.put("transcriptText", transcript);
                updates.put("transcriptionComplete", true);
                updates.put("status", ProcessingStatus.TRANSCRIPTION_COMPLETE.name());
                updates.put("lastUpdated", Timestamp.now());

                log.info("[{}] Saving transcript with size: {} characters", metadataId,
                        transcript != null ? transcript.length() : 0);

                firebaseService.updateDataWithMap(firebaseService.getAudioMetadataCollectionName(),
                        metadataId, updates);
                log.info(
                        "[{}] Successfully saved transcript, set transcriptionComplete=true, and status=TRANSCRIPTION_COMPLETE.",
                        metadataId);
                invalidateCache(userId);

                try {
                    log.debug(
                            "[{}] Adding a short delay to ensure Firestore consistency before summarization...",
                            metadataId);
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[{}] Delay before summarization was interrupted", metadataId);
                }

                checkCompletionAndTriggerSummarization(metadataId, userId);

                try {
                    Files.deleteIfExists(tempFilePath);
                    log.debug("[{}] Deleted temporary audio file: {}", metadataId, tempFilePath);
                } catch (IOException e) {
                    log.warn("[{}] Failed to delete temporary audio file: {}", metadataId,
                            e.getMessage());
                }
            } catch (Exception e) {
                log.error("[{}] Error during transcription process: {}", metadataId, e.getMessage(),
                        e);
                updateMetadataStatusToFailed(metadataId, userId,
                        "Error during transcription: " + e.getMessage());
            }
        } finally {
            metadataLock.unlock();
            if (metadataLock instanceof ReentrantLock) {
                ReentrantLock reentrantLock = (ReentrantLock) metadataLock;
                if (!reentrantLock.isLocked() && !reentrantLock.hasQueuedThreads()) {
                    metadataLocks.remove(metadataId);
                }
            }
        }
    }

    private Path downloadAudioToFile(Recording recording, String metadataId) {
        if (recording.getAudioUrl() == null || recording.getAudioUrl().isEmpty()) {
            log.error("[{}] Recording object has no audioUrl. Cannot download.", metadataId);
            updateMetadataStatusToFailed(metadataId, recording.getUserId(),
                    "Audio URL missing in Recording document.");
            return null;
        }
        String nhostId = extractNhostIdFromUrl(recording.getAudioUrl());
        if (nhostId == null) {
            log.error("[{}] Could not extract Nhost file ID from URL: {}", metadataId,
                    recording.getAudioUrl());
            updateMetadataStatusToFailed(metadataId, recording.getUserId(),
                    "Invalid Nhost Audio URL format.");
            return null;
        }

        String fileName = recording.getFileName();
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "audio.mp3";
            log.info("[{}] No filename found in recording. Using default name: {}", metadataId,
                    fileName);
        }

        Path tempFilePath = tempFileDir.resolve(metadataId + "_" + fileName);

        try {
            log.info("[{}] Downloading audio file with Nhost ID: {} to {}", metadataId, nhostId,
                    tempFilePath.getFileName());
            nhostStorageService.downloadFileToPath(nhostId, tempFilePath);
            log.info("[{}] Audio downloaded successfully.", metadataId);
            return tempFilePath;
        } catch (IOException e) {
            log.error("[{}] Failed to download audio file from Nhost (ID: {}). Error: {}",
                    metadataId, nhostId, e.getMessage(), e);
            updateMetadataStatusToFailed(metadataId, recording.getUserId(),
                    "Failed to download audio file: " + e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[{}] Unexpected error downloading audio file from Nhost (ID: {}). Error: {}",
                    metadataId, nhostId, e.getMessage(), e);
            updateMetadataStatusToFailed(metadataId, recording.getUserId(),
                    "Unexpected download error: " + e.getMessage());
            return null;
        }
    }

    private Integer calculateAudioDuration(Path audioFilePath, String metadataId) {
        Integer durationSec = null;
        try {
            File audioFile = audioFilePath.toFile();
            String fileName = audioFilePath.getFileName().toString().toLowerCase();

            if (fileName.endsWith(".aac") || fileName.endsWith(".m4a")) {
                log.info(
                        "[{}] AAC file detected: {}. JAudioTagger cannot reliably read AAC duration. Setting a default duration.",
                        metadataId, audioFilePath.getFileName());
                return 600;
            }

            if (audioFile.exists() && audioFile.length() > 0) {
                AudioFile f = AudioFileIO.read(audioFile);
                AudioHeader header = f.getAudioHeader();
                if (header != null) {
                    durationSec = header.getTrackLength();
                    log.info("[{}] Calculated audio duration using JAudioTagger: {} seconds.",
                            metadataId, durationSec);
                    return durationSec;
                } else {
                    log.warn(
                            "[{}] JAudioTagger could not read audio header for {}. Duration not calculated.",
                            metadataId, audioFilePath.getFileName());
                }
            } else {
                log.warn(
                        "[{}] Temporary audio file {} does not exist or is empty. Cannot calculate duration.",
                        metadataId, audioFilePath.toAbsolutePath());
            }
        } catch (CannotReadException | IOException | TagException | ReadOnlyFileException
                | InvalidAudioFrameException e) {
            log.warn(
                    "[{}] JAudioTagger failed to read audio file {} for duration calculation. Error: {}. Proceeding without duration.",
                    metadataId, audioFilePath.getFileName(), e.getMessage());
            log.debug("JAudioTagger exception details:", e);
        } catch (Exception e) {
            log.error(
                    "[{}] Unexpected error calculating duration using JAudioTagger for {}. Error: {}",
                    metadataId, audioFilePath.getFileName(), e.getMessage(), e);
        }
        return null;
    }


    private void checkCompletionAndTriggerSummarization(String metadataId,
            @Nullable String userId) {
        Lock lock = null;
        try {
            lock = metadataLocks.computeIfAbsent(metadataId, k -> new ReentrantLock());
            lock.lock();
            log.debug("[{}] Acquired lock for trigger summarization check", metadataId);

            log.info(
                    "[{}] Checking if both transcription and PDF conversion are complete to trigger summarization...",
                    metadataId);

            Map<String, Object> latestMetadataMap = firebaseService
                    .getData(firebaseService.getAudioMetadataCollectionName(), metadataId);

            if (latestMetadataMap == null) {
                log.error(
                        "[{}] Failed to re-fetch metadata after transcription update. Cannot check for summarization trigger.",
                        metadataId);
                return;
            }

            AudioMetadata latestMetadata = AudioMetadata.fromMap(latestMetadataMap);

            ProcessingStatus currentStatus = latestMetadata.getStatus();
            if (currentStatus == ProcessingStatus.SUMMARIZATION_QUEUED
                    || currentStatus == ProcessingStatus.SUMMARIZING
                    || currentStatus == ProcessingStatus.SUMMARY_COMPLETE
                    || currentStatus == ProcessingStatus.RECOMMENDATIONS_QUEUED
                    || currentStatus == ProcessingStatus.GENERATING_RECOMMENDATIONS
                    || currentStatus == ProcessingStatus.COMPLETE
                    || currentStatus == ProcessingStatus.FAILED) {

                log.info(
                        "[{}] Summarization already triggered or completed (current status: {}). Skipping duplicate trigger.",
                        metadataId, currentStatus);
                return;
            }

            boolean statusChangeInProgress = false;
            try {
                Map<String, Object> latestStatusCheck = firebaseService
                        .getData(firebaseService.getAudioMetadataCollectionName(), metadataId);

                if (latestStatusCheck != null) {
                    AudioMetadata checkMetadata = AudioMetadata.fromMap(latestStatusCheck);
                    ProcessingStatus checkStatus = checkMetadata.getStatus();

                    if (checkStatus != currentStatus) {
                        log.info(
                                "[{}] Status changed from {} to {} while processing. Likely another service triggered summarization. Skipping.",
                                metadataId, currentStatus, checkStatus);
                        statusChangeInProgress = true;
                    }
                }
            } catch (Exception e) {
                log.warn(
                        "[{}] Error during additional status check: {}. Proceeding with normal flow.",
                        metadataId, e.getMessage());
            }

            if (statusChangeInProgress) {
                return;
            }

            boolean transcriptionDone = latestMetadata.isTranscriptionComplete();
            boolean pdfDone = latestMetadata.isPdfConversionComplete();
            boolean isAudioOnly = latestMetadata.isAudioOnly();

            log.debug(
                    "[{}] Completion status check: TranscriptionDone={}, PdfConversionDone={}, AudioOnly={}",
                    metadataId, transcriptionDone, pdfDone, isAudioOnly);

            boolean readyForSummarization = transcriptionDone && (pdfDone || isAudioOnly);

            if (readyForSummarization) {
                log.info(
                        "[{}] Conditions met for summarization: AudioOnly={}, TranscriptionDone={}, PdfDone={}. Sending message to summarization queue.",
                        metadataId, isAudioOnly, transcriptionDone, pdfDone);

                boolean statusUpdated = updateMetadataStatus(metadataId, userId,
                        ProcessingStatus.SUMMARIZATION_QUEUED, null);

                if (!statusUpdated) {
                    log.warn(
                            "[{}] Failed to update status to SUMMARIZATION_QUEUED. Another process may have already claimed this task.",
                            metadataId);
                    return;
                }

                String messageId = UUID.randomUUID().toString();
                Map<String, String> messagePayload = new HashMap<>();
                messagePayload.put("metadataId", metadataId);
                messagePayload.put("messageId", messageId);

                rabbitTemplate.convertAndSend(RabbitMQConfig.PROCESSING_EXCHANGE_NAME,
                        RabbitMQConfig.SUMMARIZATION_ROUTING_KEY, messagePayload);

                log.info(
                        "[{}] Message (ID: {}) sent successfully to queue '{}' with routing key '{}'.",
                        metadataId, messageId, RabbitMQConfig.SUMMARIZATION_QUEUE_NAME,
                        RabbitMQConfig.SUMMARIZATION_ROUTING_KEY);

            } else {
                log.info(
                        "[{}] Conditions not yet met for final summarization (Transcription: {}, PDF: {}). Waiting for other process.",
                        metadataId, transcriptionDone, pdfDone);
            }

        } catch (FirestoreInteractionException e) {
            log.error(
                    "[{}] Failed to re-fetch metadata or update status for summarization check. Error: {}",
                    metadataId, e.getMessage(), e);
        } finally {
            if (lock != null) {
                lock.unlock();
                log.debug("[{}] Released lock for trigger summarization check", metadataId);
            }
        }
    }

    private void updateMetadataStatusToFailed(String metadataId, @Nullable String userId,
            String reason) {
        updateMetadataStatus(metadataId, userId, ProcessingStatus.FAILED, reason);
        log.error("[{}] Processing failed. Reason: {}", metadataId, reason);
    }

    private boolean updateMetadataStatus(String metadataId, @Nullable String userId,
            ProcessingStatus status, @Nullable String reason) {
        try {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", status.name());
            updates.put("lastUpdated", Timestamp.now());
            if (reason != null) {
                updates.put("failureReason", reason);
            }

            firebaseService.updateDataWithMap(firebaseService.getAudioMetadataCollectionName(),
                    metadataId, updates);
            log.info("[{}] Metadata status updated to {}.", metadataId, status);
            if (status == ProcessingStatus.FAILED
                    || status == ProcessingStatus.TRANSCRIPTION_COMPLETE
                    || status == ProcessingStatus.SUMMARIZATION_QUEUED) {
                invalidateCache(userId);
            }
            return true;
        } catch (FirestoreInteractionException e) {
            log.error("[{}] CRITICAL: Failed to update metadata status to {}. Error: {}",
                    metadataId, status, e.getMessage(), e);
            return false;
        }
    }

    private String extractNhostIdFromUrl(String url) {
        if (url == null)
            return null;
        try {
            String[] parts = url.split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("files".equals(parts[i]) && i + 1 < parts.length) {
                    try {
                        UUID.fromString(parts[i + 1]);
                        return parts[i + 1];
                    } catch (IllegalArgumentException e) {
                        log.warn("Extracted part '{}' is not a valid UUID from URL: {}",
                                parts[i + 1], url);
                        return null;
                    }
                }
            }
            log.warn("Could not find '/files/' segment followed by an ID in URL: {}", url);
        } catch (Exception e) {
            log.error("Error parsing Nhost URL '{}': {}", url, e.getMessage());
        }
        return null;
    }

    private void invalidateCache(@Nullable String userId) {
        if (userId != null) {
            Cache cache = cacheManager.getCache(CACHE_METADATA_BY_USER);
            if (cache != null) {
                cache.evict(userId);
                log.debug("Invalidated cache '{}' for userId: {}", CACHE_METADATA_BY_USER, userId);
            } else {
                log.warn("Cache '{}' not found. Could not invalidate for userId: {}",
                        CACHE_METADATA_BY_USER, userId);
            }
        } else {
            log.warn("Cannot invalidate cache because userId is null.");
        }
    }
}

package edu.cit.audioscholar.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import edu.cit.audioscholar.config.RabbitMQConfig;
import edu.cit.audioscholar.dto.AudioProcessingMessage;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.ProcessingStatus;
import edu.cit.audioscholar.model.Recording;
import edu.cit.audioscholar.model.Summary;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.CannotReadException;

@Service
public class AudioSummarizationListenerService {
        private static final Logger log =
                        LoggerFactory.getLogger(AudioSummarizationListenerService.class);
        private static final String CACHE_METADATA_BY_USER = "audioMetadataByUser";
        private static final Pattern WORD_PATTERN = Pattern.compile("\\b\\w+\\b"); // Simple word extraction
        private static final double MIN_UNIQUE_WORD_RATIO = 0.3; // Threshold for repetition
        private static final int MINIMUM_TRANSCRIPT_WORDS = 30; // Use word count instead of chars

        private final FirebaseService firebaseService;
        private final NhostStorageService nhostStorageService;
        private final GeminiService geminiService;
        private final LearningMaterialRecommenderService learningMaterialRecommenderService;
        private final RecordingService recordingService;
        private final SummaryService summaryService;
        private final ObjectMapper objectMapper;
        private final CacheManager cacheManager;
        private final Path tempFileDir;

        public AudioSummarizationListenerService(FirebaseService firebaseService,
                        NhostStorageService nhostStorageService, GeminiService geminiService,
                        LearningMaterialRecommenderService learningMaterialRecommenderService,
                        @Lazy RecordingService recordingService,
                        @Lazy SummaryService summaryService, ObjectMapper objectMapper,
                        CacheManager cacheManager,
                        @Value("${app.temp-file-dir}") String tempFileDirStr) {
                this.firebaseService = firebaseService;
                this.nhostStorageService = nhostStorageService;
                this.geminiService = geminiService;
                this.learningMaterialRecommenderService = learningMaterialRecommenderService;
                this.recordingService = recordingService;
                this.summaryService = summaryService;
                this.objectMapper = objectMapper;
                this.cacheManager = cacheManager;
                this.tempFileDir = Paths.get(tempFileDirStr);
                try {
                        Files.createDirectories(this.tempFileDir);
                } catch (IOException e) {
                        log.error("Could not create temporary directory for listener: {}",
                                        this.tempFileDir.toAbsolutePath(), e);
                }
        }

        @RabbitListener(queues = RabbitMQConfig.PROCESSING_QUEUE_NAME)
        public void handleAudioProcessingRequest(AudioProcessingMessage message) {
                if (message == null || message.getRecordingId() == null
                                || message.getRecordingId().isEmpty()
                                || message.getMetadataId() == null
                                || message.getMetadataId().isEmpty()) {
                        log.error("[AMQP Listener] Received invalid or incomplete message from queue '{}'. Ignoring. Message: {}",
                                        RabbitMQConfig.PROCESSING_QUEUE_NAME, message);
                        return;
                }

                final String recordingId = message.getRecordingId();
                final String metadataId = message.getMetadataId();
                log.info("[AMQP Listener] Received request for recordingId: {}, metadataId: {}",
                                recordingId, metadataId);

                AudioMetadata metadata = null;
                String userId = null;
                Recording recording = null;
                Path tempAudioFilePath = null;

                try {
                        log.debug("[{}] Fetching AudioMetadata document...", metadataId);
                        metadata = firebaseService.getAudioMetadataById(metadataId);
                        if (metadata == null) {
                                log.error("[{}] AudioMetadata not found for ID. Cannot process recording {}.",
                                                metadataId, recordingId);
                                return;
                        }
                        userId = metadata.getUserId();
                        if (userId == null) {
                                log.error("[{}] userId is null in fetched metadata. Cannot proceed with cache eviction correctly.", metadataId);
                        }
                        log.info("[{}] Found metadata. Current status: {}, User: {}", metadataId,
                                        metadata.getStatus(), userId);

                        if (metadata.getStatus() != ProcessingStatus.PENDING) {
                                log.warn("[AMQP Listener] Metadata {} status is not PENDING (it's {}). Skipping summarization processing.",
                                                metadataId, metadata.getStatus());
                                return;
                        }

                        log.debug("[{}] Fetching Recording document...", recordingId);
                        recording = recordingService.getRecordingById(recordingId);
                        if (recording == null) {
                                log.error("[{}] Recording document not found, but metadata {} exists. Cannot process audio.",
                                                recordingId, metadataId);
                                updateMetadataStatusToFailed(metadataId, userId,
                                                "Associated Recording document not found.");
                                return;
                        }
                        log.info("[{}] Found recording.", recordingId);

                        log.info("[{}] Updating metadata {} status to PROCESSING.", recordingId,
                                        metadataId);
                        updateMetadataStatus(metadataId, userId, ProcessingStatus.PROCESSING, null);

                        tempAudioFilePath = downloadAudioToFile(recording, metadataId);
                        if (tempAudioFilePath == null) {
                                return;
                        }

                        // --- Calculate Duration using JAudioTagger --- 
                        Integer durationSec = null;
                        try {
                                File audioFile = tempAudioFilePath.toFile();
                                if (audioFile.exists() && audioFile.length() > 0) {
                                        AudioFile f = AudioFileIO.read(audioFile);
                                        AudioHeader header = f.getAudioHeader();
                                        if (header != null) {
                                                durationSec = header.getTrackLength(); // Duration in seconds
                                                log.info("[{}] Calculated audio duration using JAudioTagger: {} seconds.", recordingId, durationSec);
                                                // Update Firestore immediately
                                                Map<String, Object> durationUpdate = Map.of("durationSeconds", durationSec);
                                                firebaseService.updateDataWithMap(
                                                        firebaseService.getAudioMetadataCollectionName(),
                                                        metadataId,
                                                        durationUpdate
                                                );
                                                log.info("[{}] Successfully updated durationSeconds ({}) in metadata.", recordingId, durationSec);
                                        } else {
                                                log.warn("[{}] JAudioTagger could not read audio header for {}. Duration not calculated.", 
                                                         recordingId, tempAudioFilePath.getFileName());
                                        }
                                } else {
                                        log.warn("[{}] Temporary audio file {} does not exist or is empty. Cannot calculate duration.", 
                                                 recordingId, tempAudioFilePath.toAbsolutePath());
                                }
                        } catch (CannotReadException | IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException e) {
                                log.warn("[{}] JAudioTagger failed to read audio file {} for duration. Error: {}. Proceeding without duration.", 
                                         recordingId, tempAudioFilePath.getFileName(), e.getMessage());
                                // Log the full stack trace at debug level if needed
                                log.debug("JAudioTagger exception details:", e);
                        } catch (Exception e) {
                                log.error("[{}] Unexpected error calculating duration using JAudioTagger for {}. Error: {}", 
                                         recordingId, tempAudioFilePath.getFileName(), e.getMessage(), e);
                        }
                        // --- End Duration Calculation ---

                        String transcriptText = performTranscription(recording, tempAudioFilePath,
                                        metadataId);
                        if (transcriptText == null) {
                                log.warn("[{}] Transcription failed or returned null. Halting processing.", recordingId);
                                return;
                        }

                        // --- Quality Gate Starts ---
                        // Check 1: No speech detected marker
                        final String noSpeechMarker = "[NO SPEECH DETECTED]";
                        if (noSpeechMarker.equalsIgnoreCase(transcriptText.trim())) {
                            log.warn("[{}] Transcription resulted in '{}'. No speech detected or audio unsuitable. Halting summarization and recommendations.",
                                     recordingId, noSpeechMarker);
                            updateMetadataStatus(metadataId, userId, ProcessingStatus.PROCESSING_HALTED_UNSUITABLE_CONTENT, "No speech detected in audio");
                            return;
                        }

                        // Extract words for checks
                        String[] words = WORD_PATTERN.matcher(transcriptText.toLowerCase()).results()
                                .map(mr -> mr.group()).toArray(String[]::new);
                        int wordCount = words.length;

                        // Check 2: Minimum word count
                        if (wordCount < MINIMUM_TRANSCRIPT_WORDS) {
                            log.warn("[{}] Transcript word count ({}) is below the minimum threshold of {}. Considered unsuitable. Halting summarization and recommendations.",
                                     recordingId, wordCount, MINIMUM_TRANSCRIPT_WORDS);
                            updateMetadataStatus(metadataId, userId, ProcessingStatus.PROCESSING_HALTED_UNSUITABLE_CONTENT,
                                                 "Transcript too short to be suitable lecture material.");
                            return; // Stop further processing
                        }

                        // Check 3: Repetitiveness
                        if (isTranscriptTooRepetitive(words)) {
                            log.warn("[{}] Transcript appears too repetitive (unique word ratio below threshold). Considered unsuitable. Halting summarization and recommendations.",
                                     recordingId);
                            updateMetadataStatus(metadataId, userId, ProcessingStatus.PROCESSING_HALTED_UNSUITABLE_CONTENT,
                                                 "Transcript content appears too repetitive.");
                            return; // Stop further processing
                        }
                        // --- Quality Gate Ends ---

                        log.info("[{}] Transcript passed quality checks (length, repetition). Proceeding with processing.", recordingId);

                        if (!saveTranscript(metadataId, transcriptText)) {
                                updateMetadataStatusToFailed(metadataId, userId,
                                                "Failed to save transcript to metadata.");
                                return;
                        }
                        metadata.setTranscriptText(transcriptText);

                        String summarizationJson =
                                        performSummarization(recording, transcriptText, metadataId);
                        if (summarizationJson == null) {
                                return;
                        }

                        Summary savedSummary = parseAndSaveSummary(recording, metadataId,
                                        summarizationJson);
                        if (savedSummary == null) {
                                AudioMetadata currentMeta =
                                                firebaseService.getAudioMetadataById(metadataId);
                                if (currentMeta != null && currentMeta
                                                .getStatus() != ProcessingStatus.FAILED) {
                                        updateMetadataStatusToFailed(metadataId, userId,
                                                        "Failed to save summary after successful generation.");
                                }
                                return;
                        }

                        finalizeProcessing(metadataId, userId, savedSummary.getSummaryId());
                        triggerRecommendations(recording.getUserId(), recordingId,
                                        savedSummary.getSummaryId());

                } catch (FirestoreInteractionException e) {
                        log.error("[{}] Firestore error during processing for metadata {}.",
                                        recordingId, metadataId, e);
                        updateMetadataStatusToFailed(metadataId, userId,
                                        "Firestore interaction failed during processing.");
                } catch (ExecutionException | InterruptedException e) {
                        log.error("[{}] Concurrency/Execution error during processing for metadata {}.",
                                        recordingId, metadataId, e);
                        Thread.currentThread().interrupt();
                        updateMetadataStatusToFailed(metadataId, userId,
                                        "Concurrency error during processing.");
                } catch (RuntimeException e) {
                        log.error("[{}] Runtime error during processing for metadata {}.",
                                        recordingId, metadataId, e);
                        AudioMetadata currentMeta =
                                        firebaseService.getAudioMetadataById(metadataId);
                        if (currentMeta != null
                                        && currentMeta.getStatus() != ProcessingStatus.FAILED) {
                                updateMetadataStatusToFailed(metadataId, userId,
                                                "Processing runtime error: " + e.getMessage());
                        }
                } catch (Exception e) {
                        log.error("[{}] Unexpected error during processing for metadata {}.",
                                        recordingId, metadataId, e);
                        AudioMetadata currentMeta =
                                        firebaseService.getAudioMetadataById(metadataId);
                        if (currentMeta != null
                                        && currentMeta.getStatus() != ProcessingStatus.FAILED) {
                                updateMetadataStatusToFailed(metadataId, userId,
                                                "Unexpected processing error: " + e.getMessage());
                        }
                } finally {
                        if (tempAudioFilePath != null) {
                                try {
                                        log.debug("[{}] Deleting temporary audio file: {}",
                                                        recordingId,
                                                        tempAudioFilePath.toAbsolutePath());
                                        Files.deleteIfExists(tempAudioFilePath);
                                        log.info("[{}] Successfully deleted temporary audio file: {}",
                                                        recordingId,
                                                        tempAudioFilePath.toAbsolutePath());
                                } catch (IOException e) {
                                        log.warn("[{}] Failed to delete temporary audio file: {}. Error: {}",
                                                        recordingId,
                                                        tempAudioFilePath.toAbsolutePath(),
                                                        e.getMessage());
                                }
                        }
                }
        }

        private Path downloadAudioToFile(Recording recording, String metadataId) {
                String recordingId = recording.getRecordingId();
                String userId = recording.getUserId();
                Path tempFilePath = null;
                try {
                        String nhostFileId = extractNhostIdFromUrl(recording.getAudioUrl());
                        if (nhostFileId == null) {
                                log.error("[{}] Could not extract Nhost File ID from URL: {}. Cannot download audio.",
                                                recordingId, recording.getAudioUrl());
                                updateMetadataStatusToFailed(metadataId, userId,
                                                "Invalid Nhost URL in Recording document.");
                                return null;
                        }
                        String fileExtension = Optional.ofNullable(recording.getFileName())
                                        .map(f -> f.contains(".") ? f.substring(f.lastIndexOf("."))
                                                        : ".tmp")
                                        .orElse(".tmp");
                        tempFilePath = Files.createTempFile(tempFileDir,
                                        "audio_" + recordingId + "_", fileExtension);
                        log.info("[{}] Created temporary file path for download: {}", recordingId,
                                        tempFilePath.toAbsolutePath());
                        nhostStorageService.downloadFileToPath(nhostFileId, tempFilePath);
                        log.info("[{}] Successfully downloaded audio from Nhost file ID {} to {}",
                                        recordingId, nhostFileId, tempFilePath.toAbsolutePath());
                        return tempFilePath;
                } catch (IOException e) {
                        log.error("[{}] IOException during audio download/saving for metadata {}. Temp path: {}. Error: {}",
                                        recordingId, metadataId, tempFilePath, e.getMessage(), e);
                        updateMetadataStatusToFailed(metadataId, userId,
                                        "Audio download or saving failed: " + e.getMessage());
                        if (tempFilePath != null) {
                                try {
                                        Files.deleteIfExists(tempFilePath);
                                } catch (IOException ignored) {
                                }
                        }
                        return null;
                } catch (Exception e) {
                        log.error("[{}] Unexpected exception during audio download for metadata {}. Temp path: {}. Error: {}",
                                        recordingId, metadataId, tempFilePath, e.getMessage(), e);
                        updateMetadataStatusToFailed(metadataId, userId,
                                        "Unexpected download error: " + e.getMessage());
                        if (tempFilePath != null) {
                                try {
                                        Files.deleteIfExists(tempFilePath);
                                } catch (IOException ignored) {
                                }
                        }
                        return null;
                }
        }

        private String performTranscription(Recording recording, Path audioFilePath,
                        String metadataId) {
                String recordingId = recording.getRecordingId();
                String userId = recording.getUserId();
                try {
                        log.info("[{}] Calling Gemini Transcription API (Flash Model) using file: {}",
                                        recordingId, audioFilePath.toAbsolutePath());
                        String transcriptionResult = geminiService.callGeminiTranscriptionAPI(
                                        audioFilePath, recording.getFileName());
                        if (isErrorResponse(transcriptionResult)) {
                                log.error("[{}] Gemini Transcription API failed. Response: {}",
                                                recordingId, transcriptionResult);
                                String errorDetails = "Transcription API Error";
                                try {
                                        JsonNode errorNode =
                                                        objectMapper.readTree(transcriptionResult);
                                        errorDetails = errorNode.path("error")
                                                        .asText("Transcription API Error") + ": "
                                                        + errorNode.path("details").asText(
                                                                        "Details unavailable");
                                } catch (JsonProcessingException ignored) {
                                }
                                updateMetadataStatusToFailed(metadataId, userId, errorDetails);
                                return null;
                        }
                        if (transcriptionResult.isBlank()) {
                                log.error("[{}] Gemini Transcription API returned an empty result.",
                                                recordingId);
                                updateMetadataStatusToFailed(metadataId, userId,
                                                "Audio transcription returned empty result.");
                                return null;
                        }
                        log.info("[{}] Transcription successful.", recordingId);
                        log.debug("[{}] Transcript (first 100 chars): {}", recordingId,
                                        transcriptionResult.substring(0,
                                                        Math.min(transcriptionResult.length(), 100))
                                                        + "...");
                        return transcriptionResult;
                } catch (IOException e) {
                        log.error("[{}] IOException during transcription (reading temp file?) for metadata {}. File: {}. Error: {}",
                                        recordingId, metadataId, audioFilePath.toAbsolutePath(),
                                        e.getMessage(), e);
                        updateMetadataStatusToFailed(metadataId, userId,
                                        "Transcription I/O error: " + e.getMessage());
                        return null;
                } catch (Exception e) {
                        log.error("[{}] Unexpected exception during transcription for metadata {}. File: {}. Error: {}",
                                        recordingId, metadataId, audioFilePath.toAbsolutePath(),
                                        e.getMessage(), e);
                        updateMetadataStatusToFailed(metadataId, userId,
                                        "Unexpected transcription error: " + e.getMessage());
                        return null;
                }
        }

        private String performSummarization(Recording recording, String transcriptText,
                        String metadataId) {
                String recordingId = recording.getRecordingId();
                String userId = recording.getUserId();
                try {
                        String prompt = createSummarizationPrompt(recording);
                        log.debug("[{}] Generated Gemini Summarization prompt for JSON mode: '{}'",
                                        recordingId, prompt);
                        log.info("[{}] Calling Gemini Summarization API (Pro Model, JSON Mode)...",
                                        recordingId);
                        String summarizationResult = geminiService
                                        .callGeminiSummarizationAPI(prompt, transcriptText);
                        if (isErrorResponse(summarizationResult)) {
                                log.error("[{}] Gemini Summarization API failed. Response: {}",
                                                recordingId, summarizationResult);
                                String errorDetails = "Summarization API Error";
                                try {
                                        JsonNode errorNode =
                                                        objectMapper.readTree(summarizationResult);
                                        errorDetails = errorNode.path("error")
                                                        .asText("Summarization API Error") + ": "
                                                        + errorNode.path("details").asText(
                                                                        "Details unavailable");
                                } catch (JsonProcessingException ignored) {
                                }
                                updateMetadataStatusToFailed(metadataId, userId, errorDetails);
                                return null;
                        }
                        if (summarizationResult.isBlank()) {
                                log.error("[{}] Gemini Summarization API returned an empty result.",
                                                recordingId);
                                updateMetadataStatusToFailed(metadataId, userId,
                                                "Summarization returned empty result.");
                                return null;
                        }
                        log.info("[{}] Summarization API call successful. Received JSON response.",
                                        recordingId);
                        log.debug("[{}] Summarization JSON (first 500 chars): {}", recordingId,
                                        summarizationResult.substring(0,
                                                        Math.min(summarizationResult.length(), 500))
                                                        + "...");
                        return summarizationResult;
                } catch (Exception e) {
                        log.error("[{}] Unexpected exception during summarization for metadata {}. Error: {}",
                                        recordingId, metadataId, e.getMessage(), e);
                        updateMetadataStatusToFailed(metadataId, userId,
                                        "Unexpected summarization error: " + e.getMessage());
                        return null;
                }
        }


        private boolean saveTranscript(String metadataId, String transcriptText) {
                log.info("[{}] Attempting to save transcript text to metadata.", metadataId);
                try {
                        Map<String, Object> updateMap = Map.of("transcriptText", transcriptText);
                        firebaseService.updateDataWithMap(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, updateMap);
                        log.info("[{}] Successfully saved transcript text to metadata.",
                                        metadataId);
                        return true;
                } catch (Exception e) {
                        log.error("[{}] CRITICAL: Failed to save transcript text to metadata. Error: {}",
                                        metadataId, e.getMessage(), e);
                        if (e instanceof InterruptedException)
                                Thread.currentThread().interrupt();
                        return false;
                }
        }

        private Summary parseAndSaveSummary(Recording recording, String metadataId,
                        String summarizationJson) {
                String recordingId = recording.getRecordingId();
                String userId = recording.getUserId();
                try {
                        JsonNode responseNode = objectMapper.readTree(summarizationJson);
                        if (responseNode.has("error")) {
                                String errorTitle =
                                                responseNode.path("error").asText("Unknown Error");
                                String errorDetails = responseNode.path("details")
                                                .asText("No details provided");
                                log.error("[{}] Parsed JSON indicates an error (should have been caught earlier): {} - {}",
                                                recordingId, errorTitle, errorDetails);
                                updateMetadataStatusToFailed(metadataId, userId,
                                                "AI service failed: " + errorTitle);
                                return null;
                        }
                        String summaryText = responseNode.path("summaryText").asText(null);
                        List<String> keyPoints =
                                        parseStringList(responseNode, "keyPoints", recordingId);
                        List<String> topics = parseStringList(responseNode, "topics", recordingId);
                        List<Map<String, String>> glossary =
                                        parseGlossary(responseNode, recordingId);
                        if (summaryText == null) {
                                log.error("[{}] Gemini JSON response missing required 'summaryText' field. Response: {}",
                                                recordingId, summarizationJson);
                                updateMetadataStatusToFailed(metadataId, userId,
                                                "AI response missing required 'summaryText'.");
                                return null;
                        }
                        log.info("[{}] Successfully parsed JSON response. Found {} key points, {} topics, and {} glossary items.",
                                        recordingId, keyPoints.size(), topics.size(),
                                        glossary.size());
                        Summary structuredSummary = new Summary();
                        structuredSummary.setSummaryId(UUID.randomUUID().toString());
                        structuredSummary.setRecordingId(recordingId);
                        structuredSummary.setFormattedSummaryText(summaryText);
                        structuredSummary.setKeyPoints(keyPoints);
                        structuredSummary.setTopics(topics);
                        structuredSummary.setGlossary(glossary);
                        log.info("[{}] Attempting to save summary (ID: {}) with {} key points, {} topics, {} glossary items to Firestore.",
                                        recordingId, structuredSummary.getSummaryId(),
                                        keyPoints.size(), topics.size(), glossary.size());
                        Summary savedSummary = summaryService.createSummary(structuredSummary);
                        log.info("[{}] Summary (ID: {}) saved successfully.", recordingId,
                                        savedSummary.getSummaryId());
                        return savedSummary;
                } catch (JsonProcessingException e) {
                        log.error("[{}] Failed to parse JSON response received from Summarization API. Error: {}. Response: {}",
                                        recordingId, e.getMessage(),
                                        summarizationJson.substring(0,
                                                        Math.min(summarizationJson.length(), 1000))
                                                        + "...");
                        updateMetadataStatusToFailed(metadataId, userId,
                                        "Failed to parse AI service JSON response.");
                        return null;
                } catch (ExecutionException | InterruptedException e) {
                        log.error("[{}] Error during summary saving for metadata {}.", recordingId,
                                        metadataId, e);
                        if (e instanceof InterruptedException)
                                Thread.currentThread().interrupt();
                        updateMetadataStatusToFailed(metadataId, userId,
                                        "Failed during summary saving: " + e.getMessage());
                        return null;
                } catch (Exception e) {
                        log.error("[{}] Unexpected exception during summary processing/saving for metadata {}.",
                                        recordingId, metadataId, e);
                        updateMetadataStatusToFailed(metadataId, userId,
                                        "Unexpected exception during summary processing: "
                                                        + e.getMessage());
                        return null;
                }
        }

        private void finalizeProcessing(String metadataId, String userId, String summaryId) {
                log.info("[{}] Finalizing processing: linking summaryId {} and setting status to COMPLETED for user {}.",
                                metadataId, summaryId, userId);
                try {
                        Map<String, Object> updateMap = new HashMap<>();
                        updateMap.put("summaryId", summaryId);
                        updateMap.put("status", ProcessingStatus.COMPLETED.name());
                        updateMap.put("failureReason", null);
                        updateMap.put("lastUpdated", Timestamp.now());

                        firebaseService.updateDataWithMap(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, updateMap);
                        log.info("[{}] Successfully updated metadata status to COMPLETED and linked summaryId {}.",
                                        metadataId, summaryId);

                        Cache userMetadataCache = cacheManager.getCache(CACHE_METADATA_BY_USER);
                        if (userMetadataCache != null && userId != null) {
                                log.info("[{}] Evicting cache entry for user {} from cache: {}", metadataId,
                                        userId, CACHE_METADATA_BY_USER);
                                userMetadataCache.evict(userId);
                        } else if (userId == null) {
                                log.warn("[{}] Cannot evict user cache in finalizeProcessing because userId is null.", metadataId);
                        } else {
                                log.warn("[{}] Cache '{}' not found for eviction during finalization.", metadataId,
                                        CACHE_METADATA_BY_USER);
                        }

                } catch (Exception e) {
                        log.error("[{}] CRITICAL: Failed to finalize metadata status update for user {}. Error: {}",
                                        metadataId, userId, e.getMessage(), e);
                        if (e instanceof InterruptedException)
                                Thread.currentThread().interrupt();
                }
        }

        private void triggerRecommendations(String userId, String recordingId, String summaryId) {
                if (learningMaterialRecommenderService == null) {
                        log.error("[{}] LearningMaterialRecommenderService is null. Cannot trigger recommendations.",
                                        recordingId);
                        updateMetadataStatusToFailed(recordingId, userId,
                                        "Internal server error: Recommendation service unavailable.");
                        return;
                }

                log.info("[{}] Triggering recommendation generation for user {}...", recordingId,
                                userId);
                try {
                        learningMaterialRecommenderService.generateAndSaveRecommendations(userId,
                                        recordingId, summaryId);
                        log.info("[{}] Recommendation generation process triggered successfully.",
                                        recordingId);
                } catch (Exception e) {
                        log.error("[{}] Failed to trigger recommendation generation: {}",
                                        recordingId, e.getMessage(), e);
                }
        }

        private String createSummarizationPrompt(Recording recording) {
                String titleClause = "";
                if (recording.getTitle() != null && !recording.getTitle().isBlank()) {
                        titleClause = " titled '" + recording.getTitle() + "'";
                }
                return """
                                Analyze the following lecture transcript%s.
                                Generate a concise, well-structured summary using Markdown in the `summaryText` field. Use headings (##) for main sections and bullet points (* or -) for details. Focus on core arguments, findings, definitions, and conclusions.
                                Identify the main key points or action items discussed and list them as distinct strings in the `keyPoints` array.
                                List the 3-5 most important topics or keywords suitable for searching related content in the `topics` array.
                                Identify important **terms, concepts, acronyms, proper nouns (people, places, organizations mentioned), and technical vocabulary** discussed in the transcript. For each, provide a concise definition relevant to the transcript's context. Structure this as an array of objects in the `glossary` field, where each object has a `term` (string) and a `definition` (string). Aim for comprehensive coverage of potentially unfamiliar items for a learner.
                                Ensure the entire output strictly adheres to the provided JSON schema. Output only the JSON object.
                                """
                                .formatted(titleClause);
        }

        private boolean isErrorResponse(String response) {
                if (response == null || response.isBlank()) {
                        return true;
                }
                if (response.trim().startsWith("{\"error\":")) {
                        try {
                                JsonNode node = objectMapper.readTree(response);
                                return node.has("error");
                        } catch (JsonProcessingException e) {
                                log.warn("Could not parse potential error JSON: {}", response, e);
                                return true;
                        }
                }
                return false;
        }


        private List<String> parseStringList(JsonNode parentNode, String fieldName,
                        String recordingId) {
                if (parentNode.hasNonNull(fieldName) && parentNode.path(fieldName).isArray()) {
                        try {
                                return objectMapper.convertValue(parentNode.path(fieldName),
                                                new TypeReference<List<String>>() {});
                        } catch (IllegalArgumentException e) {
                                log.warn("[{}] Failed to parse '{}' field as List<String>. Proceeding with empty list. Error: {}",
                                                recordingId, fieldName, e.getMessage());
                                return Collections.emptyList();
                        }
                } else {
                        log.warn("[{}] Gemini JSON response missing '{}' array field or not an array. Proceeding with empty list.",
                                        recordingId, fieldName);
                        return Collections.emptyList();
                }
        }

        private List<Map<String, String>> parseGlossary(JsonNode parentNode, String recordingId) {
                if (parentNode.hasNonNull("glossary") && parentNode.path("glossary").isArray()) {
                        try {
                                List<Map<String, String>> glossary = objectMapper.convertValue(
                                                parentNode.path("glossary"),
                                                new TypeReference<List<Map<String, String>>>() {});
                                glossary.removeIf(item -> {
                                        boolean invalid = !item.containsKey("term")
                                                        || !item.containsKey("definition")
                                                        || !(item.get("term") instanceof String)
                                                        || !(item.get("definition") instanceof String)
                                                        || ((String) item.get("term")).isBlank()
                                                        || ((String) item.get("definition"))
                                                                        .isBlank();
                                        if (invalid) {
                                                log.warn("[{}] Invalid or incomplete glossary item structure found: {}. Skipping item.",
                                                                recordingId, item);
                                        }
                                        return invalid;
                                });
                                log.info("[{}] Successfully parsed glossary with {} valid items.",
                                                recordingId, glossary.size());
                                return glossary;
                        } catch (IllegalArgumentException e) {
                                log.warn("[{}] Failed to parse 'glossary' field, likely due to incorrect item structure. Proceeding with empty glossary. Error: {}",
                                                recordingId, e.getMessage());
                                return Collections.emptyList();
                        }
                } else {
                        log.info("[{}] Gemini JSON response missing 'glossary' array field or not an array. Proceeding with empty glossary.",
                                        recordingId);
                        return Collections.emptyList();
                }
        }

        private void updateMetadataStatusToFailed(String metadataId, @Nullable String userId, String reason) {
                updateMetadataStatus(metadataId, userId, ProcessingStatus.FAILED, reason);
        }

        private void updateMetadataStatus(String metadataId, @Nullable String userId, ProcessingStatus status, String reason) {
                try {
                        log.info("Updating metadata {} status to {} with reason: {}", metadataId, status, reason);
                        firebaseService.updateAudioMetadataStatusAndReason(metadataId, userId, status, reason);
                        // Invalidate relevant cache entries if necessary
                        Cache userCache = cacheManager.getCache(CACHE_METADATA_BY_USER);
                        if (userCache != null) {
                                // We need the userId associated with metadataId to clear the correct cache entry.
                                // This might require fetching the metadata first, or passing userId down.
                                // For now, we might have to clear all user caches if we don't have userId readily available.
                                // Or, enhance FirebaseService to return the userId when fetching by metadataId if needed here.
                                // Let's assume FirebaseService handles necessary cache invalidation or we accept potential staleness.
                                log.warn("Cache invalidation for metadata update might require userId. Consider enhancing logic if stale data becomes an issue.");
                                // Example (if userId was available): userCache.evict(userId);
                        }
                } catch (Exception e) {
                        log.error("CRITICAL: Failed to update metadata {} status to {} for user {} after processing decision. Reason: {}. Error: {}",
                                  metadataId, status, userId != null ? userId : "<unknown>", reason, e.getMessage(), e);
                        if(e instanceof InterruptedException) Thread.currentThread().interrupt();
                        // Logged, but continue execution if possible (e.g., if returning after update)
                }
        }

        private String extractNhostIdFromUrl(String url) {
                if (url == null)
                        return null;
                try {
                        String[] parts = url.split("/");
                        if (parts.length > 2 && "files".equals(parts[parts.length - 2])) {
                                String potentialId = parts[parts.length - 1];
                                if (potentialId.matches("[a-zA-Z0-9\\-]+")) {
                                        log.debug("Extracted Nhost ID '{}' from URL '{}'",
                                                        potentialId, url);
                                        return potentialId;
                                }
                        }
                } catch (Exception e) {
                        log.error("Failed to parse Nhost ID from URL '{}': {}", url,
                                        e.getMessage());
                }
                log.warn("Could not extract Nhost ID using expected pattern from URL: {}", url);
                return null;
        }

        /**
         * Checks if the transcript is overly repetitive based on unique word ratio.
         * @param words Array of words extracted from the transcript.
         * @return true if the transcript is deemed too repetitive, false otherwise.
         */
        private boolean isTranscriptTooRepetitive(String[] words) {
                if (words == null || words.length == 0) {
                        return false; // Cannot determine, assume not repetitive
                }
                int totalWords = words.length;
                Set<String> uniqueWords = new HashSet<>(Arrays.asList(words));
                int uniqueWordCount = uniqueWords.size();

                // Avoid division by zero for very short inputs handled by length check
                if (totalWords == 0) return false;

                double uniqueRatio = (double) uniqueWordCount / totalWords;
                log.debug("Repetition check: {} unique words / {} total words = ratio {}", uniqueWordCount, totalWords, String.format("%.3f", uniqueRatio));

                return uniqueRatio < MIN_UNIQUE_WORD_RATIO;
        }

}

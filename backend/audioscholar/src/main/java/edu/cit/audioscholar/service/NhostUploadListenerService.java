package edu.cit.audioscholar.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import edu.cit.audioscholar.config.RabbitMQConfig;
import edu.cit.audioscholar.dto.AudioProcessingMessage;
import edu.cit.audioscholar.dto.NhostUploadMessage;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.ProcessingStatus;

@Service
public class NhostUploadListenerService {

        private static final Logger log = LoggerFactory.getLogger(NhostUploadListenerService.class);

        private final FirebaseService firebaseService;
        private final NhostStorageService nhostStorageService;
        private final RabbitTemplate rabbitTemplate;
        private final ObjectMapper objectMapper;
        private final Map<String, Lock> metadataLocks = new HashMap<>();

        public NhostUploadListenerService(FirebaseService firebaseService,
                        NhostStorageService nhostStorageService, RabbitTemplate rabbitTemplate,
                        ObjectMapper objectMapper) {
                this.firebaseService = firebaseService;
                this.nhostStorageService = nhostStorageService;
                this.rabbitTemplate = rabbitTemplate;
                this.objectMapper = objectMapper;
        }

        @RabbitListener(queues = RabbitMQConfig.UPLOAD_QUEUE_NAME)
        public void handleNhostUploadRequest(NhostUploadMessage message) {
                if (message == null || message.getMetadataId() == null
                                || message.getFileType() == null
                                || message.getTempFilePath() == null
                                || message.getOriginalFilename() == null
                                || message.getOriginalContentType() == null) {
                        log.error("[Nhost Upload Listener] Received invalid message (null or missing fields): {}",
                                        message);
                        return;
                }

                String metadataId = message.getMetadataId();
                String fileType = message.getFileType();
                String tempFilePathStr = message.getTempFilePath();
                String originalFilename = message.getOriginalFilename();
                String originalContentType = message.getOriginalContentType();
                Path tempFilePath = null;

                if (!StringUtils.hasText(tempFilePathStr)) {
                        log.error("[Nhost Upload Listener] Received message for metadata {} ({}) with missing tempFilePath. Cannot process.",
                                        metadataId, fileType);
                        return;
                }
                tempFilePath = Paths.get(tempFilePathStr);

                log.info("[Nhost Upload Listener] Received request for metadataId: {}, fileType: {}, tempPath: {}",
                                metadataId, fileType, tempFilePathStr);

                if (!Files.exists(tempFilePath) || !Files.isReadable(tempFilePath)) {
                        log.error("[Nhost Upload Listener] Temporary file for '{}' does not exist or cannot be read: {}. Setting to FAILED.",
                                        fileType, tempFilePathStr);
                        updateStatus(metadataId, null, ProcessingStatus.FAILED,
                                        "Temporary file missing/unreadable before upload: "
                                                        + tempFilePath.getFileName());
                        return;
                }

                AudioMetadata metadata = null;
                String userId = null;
                boolean isAudio = fileType.equalsIgnoreCase("audio");
                boolean isPptx = fileType.equalsIgnoreCase("powerpoint");

                if (!isAudio && !isPptx) {
                        log.error("[Nhost Upload Listener] Received message for metadataId: {} with unknown fileType: {}. Discarding.",
                                        metadataId, fileType);
                        deleteTempFileHelper(tempFilePathStr, metadataId, "unknown");
                        return;
                }

                Lock lock = metadataLocks.computeIfAbsent(metadataId, k -> new ReentrantLock());
                lock.lock();
                log.debug("Acquired lock for metadataId {}", metadataId);

                try {
                        metadata = firebaseService.getAudioMetadataById(metadataId);
                        if (metadata == null) {
                                log.error("[Nhost Upload Listener] Metadata not found for ID: {} after acquiring lock. Cannot process upload message.",
                                                metadataId);
                                deleteTempFileHelper(tempFilePathStr, metadataId, fileType);
                                return;
                        }
                        userId = metadata.getUserId();
                        log.info("[{}] Found metadata. Current status: {}. User: {}", metadataId,
                                        metadata.getStatus(), userId);

                        if (metadata.getStatus() != ProcessingStatus.UPLOAD_IN_PROGRESS) {
                                log.warn("[{}] Metadata status is not UPLOAD_IN_PROGRESS (it's {}). Skipping Nhost upload for this message.",
                                                metadataId, metadata.getStatus());
                                deleteTempFileHelper(tempFilePathStr, metadataId, fileType);
                                return;
                        }

                        if (isAudio && StringUtils.hasText(metadata.getNhostFileId())) {
                                log.warn("[Nhost Upload Listener] Received audio upload message for {}, but audio Nhost ID {} already exists. Skipping.",
                                                metadataId, metadata.getNhostFileId());
                                deleteTempFileHelper(tempFilePathStr, metadataId, fileType);
                                return;
                        }
                        if (isPptx && StringUtils.hasText(metadata.getNhostPptxFileId())) {
                                log.warn("[Nhost Upload Listener] Received pptx upload message for {}, but pptx Nhost ID {} already exists. Skipping.",
                                                metadataId, metadata.getNhostPptxFileId());
                                deleteTempFileHelper(tempFilePathStr, metadataId, fileType);
                                return;
                        }

                        File fileToUpload = tempFilePath.toFile();
                        String nhostFileId = null;
                        String publicUrl = null;

                        try {
                                log.info("[{}] Attempting to upload file {} to Nhost Storage with content type {}...",
                                                metadataId, originalFilename, originalContentType);
                                nhostFileId = nhostStorageService.uploadFile(fileToUpload,
                                                originalFilename, originalContentType);
                                log.info("[{}] File uploaded successfully to Nhost. File ID: {}",
                                                metadataId, nhostFileId);

                                if (isAudio && nhostFileId != null) {
                                        publicUrl = nhostStorageService.getPublicUrl(nhostFileId);
                                        log.info("[Nhost Upload Listener] Nhost Public URL for audio: {}",
                                                        publicUrl);
                                }
                        } catch (IOException e) {
                                log.error("[{}] IOException during Nhost upload processing for temp file {}. Error: {}",
                                                metadataId, tempFilePathStr, e.getMessage(), e);
                                updateStatus(metadataId, userId, ProcessingStatus.FAILED,
                                                "Nhost upload failed: " + e.getMessage());
                                deleteTempFileHelper(tempFilePathStr, metadataId, fileType);
                                return;
                        }

                        updateMetadataAfterUpload(metadata, nhostFileId, isAudio);

                        checkUploadCompletionAndTriggerProcessing(metadata);

                        deleteTempFileHelper(tempFilePathStr, metadataId, fileType);

                } catch (Exception e) {
                        log.error("[Nhost Upload Listener] Unexpected error processing upload for metadataId {}: {}",
                                        metadataId, e.getMessage(), e);
                        updateStatus(metadataId, userId, ProcessingStatus.FAILED,
                                        "Unexpected error during upload handling: "
                                                        + e.getMessage());
                        if (tempFilePathStr != null) {
                                deleteTempFileHelper(tempFilePathStr, metadataId, fileType);
                        }
                } finally {
                        lock.unlock();
                        log.debug("Released lock for metadataId {}", metadataId);
                }
        }

        private boolean triggerParallelProcessing(String metadataId, boolean pptxExpected) {
                String userId = null;
                AudioMetadata metadata = firebaseService.getAudioMetadataById(metadataId);
                if (metadata == null) {
                        log.error("Cannot trigger processing, metadata {} not found.", metadataId);
                        return false;
                }
                userId = metadata.getUserId();

                boolean transcriptionSent = false;
                boolean pptxConversionSent = false;

                AudioProcessingMessage transcriptionMessage =
                                new AudioProcessingMessage(metadataId, userId, metadataId);
                sendMessage(metadataId, RabbitMQConfig.PROCESSING_EXCHANGE_NAME,
                                RabbitMQConfig.TRANSCRIPTION_ROUTING_KEY, transcriptionMessage,
                                "transcription queue");
                transcriptionSent = true;

                if (pptxExpected) {
                        AudioProcessingMessage pptxMessage =
                                        new AudioProcessingMessage(metadataId, userId, metadataId);
                        sendMessage(metadataId, RabbitMQConfig.PROCESSING_EXCHANGE_NAME,
                                        RabbitMQConfig.PPTX_CONVERSION_ROUTING_KEY, pptxMessage,
                                        "PPTX conversion queue");
                        pptxConversionSent = true;
                }

                return transcriptionSent && (!pptxExpected || pptxConversionSent);
        }

        private void updateStatus(String metadataId, @Nullable String userId,
                        ProcessingStatus status, @Nullable String reason) {
                try {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("status", status.name());
                        updates.put("lastUpdated", Timestamp.now());
                        if (status == ProcessingStatus.FAILED) {
                                updates.put("failureReason",
                                                reason != null ? reason : "Unknown Failure");
                                log.error("[{}] Setting status to FAILED. Reason: {}", metadataId,
                                                updates.get("failureReason"));
                        } else {
                                updates.put("failureReason", null);
                        }
                        firebaseService.updateDataWithMap(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, updates);
                        log.info("[{}] Metadata status updated to {}.", metadataId, status);

                        if (status == ProcessingStatus.FAILED
                                        || status == ProcessingStatus.PROCESSING_QUEUED
                                        || status == ProcessingStatus.UPLOADED) {
                                invalidateUserCache(userId);
                        }
                } catch (FirestoreInteractionException e) {
                        log.error("[{}] CRITICAL: Failed to update metadata status to {}. Error: {}",
                                        metadataId, status, e.getMessage(), e);
                }
        }

        private void sendMessage(String metadataId, String exchange, String routingKey,
                        Object messagePayload, String messageDescription) {
                try {
                        rabbitTemplate.convertAndSend(exchange, routingKey, messagePayload);
                        log.info("Sent message ({}) for metadataId {} to exchange '{}' with key '{}'",
                                        messageDescription, metadataId, exchange, routingKey);
                } catch (AmqpException e) {
                        log.error("Failed to send message ({}) for metadataId {} to exchange '{}' key '{}': {}",
                                        messageDescription, metadataId, exchange, routingKey,
                                        e.getMessage(), e);
                        throw e;
                }
        }

        private void deleteTempFileHelper(@Nullable String tempFilePathStr, String metadataId,
                        String fileType) {
                if (!StringUtils.hasText(tempFilePathStr))
                        return;
                Path tempPath = Paths.get(tempFilePathStr);
                try {
                        if (Files.exists(tempPath)) {
                                Files.delete(tempPath);
                                log.info("Cleaned up temporary {} file {} for metadataId {}",
                                                fileType, tempPath, metadataId);
                        }
                } catch (IOException ioException) {
                        log.warn("Could not delete temporary {} file {} for metadataId {}: {}",
                                        fileType, tempPath, metadataId, ioException.getMessage());
                }
        }

        private void updateMetadataAfterUpload(AudioMetadata metadata, String nhostFileId,
                        boolean isAudio) {
                String metadataId = metadata.getId();
                String userId = metadata.getUserId();
                Map<String, Object> updates = new HashMap<>();
                String fieldName = isAudio ? "nhostFileId" : "nhostPptxFileId";
                updates.put(fieldName, nhostFileId);

                if (isAudio) {
                        updates.put("audioUploadComplete", true);
                        metadata.setAudioUploadComplete(true);
                        metadata.setNhostFileId(nhostFileId);

                        if (!StringUtils.hasText(metadata.getOriginalPptxFileName())) {
                                updates.put("audioOnly", true);
                                metadata.setAudioOnly(true);
                                log.info("[{}] Marking upload as audio-only (no PowerPoint file detected)",
                                                metadataId);
                        }

                        String publicUrl = nhostStorageService.getPublicUrl(nhostFileId);
                        if (publicUrl != null) {
                                updates.put("audioUrl", publicUrl);
                                metadata.setStorageUrl(publicUrl);
                                try {
                                        Map<String, Object> recordingUpdates = new HashMap<>();
                                        recordingUpdates.put("audioUrl", publicUrl);
                                        if (StringUtils.hasText(metadata.getFileName())) {
                                                recordingUpdates.put("fileName",
                                                                metadata.getFileName());
                                        }
                                        firebaseService.updateDataWithMap("recordings", metadataId,
                                                        recordingUpdates);
                                        log.info("[{}] Updated Recording document {} with audioUrl: {}",
                                                        metadataId, metadataId, publicUrl);
                                } catch (FirestoreInteractionException e) {
                                        log.error("[{}] Failed to update Recording document {} with audioUrl: {}",
                                                        metadataId, metadataId, e.getMessage(), e);
                                }
                        }
                } else {
                        updates.put("pdfConversionComplete", false);
                        metadata.setPdfConversionComplete(false);
                        metadata.setNhostPptxFileId(nhostFileId);

                        if (StringUtils.hasText(metadata.getOriginalPptxFileName())) {
                                try {
                                        Map<String, Object> recordingUpdates = new HashMap<>();
                                        recordingUpdates.put("pptxFileName",
                                                        metadata.getOriginalPptxFileName());
                                        firebaseService.updateDataWithMap("recordings", metadataId,
                                                        recordingUpdates);
                                } catch (FirestoreInteractionException e) {
                                        log.error("[{}] Failed to update Recording document {} with pptxFileName: {}",
                                                        metadataId, metadataId, e.getMessage(), e);
                                }
                        }

                        if (metadata.isWaitingForPdf()) {
                                updates.put("waitingForPdf", false);
                                metadata.setWaitingForPdf(false);
                                log.info("[{}] Resetting waitingForPdf flag as PowerPoint file is now uploaded",
                                                metadataId);
                        }
                }

                updates.put("lastUpdated", Timestamp.now());
                metadata.setLastUpdated(Timestamp.now());

                try {
                        firebaseService.updateDataWithMap(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, updates);
                        log.info("[{}] Successfully updated metadata for {} file upload.",
                                        metadataId, isAudio ? "audio" : "PowerPoint");

                        if (!isAudio) {
                                log.info("[{}] PowerPoint metadata updated. Sending message to PPTX conversion queue.",
                                                metadataId);
                                AudioProcessingMessage conversionMessage =
                                                new AudioProcessingMessage();
                                conversionMessage.setMetadataId(metadataId);
                                conversionMessage.setUserId(userId);

                                try {
                                        rabbitTemplate.convertAndSend(
                                                        RabbitMQConfig.PROCESSING_EXCHANGE_NAME,
                                                        RabbitMQConfig.PPTX_CONVERSION_ROUTING_KEY,
                                                        conversionMessage);
                                        log.info("[{}] Message sent to queue {} with routing key {}",
                                                        metadataId,
                                                        RabbitMQConfig.PPTX_CONVERSION_QUEUE_NAME,
                                                        RabbitMQConfig.PPTX_CONVERSION_ROUTING_KEY);
                                } catch (Exception e) {
                                        log.error("[{}] Failed to send message to PPTX conversion queue: {}",
                                                        metadataId, e.getMessage(), e);
                                }
                        }

                } catch (FirestoreInteractionException e) {
                        log.error("[{}] Failed to update metadata for {} file upload: {}",
                                        metadataId, isAudio ? "audio" : "PowerPoint",
                                        e.getMessage(), e);
                        updateStatus(metadataId, userId, ProcessingStatus.FAILED,
                                        "Failed to update metadata after Nhost upload");
                }
        }

        private void checkUploadCompletionAndTriggerProcessing(AudioMetadata latestMetadata) {
                if (latestMetadata == null) {
                        log.error("[Completion Check] Received null metadata. Cannot proceed.");
                        return;
                }
                String metadataId = latestMetadata.getId();
                ProcessingStatus currentStatus = latestMetadata.getStatus();

                if (currentStatus != ProcessingStatus.UPLOAD_IN_PROGRESS
                                && currentStatus != ProcessingStatus.UPLOAD_PENDING) {
                        log.info("[Completion Check - {}] Status is already {}. No action needed.",
                                        metadataId, currentStatus);
                        return;
                }

                boolean audioUploadDone = StringUtils.hasText(latestMetadata.getNhostFileId());
                boolean pptxUploadRequired =
                                StringUtils.hasText(latestMetadata.getOriginalPptxFileName());
                boolean pptxProcessingDone =
                                !pptxUploadRequired || latestMetadata.isPdfConversionComplete();

                boolean allProcessingReady = audioUploadDone && pptxProcessingDone;

                log.info("[Completion Check - {}] Details: AudioDone={}, PptxRequired={}, PptxConvDone={}, AllReadyForNext={}",
                                metadataId, audioUploadDone, pptxUploadRequired,
                                latestMetadata.isPdfConversionComplete(), allProcessingReady);

                if (allProcessingReady) {
                        log.info("[Completion Check - {}] All required uploads/conversions complete. Triggering next steps (Transcription) and setting status to PROCESSING_QUEUED.",
                                        metadataId);

                        updateStatus(metadataId, latestMetadata.getUserId(),
                                        ProcessingStatus.PROCESSING_QUEUED, null);

                        AudioProcessingMessage transcriptionMessage = new AudioProcessingMessage();
                        transcriptionMessage.setMetadataId(metadataId);
                        transcriptionMessage.setUserId(latestMetadata.getUserId());

                        try {
                                rabbitTemplate.convertAndSend(
                                                RabbitMQConfig.PROCESSING_EXCHANGE_NAME,
                                                RabbitMQConfig.TRANSCRIPTION_ROUTING_KEY,
                                                transcriptionMessage);
                                log.info("Sent message (transcription queue) for metadataId {} to exchange '{}' with key '{}'",
                                                metadataId, RabbitMQConfig.PROCESSING_EXCHANGE_NAME,
                                                RabbitMQConfig.TRANSCRIPTION_ROUTING_KEY);
                        } catch (Exception e) {
                                log.error("[{}] Failed to send message to transcription queue: {}",
                                                metadataId, e.getMessage(), e);
                                updateStatus(metadataId, latestMetadata.getUserId(),
                                                ProcessingStatus.FAILED,
                                                "Failed to queue transcription");
                        }

                } else {
                        log.info("[Completion Check - {}] Not all uploads/conversions complete yet. Current status: {}. Waiting...",
                                        metadataId, currentStatus);
                }
        }

        private void invalidateUserCache(@Nullable String userId) {}
}

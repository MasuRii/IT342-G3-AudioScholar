package edu.cit.audioscholar.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import edu.cit.audioscholar.config.RabbitMQConfig;
import edu.cit.audioscholar.dto.AudioProcessingMessage;
import edu.cit.audioscholar.dto.NhostUploadMessage;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.ProcessingStatus;
import edu.cit.audioscholar.model.Recording;
import org.springframework.lang.Nullable;

@Service
public class NhostUploadListenerService {

        private static final Logger log = LoggerFactory.getLogger(NhostUploadListenerService.class);

        private final FirebaseService firebaseService;
        private final NhostStorageService nhostStorageService;
        private final RecordingService recordingService;
        private final RabbitTemplate rabbitTemplate;

        public NhostUploadListenerService(FirebaseService firebaseService,
                        NhostStorageService nhostStorageService,
                        @Lazy RecordingService recordingService, RabbitTemplate rabbitTemplate) {
                this.firebaseService = firebaseService;
                this.nhostStorageService = nhostStorageService;
                this.recordingService = recordingService;
                this.rabbitTemplate = rabbitTemplate;
        }

        @RabbitListener(queues = RabbitMQConfig.UPLOAD_QUEUE_NAME)
        public void handleNhostUploadRequest(NhostUploadMessage message) {
                if (message == null || message.getMetadataId() == null
                                || message.getRecordingId() == null) {
                        log.error("[Nhost Upload Listener] Received invalid message: {}", message);
                        return;
                }

                String metadataId = message.getMetadataId();
                String recordingId = message.getRecordingId();
                log.info("[Nhost Upload Listener] Received request for metadataId: {}, recordingId: {}",
                                metadataId, recordingId);

                AudioMetadata metadata = null;
                String userId = null;
                File tempFile = null;
                String tempFilePath = null;

                try {
                        Map<String, Object> metaMap = firebaseService.getData(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId);
                        metadata = AudioMetadata.fromMap(metaMap);

                        if (metadata == null) {
                                log.error("[Nhost Upload Listener] Metadata not found for ID: {}. Cannot process.",
                                                metadataId);
                                return;
                        }

                        userId = metadata.getUserId();
                        tempFilePath = metadata.getTempFilePath();

                        if (metadata.getStatus() != ProcessingStatus.UPLOAD_PENDING) {
                                log.warn("[Nhost Upload Listener] Metadata {} status is not UPLOAD_PENDING (it's {}). Skipping upload.",
                                                metadataId, metadata.getStatus());
                                if (tempFilePath != null) {
                                        deleteTempFile(tempFilePath, metadataId);
                                }
                                return;
                        }

                        if (tempFilePath == null || tempFilePath.isBlank()) {
                                log.error("[Nhost Upload Listener] Metadata {} has status UPLOAD_PENDING but tempFilePath is missing. Cannot proceed.",
                                                metadataId);
                                firebaseService.updateAudioMetadataStatusAndReason(metadataId, userId, ProcessingStatus.FAILED,
                                                "Temporary file path missing in metadata.");
                                return;
                        }

                        tempFile = new File(tempFilePath);
                        if (!tempFile.exists() || !tempFile.canRead()) {
                                log.error("[Nhost Upload Listener] Temporary file does not exist or cannot be read: {}. Cannot proceed.",
                                                tempFilePath);
                                firebaseService.updateAudioMetadataStatusAndReason(metadataId, userId, ProcessingStatus.FAILED,
                                                "Temporary file missing or unreadable: " + tempFilePath);
                                deleteTempFile(tempFilePath, metadataId);
                                return;
                        }

                        firebaseService.updateAudioMetadataStatusAndReason(metadataId, userId, ProcessingStatus.UPLOADING_TO_STORAGE,
                                null);
                        log.info("[Nhost Upload Listener] Updated status for metadata {} to UPLOADING_TO_STORAGE.", metadataId);

                        log.info("[Nhost Upload Listener] Starting Nhost upload for temp file: {}",
                                        tempFilePath);
                        String nhostFileId = nhostStorageService.uploadFile(tempFile,
                                        metadata.getFileName(), metadata.getContentType());
                        String storageUrl = nhostStorageService.getPublicUrl(nhostFileId);
                        log.info("[Nhost Upload Listener] Nhost upload successful. File ID: {}, URL: {}",
                                        nhostFileId, storageUrl);

                        Map<String, Object> currentMetaMap = firebaseService.getData(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId);
                        AudioMetadata currentMetadata = AudioMetadata.fromMap(currentMetaMap);
                        if (currentMetadata == null) {
                                log.error("[Nhost Upload Listener] CRITICAL: Metadata {} disappeared after successful Nhost upload. Manual intervention needed.",
                                                metadataId);
                                throw new IllegalStateException("Metadata " + metadataId
                                                + " disappeared after successful Nhost upload.");
                        }

                        String currentUserId = currentMetadata.getUserId();
                        if (currentUserId == null) {
                                log.error("[Nhost Upload Listener] CRITICAL: User ID is null in metadata {} just before creating Recording.", metadataId);
                                firebaseService.updateAudioMetadataStatusAndReason(metadataId, null, ProcessingStatus.FAILED,
                                        "User ID missing in metadata before Recording creation.");
                                throw new IllegalStateException("User ID missing for metadata " + metadataId);
                        }

                        Recording recording = new Recording();
                        recording.setRecordingId(recordingId);
                        recording.setUserId(currentUserId);
                        recording.setAudioUrl(storageUrl);
                        recording.setFileName(currentMetadata.getFileName());
                        recording.setTitle(currentMetadata.getTitle());
                        recordingService.createRecording(recording);
                        log.info("[Nhost Upload Listener] Recording document {} created and linked to user {}.",
                                        recordingId, currentUserId);

                        Map<String, Object> finalMetaUpdates = new HashMap<>();
                        finalMetaUpdates.put("nhostFileId", nhostFileId);
                        finalMetaUpdates.put("storageUrl", storageUrl);
                        finalMetaUpdates.put("tempFilePath", null);
                        finalMetaUpdates.put("failureReason", null);
                        finalMetaUpdates.put("lastUpdated", com.google.cloud.Timestamp.now());

                        firebaseService.updateDataWithMap(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, finalMetaUpdates);
                        log.info("[Nhost Upload Listener] Metadata {} updated with Nhost details.",
                                        metadataId);

                        firebaseService.updateAudioMetadataStatusAndReason(metadataId, currentUserId, ProcessingStatus.PENDING, null);
                        log.info("[Nhost Upload Listener] Metadata {} status updated to PENDING.", metadataId);

                        AudioProcessingMessage nextMessage = new AudioProcessingMessage(recordingId,
                                        currentUserId, metadataId);
                        try {
                                log.info("[Nhost Upload Listener] Sending message to processing queue '{}' for metadataId: {}",
                                                RabbitMQConfig.PROCESSING_QUEUE_NAME, metadataId);
                                rabbitTemplate.convertAndSend(
                                                RabbitMQConfig.PROCESSING_EXCHANGE_NAME,
                                                RabbitMQConfig.PROCESSING_ROUTING_KEY, nextMessage);
                                log.info("[Nhost Upload Listener] Successfully sent message to processing queue for metadataId: {}",
                                                metadataId);
                        } catch (AmqpException e) {
                                log.error("[Nhost Upload Listener] CRITICAL: Failed to send message to processing queue '{}' for metadataId {}. Manual intervention likely needed to trigger processing. Error: {}",
                                                RabbitMQConfig.PROCESSING_QUEUE_NAME, metadataId,
                                                e.getMessage(), e);
                                firebaseService.updateAudioMetadataStatusAndReason(metadataId, currentUserId, ProcessingStatus.FAILED,
                                        "Upload successful, but failed to queue for processing.");
                        }

                        deleteTempFile(tempFilePath, metadataId);

                } catch (Exception e) {
                        log.error("[Nhost Upload Listener] Error processing upload for metadataId {}: {}",
                                        metadataId, e.getMessage(), e);

                        String failureReason = "Upload/Processing failed: " + e.getMessage();
                        try {
                                firebaseService.updateAudioMetadataStatusAndReason(metadataId, userId, ProcessingStatus.FAILED, failureReason);
                        } catch (Exception updateEx) {
                                log.error("[Nhost Upload Listener] Further error setting status to FAILED for {}: {}", metadataId, updateEx.getMessage());
                        }
                        deleteTempFile(tempFilePath, metadataId);

                        if (e instanceof RuntimeException) {
                                throw (RuntimeException) e;
                        } else {
                                throw new RuntimeException(
                                                "Processing failed for message: " + message, e);
                        }
                }
        }

        private void deleteTempFile(String tempFilePath, String metadataId) {
                if (tempFilePath != null && !tempFilePath.isBlank()) {
                        try {
                                boolean deleted = Files.deleteIfExists(Paths.get(tempFilePath));
                                if (deleted) {
                                        log.info("[Nhost Upload Listener] Deleted temporary file: {}",
                                                        tempFilePath);
                                } else {
                                        log.debug("[Nhost Upload Listener] Temporary file not found for deletion (already deleted?): {}", tempFilePath);
                                }
                        } catch (IOException e) {
                                log.warn("[Nhost Upload Listener] Failed to delete temporary file for metadata {}: {}. Error: {}",
                                        metadataId, tempFilePath, e.getMessage());
                        }
                } else {
                        log.debug("[Nhost Upload Listener] No temporary file path provided for deletion (metadataId: {}).",
                                        metadataId);
                }
        }
}

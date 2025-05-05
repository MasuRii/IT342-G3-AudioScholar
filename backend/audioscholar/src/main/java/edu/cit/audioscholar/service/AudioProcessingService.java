package edu.cit.audioscholar.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.FieldValue;
import edu.cit.audioscholar.config.RabbitMQConfig;
import edu.cit.audioscholar.dto.NhostUploadMessage;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.exception.InvalidAudioFileException;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.ProcessingStatus;

@Service
public class AudioProcessingService {

        private static final Logger log = LoggerFactory.getLogger(AudioProcessingService.class);
        private static final String CACHE_METADATA_BY_ID = "audioMetadataById";
        private static final String CACHE_METADATA_BY_USER = "audioMetadataByUser";

        private final FirebaseService firebaseService;
        private final RabbitTemplate rabbitTemplate;
        private final NhostStorageService nhostStorageService;
        private final LearningMaterialRecommenderService learningMaterialRecommenderService;
        private final RecordingService recordingService;
        private final String maxFileSizeValue;
        private final Path tempFileDir;
        private final CacheManager cacheManager;
        private final ObjectMapper objectMapper;

        public AudioProcessingService(FirebaseService firebaseService,
                        RabbitTemplate rabbitTemplate, NhostStorageService nhostStorageService,
                        LearningMaterialRecommenderService learningMaterialRecommenderService,
                        RecordingService recordingService,
                        @Value("${spring.servlet.multipart.max-file-size}") String maxFileSizeValue,
                        @Value("${app.temp-file-dir}") String tempFileDirStr,
                        CacheManager cacheManager, ObjectMapper objectMapper) {
                this.firebaseService = firebaseService;
                this.rabbitTemplate = rabbitTemplate;
                this.nhostStorageService = nhostStorageService;
                this.learningMaterialRecommenderService = learningMaterialRecommenderService;
                this.recordingService = recordingService;
                this.maxFileSizeValue = maxFileSizeValue;

                this.tempFileDir = Paths.get(tempFileDirStr);
                try {
                        Files.createDirectories(this.tempFileDir);
                        log.info("Temporary file directory set to: {}",
                                        this.tempFileDir.toAbsolutePath());
                } catch (IOException e) {
                        log.error("Could not create temporary file directory: {}",
                                        this.tempFileDir.toAbsolutePath(), e);
                        throw new RuntimeException("Failed to initialize temporary file directory",
                                        e);
                }

                this.cacheManager = cacheManager;
                this.objectMapper = objectMapper;
        }

        private long getMaxFileSizeInBytes() {
                return DataSize.parse(maxFileSizeValue).toBytes();
        }

        @Caching(evict = {@CacheEvict(value = CACHE_METADATA_BY_USER, allEntries = true)})
        public AudioMetadata queueFilesForUpload(MultipartFile audioFile,
                        @Nullable MultipartFile powerpointFile, @Nullable String title,
                        @Nullable String description, String userId) throws IOException,
                        InvalidAudioFileException, FirestoreInteractionException {

                log.info("Queueing files for upload: Audio: {}, PowerPoint: {}, Title: {}, User: {}",
                                audioFile.getOriginalFilename(),
                                (powerpointFile != null ? powerpointFile.getOriginalFilename()
                                                : "N/A"),
                                title, userId);

                validateMultipartFile(audioFile, "Audio", userId);
                if (powerpointFile != null && !powerpointFile.isEmpty()) {
                        validateMultipartFile(powerpointFile, "PowerPoint", userId);
                } else {
                        powerpointFile = null;
                }
                long maxBytes = getMaxFileSizeInBytes();
                if (audioFile.getSize() > maxBytes || (powerpointFile != null
                                && powerpointFile.getSize() > maxBytes)) {
                        log.warn("Validation failed for user {}: A file size exceeds limit of {} bytes.",
                                        userId, maxBytes);
                        throw new InvalidAudioFileException(
                                        "File size exceeds the maximum allowed limit ("
                                                        + maxFileSizeValue + ").");
                }

                Path tempAudioPath = null;
                Path tempPptxPath = null;
                AudioMetadata initialMetadata = null;

                try {
                        tempAudioPath = saveTemporaryFile(audioFile, "audio");
                        log.info("Audio file saved temporarily to: {}",
                                        tempAudioPath.toAbsolutePath());

                        if (powerpointFile != null) {
                                tempPptxPath = saveTemporaryFile(powerpointFile, "pptx");
                                log.info("PowerPoint file saved temporarily to: {}",
                                                tempPptxPath.toAbsolutePath());
                        }

                        String metadataId = UUID.randomUUID().toString();
                        log.info("Generated metadataId: {} for upload by user {}", metadataId,
                                        userId);

                        initialMetadata = new AudioMetadata();
                        initialMetadata.setId(metadataId);
                        initialMetadata.setUserId(userId);
                        initialMetadata.setUploadTimestamp(Timestamp.of(new Date()));
                        initialMetadata.setLastUpdated(Timestamp.of(new Date()));
                        initialMetadata.setStatus(ProcessingStatus.UPLOAD_PENDING);
                        initialMetadata.setTranscriptionComplete(false);
                        initialMetadata.setPdfConversionComplete(false);
                        initialMetadata.setRecordingId(metadataId);

                        String originalAudioFilename = StringUtils.cleanPath(
                                        Objects.requireNonNull(audioFile.getOriginalFilename(),
                                                        "Audio filename cannot be null"));
                        String originalAudioContentType = audioFile.getContentType();
                        initialMetadata.setFileName(originalAudioFilename);
                        initialMetadata.setFileSize(audioFile.getSize());
                        initialMetadata.setContentType(originalAudioContentType);
                        initialMetadata.setTempFilePath(tempAudioPath.toAbsolutePath().toString());

                        initialMetadata.setTitle(
                                        StringUtils.hasText(title) ? title : originalAudioFilename);
                        initialMetadata.setDescription(description);

                        String originalPptxFilename = null;
                        String originalPptxContentType = null;
                        if (powerpointFile != null) {
                                originalPptxFilename = StringUtils.cleanPath(Objects.requireNonNull(
                                                powerpointFile.getOriginalFilename()));
                                originalPptxContentType = powerpointFile.getContentType();
                                initialMetadata.setOriginalPptxFileName(originalPptxFilename);
                                initialMetadata.setPptxFileSize(powerpointFile.getSize());
                                initialMetadata.setPptxContentType(originalPptxContentType);
                                initialMetadata.setTempPptxFilePath(
                                                tempPptxPath.toAbsolutePath().toString());
                                initialMetadata.setAudioOnly(false);
                        } else {
                                initialMetadata.setAudioOnly(true);
                                initialMetadata.setPdfConversionComplete(true);
                                log.info("Audio-only upload detected for user {}. Setting audioOnly flag to true.",
                                                userId);
                        }

                        try {
                                Map<String, Object> recordingData = new HashMap<>();
                                recordingData.put("id", metadataId);
                                recordingData.put("userId", userId);
                                recordingData.put("title", initialMetadata.getTitle());
                                recordingData.put("description", initialMetadata.getDescription());
                                recordingData.put("fileSize", initialMetadata.getFileSize());
                                recordingData.put("contentType", initialMetadata.getContentType());
                                recordingData.put("createdAt", Timestamp.now());

                                firebaseService.saveData("recordings", metadataId, recordingData);
                                log.info("Created Recording document with ID: {} for user: {}",
                                                metadataId, userId);

                                firebaseService.saveData(
                                                firebaseService.getAudioMetadataCollectionName(),
                                                metadataId, initialMetadata.toMap());
                                log.info("Initial metadata (ID: {}) saved to Firestore with status UPLOAD_PENDING.",
                                                metadataId);
                        } catch (Exception e) {
                                log.error("Firestore error saving initial metadata for user {}: {}",
                                                userId, e.getMessage(), e);
                                deleteTemporaryFile(tempAudioPath);
                                deleteTemporaryFile(tempPptxPath);
                                throw new FirestoreInteractionException(
                                                "Failed to save initial metadata to database.", e);
                        }

                        try {
                                initialMetadata = updateMetadataStatus(metadataId, userId,
                                                ProcessingStatus.UPLOAD_IN_PROGRESS, null, true);

                                String audioTempPathStr = tempAudioPath.toAbsolutePath().toString();
                                sendUploadMessage(metadataId, "audio", audioTempPathStr,
                                                RabbitMQConfig.UPLOAD_AUDIO_ROUTING_KEY,
                                                originalAudioFilename, originalAudioContentType);

                                String pptxTempPathStr = null;
                                if (powerpointFile != null && tempPptxPath != null) {
                                        pptxTempPathStr = tempPptxPath.toAbsolutePath().toString();
                                        sendUploadMessage(metadataId, "powerpoint", pptxTempPathStr,
                                                        RabbitMQConfig.UPLOAD_PPTX_ROUTING_KEY,
                                                        originalPptxFilename,
                                                        originalPptxContentType);
                                }

                                try {
                                        Map<String, Object> updates = new HashMap<>();
                                        updates.put("status", ProcessingStatus.UPLOAD_IN_PROGRESS);
                                        updates.put("lastUpdated", Timestamp.of(new Date()));
                                        updates.put("tempFilePath", null);
                                        updates.put("tempPptxFilePath", null);

                                        firebaseService.updateData(firebaseService
                                                        .getAudioMetadataCollectionName(),
                                                        metadataId, updates);
                                        log.info("Updated metadata {} status to UPLOAD_IN_PROGRESS and cleared temp paths.",
                                                        metadataId);

                                        initialMetadata.setStatus(
                                                        ProcessingStatus.UPLOAD_IN_PROGRESS);
                                        initialMetadata.setTempFilePath(null);
                                        initialMetadata.setTempPptxFilePath(null);
                                } catch (Exception e) {
                                        log.error("Firestore error updating metadata {} status to UPLOAD_IN_PROGRESS for user {}: {}",
                                                        metadataId, userId, e.getMessage(), e);
                                        try {
                                                Map<String, Object> failureUpdates =
                                                                new HashMap<>();
                                                failureUpdates.put("status",
                                                                ProcessingStatus.FAILED);
                                                failureUpdates.put("failureReason",
                                                                "Failed during post-queue update: "
                                                                                + e.getMessage());
                                                failureUpdates.put("lastUpdated",
                                                                Timestamp.of(new Date()));
                                                failureUpdates.put("tempFilePath", null);
                                                failureUpdates.put("tempPptxFilePath", null);
                                                firebaseService.updateData(firebaseService
                                                                .getAudioMetadataCollectionName(),
                                                                metadataId, failureUpdates);
                                        } catch (Exception finalFailEx) {
                                                log.error("CRITICAL: Failed even to update metadata {} status to FAILED after UPLOAD_IN_PROGRESS update failure: {}",
                                                                metadataId,
                                                                finalFailEx.getMessage(),
                                                                finalFailEx);
                                        }
                                        throw new FirestoreInteractionException(
                                                        "Failed to update metadata status after queueing.",
                                                        e);
                                }

                                log.info("Successfully queued file(s) for upload. Metadata ID: {}, Status: {}, User ID: {}",
                                                initialMetadata.getId(),
                                                initialMetadata.getStatus(), userId);
                                return initialMetadata;

                        } catch (AmqpException | FirestoreInteractionException e) {
                                log.error("Error during file processing trigger for user {}: {}",
                                                userId, e.getMessage(), e);
                                if (initialMetadata != null && initialMetadata.getId() != null
                                                && initialMetadata
                                                                .getStatus() != ProcessingStatus.FAILED) {
                                        try {
                                                Map<String, Object> failureUpdates =
                                                                new HashMap<>();
                                                failureUpdates.put("status",
                                                                ProcessingStatus.FAILED);
                                                failureUpdates.put("failureReason",
                                                                "Failed during processing trigger: "
                                                                                + e.getMessage());
                                                failureUpdates.put("lastUpdated",
                                                                Timestamp.of(new Date()));
                                                failureUpdates.put("tempFilePath", null);
                                                failureUpdates.put("tempPptxFilePath", null);
                                                firebaseService.updateData(firebaseService
                                                                .getAudioMetadataCollectionName(),
                                                                initialMetadata.getId(),
                                                                failureUpdates);
                                                log.warn("Updated metadata {} status to FAILED due to trigger error: {}",
                                                                initialMetadata.getId(),
                                                                e.getMessage());
                                        } catch (Exception updateEx) {
                                                log.error("Failed to update metadata {} status to FAILED after trigger error: {}",
                                                                initialMetadata.getId(),
                                                                updateEx.getMessage(), updateEx);
                                        }
                                }
                                deleteTemporaryFile(tempAudioPath);
                                deleteTemporaryFile(tempPptxPath);
                                if (e instanceof FirestoreInteractionException)
                                        throw (FirestoreInteractionException) e;
                                if (e instanceof AmqpException)
                                        throw new RuntimeException(
                                                        "Failed to send message to upload queue.",
                                                        e);
                                throw new RuntimeException("Failed to queue files for processing.",
                                                e);
                        }
                } catch (IOException | FirestoreInteractionException e) {
                        log.error("Error during initial file saving or metadata creation for user {}: {}",
                                        userId, e.getMessage(), e);
                        deleteTemporaryFile(tempAudioPath);
                        deleteTemporaryFile(tempPptxPath);
                        throw e;
                }
        }

        private void validateMultipartFile(MultipartFile file, String fileTypeLabel, String userId)
                        throws InvalidAudioFileException {
                if (file.isEmpty()) {
                        log.warn("Validation failed for user {}: {} file is empty.", userId,
                                        fileTypeLabel);
                        throw new InvalidAudioFileException(
                                        fileTypeLabel + " file cannot be empty.");
                }
                long maxBytes = getMaxFileSizeInBytes();
                if (file.getSize() > maxBytes) {
                        log.warn("Validation failed for user {}: {} file size {} exceeds limit of {} bytes.",
                                        userId, fileTypeLabel, file.getSize(), maxBytes);
                        throw new InvalidAudioFileException(fileTypeLabel
                                        + " file size exceeds the maximum allowed limit ("
                                        + maxFileSizeValue + ").");
                }
                log.debug("Validation passed for {} file: {}", fileTypeLabel,
                                file.getOriginalFilename());
        }

        private Path saveTemporaryFile(MultipartFile file, String prefix) throws IOException {
                String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(
                                file.getOriginalFilename(), "Filename cannot be null"));
                String fileExtension = StringUtils.getFilenameExtension(originalFilename);
                String tempFilename = prefix + "-" + UUID.randomUUID()
                                + (fileExtension != null ? "." + fileExtension : "");
                Path tempFilePath = this.tempFileDir.resolve(tempFilename);

                try (InputStream inputStream = file.getInputStream()) {
                        Files.copy(inputStream, tempFilePath, StandardCopyOption.REPLACE_EXISTING);
                        return tempFilePath;
                } catch (IOException e) {
                        log.error("Failed to save uploaded file temporarily to {}: {}",
                                        tempFilePath.toAbsolutePath(), e.getMessage(), e);
                        throw new IOException("Failed to save temporary " + prefix + " file.", e);
                }
        }

        private void sendUploadMessage(String metadataId, String fileType, String tempFilePath,
                        String routingKey, String originalFilename, String originalContentType) {
                try {
                        NhostUploadMessage message = new NhostUploadMessage(metadataId, fileType,
                                        tempFilePath, originalFilename, originalContentType);

                        rabbitTemplate.convertAndSend(RabbitMQConfig.PROCESSING_EXCHANGE_NAME,
                                        routingKey, message);
                        log.info("Sent {} upload message for metadataId {} to exchange '{}' with routing key '{}'",
                                        fileType, metadataId,
                                        RabbitMQConfig.PROCESSING_EXCHANGE_NAME, routingKey);
                } catch (Exception e) {
                        log.error("Failed to send {} upload message for metadataId {} to queue. Error: {}",
                                        fileType, metadataId, e.getMessage(), e);
                        updateMetadataStatus(metadataId, null, ProcessingStatus.FAILED,
                                        "Failed to queue file for upload: " + e.getMessage(), true);
                }
        }

        private void deleteTemporaryFile(@Nullable Path tempPath) {
                if (tempPath != null) {
                        try {
                                boolean deleted = Files.deleteIfExists(tempPath);
                                if (deleted) {
                                        log.info("Cleaned up temporary file: {}",
                                                        tempPath.toAbsolutePath());
                                }
                        } catch (IOException e) {
                                log.warn("Failed to clean up temporary file: {}. Error: {}",
                                                tempPath.toAbsolutePath(), e.getMessage());
                        }
                }
        }

        public List<AudioMetadata> getAllAudioMetadataList() {
                log.warn("getAllAudioMetadataList called - fetching all metadata. Consider pagination/security.");
                return firebaseService.getAllAudioMetadata();
        }

        @Cacheable(value = CACHE_METADATA_BY_USER,
                        key = "#userId + '-' + #pageSize + '-' + (#lastDocumentId ?: 'null')")
        public List<AudioMetadata> getAudioMetadataListForUser(String userId, int pageSize,
                        @Nullable String lastDocumentId) {
                log.info("Fetching audio metadata list for user ID: {}, pageSize: {}, lastId: {} (Cache MISS or expired)",
                                userId, pageSize, lastDocumentId);
                try {
                        List<AudioMetadata> userMetadata = firebaseService
                                        .getAudioMetadataByUserId(userId, pageSize, lastDocumentId);
                        log.info("Retrieved {} audio metadata records for user {} (page)",
                                        userMetadata.size(), userId);
                        return userMetadata;
                } catch (FirestoreInteractionException e) {
                        log.error("Firestore interaction failed retrieving metadata list for user {}",
                                        userId, e);
                        throw e;
                } catch (RuntimeException e) {
                        log.error("Unexpected runtime exception retrieving metadata list for user {}",
                                        userId, e);
                        throw e;
                }
        }

        @Cacheable(value = CACHE_METADATA_BY_ID, key = "#metadataId", unless = "#result == null")
        public AudioMetadata getAudioMetadataById(String metadataId) {
                log.info("Fetching audio metadata by ID: {} (Cache MISS or expired)", metadataId);
                try {
                        Map<String, Object> data = firebaseService.getData(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId);
                        AudioMetadata metadata = AudioMetadata.fromMap(data);
                        if (metadata != null) {
                                log.info("Found metadata for ID {}", metadataId);
                        } else {
                                log.warn("Metadata not found for ID {}", metadataId);
                        }
                        return metadata;
                } catch (FirestoreInteractionException e) {
                        log.error("Firestore interaction failed retrieving metadata by ID {}",
                                        metadataId, e);
                        throw e;
                } catch (RuntimeException e) {
                        log.error("Unexpected runtime exception retrieving metadata by ID {}",
                                        metadataId, e);
                        throw e;
                }
        }

        @Caching(evict = {@CacheEvict(value = CACHE_METADATA_BY_ID, key = "#metadataId"),
                        @CacheEvict(value = CACHE_METADATA_BY_USER, allEntries = true)})
        public boolean deleteAudioMetadata(String metadataId) {
                log.info("Initiating cascading delete for AudioMetadata ID: {}", metadataId);
                AudioMetadata metadata = null;
                try {
                        metadata = getAudioMetadataById(metadataId);

                        if (metadata == null) {
                                log.warn("AudioMetadata not found for ID: {}. Cannot perform deletion.",
                                                metadataId);
                                return false;
                        }

                        String nhostFileId = metadata.getNhostFileId();
                        String recordingId = metadata.getRecordingId();

                        if (StringUtils.hasText(nhostFileId)) {
                                try {
                                        log.info("Attempting to delete Nhost file ID: {} associated with metadata {}",
                                                        nhostFileId, metadataId);
                                        nhostStorageService.deleteFile(nhostFileId);
                                        log.info("Successfully requested deletion of Nhost file ID: {}",
                                                        nhostFileId);
                                } catch (Exception e) {
                                        log.error("Failed to delete Nhost file ID {} for metadata {}. Error: {}",
                                                        nhostFileId, metadataId, e.getMessage());
                                }
                        } else {
                                log.warn("No NhostFileId found in metadata {} to delete.",
                                                metadataId);
                        }

                        String nhostPptxFileId = metadata.getNhostPptxFileId();
                        if (StringUtils.hasText(nhostPptxFileId)) {
                                try {
                                        log.info("Attempting to delete PowerPoint Nhost file ID: {} associated with metadata {}",
                                                        nhostPptxFileId, metadataId);
                                        nhostStorageService.deleteFile(nhostPptxFileId);
                                        log.info("Successfully requested deletion of PowerPoint Nhost file ID: {}",
                                                        nhostPptxFileId);
                                } catch (Exception e) {
                                        log.error("Failed to delete PowerPoint Nhost file ID {} for metadata {}. Error: {}",
                                                        nhostPptxFileId, metadataId,
                                                        e.getMessage());
                                }
                        } else {
                                log.debug("No NhostPptxFileId found in metadata {} to delete.",
                                                metadataId);
                        }

                        String generatedPdfNhostFileId = metadata.getGeneratedPdfNhostFileId();
                        if (StringUtils.hasText(generatedPdfNhostFileId)) {
                                try {
                                        log.info("Attempting to delete generated PDF Nhost file ID: {} associated with metadata {}",
                                                        generatedPdfNhostFileId, metadataId);
                                        nhostStorageService.deleteFile(generatedPdfNhostFileId);
                                        log.info("Successfully requested deletion of generated PDF Nhost file ID: {}",
                                                        generatedPdfNhostFileId);
                                } catch (Exception e) {
                                        log.error("Failed to delete generated PDF Nhost file ID {} for metadata {}. Error: {}",
                                                        generatedPdfNhostFileId, metadataId,
                                                        e.getMessage());
                                }
                        } else {
                                log.debug("No generatedPdfNhostFileId found in metadata {} to delete.",
                                                metadataId);
                        }

                        if (StringUtils.hasText(recordingId)) {
                                try {
                                        log.info("Attempting to delete Learning Recommendations for Recording ID: {} associated with metadata {}",
                                                        recordingId, metadataId);
                                        learningMaterialRecommenderService
                                                        .deleteRecommendationsByRecordingId(
                                                                        recordingId);
                                        log.info("Successfully initiated deletion for Learning Recommendations for Recording ID: {}",
                                                        recordingId);
                                } catch (Exception e) {
                                        log.error("Failed to delete Learning Recommendations for Recording ID {} for metadata {}. Error: {}",
                                                        recordingId, metadataId, e.getMessage());
                                }

                                try {
                                        log.info("Attempting to delete Recording ID: {} and its related Summary associated with metadata {}",
                                                        recordingId, metadataId);
                                        recordingService.deleteRecording(recordingId);
                                        log.info("Successfully initiated deletion for Recording ID: {}",
                                                        recordingId);
                                } catch (Exception e) {
                                        log.error("Failed to delete Recording ID {} (and potentially related data) for metadata {}. Error: {}",
                                                        recordingId, metadataId, e.getMessage());
                                }
                        } else {
                                log.warn("No RecordingId found in metadata {}. Cannot delete associated Recording, Summary, or Recommendations.",
                                                metadataId);
                        }

                        log.info("Attempting to delete AudioMetadata document ID: {}", metadataId);
                        firebaseService.deleteData(firebaseService.getAudioMetadataCollectionName(),
                                        metadataId);
                        log.info("Successfully deleted AudioMetadata document ID: {}", metadataId);

                        if (metadata.getTempFilePath() != null
                                        && !metadata.getTempFilePath().isBlank()) {
                                try {
                                        Files.deleteIfExists(Paths.get(metadata.getTempFilePath()));
                                        log.info("Deleted associated temporary file: {}",
                                                        metadata.getTempFilePath());
                                } catch (IOException e) {
                                        log.warn("Could not delete temporary file {} for metadata {}",
                                                        metadata.getTempFilePath(), metadataId, e);
                                }
                        }

                        if (metadata.getTempPptxFilePath() != null
                                        && !metadata.getTempPptxFilePath().isBlank()) {
                                try {
                                        Files.deleteIfExists(
                                                        Paths.get(metadata.getTempPptxFilePath()));
                                        log.info("Deleted associated temporary PowerPoint file: {}",
                                                        metadata.getTempPptxFilePath());
                                } catch (IOException e) {
                                        log.warn("Could not delete temporary PowerPoint file {} for metadata {}",
                                                        metadata.getTempPptxFilePath(), metadataId,
                                                        e);
                                }
                        }

                        return true;

                } catch (FirestoreInteractionException e) {
                        log.error("Firestore error during cascading delete process for metadata ID {}: {}",
                                        metadataId, e.getMessage(), e);
                        if (metadata != null && metadata.getTempFilePath() != null
                                        && !metadata.getTempFilePath().isBlank()) {
                                try {
                                        Files.deleteIfExists(Paths.get(metadata.getTempFilePath()));
                                } catch (IOException ignored) {
                                }
                        }
                        if (metadata != null && metadata.getTempPptxFilePath() != null
                                        && !metadata.getTempPptxFilePath().isBlank()) {
                                try {
                                        Files.deleteIfExists(
                                                        Paths.get(metadata.getTempPptxFilePath()));
                                } catch (IOException ignored) {
                                }
                        }
                        return false;
                } catch (Exception e) {
                        log.error("Unexpected error during cascading delete for metadata ID {}: {}",
                                        metadataId, e.getMessage(), e);
                        if (metadata != null && metadata.getTempFilePath() != null
                                        && !metadata.getTempFilePath().isBlank()) {
                                try {
                                        Files.deleteIfExists(Paths.get(metadata.getTempFilePath()));
                                } catch (IOException ignored) {
                                }
                        }
                        if (metadata != null && metadata.getTempPptxFilePath() != null
                                        && !metadata.getTempPptxFilePath().isBlank()) {
                                try {
                                        Files.deleteIfExists(
                                                        Paths.get(metadata.getTempPptxFilePath()));
                                } catch (IOException ignored) {
                                }
                        }
                        return false;
                }
        }

        @Caching(evict = {@CacheEvict(value = CACHE_METADATA_BY_ID, key = "#metadataId"),
                        @CacheEvict(value = CACHE_METADATA_BY_USER, allEntries = true)})
        public void updateAudioMetadata(String metadataId, Map<String, Object> updates)
                        throws FirestoreInteractionException {
                log.info("Updating AudioMetadata for ID: {} with updates: {}", metadataId,
                                updates.keySet());
                if (!updates.containsKey("lastUpdated")) {
                        updates.put("lastUpdated", Timestamp.of(new Date()));
                }
                try {
                        firebaseService.updateData(firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, updates);
                        log.info("Successfully updated AudioMetadata for ID: {}", metadataId);
                } catch (FirestoreInteractionException e) {
                        log.error("Firestore error updating metadata for ID {}: {}", metadataId,
                                        e.getMessage(), e);
                        throw e;
                } catch (Exception e) {
                        log.error("Unexpected error updating metadata for ID {}: {}", metadataId,
                                        e.getMessage(), e);
                        throw new FirestoreInteractionException(
                                        "Unexpected error updating metadata.", e);
                }
        }

        @Caching(evict = {@CacheEvict(value = CACHE_METADATA_BY_ID, key = "#metadataId"),
                        @CacheEvict(value = CACHE_METADATA_BY_USER, allEntries = true)})
        public void updateAudioMetadataStatus(String metadataId, ProcessingStatus newStatus,
                        @Nullable String failureReason) {
                log.info("Updating status for metadata ID: {} to {} (FailureReason: {})",
                                metadataId, newStatus, failureReason);
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", newStatus.name());
                updates.put("lastUpdated", Timestamp.of(new Date()));
                if (failureReason != null) {
                        updates.put("failureReason", failureReason);
                }

                try {
                        updateAudioMetadata(metadataId, updates);
                        log.info("Status updated successfully for metadata ID: {}", metadataId);
                } catch (FirestoreInteractionException e) {
                        log.error("Failed to update status for metadata ID: {}", metadataId);
                }
        }

        private AudioMetadata updateMetadataStatus(String metadataId, String userId,
                        @Nullable ProcessingStatus status, @Nullable String failureReason,
                        boolean clearTempPaths) {
                log.info("Updating status for metadata ID: {} to {} (FailureReason: {})",
                                metadataId, status, failureReason);
                Map<String, Object> updates = new HashMap<>();
                if (status != null) {
                        updates.put("status", status.name());
                }
                if (failureReason != null) {
                        updates.put("failureReason", failureReason);
                }
                if (clearTempPaths) {
                        updates.put("tempAudioPath", FieldValue.delete());
                        updates.put("tempPptxPath", FieldValue.delete());
                        log.info("Clearing temp file paths from metadata {}.", metadataId);
                }
                updates.put("lastUpdated", Timestamp.now());

                try {
                        firebaseService.updateDataWithMap(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, updates);
                        log.info("Updated metadata {} with fields: {}. Status now might be {}",
                                        metadataId, updates.keySet(),
                                        status != null ? status : "(unchanged)");
                        invalidateUserCache(userId);

                        AudioMetadata metadata = getAudioMetadataById(metadataId);
                        if (metadata != null) {
                                metadata.setStatus(status != null ? status : metadata.getStatus());
                                metadata.setFailureReason(failureReason);
                                metadata.setLastUpdated(Timestamp.of(new Date()));
                                updateAudioMetadata(metadataId, metadata.toMap());
                        }
                        return metadata;
                } catch (FirestoreInteractionException e) {
                        log.error("CRITICAL: Failed to update metadata status/paths for {}. Error: {}",
                                        metadataId, e.getMessage(), e);
                        throw e;
                }
        }

        private void invalidateUserCache(String userId) {}

        private void cleanupTempFile(Path tempPath) {
                if (tempPath != null) {
                        try {
                                Files.deleteIfExists(tempPath);
                                log.info("Cleaned up temporary file due to pre-queueing error: {}",
                                                tempPath.getFileName());
                        } catch (IOException ex) {
                                log.warn("Failed to clean up temporary file {} after error: {}",
                                                tempPath.getFileName(), ex.getMessage());
                        }
                }
        }
}

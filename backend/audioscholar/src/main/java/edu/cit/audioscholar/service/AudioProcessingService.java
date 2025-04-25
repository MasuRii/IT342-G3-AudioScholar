package edu.cit.audioscholar.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import com.google.cloud.Timestamp;
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
        private final String maxFileSizeValue;
        private final Path tempFileDir;

        public AudioProcessingService(FirebaseService firebaseService,
                        RabbitTemplate rabbitTemplate,
                        @Value("${spring.servlet.multipart.max-file-size}") String maxFileSizeValue,
                        @Value("${app.temp-file-dir}") String tempFileDirStr) {
                this.firebaseService = firebaseService;
                this.rabbitTemplate = rabbitTemplate;
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
        }

        private long getMaxFileSizeInBytes() {
                return DataSize.parse(maxFileSizeValue).toBytes();
        }

        @Caching(evict = {@CacheEvict(value = CACHE_METADATA_BY_USER, allEntries = true)})
        public AudioMetadata queueAudioForUpload(MultipartFile file, String title,
                        String description, String userId) throws IOException,
                        InvalidAudioFileException, FirestoreInteractionException {

                log.info("Queueing for upload: File: {}, Title: {}, User: {}",
                                file.getOriginalFilename(), title, userId);

                if (file.isEmpty()) {
                        log.warn("Validation failed for user {}: File is empty.", userId);
                        throw new InvalidAudioFileException("Uploaded file cannot be empty.");
                }
                long maxBytes = getMaxFileSizeInBytes();
                if (file.getSize() > maxBytes) {
                        log.warn("Validation failed for user {}: File size {} exceeds limit of {} bytes.",
                                        userId, file.getSize(), maxBytes);
                        throw new InvalidAudioFileException(
                                        "File size exceeds the maximum allowed limit ("
                                                        + maxFileSizeValue + ").");
                }
                log.info("Initial validation passed for user {}: File: {}, Size: {}", userId,
                                file.getOriginalFilename(), file.getSize());

                String originalFilename = StringUtils.cleanPath(
                                file.getOriginalFilename() != null ? file.getOriginalFilename()
                                                : "audiofile");
                String fileExtension = StringUtils.getFilenameExtension(originalFilename);
                String tempFilename = UUID.randomUUID()
                                + (fileExtension != null ? "." + fileExtension : "");
                Path tempFilePath = this.tempFileDir.resolve(tempFilename);
                String tempFileAbsolutePath = tempFilePath.toAbsolutePath().toString();

                try (InputStream inputStream = file.getInputStream()) {
                        Files.copy(inputStream, tempFilePath, StandardCopyOption.REPLACE_EXISTING);
                        log.info("File saved temporarily to: {}", tempFileAbsolutePath);
                } catch (IOException e) {
                        log.error("Failed to save uploaded file temporarily to {}: {}",
                                        tempFileAbsolutePath, e.getMessage(), e);
                        throw new IOException("Failed to save temporary file.", e);
                }

                String recordingId = UUID.randomUUID().toString();
                String metadataId = UUID.randomUUID().toString();
                log.info("Generated recordingId: {} and metadataId: {} for upload by user {}",
                                recordingId, metadataId, userId);

                AudioMetadata initialMetadata = new AudioMetadata(metadataId, userId,
                                originalFilename, file.getSize(), file.getContentType(),
                                StringUtils.hasText(title) ? title : originalFilename, description,
                                Timestamp.of(new Date()), recordingId,
                                ProcessingStatus.UPLOAD_PENDING, tempFileAbsolutePath);

                try {
                        firebaseService.saveData(firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, initialMetadata.toMap());
                        log.info("Initial metadata (ID: {}) saved to Firestore with status UPLOAD_PENDING.",
                                        metadataId);
                } catch (Exception e) {
                        log.error("Firestore error saving initial metadata for user {}: {}", userId,
                                        e.getMessage(), e);
                        try {
                                Files.deleteIfExists(tempFilePath);
                        } catch (IOException ignored) {
                        }
                        throw new FirestoreInteractionException(
                                        "Failed to save initial metadata to database.", e);
                }

                NhostUploadMessage message = new NhostUploadMessage(metadataId, recordingId);
                try {
                        log.info("Sending message to Nhost upload queue '{}' for metadataId: {}",
                                        RabbitMQConfig.UPLOAD_QUEUE_NAME, metadataId);
                        rabbitTemplate.convertAndSend(RabbitMQConfig.PROCESSING_EXCHANGE_NAME,
                                        RabbitMQConfig.UPLOAD_ROUTING_KEY, message);
                        log.info("Successfully sent message to Nhost upload queue for metadataId: {}",
                                        metadataId);
                } catch (AmqpException e) {
                        log.error("Failed to send message to Nhost upload queue '{}' for metadataId {}: {}",
                                        RabbitMQConfig.UPLOAD_QUEUE_NAME, metadataId,
                                        e.getMessage(), e);
                        try {
                                initialMetadata.setStatus(ProcessingStatus.FAILED);
                                initialMetadata.setFailureReason(
                                                "Failed to queue for upload: " + e.getMessage());
                                initialMetadata.setTempFilePath(null);
                                firebaseService.updateData(
                                                firebaseService.getAudioMetadataCollectionName(),
                                                metadataId, initialMetadata.toMap());
                                log.info("Updated metadata {} status to FAILED due to queuing error.",
                                                metadataId);
                        } catch (Exception updateEx) {
                                log.error("Failed to update metadata {} status to FAILED after queuing error: {}",
                                                metadataId, updateEx.getMessage(), updateEx);
                        } finally {
                                try {
                                        Files.deleteIfExists(tempFilePath);
                                } catch (IOException ignored) {
                                }
                        }
                        throw new RuntimeException("Failed to queue audio for storage upload.", e);
                }

                return initialMetadata;
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
                log.warn("Attempting to delete ONLY AudioMetadata with ID: {}. Associated Recording/Summary/File NOT deleted by this method.",
                                metadataId);
                try {
                        AudioMetadata metadata = getAudioMetadataById(metadataId);
                        if (metadata != null && metadata.getTempFilePath() != null && (metadata
                                        .getStatus() == ProcessingStatus.UPLOAD_PENDING
                                        || metadata.getStatus() == ProcessingStatus.FAILED)) {
                                try {
                                        Files.deleteIfExists(Paths.get(metadata.getTempFilePath()));
                                        log.info("Deleted associated temporary file for failed/pending metadata {}",
                                                        metadataId);
                                } catch (IOException e) {
                                        log.warn("Could not delete temporary file {} for metadata {}",
                                                        metadata.getTempFilePath(), metadataId, e);
                                }
                        }

                        firebaseService.deleteData(firebaseService.getAudioMetadataCollectionName(),
                                        metadataId);
                        log.info("Successfully deleted AudioMetadata from Firestore with ID: {} and evicted related caches.",
                                        metadataId);
                        return true;
                } catch (FirestoreInteractionException e) {
                        log.error("Firestore interaction failed during metadata deletion process for ID {}",
                                        metadataId, e);
                        return false;
                } catch (RuntimeException e) {
                        log.error("Unexpected runtime exception during metadata deletion for ID {}",
                                        metadataId, e);
                        return false;
                }
        }

}

package edu.cit.audioscholar.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
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
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import edu.cit.audioscholar.config.RabbitMQConfig;
import edu.cit.audioscholar.dto.AudioProcessingMessage;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.exception.InvalidAudioFileException;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.ProcessingStatus;
import edu.cit.audioscholar.model.Summary;


@Service
public class AudioProcessingService {

    private static final Logger log = LoggerFactory.getLogger(AudioProcessingService.class);
    private static final String CACHE_METADATA_BY_ID = "audioMetadataById";
    private static final String CACHE_METADATA_BY_USER = "audioMetadataByUser";

    private final FirebaseService firebaseService;
    private final RabbitTemplate rabbitTemplate;
    private final RecordingService recordingService;
    private final String maxFileSizeValue;
    private final String queueName = RabbitMQConfig.QUEUE_NAME;

    public AudioProcessingService(FirebaseService firebaseService, RabbitTemplate rabbitTemplate,
            RecordingService recordingService,
            @Value("${spring.servlet.multipart.max-file-size}") String maxFileSizeValue) {
        this.firebaseService = firebaseService;
        this.rabbitTemplate = rabbitTemplate;
        this.recordingService = recordingService;
        this.maxFileSizeValue = maxFileSizeValue;
    }

    private long getMaxFileSizeInBytes() {
        return DataSize.parse(maxFileSizeValue).toBytes();
    }

    @Caching(evict = {@CacheEvict(value = CACHE_METADATA_BY_USER, allEntries = true)})
    public AudioMetadata uploadAndSaveMetadata(MultipartFile file, String title, String description,
            String userId)
            throws IOException, InvalidAudioFileException, FirestoreInteractionException {

        log.info("Starting upload process for file: {}, Title: {}, User: {}",
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
                    "File size exceeds the maximum allowed limit (" + maxFileSizeValue + ").");
        }
        log.info("Initial validation passed for user {}: File: {}, Size: {}", userId,
                file.getOriginalFilename(), file.getSize());

        String recordingId = UUID.randomUUID().toString();
        log.info("Generated recordingId: {} for upload by user {}", recordingId, userId);

        AudioMetadata savedMetadata;
        try {
            savedMetadata =
                    recordingService.uploadAudioFile(file, title, description, userId, recordingId);

            savedMetadata.setStatus(ProcessingStatus.PENDING);
            firebaseService.updateAudioMetadataStatus(savedMetadata.getId(),
                    ProcessingStatus.PENDING);
            log.info(
                    "RecordingService processed upload. Metadata ID: {}, Recording ID: {}. Updated METADATA status to PENDING.",
                    savedMetadata.getId(), recordingId);

        } catch (ExecutionException | InterruptedException e) {
            log.error("Firestore error during recording/metadata creation for user {}: {}", userId,
                    e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new FirestoreInteractionException(
                    "Failed to save recording/metadata to database.", e);
        } catch (IOException | InvalidAudioFileException | IllegalArgumentException e) {
            log.error("Error during file processing, validation, or saving for user {}: {}", userId,
                    e.getMessage(), e);
            throw e;
        } catch (RuntimeException e) {
            log.error("Unexpected runtime error during recording/metadata creation for user {}: {}",
                    userId, e.getMessage(), e);
            throw e;
        }

        AudioProcessingMessage message =
                new AudioProcessingMessage(recordingId, userId, savedMetadata.getId());

        try {
            log.info("Sending message to queue '{}' for recordingId: {}, metadataId: {}", queueName,
                    recordingId, savedMetadata.getId());
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY,
                    message);
            log.info("Successfully sent message for recordingId: {}", recordingId);


        } catch (AmqpException e) {
            log.error("Failed to send message to queue '{}' for recordingId {}: {}", queueName,
                    recordingId, e.getMessage(), e);
            try {
                savedMetadata.setStatus(ProcessingStatus.FAILED);
                firebaseService.updateAudioMetadataStatus(savedMetadata.getId(),
                        ProcessingStatus.FAILED);
                log.info("Updated metadata {} status to FAILED due to queueing error.",
                        savedMetadata.getId());
            } catch (Exception updateEx) {
                log.error("Failed to update metadata {} status to FAILED after queueing error: {}",
                        savedMetadata.getId(), updateEx.getMessage(), updateEx);
            }
            throw new RuntimeException("Failed to queue audio for processing.", e);
        } catch (Exception e) {
            log.error("Error during post-queueing operations for metadata {}: {}",
                    savedMetadata.getId(), e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        return savedMetadata;
    }



    public List<AudioMetadata> getAllAudioMetadataList() {
        log.warn(
                "getAllAudioMetadataList called - fetching all metadata. Consider pagination/security.");
        return firebaseService.getAllAudioMetadata();
    }

    @Cacheable(value = CACHE_METADATA_BY_USER,
            key = "#userId + '-' + #pageSize + '-' + (#lastDocumentId ?: 'null')")
    public List<AudioMetadata> getAudioMetadataListForUser(String userId, int pageSize,
            @Nullable String lastDocumentId) {
        log.info(
                "Fetching audio metadata list for user ID: {}, pageSize: {}, lastId: {} (Cache MISS or expired)",
                userId, pageSize, lastDocumentId);
        try {
            List<AudioMetadata> userMetadata =
                    firebaseService.getAudioMetadataByUserId(userId, pageSize, lastDocumentId);
            log.info("Retrieved {} audio metadata records for user {} (page)", userMetadata.size(),
                    userId);
            return userMetadata;
        } catch (FirestoreInteractionException e) {
            log.error("Firestore interaction failed retrieving metadata list for user {}", userId,
                    e);
            throw e;
        } catch (RuntimeException e) {
            log.error("Unexpected runtime exception retrieving metadata list for user {}", userId,
                    e);
            throw e;
        }
    }

    @Cacheable(value = CACHE_METADATA_BY_ID, key = "#metadataId", unless = "#result == null")
    public AudioMetadata getAudioMetadataById(String metadataId) {
        log.info("Fetching audio metadata by ID: {} (Cache MISS or expired)", metadataId);
        try {
            AudioMetadata metadata = firebaseService.getAudioMetadataById(metadataId);
            if (metadata != null) {
                log.info("Found metadata for ID {}", metadataId);
            } else {
                log.warn("Metadata not found for ID {}", metadataId);
            }
            return metadata;
        } catch (FirestoreInteractionException e) {
            log.error("Firestore interaction failed retrieving metadata by ID {}", metadataId, e);
            throw e;
        } catch (RuntimeException e) {
            log.error("Unexpected runtime exception retrieving metadata by ID {}", metadataId, e);
            throw e;
        }
    }

    @Caching(evict = {@CacheEvict(value = CACHE_METADATA_BY_ID, key = "#metadataId"),
            @CacheEvict(value = CACHE_METADATA_BY_USER, allEntries = true)})
    public boolean deleteAudioMetadata(String metadataId) {
        log.warn(
                "Attempting to delete ONLY AudioMetadata with ID: {}. Associated Recording/Summary/File NOT deleted by this method.",
                metadataId);
        try {
            firebaseService.deleteData(firebaseService.getAudioMetadataCollectionName(),
                    metadataId);
            log.info(
                    "Successfully deleted AudioMetadata from Firestore with ID: {} and evicted related caches.",
                    metadataId);
            return true;
        } catch (FirestoreInteractionException e) {
            log.error("Firestore interaction failed during metadata deletion process for ID {}",
                    metadataId, e);
            return false;
        } catch (RuntimeException e) {
            log.error("Unexpected runtime exception during metadata deletion for ID {}", metadataId,
                    e);
            return false;
        }
    }


    @Deprecated
    public Summary processAndSummarize(byte[] audioData, MultipartFile fileInfo, String userId)
            throws Exception {
        log.warn("DEPRECATED processAndSummarize called. Use async flow via /api/audio/upload.");
        throw new UnsupportedOperationException(
                "Deprecated method processAndSummarize should not be used.");
    }

    @Deprecated
    private String callGeminiWithAudio(String base64Audio, String fileName) {
        throw new UnsupportedOperationException(
                "Deprecated method callGeminiWithAudio should not be used.");
    }

    @Deprecated
    private Summary createSummaryFromResponse(String aiResponse) throws Exception {
        throw new UnsupportedOperationException(
                "Deprecated method createSummaryFromResponse should not be used.");
    }
}

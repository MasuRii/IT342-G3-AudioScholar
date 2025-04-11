package edu.cit.audioscholar.service;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.google.cloud.Timestamp;

import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.Summary;


@Service
public class AudioProcessingService {

    private static final Logger log = LoggerFactory.getLogger(AudioProcessingService.class);

    private static final String CACHE_METADATA_BY_ID = "audioMetadataById";
    private static final String CACHE_METADATA_BY_USER = "audioMetadataByUser";

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private FirebaseService firebaseService;

    @Autowired
    private NhostStorageService nhostStorageService;


    @CacheEvict(value = CACHE_METADATA_BY_USER, allEntries = true)
    public AudioMetadata uploadAndSaveMetadata(MultipartFile file, String title, String description, String userId)
            throws IOException {

        log.info("Starting upload process for file: {}, Title: {}, User: {}",
                   file.getOriginalFilename(), title, userId);

        String nhostFileId;
        String storageUrl;
        try {
            nhostFileId = nhostStorageService.uploadFile(file);
            log.info("File uploaded to Nhost, ID: {}", nhostFileId);
            storageUrl = nhostStorageService.getPublicUrl(nhostFileId);
            log.info("Constructed Nhost public URL: {}", storageUrl);
        } catch (IOException e) {
            log.error("IOException during Nhost upload for user {}, file {}", userId, file.getOriginalFilename(), e);
            throw e;
        } catch (RuntimeException e) {
             log.error("RuntimeException during Nhost upload/URL generation for user {}, file {}", userId, file.getOriginalFilename(), e);
             throw e;
        }


        AudioMetadata metadata = new AudioMetadata();
        metadata.setUserId(userId);
        metadata.setFileName(file.getOriginalFilename());
        metadata.setFileSize(file.getSize());
        metadata.setContentType(file.getContentType());
        metadata.setTitle(title != null ? title : "");
        metadata.setDescription(description != null ? description : "");
        metadata.setNhostFileId(nhostFileId);
        metadata.setStorageUrl(storageUrl);
        metadata.setUploadTimestamp(Timestamp.now());

        try {
            AudioMetadata savedMetadata = firebaseService.saveAudioMetadata(metadata);
            log.info("AudioMetadata saved to Firestore with ID: {}. Evicting cache '{}' (all entries)",
                     savedMetadata.getId(), CACHE_METADATA_BY_USER);
            return savedMetadata;
        } catch (FirestoreInteractionException e) {
             log.error("Firestore interaction failed during metadata save for user {}, file {}", userId, file.getOriginalFilename(), e);
             throw e;
        } catch (RuntimeException e) {
             log.error("Unexpected runtime exception saving AudioMetadata for user {}, file {}", userId, file.getOriginalFilename(), e);
             throw e;
        }
    }

    public List<AudioMetadata> getAllAudioMetadataList() {
        return firebaseService.getAllAudioMetadata();
    }

    @Cacheable(value = CACHE_METADATA_BY_USER, key = "#userId + '-' + #pageSize + '-' + T(String).valueOf(#lastDocumentId)")
    public List<AudioMetadata> getAudioMetadataListForUser(String userId, int pageSize, @Nullable String lastDocumentId) {
        log.info("Fetching audio metadata list for user ID: {}, pageSize: {}, lastId: {} (Cache MISS or expired)",
                 userId, pageSize, lastDocumentId);
        try {
            List<AudioMetadata> userMetadata = firebaseService.getAudioMetadataByUserId(userId, pageSize, lastDocumentId);
            log.info("Retrieved {} audio metadata records for user {} (page)", userMetadata.size(), userId);
            return userMetadata;
        } catch (FirestoreInteractionException e) {
            log.error("Firestore interaction failed retrieving metadata list for user {}", userId, e);
            throw e;
        } catch (RuntimeException e) {
             log.error("Unexpected runtime exception retrieving metadata list for user {}", userId, e);
             throw e;
        }
    }

    @Cacheable(value = CACHE_METADATA_BY_ID, key = "#metadataId", unless="#result == null")
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


    public boolean deleteAudioMetadata(String metadataId) {
        log.info("Attempting to delete AudioMetadata from Firestore with ID: {}", metadataId);
        AudioMetadata metadataToDelete = null;
        try {
            metadataToDelete = firebaseService.getAudioMetadataById(metadataId);

            if (metadataToDelete == null) {
                log.warn("Metadata with ID {} not found for deletion.", metadataId);
                return false;
            }

            String userId = metadataToDelete.getUserId();

            firebaseService.deleteData(firebaseService.getAudioMetadataCollectionName(), metadataId);

            evictCachesForDeletion(metadataId, userId);

            log.info("Successfully deleted AudioMetadata from Firestore with ID: {}", metadataId);
            return true;
        } catch (FirestoreInteractionException e) {
            log.error("Firestore interaction failed during metadata deletion process for ID {}", metadataId, e);
            return false;
        } catch (RuntimeException e) {
             log.error("Unexpected runtime exception during metadata deletion for ID {}", metadataId, e);
             return false;
        }
    }

    @Caching(evict = {
        @CacheEvict(value = CACHE_METADATA_BY_ID, key = "#metadataId"),
        @CacheEvict(value = CACHE_METADATA_BY_USER, allEntries = true)
    })
    public void evictCachesForDeletion(String metadataId, String userId) {
        log.info("Evicting caches: '{}' key '{}' and '{}' (all entries)",
                 CACHE_METADATA_BY_ID, metadataId, CACHE_METADATA_BY_USER);
    }



    public Summary processAndSummarize(byte[] audioData, MultipartFile fileInfo, String userId) throws Exception {
        log.warn("processAndSummarize called with raw byte array for file: {}. Consider streaming or using saved file.", fileInfo.getOriginalFilename());
        log.info("Starting processAndSummarize for file: {}, User: {}", fileInfo.getOriginalFilename(), userId);

        AudioMetadata metadata = uploadAndSaveMetadata(fileInfo, fileInfo.getOriginalFilename(), "", userId);


        log.info("Metadata created with ID: {} for file: {}", metadata.getId(), fileInfo.getOriginalFilename());

        String base64Audio = Base64.getEncoder().encodeToString(audioData);
        String aiResponse = callGeminiWithAudio(base64Audio, metadata.getFileName());

        Summary summary = createSummaryFromResponse(aiResponse);
        summary.setRecordingId(metadata.getId());

        log.info("Successfully processed and summarized audio for metadata ID: {}", metadata.getId());
        return summary;
    }

    private String callGeminiWithAudio(String base64Audio, String fileName) {
         log.info("Calling Gemini for file: {}", fileName);
        String prompt = "Please analyze this audio and provide the following:[...]";

        try {
            String response = geminiService.callGeminiAPIWithAudio(prompt, base64Audio, fileName);
            log.info("Received response from Gemini for file: {}", fileName);
            return response;
        } catch (Exception e) {
            log.error("Error calling Gemini API for file: {}", fileName, e);
            throw new RuntimeException("Failed to get response from Gemini API for file: " + fileName, e);
        }
    }

    private Summary createSummaryFromResponse(String aiResponse) throws Exception {
        log.info("Parsing Gemini response.");
        System.out.println("Received AI Response (needs parsing): " + aiResponse);
        log.warn("Parsing logic for Gemini response is not fully implemented in createSummaryFromResponse.");
        Summary summary = new Summary();
        return summary;
    }
}
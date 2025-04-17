package edu.cit.audioscholar.service;

import edu.cit.audioscholar.model.ProcessingStatus;
import edu.cit.audioscholar.model.Summary;
import edu.cit.audioscholar.model.LearningRecommendation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.cloud.FirestoreClient;

import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.AudioMetadata;

@Service
public class FirebaseService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseService.class);
    private final String audioMetadataCollectionName;
    private final String summariesCollectionName;
    private final String recommendationsCollectionName;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public FirebaseService(
            @Value("${firebase.firestore.collection.audiometadata}") String audioMetadataCollectionName,
            @Value("${firebase.firestore.collection.summaries}") String summariesCollectionName,
            @Value("${firebase.firestore.collection.recommendations}") String recommendationsCollectionName) {
        this.audioMetadataCollectionName = audioMetadataCollectionName;
        this.summariesCollectionName = summariesCollectionName;
        this.recommendationsCollectionName = recommendationsCollectionName;
    }

    public String getAudioMetadataCollectionName() {
        return audioMetadataCollectionName;
    }

    private Firestore getFirestore() {
        return FirestoreClient.getFirestore();
    }


    public String saveData(String collection, String document, Map<String, Object> data) {
        try {
            Firestore firestore = getFirestore();
            ApiFuture<WriteResult> future = firestore.collection(collection).document(document).set(data);
            log.info("Data saved to {}/{}", collection, document);
            return future.get().getUpdateTime().toString();
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error saving data to {}/{}", collection, document, e);
            throw new FirestoreInteractionException("Error saving data to Firestore", e);
        }
    }

    public Map<String, Object> getData(String collection, String document) {
         try {
            Firestore firestore = getFirestore();
            DocumentSnapshot snapshot = firestore.collection(collection).document(document).get().get();
            if (snapshot.exists()) {
                log.debug("Data retrieved from {}/{}", collection, document);
                return snapshot.getData();
            } else {
                log.warn("No document found at {}/{}", collection, document);
                return null;
            }
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error getting data from {}/{}", collection, document, e);
            throw new FirestoreInteractionException("Error getting data from Firestore", e);
        }
    }

    public String updateData(String collection, String document, Map<String, Object> data) {
         try {
            Firestore firestore = getFirestore();
            ApiFuture<WriteResult> future = firestore.collection(collection).document(document).update(data);
            log.info("Data updated for {}/{}", collection, document);
            return future.get().getUpdateTime().toString();
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error updating data for {}/{}", collection, document, e);
            throw new FirestoreInteractionException("Error updating data in Firestore", e);
        }
    }

    public String deleteData(String collection, String document) {
        try {
            Firestore firestore = getFirestore();
            ApiFuture<WriteResult> future = firestore.collection(collection).document(document).delete();
            log.info("Data deleted from {}/{}", collection, document);
            return future.get().getUpdateTime().toString();
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error deleting data from {}/{}", collection, document, e);
            throw new FirestoreInteractionException("Error deleting data from Firestore", e);
        }
    }

     public List<Map<String, Object>> queryCollection(String collection, String field, Object value) {
        try {
            Firestore firestore = getFirestore();
            ApiFuture<QuerySnapshot> future = firestore.collection(collection).whereEqualTo(field, value).get();
            List<Map<String, Object>> results = new ArrayList<>();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            for (QueryDocumentSnapshot document : documents) {
                results.add(document.getData());
            }
            log.info("Query on collection '{}' where '{}' == '{}' returned {} results.", collection, field, value, results.size());
            return results;
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error querying collection '{}' where '{}' == '{}'", collection, field, value, e);
            throw new FirestoreInteractionException("Error querying collection in Firestore", e);
        }
    }


    public AudioMetadata saveAudioMetadata(AudioMetadata metadata) {
        try {
            Firestore firestore = getFirestore();
            CollectionReference colRef = firestore.collection(audioMetadataCollectionName);
            Map<String, Object> dataMap = convertToMap(metadata);

            ApiFuture<DocumentReference> futureRef = colRef.add(dataMap);
            String generatedId = futureRef.get().getId();
            metadata.setId(generatedId);


            log.info("Saved AudioMetadata to Firestore collection {} with generated ID: {}",
                       audioMetadataCollectionName, generatedId);

            return metadata;
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            String userId = (metadata != null) ? metadata.getUserId() : "unknown";
            log.error("Error saving AudioMetadata for user {}", userId, e);
            throw new FirestoreInteractionException("Failed to save AudioMetadata", e);
        }
    }

    public void updateAudioMetadataStatus(String metadataId, ProcessingStatus status) throws FirestoreInteractionException {
        if (metadataId == null || status == null) {
            log.error("Cannot update status with null metadataId or status. ID: {}, Status: {}", metadataId, status);
            throw new IllegalArgumentException("Metadata ID and Status cannot be null for update.");
        }
        log.info("Attempting to update status to {} for metadata ID: {}", status, metadataId);
        try {
            Firestore firestore = getFirestore();
            DocumentReference docRef = firestore.collection(audioMetadataCollectionName).document(metadataId);
            ApiFuture<WriteResult> future = docRef.update("status", status.name());
            future.get();
            log.info("Successfully updated status to {} for metadata ID: {}", status, metadataId);
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error updating status for metadata ID {}: {}", metadataId, e.getMessage(), e);
            throw new FirestoreInteractionException("Failed to update status for metadata " + metadataId, e);
        } catch (Exception e) {
             log.error("Unexpected error updating status for metadata ID {}: {}", metadataId, e.getMessage(), e);
             throw new FirestoreInteractionException("Unexpected error updating status for metadata " + metadataId, e);
        }
    }

    public List<AudioMetadata> getAllAudioMetadata() {
        log.warn("Executing getAllAudioMetadata - Fetching all documents from {}. Consider pagination for large datasets.", audioMetadataCollectionName);
        try {
            Firestore firestore = getFirestore();
            ApiFuture<QuerySnapshot> future = firestore.collection(audioMetadataCollectionName).get();
            List<AudioMetadata> audioMetadataList = new ArrayList<>();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            for (QueryDocumentSnapshot document : documents) {
                AudioMetadata metadata = fromDocumentSnapshot(document);
                if (metadata != null) {
                    audioMetadataList.add(metadata);
                }
            }
            log.info("Retrieved {} total AudioMetadata documents from Firestore.", audioMetadataList.size());
            return audioMetadataList;
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error retrieving all AudioMetadata documents", e);
            throw new FirestoreInteractionException("Failed to retrieve all AudioMetadata", e);
        }
    }

    public List<AudioMetadata> getAudioMetadataByUserId(String userId, int pageSize, @Nullable String lastDocumentId) {
        log.info("Retrieving AudioMetadata for user ID: {}, page size: {}, starting after document ID: {}",
                 userId, pageSize, lastDocumentId == null ? "N/A" : lastDocumentId);
        try {
            Firestore firestore = getFirestore();
            CollectionReference colRef = firestore.collection(audioMetadataCollectionName);
            Query query = colRef.whereEqualTo("userId", userId)
                                .orderBy("uploadTimestamp", Query.Direction.DESCENDING)
                                .limit(pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE);

            if (lastDocumentId != null && !lastDocumentId.isEmpty()) {
                DocumentSnapshot lastSnapshot = colRef.document(lastDocumentId).get().get();
                if (lastSnapshot.exists()) {
                    query = query.startAfter(lastSnapshot);
                    log.debug("Pagination query starting after document: {}", lastDocumentId);
                } else {
                    log.warn("lastDocumentId '{}' provided for pagination but document not found. Fetching first page.", lastDocumentId);
                }
            }

            ApiFuture<QuerySnapshot> future = query.get();
            List<AudioMetadata> userMetadataList = new ArrayList<>();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();

            for (QueryDocumentSnapshot document : documents) {
                AudioMetadata metadata = fromDocumentSnapshot(document);
                 if (metadata != null) {
                    userMetadataList.add(metadata);
                }
            }
            log.info("Retrieved {} AudioMetadata documents for user ID: {} (page)",
                       userMetadataList.size(), userId);
            return userMetadataList;
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error retrieving paginated AudioMetadata for user ID: {}", userId, e);
            throw new FirestoreInteractionException("Failed to retrieve paginated AudioMetadata for user " + userId, e);
        }
    }

    public AudioMetadata getAudioMetadataById(String metadataId) {
        log.debug("Retrieving AudioMetadata document by ID: {}", metadataId);
        try {
            Firestore firestore = getFirestore();
            DocumentReference docRef = firestore.collection(audioMetadataCollectionName).document(metadataId);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot document = future.get();

            if (document.exists()) {
                AudioMetadata metadata = fromDocumentSnapshot(document);
                if (metadata != null) {
                    log.info("Retrieved AudioMetadata document with ID: {}", metadataId);
                } else {
                     log.error("Failed to map document data to AudioMetadata object for ID: {}", metadataId);
                }
                return metadata;
            } else {
                log.warn("No AudioMetadata document found with ID: {}", metadataId);
                return null;
            }
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error retrieving AudioMetadata document by ID: {}", metadataId, e);
            throw new FirestoreInteractionException("Failed to retrieve AudioMetadata by ID " + metadataId, e);
        }
    }


    public void saveSummary(Summary summary) throws FirestoreInteractionException {
        if (summary == null || summary.getSummaryId() == null || summary.getSummaryId().isEmpty()) {
            log.error("Cannot save summary with null object or empty summaryId.");
            throw new IllegalArgumentException("Summary object and summaryId cannot be null or empty.");
        }
        String summaryId = summary.getSummaryId();
        String recordingId = summary.getRecordingId();
        log.info("[{}] Attempting to save summary with ID: {} to collection: {}", recordingId, summaryId, summariesCollectionName);
        try {
            Firestore firestore = getFirestore();
            DocumentReference docRef = firestore.collection(summariesCollectionName).document(summaryId);
            Map<String, Object> dataMap = summary.toMap();
            ApiFuture<WriteResult> future = docRef.set(dataMap);
            future.get();
            log.info("[{}] Successfully saved summary with ID: {} to Firestore.", recordingId, summaryId);
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[{}] Error saving summary with ID: {} to Firestore.", recordingId, summaryId, e);
            throw new FirestoreInteractionException("Failed to save summary " + summaryId, e);
        } catch (Exception e) {
             log.error("[{}] Unexpected error saving summary with ID: {}", recordingId, summaryId, e);
             throw new FirestoreInteractionException("Unexpected error saving summary " + summaryId, e);
        }
    }


    public void saveLearningRecommendations(List<LearningRecommendation> recommendations) throws FirestoreInteractionException {
        if (recommendations == null || recommendations.isEmpty()) {
            log.warn("Attempted to save an empty or null list of recommendations.");
            return;
        }

        String recordingId = recommendations.get(0).getRecordingId();
        log.info("[{}] Attempting to save {} recommendations to collection: {}",
                 recordingId != null ? recordingId : "UNKNOWN", recommendations.size(), recommendationsCollectionName);

        try {
            Firestore firestore = getFirestore();
            WriteBatch batch = firestore.batch();
            CollectionReference colRef = firestore.collection(recommendationsCollectionName);

            for (LearningRecommendation recommendation : recommendations) {
                DocumentReference docRef = colRef.document();
                batch.set(docRef, recommendation);
            }

            ApiFuture<List<WriteResult>> future = batch.commit();
            future.get();

            log.info("[{}] Successfully saved {} recommendations to Firestore.",
                     recordingId != null ? recordingId : "UNKNOWN", recommendations.size());

        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[{}] Error saving batch of recommendations to Firestore.",
                      recordingId != null ? recordingId : "UNKNOWN", e);
            throw new FirestoreInteractionException("Failed to save batch of recommendations for recording " + (recordingId != null ? recordingId : "UNKNOWN"), e);
        } catch (Exception e) {
             log.error("[{}] Unexpected error saving batch of recommendations.",
                       recordingId != null ? recordingId : "UNKNOWN", e);
             throw new FirestoreInteractionException("Unexpected error saving recommendations for recording " + (recordingId != null ? recordingId : "UNKNOWN"), e);
        }
    }

    public List<LearningRecommendation> getLearningRecommendationsByRecordingId(String recordingId) throws FirestoreInteractionException {
        if (recordingId == null || recordingId.isEmpty()) {
            log.error("Cannot retrieve recommendations with a null or empty recordingId.");
            throw new IllegalArgumentException("Recording ID cannot be null or empty.");
        }

        log.info("Retrieving recommendations for recording ID: {} from collection: {}", recordingId, recommendationsCollectionName);
        List<LearningRecommendation> recommendationList = new ArrayList<>();

        try {
            Firestore firestore = getFirestore();
            CollectionReference colRef = firestore.collection(recommendationsCollectionName);

            Query query = colRef.whereEqualTo("recordingId", recordingId)
                                .orderBy("createdAt", Query.Direction.ASCENDING);

            ApiFuture<QuerySnapshot> future = query.get();
            QuerySnapshot querySnapshot = future.get();

            List<QueryDocumentSnapshot> documents = querySnapshot.getDocuments();
            for (QueryDocumentSnapshot document : documents) {
                try {
                    LearningRecommendation recommendation = document.toObject(LearningRecommendation.class);
                    recommendationList.add(recommendation);
                } catch (Exception e) {
                    log.error("Error mapping Firestore document {} to LearningRecommendation object for recordingId {}: {}",
                              document.getId(), recordingId, e.getMessage(), e);
                }
            }

            log.info("Retrieved {} recommendations for recording ID: {}", recommendationList.size(), recordingId);
            return recommendationList;

        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error retrieving recommendations for recording ID: {}", recordingId, e);
            throw new FirestoreInteractionException("Failed to retrieve recommendations for recording " + recordingId, e);
        } catch (Exception e) {
             log.error("Unexpected error retrieving recommendations for recording ID: {}", recordingId, e);
             throw new FirestoreInteractionException("Unexpected error retrieving recommendations for recording " + recordingId, e);
        }
    }




    private Map<String, Object> convertToMap(AudioMetadata metadata) {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", metadata.getUserId());
        map.put("fileName", metadata.getFileName());
        map.put("fileSize", metadata.getFileSize());
        map.put("contentType", metadata.getContentType());
        map.put("title", metadata.getTitle());
        map.put("description", metadata.getDescription());
        map.put("nhostFileId", metadata.getNhostFileId());
        map.put("storageUrl", metadata.getStorageUrl());
        map.put("uploadTimestamp", metadata.getUploadTimestamp());
        map.put("status", metadata.getStatus() != null ? metadata.getStatus().name() : null);
        return map;
    }

    private AudioMetadata fromDocumentSnapshot(DocumentSnapshot document) {
        if (document == null || !document.exists()) {
            return null;
        }
        try {
            Map<String, Object> data = document.getData();
            if (data == null) {
                log.warn("Document snapshot data is null for ID: {}", document.getId());
                return null;
            }

            AudioMetadata metadata = new AudioMetadata();
            metadata.setId(document.getId());
            metadata.setUserId((String) data.get("userId"));
            metadata.setFileName((String) data.get("fileName"));
            Object fileSizeObj = data.get("fileSize");
            if (fileSizeObj instanceof Number) {
                metadata.setFileSize(((Number) fileSizeObj).longValue());
            }
            metadata.setContentType((String) data.get("contentType"));
            metadata.setTitle((String) data.get("title"));
            metadata.setDescription((String) data.get("description"));
            metadata.setNhostFileId((String) data.get("nhostFileId"));
            metadata.setStorageUrl((String) data.get("storageUrl"));

            Object timestampObj = data.get("uploadTimestamp");
            if (timestampObj instanceof Timestamp) {
                 metadata.setUploadTimestamp((Timestamp) timestampObj);
            } else if (timestampObj != null) {
                log.warn("uploadTimestamp field was not of expected type com.google.cloud.Timestamp for document ID: {}. Type found: {}", document.getId(), timestampObj.getClass().getName());
            }


            String statusStr = (String) data.get("status");
            if (statusStr != null) {
                try {
                    metadata.setStatus(ProcessingStatus.valueOf(statusStr));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid status value '{}' found in Firestore for document ID: {}. Setting status to null.", statusStr, document.getId());
                    metadata.setStatus(null);
                }
            } else {
                 metadata.setStatus(ProcessingStatus.UPLOADED);
                 log.debug("Status field missing for document ID: {}. Defaulting to UPLOADED.", document.getId());
            }

            return metadata;
        } catch (Exception e) {
            log.error("Error mapping Firestore document data to AudioMetadata for ID: {}. Error: {}", document.getId(), e.getMessage(), e);
            return null;
        }
    }
}
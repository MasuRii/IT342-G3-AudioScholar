package edu.cit.audioscholar.service;

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
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.cloud.FirestoreClient;

import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.AudioMetadata;

@Service
public class FirebaseService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseService.class);
    private final String audioMetadataCollectionName;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public FirebaseService(@Value("${firebase.firestore.collection.audiometadata}") String audioMetadataCollectionName) {
        this.audioMetadataCollectionName = audioMetadataCollectionName;
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

            ApiFuture<DocumentReference> future = colRef.add(dataMap);
            String generatedId = future.get().getId();
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

    public List<AudioMetadata> getAllAudioMetadata() {
        log.warn("Executing getAllAudioMetadata - Fetching all documents from {}. Consider pagination for large datasets.", audioMetadataCollectionName);
        try {
            Firestore firestore = getFirestore();
            ApiFuture<QuerySnapshot> future = firestore.collection(audioMetadataCollectionName)
                                                     .get();
            List<AudioMetadata> audioMetadataList = new ArrayList<>();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            for (QueryDocumentSnapshot document : documents) {
                AudioMetadata metadata = document.toObject(AudioMetadata.class);
                metadata.setId(document.getId());
                audioMetadataList.add(metadata);
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
                AudioMetadata metadata = document.toObject(AudioMetadata.class);
                metadata.setId(document.getId());
                userMetadataList.add(metadata);
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
                AudioMetadata metadata = document.toObject(AudioMetadata.class);
                metadata.setId(document.getId());
                log.info("Retrieved AudioMetadata document with ID: {}", metadataId);
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
        return map;
    }
}
package edu.cit.audioscholar.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.cloud.FirestoreClient;

import edu.cit.audioscholar.model.AudioMetadata;

@Service
public class FirebaseService {

    private static final Logger LOGGER = Logger.getLogger(FirebaseService.class.getName());
    private final String audioMetadataCollectionName;

    public FirebaseService(@Value("${firebase.firestore.collection.audiometadata}") String audioMetadataCollectionName) {
        this.audioMetadataCollectionName = audioMetadataCollectionName;
    }

    public String getAudioMetadataCollectionName() {
        return audioMetadataCollectionName;
    }

    private Firestore getFirestore() {
        return FirestoreClient.getFirestore();
    }

    public String saveData(String collection, String document, Map<String, Object> data)
            throws ExecutionException, InterruptedException {
        Firestore firestore = getFirestore();
        ApiFuture<WriteResult> future = firestore.collection(collection).document(document).set(data);
        return future.get().getUpdateTime().toString();
    }

    public Map<String, Object> getData(String collection, String document)
            throws ExecutionException, InterruptedException {
        Firestore firestore = getFirestore();
        DocumentSnapshot snapshot = firestore.collection(collection).document(document).get().get();
        return snapshot.exists() ? snapshot.getData() : null;
    }

    public String updateData(String collection, String document, Map<String, Object> data)
            throws ExecutionException, InterruptedException {
        Firestore firestore = getFirestore();
        ApiFuture<WriteResult> future = firestore.collection(collection).document(document).update(data);
        return future.get().getUpdateTime().toString();
    }

    public String deleteData(String collection, String document)
            throws ExecutionException, InterruptedException {
        Firestore firestore = getFirestore();
        ApiFuture<WriteResult> future = firestore.collection(collection).document(document).delete();
        return future.get().getUpdateTime().toString();
    }

     public List<Map<String, Object>> queryCollection(String collection, String field, Object value)
            throws ExecutionException, InterruptedException {
        Firestore firestore = getFirestore();
        ApiFuture<QuerySnapshot> future = firestore.collection(collection).whereEqualTo(field, value).get();
        List<Map<String, Object>> results = new ArrayList<>();

        for (DocumentSnapshot document : future.get().getDocuments()) {
            if (document.exists()) {
                results.add(document.getData());
            }
        }

        return results;
    }


    public AudioMetadata saveAudioMetadata(AudioMetadata metadata) throws ExecutionException, InterruptedException {
        Firestore firestore = getFirestore();
        CollectionReference colRef = firestore.collection(audioMetadataCollectionName);

        ApiFuture<DocumentReference> future = colRef.add(convertToMap(metadata));
        String generatedId = future.get().getId();
        metadata.setId(generatedId);

        LOGGER.log(Level.INFO, "Saved AudioMetadata to Firestore collection {0} with generated ID: {1}",
                   new Object[]{audioMetadataCollectionName, generatedId});

        return metadata;
    }

    public List<AudioMetadata> getAllAudioMetadata() throws ExecutionException, InterruptedException {
        Firestore firestore = getFirestore();
        ApiFuture<QuerySnapshot> future = firestore.collection(audioMetadataCollectionName).get();
        List<AudioMetadata> audioMetadataList = new ArrayList<>();

        for (DocumentSnapshot document : future.get().getDocuments()) {
            if (document.exists()) {
                AudioMetadata metadata = document.toObject(AudioMetadata.class);
                metadata.setId(document.getId());
                audioMetadataList.add(metadata);
            }
        }
        LOGGER.log(Level.INFO, "Retrieved {0} AudioMetadata documents from Firestore.", audioMetadataList.size());
        return audioMetadataList;
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
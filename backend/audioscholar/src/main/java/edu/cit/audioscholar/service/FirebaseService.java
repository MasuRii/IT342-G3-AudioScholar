package edu.cit.audioscholar.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.firebase.cloud.FirestoreClient;
import edu.cit.audioscholar.model.AudioMetadata;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class FirebaseService {

    private static final String AUDIO_METADATA_COLLECTION = "audio_metadata";
    private final Storage storage = StorageOptions.getDefaultInstance().getService(); // Initialize Firebase Storage
    private final String bucketName = "audioscholar-39b22.appspot.com"; // Replace with your Firebase Storage bucket name

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

    public String saveAudioMetadata(AudioMetadata metadata) throws ExecutionException, InterruptedException {
        Firestore firestore = getFirestore();
        // Use the audioId as the document ID in Firestore
        ApiFuture<WriteResult> future = firestore.collection(AUDIO_METADATA_COLLECTION)
                .document(metadata.getId())
                .set(convertToMap(metadata));
        return future.get().getUpdateTime().toString();
    }

    public List<AudioMetadata> getAllAudioMetadata() throws ExecutionException, InterruptedException {
        Firestore firestore = getFirestore();
        ApiFuture<QuerySnapshot> future = firestore.collection(AUDIO_METADATA_COLLECTION).get();
        List<AudioMetadata> audioMetadataList = new ArrayList<>();

        for (DocumentSnapshot document : future.get().getDocuments()) {
            if (document.exists()) {
                audioMetadataList.add(document.toObject(AudioMetadata.class));
            }
        }
        return audioMetadataList;
    }

    public String uploadAudioToStorage(byte[] audioData, String fileName) throws IOException {
        BlobId blobId = BlobId.of(bucketName, "audio_uploads/" + fileName); // Define the storage path
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(getMediaType(fileName)).build();
        storage.create(blobInfo, audioData);
        return blobInfo.getMediaLink(); // Or a different URL format if needed
    }

    private String getMediaType(String fileName) {
        if (fileName.endsWith(".wav")) return "audio/wav";
        if (fileName.endsWith(".mp3")) return "audio/mpeg"; // Use "audio/mpeg" for mp3
        if (fileName.endsWith(".aiff")) return "audio/aiff";
        if (fileName.endsWith(".aac")) return "audio/aac";
        if (fileName.endsWith(".ogg") || fileName.endsWith(".oga")) return "audio/ogg";
        if (fileName.endsWith(".flac")) return "audio/flac";
        return "application/octet-stream"; // Default
    }

    private Map<String, Object> convertToMap(AudioMetadata metadata) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", metadata.getId());
        map.put("fileName", metadata.getFileName());
        map.put("fileSize", metadata.getFileSize());
        map.put("duration", metadata.getDuration());
        map.put("title", metadata.getTitle());
        map.put("description", metadata.getDescription());
        map.put("firebaseStorageUrl", metadata.getFirebaseStorageUrl()); // Add the storage URL
        return map;
    }
}
package edu.cit.audioscholar.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class FirebaseService {

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
}
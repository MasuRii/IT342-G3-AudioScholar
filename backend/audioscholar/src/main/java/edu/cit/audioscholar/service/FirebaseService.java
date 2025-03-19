package main.java.edu.cit.audioscholar.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class FirebaseService {

    public String saveData(String collection, String document, Map<String, Object> data) 
            throws ExecutionException, InterruptedException {
        Firestore firestore = FirestoreClient.getFirestore();
        ApiFuture<WriteResult> future = firestore.collection(collection).document(document).set(data);
        return future.get().getUpdateTime().toString();
    }
    
    public Object getData(String collection, String document) 
            throws ExecutionException, InterruptedException {
        Firestore firestore = FirestoreClient.getFirestore();
        return firestore.collection(collection).document(document).get().get().getData();
    }
    
    // Add more methods for update, delete, query operations as needed
}
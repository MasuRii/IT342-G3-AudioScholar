package edu.cit.audioscholar.service;

import edu.cit.audioscholar.model.Recording;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class RecordingService {
    private static final String COLLECTION_NAME = "recordings";
    
    private final FirebaseService firebaseService;
    private final UserService userService;
    
    public RecordingService(FirebaseService firebaseService, UserService userService) {
        this.firebaseService = firebaseService;
        this.userService = userService;
    }
    
    public Recording createRecording(Recording recording) throws ExecutionException, InterruptedException {
        // Generate ID if not provided
        if (recording.getRecordingId() == null) {
            recording.setRecordingId(UUID.randomUUID().toString());
        }
        
        // Set timestamps
        recording.setCreatedAt(new Date());
        recording.setUpdatedAt(new Date());
        
        // Save to Firestore
        firebaseService.saveData(COLLECTION_NAME, recording.getRecordingId(), recording.toMap());
        
        // Update user's recordings list
        var user = userService.getUserById(recording.getUserId());
        if (user != null) {
            user.getRecordingIds().add(recording.getRecordingId());
            userService.updateUser(user);
        }
        
        return recording;
    }
    
    public Recording getRecordingById(String recordingId) throws ExecutionException, InterruptedException {
        Map<String, Object> data = firebaseService.getData(COLLECTION_NAME, recordingId);
        return data != null ? Recording.fromMap(data) : null;
    }
    
    public Recording updateRecording(Recording recording) throws ExecutionException, InterruptedException {
        recording.setUpdatedAt(new Date());
        firebaseService.updateData(COLLECTION_NAME, recording.getRecordingId(), recording.toMap());
        return recording;
    }
    
    public void deleteRecording(String recordingId) throws ExecutionException, InterruptedException {
        Recording recording = getRecordingById(recordingId);
        if (recording != null) {
            // Remove from user's recordings list
            var user = userService.getUserById(recording.getUserId());
            if (user != null) {
                user.getRecordingIds().remove(recordingId);
                userService.updateUser(user);
            }
            
            // Delete the recording
            firebaseService.deleteData(COLLECTION_NAME, recordingId);
        }
    }
    
    public List<Recording> getRecordingsByUserId(String userId) throws ExecutionException, InterruptedException {
        List<Map<String, Object>> results = firebaseService.queryCollection(COLLECTION_NAME, "userId", userId);
        return results.stream().map(Recording::fromMap).collect(Collectors.toList());
    }
}
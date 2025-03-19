package edu.cit.audioscholar.service;

import edu.cit.audioscholar.model.Summary;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
public class SummaryService {
    private static final String COLLECTION_NAME = "summaries";
    
    private final FirebaseService firebaseService;
    private final RecordingService recordingService;
    
    public SummaryService(FirebaseService firebaseService, RecordingService recordingService) {
        this.firebaseService = firebaseService;
        this.recordingService = recordingService;
    }
    
    public Summary createSummary(Summary summary) throws ExecutionException, InterruptedException {
        // Generate ID if not provided
        if (summary.getSummaryId() == null) {
            summary.setSummaryId(UUID.randomUUID().toString());
        }
        
        // Set timestamp
        summary.setCreatedAt(new Date());
        
        // Save to Firestore
        firebaseService.saveData(COLLECTION_NAME, summary.getSummaryId(), summary.toMap());
        
        // Update recording with summary ID
        var recording = recordingService.getRecordingById(summary.getRecordingId());
        if (recording != null) {
            recording.setSummaryId(summary.getSummaryId());
            recordingService.updateRecording(recording);
        }
        
        return summary;
    }
    
    public Summary getSummaryById(String summaryId) throws ExecutionException, InterruptedException {
        Map<String, Object> data = firebaseService.getData(COLLECTION_NAME, summaryId);
        return data != null ? Summary.fromMap(data) : null;
    }
    
    public Summary getSummaryByRecordingId(String recordingId) throws ExecutionException, InterruptedException {
        var results = firebaseService.queryCollection(COLLECTION_NAME, "recordingId", recordingId);
        return !results.isEmpty() ? Summary.fromMap(results.get(0)) : null;
    }
    
    public Summary updateSummary(Summary summary) throws ExecutionException, InterruptedException {
        firebaseService.updateData(COLLECTION_NAME, summary.getSummaryId(), summary.toMap());
        return summary;
    }
    
    public void deleteSummary(String summaryId) throws ExecutionException, InterruptedException {
        Summary summary = getSummaryById(summaryId);
        if (summary != null) {
            // Update recording to remove summary reference
            var recording = recordingService.getRecordingById(summary.getRecordingId());
            if (recording != null && summaryId.equals(recording.getSummaryId())) {
                recording.setSummaryId(null);
                recordingService.updateRecording(recording);
            }
            
            // Delete the summary
            firebaseService.deleteData(COLLECTION_NAME, summaryId);
        }
    }
}
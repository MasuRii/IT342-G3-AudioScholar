package edu.cit.audioscholar.service;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Service;
import edu.cit.audioscholar.model.Summary;

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
        if (summary.getSummaryId() == null) {
            summary.setSummaryId(UUID.randomUUID().toString());
        }

        summary.setCreatedAt(new Date());

        firebaseService.saveData(COLLECTION_NAME, summary.getSummaryId(), summary.toMap());

        var recording = recordingService.getRecordingById(summary.getRecordingId());
        if (recording != null) {
            recording.setSummaryId(summary.getSummaryId());
            recordingService.updateRecording(recording);
        }

        return summary;
    }

    public Summary getSummaryById(String summaryId)
            throws ExecutionException, InterruptedException {
        Map<String, Object> data = firebaseService.getData(COLLECTION_NAME, summaryId);
        return data != null ? Summary.fromMap(data) : null;
    }

    public Summary getSummaryByRecordingId(String recordingId)
            throws ExecutionException, InterruptedException {
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
            var recording = recordingService.getRecordingById(summary.getRecordingId());
            if (recording != null && summaryId.equals(recording.getSummaryId())) {
                recording.setSummaryId(null);
                recordingService.updateRecording(recording);
            }

            firebaseService.deleteData(COLLECTION_NAME, summaryId);
        }
    }
}

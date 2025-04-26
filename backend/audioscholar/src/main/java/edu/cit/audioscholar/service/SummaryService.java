package edu.cit.audioscholar.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import edu.cit.audioscholar.model.Recording;
import edu.cit.audioscholar.model.Summary;

@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);
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
            log.debug("Generated new summaryId: {}", summary.getSummaryId());
        }


        log.info("Attempting to save Summary object (ID: {}) using POJO method.",
                summary.getSummaryId());
        firebaseService.saveData(COLLECTION_NAME, summary.getSummaryId(), summary);
        log.info("Firestore saveData call completed for summary ID: {}", summary.getSummaryId());


        try {
            Recording recording = recordingService.getRecordingById(summary.getRecordingId());
            if (recording != null) {
                if (!summary.getSummaryId().equals(recording.getSummaryId())) {
                    log.info("Linking summary ID {} to recording ID {}", summary.getSummaryId(),
                            recording.getRecordingId());
                    recording.setSummaryId(summary.getSummaryId());
                    recordingService.updateRecording(recording);
                } else {
                    log.debug("Summary ID {} already linked to recording ID {}",
                            summary.getSummaryId(), recording.getRecordingId());
                }
            } else {
                log.warn("Could not find Recording with ID {} to link summary {}.",
                        summary.getRecordingId(), summary.getSummaryId());
            }
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error retrieving or updating recording {} while linking summary {}: {}",
                    summary.getRecordingId(), summary.getSummaryId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error(
                    "Unexpected error retrieving or updating recording {} while linking summary {}: {}",
                    summary.getRecordingId(), summary.getSummaryId(), e.getMessage(), e);
        }

        return summary;
    }

    public Summary getSummaryById(String summaryId)
            throws ExecutionException, InterruptedException {
        log.debug("Fetching summary by ID: {}", summaryId);
        Map<String, Object> data = firebaseService.getData(COLLECTION_NAME, summaryId);
        if (data == null) {
            log.warn("Summary not found for ID: {}", summaryId);
            return null;
        }
        Summary summary = Summary.fromMap(data);
        log.debug("Successfully fetched and mapped summary ID: {}", summaryId);
        return summary;
    }

    public Summary getSummaryByRecordingId(String recordingId)
            throws ExecutionException, InterruptedException {
        log.debug("Fetching summary by recordingId: {}", recordingId);
        List<Map<String, Object>> results =
                firebaseService.queryCollection(COLLECTION_NAME, "recordingId", recordingId);
        if (results.isEmpty()) {
            log.warn("No summary found for recordingId: {}", recordingId);
            return null;
        }
        if (results.size() > 1) {
            log.warn("Multiple summaries found for recordingId: {}. Returning the first one.",
                    recordingId);
        }
        Summary summary = Summary.fromMap(results.get(0));
        log.debug("Successfully fetched and mapped summary for recordingId: {}", recordingId);
        return summary;
    }

    public Summary updateSummary(Summary summary) throws ExecutionException, InterruptedException {
        if (summary == null || summary.getSummaryId() == null) {
            throw new IllegalArgumentException(
                    "Summary object and its ID cannot be null for update.");
        }
        log.info("Attempting to update Summary object (ID: {}) using POJO merge method.",
                summary.getSummaryId());
        firebaseService.updateData(COLLECTION_NAME, summary.getSummaryId(), summary);
        log.info("Firestore updateData call completed for summary ID: {}", summary.getSummaryId());
        return summary;
    }

    public void deleteSummary(String summaryId) throws ExecutionException, InterruptedException {
        log.info("Attempting to delete summary with ID: {}", summaryId);
        Summary summary = getSummaryById(summaryId);

        if (summary != null) {
            try {
                Recording recording = recordingService.getRecordingById(summary.getRecordingId());
                if (recording != null && summaryId.equals(recording.getSummaryId())) {
                    log.info("Unlinking summary ID {} from recording ID {}", summaryId,
                            recording.getRecordingId());
                    recording.setSummaryId(null);
                    recordingService.updateRecording(recording);
                } else if (recording != null) {
                    log.warn(
                            "Recording {} found, but it was not linked to summary {} (current link: {}). No unlink performed.",
                            summary.getRecordingId(), summaryId, recording.getSummaryId());
                } else {
                    log.warn("Recording {} not found while trying to unlink summary {}.",
                            summary.getRecordingId(), summaryId);
                }
            } catch (ExecutionException | InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error(
                        "Error retrieving or updating recording {} while unlinking summary {}: {}",
                        summary.getRecordingId(), summaryId, e.getMessage(), e);
            } catch (Exception e) {
                log.error(
                        "Unexpected error retrieving or updating recording {} while unlinking summary {}: {}",
                        summary.getRecordingId(), summaryId, e.getMessage(), e);
            }

            firebaseService.deleteData(COLLECTION_NAME, summaryId);
            log.info("Successfully deleted summary document ID: {}", summaryId);

        } else {
            log.warn("Summary document with ID {} not found. Cannot delete.", summaryId);
        }
    }
}

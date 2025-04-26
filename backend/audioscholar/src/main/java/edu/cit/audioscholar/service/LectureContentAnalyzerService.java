package edu.cit.audioscholar.service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import edu.cit.audioscholar.dto.AnalysisResults;
import edu.cit.audioscholar.model.Summary;

@Service
public class LectureContentAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(LectureContentAnalyzerService.class);

    private final SummaryService summaryService;

    public LectureContentAnalyzerService(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    public AnalysisResults analyzeLectureContent(String recordingId) {
        log.info("Starting content analysis (retrieving topics) for recording ID: {}", recordingId);
        try {
            Summary summary = summaryService.getSummaryByRecordingId(recordingId);

            if (summary == null) {
                log.warn("Summary not found for recording ID: {}", recordingId);
                return new AnalysisResults(Collections.emptyList());
            }

            List<String> topics = summary.getTopics();

            if (topics == null || topics.isEmpty()) {
                log.warn(
                        "No topics found in the stored summary for recording ID: {}. The summary might have been generated before topics were added, or topic extraction failed.",
                        recordingId);
                return new AnalysisResults(Collections.emptyList());
            }

            log.info("Successfully retrieved {} topics from summary for recording ID: {}",
                    topics.size(), recordingId);
            return new AnalysisResults(topics);

        } catch (ExecutionException | InterruptedException e) {
            log.error("Failed to retrieve summary data from Firestore for recording ID: {}",
                    recordingId, e);
            Thread.currentThread().interrupt();
            return new AnalysisResults(Collections.emptyList());
        } catch (Exception e) {
            log.error(
                    "Unexpected error during content analysis (topic retrieval) for recording ID: {}",
                    recordingId, e);
            return new AnalysisResults(Collections.emptyList());
        }
    }
}

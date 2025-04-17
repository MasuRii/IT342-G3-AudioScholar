package edu.cit.audioscholar.service;

import edu.cit.audioscholar.dto.AnalysisResults;
import edu.cit.audioscholar.model.Recording;
import edu.cit.audioscholar.model.Summary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class LectureContentAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(LectureContentAnalyzerService.class);

    private final RecordingService recordingService;
    private final GeminiService geminiService;
    private final SummaryService summaryService;

    public LectureContentAnalyzerService(RecordingService recordingService, GeminiService geminiService, SummaryService summaryService) {
        this.recordingService = recordingService;
        this.geminiService = geminiService;
        this.summaryService = summaryService;
    }

    public AnalysisResults analyzeLectureContent(String recordingId) {
        log.info("Starting content analysis for recording ID: {}", recordingId);

        try {

            Summary summary = summaryService.getSummaryByRecordingId(recordingId);

            if (summary == null) {
                log.warn("Summary not found for recording ID: {}", recordingId);
                return new AnalysisResults("Summary not found for the recording.");
            }

            String summaryText = summary.getFullText();

            if (summaryText == null || summaryText.isBlank()) {
                 log.warn("Summary fullText is missing or empty for recording ID: {}", recordingId);
                 return new AnalysisResults(Collections.emptyList());
            }

            log.debug("Retrieved summary fullText (length: {}) for recording ID: {}", summaryText.length(), recordingId);

            List<String> keywords = geminiService.extractKeywordsAndTopics(summaryText);

            if (keywords.isEmpty()) {
                log.warn("No keywords/topics were extracted for recording ID: {}. Gemini might have returned an empty list or encountered an error.", recordingId);
                return new AnalysisResults(Collections.emptyList());
            }

            log.info("Successfully extracted {} keywords/topics for recording ID: {}", keywords.size(), recordingId);
            return new AnalysisResults(keywords);

        } catch (ExecutionException | InterruptedException e) {
            log.error("Failed to retrieve data from Firestore for recording ID: {}", recordingId, e);
            Thread.currentThread().interrupt();
            return new AnalysisResults("Failed to retrieve recording/summary data from database.");
        } catch (Exception e) {
            log.error("Unexpected error during content analysis for recording ID: {}", recordingId, e);
            return new AnalysisResults("An unexpected error occurred during analysis.");
        }
    }
}
package edu.cit.audioscholar.dto;

import java.util.Date;
import java.util.List;
import edu.cit.audioscholar.model.Summary;

public class SummaryDto {

    private String summaryId;
    private String recordingId;
    private String fullText;
    private String condensedSummary;
    private List<String> keyPoints;
    private List<String> topics;
    private String formattedSummaryText;
    private Date createdAt;

    private SummaryDto() {}

    public String getSummaryId() {
        return summaryId;
    }

    public String getRecordingId() {
        return recordingId;
    }

    public String getFullText() {
        return fullText;
    }

    public String getCondensedSummary() {
        return condensedSummary;
    }

    public List<String> getKeyPoints() {
        return keyPoints;
    }

    public List<String> getTopics() {
        return topics;
    }

    public String getFormattedSummaryText() {
        return formattedSummaryText;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public static SummaryDto fromModel(Summary summary) {
        if (summary == null) {
            return null;
        }
        SummaryDto dto = new SummaryDto();
        dto.summaryId = summary.getSummaryId();
        dto.recordingId = summary.getRecordingId();
        dto.fullText = summary.getFullText();
        dto.condensedSummary = summary.getCondensedSummary();
        dto.keyPoints = summary.getKeyPoints();
        dto.topics = summary.getTopics();
        dto.formattedSummaryText = summary.getFormattedSummaryText();
        dto.createdAt = summary.getCreatedAt();
        return dto;
    }
}

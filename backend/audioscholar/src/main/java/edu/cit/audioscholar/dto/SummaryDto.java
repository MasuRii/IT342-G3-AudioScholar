package edu.cit.audioscholar.dto;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import edu.cit.audioscholar.model.Summary;

public class SummaryDto {

    private String summaryId;
    private String recordingId;
    private List<String> keyPoints;
    private List<String> topics;
    private List<Map<String, String>> glossary;
    private String formattedSummaryText;
    private Date createdAt;

    private SummaryDto() {}


    public String getSummaryId() {
        return summaryId;
    }

    public String getRecordingId() {
        return recordingId;
    }


    public List<String> getKeyPoints() {
        return (keyPoints != null) ? Collections.unmodifiableList(keyPoints) : null;
    }

    public List<String> getTopics() {
        return (topics != null) ? Collections.unmodifiableList(topics) : null;
    }

    public List<Map<String, String>> getGlossary() {
        return (glossary != null) ? Collections.unmodifiableList(glossary) : null;
    }

    public String getFormattedSummaryText() {
        return formattedSummaryText;
    }

    public Date getCreatedAt() {
        return (createdAt != null) ? (Date) createdAt.clone() : null;
    }

    public static SummaryDto fromModel(Summary summary) {
        if (summary == null) {
            return null;
        }

        SummaryDto dto = new SummaryDto();
        dto.summaryId = summary.getSummaryId();
        dto.recordingId = summary.getRecordingId();
        dto.keyPoints = summary.getKeyPoints();
        dto.topics = summary.getTopics();
        dto.glossary = summary.getGlossary();
        dto.formattedSummaryText = summary.getFormattedSummaryText();
        dto.createdAt = summary.getCreatedAt();

        return dto;
    }
}

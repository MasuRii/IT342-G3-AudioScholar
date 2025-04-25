package edu.cit.audioscholar.model;

import java.util.*;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.ServerTimestamp;

public class Summary {
    private String summaryId;
    private String recordingId;
    private List<String> keyPoints;
    private List<String> topics;
    private List<Map<String, String>> glossary;
    private String formattedSummaryText;

    @ServerTimestamp
    private Date createdAt;

    public Summary() {
        this.keyPoints = new ArrayList<>();
        this.topics = new ArrayList<>();
        this.glossary = new ArrayList<>();
    }

    public Summary(String summaryId, String recordingId, String formattedSummaryText) {
        this();
        this.summaryId = summaryId;
        this.recordingId = recordingId;
        this.formattedSummaryText = formattedSummaryText;
    }

    public String getSummaryId() {
        return summaryId;
    }

    public void setSummaryId(String summaryId) {
        this.summaryId = summaryId;
    }

    public String getRecordingId() {
        return recordingId;
    }

    public void setRecordingId(String recordingId) {
        this.recordingId = recordingId;
    }


    public List<String> getKeyPoints() {
        return keyPoints;
    }

    public void setKeyPoints(List<String> keyPoints) {
        this.keyPoints = (keyPoints != null) ? new ArrayList<>(keyPoints) : new ArrayList<>();
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = (topics != null) ? new ArrayList<>(topics) : new ArrayList<>();
    }

    public List<Map<String, String>> getGlossary() {
        return glossary;
    }

    public void setGlossary(List<Map<String, String>> glossary) {
        this.glossary = (glossary != null) ? new ArrayList<>(glossary) : new ArrayList<>();
    }

    public String getFormattedSummaryText() {
        return formattedSummaryText;
    }

    public void setFormattedSummaryText(String formattedSummaryText) {
        this.formattedSummaryText = formattedSummaryText;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("summaryId", summaryId);
        map.put("recordingId", recordingId);
        map.put("keyPoints", keyPoints);
        map.put("topics", topics);
        map.put("glossary", glossary);
        map.put("formattedSummaryText", formattedSummaryText);
        return map;
    }

    public static Summary fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Summary summary = new Summary();
        summary.summaryId = (String) map.get("summaryId");
        summary.recordingId = (String) map.get("recordingId");
        summary.formattedSummaryText = (String) map.get("formattedSummaryText");

        Object keyPointsObj = map.get("keyPoints");
        if (keyPointsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> keyPointsList = (List<String>) keyPointsObj;
            summary.keyPoints = new ArrayList<>(keyPointsList);
        } else {
            summary.keyPoints = new ArrayList<>();
        }

        Object topicsObj = map.get("topics");
        if (topicsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> topicsList = (List<String>) topicsObj;
            summary.topics = new ArrayList<>(topicsList);
        } else {
            summary.topics = new ArrayList<>();
        }

        Object glossaryObj = map.get("glossary");
        if (glossaryObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<?> rawGlossaryList = (List<?>) glossaryObj;
            List<Map<String, String>> glossaryList = new ArrayList<>();
            for (Object item : rawGlossaryList) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rawMap = (Map<String, Object>) item;
                    Map<String, String> glossaryItem = new HashMap<>();
                    Object termObj = rawMap.get("term");
                    Object defObj = rawMap.get("definition");

                    if (termObj instanceof String && defObj instanceof String) {
                        glossaryItem.put("term", (String) termObj);
                        glossaryItem.put("definition", (String) defObj);
                        glossaryList.add(glossaryItem);
                    } else {
                        System.err.println(
                                "Warning: Invalid glossary item structure (non-string term/definition) found in Firestore map: "
                                        + item);
                    }
                } else {
                    System.err.println(
                            "Warning: Invalid glossary item type (not a Map) found in Firestore list: "
                                    + (item != null ? item.getClass() : "null"));
                }
            }
            summary.glossary = glossaryList;
        } else {
            summary.glossary = new ArrayList<>();
        }

        Object createdAtObj = map.get("createdAt");
        if (createdAtObj instanceof Timestamp) {
            summary.createdAt = ((Timestamp) createdAtObj).toDate();
        } else if (createdAtObj instanceof Date) {
            summary.createdAt = (Date) createdAtObj;
        }

        return summary;
    }

    @Override
    public String toString() {
        return "Summary{" + "summaryId='" + summaryId + '\'' + ", recordingId='" + recordingId
                + '\'' + ", keyPoints=" + (keyPoints != null ? keyPoints.size() : 0) + ", topics="
                + (topics != null ? topics.size() : 0) + ", glossary="
                + (glossary != null ? glossary.size() : 0) + ", createdAt=" + createdAt + '}';
    }
}

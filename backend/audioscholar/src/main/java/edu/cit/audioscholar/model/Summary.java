package edu.cit.audioscholar.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Summary {
    private String summaryId;
    private String recordingId;
    private String fullText;
    private String condensedSummary;
    private List<String> keyPoints;
    private List<String> topics;
    private Date createdAt;
    
    public Summary() {
        this.keyPoints = new ArrayList<>();
        this.topics = new ArrayList<>();
        this.createdAt = new Date();
    }
    
    public Summary(String summaryId, String recordingId, String fullText) {
        this();
        this.summaryId = summaryId;
        this.recordingId = recordingId;
        this.fullText = fullText;
    }
    
    // Getters and setters
    public String getSummaryId() { return summaryId; }
    public void setSummaryId(String summaryId) { this.summaryId = summaryId; }
    
    public String getRecordingId() { return recordingId; }
    public void setRecordingId(String recordingId) { this.recordingId = recordingId; }
    
    public String getFullText() { return fullText; }
    public void setFullText(String fullText) { this.fullText = fullText; }
    
    public String getCondensedSummary() { return condensedSummary; }
    public void setCondensedSummary(String condensedSummary) { this.condensedSummary = condensedSummary; }
    
    public List<String> getKeyPoints() { return keyPoints; }
    public void setKeyPoints(List<String> keyPoints) { this.keyPoints = keyPoints; }
    
    public List<String> getTopics() { return topics; }
    public void setTopics(List<String> topics) { this.topics = topics; }
    
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    
    // Convert to Firestore document
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("summaryId", summaryId);
        map.put("recordingId", recordingId);
        map.put("fullText", fullText);
        map.put("condensedSummary", condensedSummary);
        map.put("keyPoints", keyPoints);
        map.put("topics", topics);
        map.put("createdAt", createdAt);
        return map;
    }
    
    // Create from Firestore document
    public static Summary fromMap(Map<String, Object> map) {
        Summary summary = new Summary();
        summary.summaryId = (String) map.get("summaryId");
        summary.recordingId = (String) map.get("recordingId");
        summary.fullText = (String) map.get("fullText");
        summary.condensedSummary = (String) map.get("condensedSummary");
        
        List<String> keyPoints = (List<String>) map.get("keyPoints");
        if (keyPoints != null) {
            summary.keyPoints = keyPoints;
        }
        
        List<String> topics = (List<String>) map.get("topics");
        if (topics != null) {
            summary.topics = topics;
        }
        
        summary.createdAt = ((com.google.cloud.Timestamp) map.get("createdAt")).toDate();
        return summary;
    }
}
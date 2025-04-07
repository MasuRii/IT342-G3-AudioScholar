package edu.cit.audioscholar.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Recording {
    private String recordingId;
    private String userId;
    private String title;
    private String audioUrl;
    private Date createdAt;
    private Date updatedAt;
    private String duration;
    private String summaryId;
    private String fileName; // Added fileName field

    public Recording() {
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }

    public Recording(String recordingId, String userId, String title, String audioUrl) {
        this();
        this.recordingId = recordingId;
        this.userId = userId;
        this.title = title;
        this.audioUrl = audioUrl;
    }

    // Getters and setters
    public String getRecordingId() { return recordingId; }
    public void setRecordingId(String recordingId) { this.recordingId = recordingId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getSummaryId() { return summaryId; }
    public void setSummaryId(String summaryId) { this.summaryId = summaryId; }

    // Getter and Setter for fileName
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }


    // Convert to Firestore document
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("recordingId", recordingId);
        map.put("userId", userId);
        map.put("title", title);
        map.put("audioUrl", audioUrl);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        map.put("duration", duration);
        map.put("summaryId", summaryId);
        map.put("fileName", fileName); // Added fileName to the map
        return map;
    }

    // Create from Firestore document
    public static Recording fromMap(Map<String, Object> map) {
        Recording recording = new Recording();
        recording.recordingId = (String) map.get("recordingId");
        recording.userId = (String) map.get("userId");
        recording.title = (String) map.get("title");
        recording.audioUrl = (String) map.get("audioUrl");
        recording.createdAt = ((com.google.cloud.Timestamp) map.get("createdAt")).toDate();
        recording.updatedAt = ((com.google.cloud.Timestamp) map.get("updatedAt")).toDate();
        recording.duration = (String) map.get("duration");
        recording.summaryId = (String) map.get("summaryId");
        recording.fileName = (String) map.get("fileName"); // Retrieve fileName from the map
        return recording;
    }
}
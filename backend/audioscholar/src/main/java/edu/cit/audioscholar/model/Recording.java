package edu.cit.audioscholar.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Recording {

    private String recordingId;
    private String userId;
    private String title;
    private String audioUrl;
    private Date createdAt;
    private Date updatedAt;
    private String duration;
    private String summaryId;
    private String fileName;
    private List<String> recommendationIds;

    public Recording() {
        this.createdAt = new Date();
        this.updatedAt = new Date();
        this.recommendationIds = new ArrayList<>();
    }

    public Recording(String recordingId, String userId, String title, String audioUrl) {
        this();
        this.recordingId = recordingId;
        this.userId = userId;
        this.title = title;
        this.audioUrl = audioUrl;
    }

    public String getRecordingId() {
        return recordingId;
    }

    public void setRecordingId(String recordingId) {
        this.recordingId = recordingId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getSummaryId() {
        return summaryId;
    }

    public void setSummaryId(String summaryId) {
        this.summaryId = summaryId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public List<String> getRecommendationIds() {
        return recommendationIds;
    }

    public void setRecommendationIds(List<String> recommendationIds) {
        this.recommendationIds = (recommendationIds != null) ? new ArrayList<>(recommendationIds)
                : new ArrayList<>();
    }


    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (recordingId != null)
            map.put("recordingId", recordingId);
        if (userId != null)
            map.put("userId", userId);
        if (title != null)
            map.put("title", title);
        if (audioUrl != null)
            map.put("audioUrl", audioUrl);
        if (createdAt != null)
            map.put("createdAt", createdAt);
        if (updatedAt != null)
            map.put("updatedAt", updatedAt);
        if (duration != null)
            map.put("duration", duration);
        if (summaryId != null)
            map.put("summaryId", summaryId);
        if (fileName != null)
            map.put("fileName", fileName);
        if (recommendationIds != null && !recommendationIds.isEmpty()) {
            map.put("recommendationIds", recommendationIds);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Recording fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Recording recording = new Recording();
        recording.recordingId = (String) map.get("recordingId");
        recording.userId = (String) map.get("userId");
        recording.title = (String) map.get("title");
        recording.audioUrl = (String) map.get("audioUrl");

        Object createdAtObj = map.get("createdAt");
        if (createdAtObj instanceof com.google.cloud.Timestamp) {
            recording.createdAt = ((com.google.cloud.Timestamp) createdAtObj).toDate();
        } else if (createdAtObj instanceof Date) {
            recording.createdAt = (Date) createdAtObj;
        }

        Object updatedAtObj = map.get("updatedAt");
        if (updatedAtObj instanceof com.google.cloud.Timestamp) {
            recording.updatedAt = ((com.google.cloud.Timestamp) updatedAtObj).toDate();
        } else if (updatedAtObj instanceof Date) {
            recording.updatedAt = (Date) updatedAtObj;
        }

        recording.duration = (String) map.get("duration");
        recording.summaryId = (String) map.get("summaryId");
        recording.fileName = (String) map.get("fileName");

        Object recIdsObj = map.get("recommendationIds");
        if (recIdsObj instanceof List) {
            try {
                List<?> rawList = (List<?>) recIdsObj;
                List<String> stringList = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof String) {
                        stringList.add((String) item);
                    } else if (item != null) {
                        System.err.println(
                                "Warning: Non-string item found in recommendationIds list: "
                                        + item.getClass().getName());
                        stringList.add(item.toString());
                    }
                }
                recording.recommendationIds = stringList;
            } catch (ClassCastException e) {
                System.err.println(
                        "Warning: Could not cast recommendationIds list items to String. List content: "
                                + recIdsObj);
                recording.recommendationIds = new ArrayList<>();
            }
        } else if (recIdsObj != null) {
            System.err.println("Warning: recommendationIds field is not a List. Type: "
                    + recIdsObj.getClass().getName());
            recording.recommendationIds = new ArrayList<>();
        }

        return recording;
    }

    @SuppressWarnings("unchecked")
    public static Recording fromMap(String documentId, Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Recording recording = new Recording();
        recording.recordingId = documentId;
        recording.userId = (String) map.get("userId");
        recording.title = (String) map.get("title");
        recording.audioUrl = (String) map.get("audioUrl");

        Object createdAtObj = map.get("createdAt");
        if (createdAtObj instanceof com.google.cloud.Timestamp) {
            recording.createdAt = ((com.google.cloud.Timestamp) createdAtObj).toDate();
        } else if (createdAtObj instanceof Date) {
            recording.createdAt = (Date) createdAtObj;
        }

        Object updatedAtObj = map.get("updatedAt");
        if (updatedAtObj instanceof com.google.cloud.Timestamp) {
            recording.updatedAt = ((com.google.cloud.Timestamp) updatedAtObj).toDate();
        } else if (updatedAtObj instanceof Date) {
            recording.updatedAt = (Date) updatedAtObj;
        }

        recording.duration = (String) map.get("duration");
        recording.summaryId = (String) map.get("summaryId");
        recording.fileName = (String) map.get("fileName");

        Object recIdsObj = map.get("recommendationIds");
        if (recIdsObj instanceof List) {
            try {
                List<?> rawList = (List<?>) recIdsObj;
                List<String> stringList = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof String) {
                        stringList.add((String) item);
                    } else if (item != null) {
                        System.err.println(
                                "Warning: Non-string item found in recommendationIds list: "
                                        + item.getClass().getName());
                        stringList.add(item.toString());
                    }
                }
                recording.recommendationIds = stringList;
            } catch (ClassCastException e) {
                System.err.println(
                        "Warning: Could not cast recommendationIds list items to String. List content: "
                                + recIdsObj);
                recording.recommendationIds = new ArrayList<>();
            }
        } else if (recIdsObj != null) {
            System.err.println("Warning: recommendationIds field is not a List. Type: "
                    + recIdsObj.getClass().getName());
            recording.recommendationIds = new ArrayList<>();
        }

        return recording;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Recording recording = (Recording) o;
        return Objects.equals(recordingId, recording.recordingId)
                && Objects.equals(userId, recording.userId)
                && Objects.equals(title, recording.title)
                && Objects.equals(audioUrl, recording.audioUrl)
                && Objects.equals(createdAt, recording.createdAt)
                && Objects.equals(updatedAt, recording.updatedAt)
                && Objects.equals(duration, recording.duration)
                && Objects.equals(summaryId, recording.summaryId)
                && Objects.equals(fileName, recording.fileName)
                && Objects.equals(recommendationIds, recording.recommendationIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordingId, userId, title, audioUrl, createdAt, updatedAt, duration,
                summaryId, fileName, recommendationIds);
    }

    @Override
    public String toString() {
        return "Recording{" + "recordingId='" + recordingId + '\'' + ", userId='" + userId + '\''
                + ", title='" + title + '\'' + ", audioUrl='" + audioUrl + '\'' + ", createdAt="
                + createdAt + ", updatedAt=" + updatedAt + ", duration='" + duration + '\''
                + ", summaryId='" + summaryId + '\'' + ", fileName='" + fileName + '\''
                + ", recommendationIds="
                + (recommendationIds != null ? recommendationIds.size() : "null") + '}';
    }
}

package edu.cit.audioscholar.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.firestore.annotation.ServerTimestamp;

public class LearningRecommendation {
    @DocumentId
    private String recommendationId;
    private String videoId;
    private String title;
    private String descriptionSnippet;
    private String thumbnailUrl;
    private String recordingId;
    @ServerTimestamp
    private Date createdAt;

    public LearningRecommendation() {}

    public LearningRecommendation(String videoId, String title, String descriptionSnippet,
            String thumbnailUrl, String recordingId) {
        this.videoId = videoId;
        this.title = title;
        this.descriptionSnippet = descriptionSnippet;
        this.thumbnailUrl = thumbnailUrl;
        this.recordingId = recordingId;
    }

    public String getRecommendationId() {
        return recommendationId;
    }

    public void setRecommendationId(String recommendationId) {
        this.recommendationId = recommendationId;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescriptionSnippet() {
        return descriptionSnippet;
    }

    public void setDescriptionSnippet(String descriptionSnippet) {
        this.descriptionSnippet = descriptionSnippet;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getRecordingId() {
        return recordingId;
    }

    public void setRecordingId(String recordingId) {
        this.recordingId = recordingId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("videoId", videoId);
        map.put("title", title);
        map.put("descriptionSnippet", descriptionSnippet);
        map.put("thumbnailUrl", thumbnailUrl);
        map.put("recordingId", recordingId);
        map.put("createdAt", createdAt);
        return map;
    }

    public static LearningRecommendation fromMap(Map<String, Object> map) {
        LearningRecommendation recommendation = new LearningRecommendation();
        recommendation.videoId = (String) map.get("videoId");
        recommendation.title = (String) map.get("title");
        recommendation.descriptionSnippet = (String) map.get("descriptionSnippet");
        recommendation.thumbnailUrl = (String) map.get("thumbnailUrl");
        recommendation.recordingId = (String) map.get("recordingId");
        Object createdAtObj = map.get("createdAt");
        if (createdAtObj instanceof Timestamp) {
            recommendation.createdAt = ((Timestamp) createdAtObj).toDate();
        } else if (createdAtObj instanceof Date) {
            recommendation.createdAt = (Date) createdAtObj;
        }
        return recommendation;
    }

    @Override
    public String toString() {
        return "LearningRecommendation{" + "recommendationId='" + recommendationId + '\''
                + ", videoId='" + videoId + '\'' + ", title='" + title + '\''
                + ", descriptionSnippet='" + descriptionSnippet + '\'' + ", thumbnailUrl='"
                + thumbnailUrl + '\'' + ", recordingId='" + recordingId + '\'' + ", createdAt="
                + createdAt + '}';
    }
}

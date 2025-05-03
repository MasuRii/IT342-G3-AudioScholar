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
    private String fallbackThumbnailUrl;
    private String thumbnailQuality;
    private String recordingId;
    private Integer relevanceScore;
    private Boolean isEducational;
    private String channelTitle;
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
        this.relevanceScore = 0;
        this.isEducational = false;
        this.thumbnailQuality = "default";
        this.fallbackThumbnailUrl = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
    }

    public LearningRecommendation(String videoId, String title, String descriptionSnippet,
            String thumbnailUrl, String recordingId, Integer relevanceScore, Boolean isEducational,
            String channelTitle) {
        this.videoId = videoId;
        this.title = title;
        this.descriptionSnippet = descriptionSnippet;
        this.thumbnailUrl = thumbnailUrl;
        this.recordingId = recordingId;
        this.relevanceScore = relevanceScore;
        this.isEducational = isEducational;
        this.channelTitle = channelTitle;
        this.thumbnailQuality = "maxres";
        this.fallbackThumbnailUrl = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
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

    public String getFallbackThumbnailUrl() {
        return fallbackThumbnailUrl;
    }

    public void setFallbackThumbnailUrl(String fallbackThumbnailUrl) {
        this.fallbackThumbnailUrl = fallbackThumbnailUrl;
    }

    public String getThumbnailQuality() {
        return thumbnailQuality;
    }

    public void setThumbnailQuality(String thumbnailQuality) {
        this.thumbnailQuality = thumbnailQuality;
    }

    public String getRecordingId() {
        return recordingId;
    }

    public void setRecordingId(String recordingId) {
        this.recordingId = recordingId;
    }

    public Integer getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(Integer relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    public Boolean getIsEducational() {
        return isEducational;
    }

    public void setIsEducational(Boolean isEducational) {
        this.isEducational = isEducational;
    }

    public String getChannelTitle() {
        return channelTitle;
    }

    public void setChannelTitle(String channelTitle) {
        this.channelTitle = channelTitle;
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
        map.put("fallbackThumbnailUrl", fallbackThumbnailUrl);
        map.put("thumbnailQuality", thumbnailQuality);
        map.put("recordingId", recordingId);
        map.put("relevanceScore", relevanceScore);
        map.put("isEducational", isEducational);
        map.put("channelTitle", channelTitle);
        map.put("createdAt", createdAt);
        return map;
    }

    public static LearningRecommendation fromMap(Map<String, Object> map) {
        LearningRecommendation recommendation = new LearningRecommendation();
        recommendation.videoId = (String) map.get("videoId");
        recommendation.title = (String) map.get("title");
        recommendation.descriptionSnippet = (String) map.get("descriptionSnippet");
        recommendation.thumbnailUrl = (String) map.get("thumbnailUrl");
        recommendation.fallbackThumbnailUrl = (String) map.get("fallbackThumbnailUrl");
        recommendation.thumbnailQuality = (String) map.get("thumbnailQuality");
        recommendation.recordingId = (String) map.get("recordingId");
        recommendation.channelTitle = (String) map.get("channelTitle");

        if (recommendation.fallbackThumbnailUrl == null && recommendation.videoId != null) {
            recommendation.fallbackThumbnailUrl =
                    "https://i.ytimg.com/vi/" + recommendation.videoId + "/hqdefault.jpg";
        }

        if (recommendation.thumbnailQuality == null) {
            recommendation.thumbnailQuality = "unknown";
        }

        Object scoreObj = map.get("relevanceScore");
        if (scoreObj instanceof Number) {
            recommendation.relevanceScore = ((Number) scoreObj).intValue();
        } else {
            recommendation.relevanceScore = 0;
        }

        Object isEduObj = map.get("isEducational");
        if (isEduObj instanceof Boolean) {
            recommendation.isEducational = (Boolean) isEduObj;
        } else {
            recommendation.isEducational = false;
        }

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
                + thumbnailUrl + '\'' + ", fallbackThumbnailUrl='" + fallbackThumbnailUrl + '\''
                + ", thumbnailQuality='" + thumbnailQuality + '\'' + ", recordingId='" + recordingId
                + '\'' + ", relevanceScore=" + relevanceScore + ", isEducational=" + isEducational
                + ", channelTitle='" + channelTitle + '\'' + ", createdAt=" + createdAt + '}';
    }
}

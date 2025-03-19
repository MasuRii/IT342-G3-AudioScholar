package edu.cit.audioscholar.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User {
    private String userId;
    private String email;
    private String displayName;
    private String profileImageUrl;
    private List<String> recordingIds;
    private List<String> favoriteRecordingIds;
    
    public User() {
        this.recordingIds = new ArrayList<>();
        this.favoriteRecordingIds = new ArrayList<>();
    }
    
    public User(String userId, String email, String displayName) {
        this();
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
    }
    
    // Getters and setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    
    public List<String> getRecordingIds() { return recordingIds; }
    public void setRecordingIds(List<String> recordingIds) { this.recordingIds = recordingIds; }
    
    public List<String> getFavoriteRecordingIds() { return favoriteRecordingIds; }
    public void setFavoriteRecordingIds(List<String> favoriteRecordingIds) { this.favoriteRecordingIds = favoriteRecordingIds; }
    
    // Convert to Firestore document
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("email", email);
        map.put("displayName", displayName);
        map.put("profileImageUrl", profileImageUrl);
        map.put("recordingIds", recordingIds);
        map.put("favoriteRecordingIds", favoriteRecordingIds);
        return map;
    }
    
    // Create from Firestore document
    public static User fromMap(Map<String, Object> map) {
        User user = new User();
        user.userId = (String) map.get("userId");
        user.email = (String) map.get("email");
        user.displayName = (String) map.get("displayName");
        user.profileImageUrl = (String) map.get("profileImageUrl");
        
        List<String> recordingIds = (List<String>) map.get("recordingIds");
        if (recordingIds != null) {
            user.recordingIds = recordingIds;
        }
        
        List<String> favoriteRecordingIds = (List<String>) map.get("favoriteRecordingIds");
        if (favoriteRecordingIds != null) {
            user.favoriteRecordingIds = favoriteRecordingIds;
        }
        
        return user;
    }
}
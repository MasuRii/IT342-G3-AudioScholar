package edu.cit.audioscholar.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class User {
    private String userId;
    private String email;
    private String displayName;
    private String profileImageUrl;
    private String provider;
    private String providerId;
    private List<String> roles;
    private List<String> recordingIds;
    private List<String> favoriteRecordingIds;

    public User() {
        this.recordingIds = new ArrayList<>();
        this.favoriteRecordingIds = new ArrayList<>();
        this.roles = new ArrayList<>();
    }

    public User(String userId, String email, String displayName, String provider, String providerId) {
        this();
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.provider = provider;
        this.providerId = providerId;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public List<String> getRecordingIds() { return recordingIds; }
    public void setRecordingIds(List<String> recordingIds) { this.recordingIds = recordingIds; }

    public List<String> getFavoriteRecordingIds() { return favoriteRecordingIds; }
    public void setFavoriteRecordingIds(List<String> favoriteRecordingIds) { this.favoriteRecordingIds = favoriteRecordingIds; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("email", email);
        map.put("displayName", displayName);
        map.put("profileImageUrl", profileImageUrl);
        map.put("provider", provider);
        map.put("providerId", providerId);
        map.put("roles", Objects.requireNonNullElseGet(roles, ArrayList::new));
        map.put("recordingIds", Objects.requireNonNullElseGet(recordingIds, ArrayList::new));
        map.put("favoriteRecordingIds", Objects.requireNonNullElseGet(favoriteRecordingIds, ArrayList::new));
        return map;
    }

    @SuppressWarnings("unchecked")
    public static User fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        User user = new User();
        user.userId = (String) map.get("userId");
        user.email = (String) map.get("email");
        user.displayName = (String) map.get("displayName");
        user.profileImageUrl = (String) map.get("profileImageUrl");
        user.provider = (String) map.get("provider");
        user.providerId = (String) map.get("providerId");

        List<String> roles = (List<String>) map.get("roles");
        user.roles = (roles != null) ? new ArrayList<>(roles) : new ArrayList<>();

        List<String> recordingIds = (List<String>) map.get("recordingIds");
        user.recordingIds = (recordingIds != null) ? new ArrayList<>(recordingIds) : new ArrayList<>();

        List<String> favoriteRecordingIds = (List<String>) map.get("favoriteRecordingIds");
        user.favoriteRecordingIds = (favoriteRecordingIds != null) ? new ArrayList<>(favoriteRecordingIds) : new ArrayList<>();

        return user;
    }
}
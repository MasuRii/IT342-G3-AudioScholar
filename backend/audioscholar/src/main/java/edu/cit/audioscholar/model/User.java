package edu.cit.audioscholar.model;

import java.util.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class User {

    private String userId;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Display name cannot be blank")
    @Size(min = 1, max = 100, message = "Display name must be between 1 and 100 characters")
    private String displayName;

    @Size(max = 50, message = "First name cannot exceed 50 characters")
    private String firstName;

    @Size(max = 50, message = "Last name cannot exceed 50 characters")
    private String lastName;

    private String profileImageUrl;
    private String provider;
    private String providerId;
    private List<String> roles;
    private List<String> recordingIds;
    private List<String> favoriteRecordingIds;
    private List<String> fcmTokens;

    public User() {
        this.recordingIds = new ArrayList<>();
        this.favoriteRecordingIds = new ArrayList<>();
        this.roles = new ArrayList<>();
        this.fcmTokens = new ArrayList<>();
        if (this.roles.isEmpty()) {
            this.roles.add("ROLE_USER");
        }
    }

    public User(String userId, String email, String displayName, String provider,
            String providerId) {
        this();
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.provider = provider;
        this.providerId = providerId;
        if (displayName != null && !displayName.isBlank()) {
            String[] names = displayName.split(" ", 2);
            this.firstName = names[0];
            if (names.length > 1) {
                this.lastName = names[1];
            }
        }
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public List<String> getRoles() {
        return roles == null ? new ArrayList<>() : roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<String> getRecordingIds() {
        return recordingIds == null ? new ArrayList<>() : recordingIds;
    }

    public void setRecordingIds(List<String> recordingIds) {
        this.recordingIds = recordingIds;
    }

    public List<String> getFavoriteRecordingIds() {
        return favoriteRecordingIds == null ? new ArrayList<>() : favoriteRecordingIds;
    }

    public void setFavoriteRecordingIds(List<String> favoriteRecordingIds) {
        this.favoriteRecordingIds = favoriteRecordingIds;
    }

    public List<String> getFcmTokens() {
        return fcmTokens == null ? new ArrayList<>() : fcmTokens;
    }

    public void setFcmTokens(List<String> fcmTokens) {
        this.fcmTokens = fcmTokens;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("email", email);
        map.put("displayName", displayName);
        map.put("firstName", firstName);
        map.put("lastName", lastName);
        map.put("profileImageUrl", profileImageUrl);
        map.put("provider", provider);
        map.put("providerId", providerId);
        map.put("roles", Objects.requireNonNullElseGet(roles, ArrayList::new));
        map.put("recordingIds", Objects.requireNonNullElseGet(recordingIds, ArrayList::new));
        map.put("favoriteRecordingIds",
                Objects.requireNonNullElseGet(favoriteRecordingIds, ArrayList::new));
        map.put("fcmTokens", Objects.requireNonNullElseGet(fcmTokens, ArrayList::new));
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
        user.firstName = (String) map.get("firstName");
        user.lastName = (String) map.get("lastName");
        user.profileImageUrl = (String) map.get("profileImageUrl");
        user.provider = (String) map.get("provider");
        user.providerId = (String) map.get("providerId");

        List<String> roles = (List<String>) map.get("roles");
        user.roles = (roles != null) ? new ArrayList<>(roles) : new ArrayList<>();
        if (user.roles.isEmpty()) {
            user.roles.add("ROLE_USER");
        }

        List<String> recordingIds = (List<String>) map.get("recordingIds");
        user.recordingIds =
                (recordingIds != null) ? new ArrayList<>(recordingIds) : new ArrayList<>();

        List<String> favoriteRecordingIds = (List<String>) map.get("favoriteRecordingIds");
        user.favoriteRecordingIds =
                (favoriteRecordingIds != null) ? new ArrayList<>(favoriteRecordingIds)
                        : new ArrayList<>();

        List<String> fcmTokens = (List<String>) map.get("fcmTokens");
        user.fcmTokens = (fcmTokens != null) ? new ArrayList<>(fcmTokens) : new ArrayList<>();

        return user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        User user = (User) o;
        return Objects.equals(userId, user.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}

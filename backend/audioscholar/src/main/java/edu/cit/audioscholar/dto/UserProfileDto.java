package edu.cit.audioscholar.dto;

import edu.cit.audioscholar.model.User;

public class UserProfileDto {

    private String userId;
    private String email;
    private String displayName;
    private String profileImageUrl;

    public UserProfileDto() {}

    public UserProfileDto(String userId, String email, String displayName, String profileImageUrl) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.profileImageUrl = profileImageUrl;
    }

    public static UserProfileDto fromUser(User user) {
        if (user == null) {
            return null;
        }
        return new UserProfileDto(
                user.getUserId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getProfileImageUrl()
        );
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

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}
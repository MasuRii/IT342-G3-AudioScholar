package edu.cit.audioscholar.dto;

import java.util.List;
import edu.cit.audioscholar.model.User;

public class UserProfileDto {

    private String userId;
    private String email;
    private String displayName;
    private String profileImageUrl;
    private String firstName;
    private String lastName;
    private String provider;
    private List<String> roles;

    public UserProfileDto() {}

    public UserProfileDto(String userId, String email, String displayName, String profileImageUrl,
            String firstName, String lastName, String provider, List<String> roles) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.profileImageUrl = profileImageUrl;
        this.firstName = firstName;
        this.lastName = lastName;
        this.provider = provider;
        this.roles = roles;
    }

    public static UserProfileDto fromUser(User user) {
        if (user == null) {
            return null;
        }
        return new UserProfileDto(user.getUserId(), user.getEmail(), user.getDisplayName(),
                user.getProfileImageUrl(), user.getFirstName(), user.getLastName(),
                user.getProvider(), user.getRoles());
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

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}

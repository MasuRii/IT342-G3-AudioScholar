package edu.cit.audioscholar.dto;

import jakarta.validation.constraints.Size;

public class UpdateUserProfileRequest {

    @Size(min = 1, max = 100, message = "Display name must be between 1 and 100 characters")
    private String displayName;


    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

}
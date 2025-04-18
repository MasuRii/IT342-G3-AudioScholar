package edu.cit.audioscholar.dto;

public class AuthResponse {
    private String message;
    private boolean success;
    private String userId;

    public AuthResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

     public AuthResponse(boolean success, String message, String userId) {
        this.success = success;
        this.message = message;
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

     public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
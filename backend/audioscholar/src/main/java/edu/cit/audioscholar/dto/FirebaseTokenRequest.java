package edu.cit.audioscholar.dto;

import jakarta.validation.constraints.NotBlank;

public class FirebaseTokenRequest {

    @NotBlank(message = "Firebase ID token cannot be blank")
    private String idToken;

    public FirebaseTokenRequest() {}

    public FirebaseTokenRequest(String idToken) {
        this.idToken = idToken;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
}
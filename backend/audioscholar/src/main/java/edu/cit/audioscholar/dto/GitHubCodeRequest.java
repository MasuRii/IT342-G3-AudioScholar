package edu.cit.audioscholar.dto;

import jakarta.validation.constraints.NotBlank;

public class GitHubCodeRequest {

    @NotBlank(message = "GitHub authorization code cannot be blank")
    private String code;

    private String state;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
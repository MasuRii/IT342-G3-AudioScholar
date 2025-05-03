package edu.cit.audioscholar.dto;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AudioProcessingMessage implements Serializable {

    private static final long serialVersionUID = 2L;

    private String recordingId;
    private String userId;
    private String metadataId;

    public AudioProcessingMessage() {}

    @JsonCreator
    public AudioProcessingMessage(@JsonProperty("recordingId") String recordingId,
            @JsonProperty("userId") String userId, @JsonProperty("metadataId") String metadataId) {
        this.recordingId = recordingId;
        this.userId = userId;
        this.metadataId = metadataId;
    }

    public String getRecordingId() {
        return recordingId;
    }

    public void setRecordingId(String recordingId) {
        this.recordingId = recordingId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMetadataId() {
        return metadataId;
    }

    public void setMetadataId(String metadataId) {
        this.metadataId = metadataId;
    }

    @Override
    public String toString() {
        return "AudioProcessingMessage{" + "recordingId='" + recordingId + '\'' + ", userId='"
                + userId + '\'' + ", metadataId='" + metadataId + '\'' + '}';
    }
}

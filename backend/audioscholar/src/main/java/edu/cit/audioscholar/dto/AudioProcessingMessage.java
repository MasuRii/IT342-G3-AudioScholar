package edu.cit.audioscholar.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AudioProcessingMessage {

    private final String audioMetadataId;

    @JsonCreator
    public AudioProcessingMessage(@JsonProperty("audioMetadataId") String audioMetadataId) {
        this.audioMetadataId = audioMetadataId;
    }

    public String getAudioMetadataId() {
        return audioMetadataId;
    }

    @Override
    public String toString() {
        return "AudioProcessingMessage{" +
               "audioMetadataId='" + audioMetadataId + '\'' +
               '}';
    }
}
package edu.cit.audioscholar.dto;

import java.io.Serializable;

public class NhostUploadMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String metadataId;
    private String recordingId;

    public NhostUploadMessage() {}

    public NhostUploadMessage(String metadataId, String recordingId) {
        this.metadataId = metadataId;
        this.recordingId = recordingId;
    }

    public String getMetadataId() {
        return metadataId;
    }

    public void setMetadataId(String metadataId) {
        this.metadataId = metadataId;
    }

    public String getRecordingId() {
        return recordingId;
    }

    public void setRecordingId(String recordingId) {
        this.recordingId = recordingId;
    }

    @Override
    public String toString() {
        return "NhostUploadMessage{" + "metadataId='" + metadataId + '\'' + ", recordingId='"
                + recordingId + '\'' + '}';
    }
}

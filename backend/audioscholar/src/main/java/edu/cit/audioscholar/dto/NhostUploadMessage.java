package edu.cit.audioscholar.dto;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NhostUploadMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String metadataId;
    private String fileType;
    private String tempFilePath;
    private String originalFilename;
    private String originalContentType;

    public NhostUploadMessage() {}

    @JsonCreator
    public NhostUploadMessage(@JsonProperty("metadataId") String metadataId,
            @JsonProperty("fileType") String fileType,
            @JsonProperty("tempFilePath") String tempFilePath,
            @JsonProperty("originalFilename") String originalFilename,
            @JsonProperty("originalContentType") String originalContentType) {
        this.metadataId = metadataId;
        this.fileType = fileType;
        this.tempFilePath = tempFilePath;
        this.originalFilename = originalFilename;
        this.originalContentType = originalContentType;
    }

    public String getMetadataId() {
        return metadataId;
    }

    public String getFileType() {
        return fileType;
    }

    public String getTempFilePath() {
        return tempFilePath;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getOriginalContentType() {
        return originalContentType;
    }

    public void setMetadataId(String metadataId) {
        this.metadataId = metadataId;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public void setTempFilePath(String tempFilePath) {
        this.tempFilePath = tempFilePath;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public void setOriginalContentType(String originalContentType) {
        this.originalContentType = originalContentType;
    }

    @Override
    public String toString() {
        return "NhostUploadMessage{" + "metadataId='" + metadataId + '\'' + ", fileType='"
                + fileType + '\'' + ", tempFilePath='" + tempFilePath + '\''
                + ", originalFilename='" + originalFilename + '\'' + ", originalContentType='"
                + originalContentType + '\'' + '}';
    }
}

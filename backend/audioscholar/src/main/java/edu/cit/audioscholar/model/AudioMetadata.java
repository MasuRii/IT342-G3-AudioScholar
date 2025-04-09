package edu.cit.audioscholar.model;

import com.google.cloud.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AudioMetadata {
    private String id;
    private String userId;
    private String fileName;
    private long fileSize;
    private String contentType;
    private String title;
    private String description;
    private String nhostFileId;
    private String storageUrl;
    private Timestamp uploadTimestamp;

    public AudioMetadata() {
    }

    public AudioMetadata(String userId, String fileName, long fileSize, String contentType, String title, String description, String nhostFileId, String storageUrl, Timestamp uploadTimestamp) {
        this.userId = userId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.title = title != null ? title : "";
        this.description = description != null ? description : "";
        this.nhostFileId = nhostFileId;
        this.storageUrl = storageUrl;
        this.uploadTimestamp = uploadTimestamp;
    }

    public AudioMetadata(String id, String userId, String fileName, long fileSize, String contentType, String title, String description, String nhostFileId, String storageUrl, Timestamp uploadTimestamp) {
        this.id = id;
        this.userId = userId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.title = title != null ? title : "";
        this.description = description != null ? description : "";
        this.nhostFileId = nhostFileId;
        this.storageUrl = storageUrl;
        this.uploadTimestamp = uploadTimestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNhostFileId() {
        return nhostFileId;
    }

    public void setNhostFileId(String nhostFileId) {
        this.nhostFileId = nhostFileId;
    }

    public String getStorageUrl() {
        return storageUrl;
    }

    public void setStorageUrl(String storageUrl) {
        this.storageUrl = storageUrl;
    }

    public Timestamp getUploadTimestamp() {
        return uploadTimestamp;
    }

    public void setUploadTimestamp(Timestamp uploadTimestamp) {
        this.uploadTimestamp = uploadTimestamp;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("userId", userId);
        map.put("fileName", fileName);
        map.put("fileSize", fileSize);
        map.put("contentType", contentType);
        map.put("title", title);
        map.put("description", description);
        map.put("nhostFileId", nhostFileId);
        map.put("storageUrl", storageUrl);
        map.put("uploadTimestamp", uploadTimestamp);
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioMetadata that = (AudioMetadata) o;
        return fileSize == that.fileSize && Objects.equals(id, that.id) && Objects.equals(userId, that.userId) && Objects.equals(fileName, that.fileName) && Objects.equals(contentType, that.contentType) && Objects.equals(title, that.title) && Objects.equals(description, that.description) && Objects.equals(nhostFileId, that.nhostFileId) && Objects.equals(storageUrl, that.storageUrl) && Objects.equals(uploadTimestamp, that.uploadTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId, fileName, fileSize, contentType, title, description, nhostFileId, storageUrl, uploadTimestamp);
    }

    @Override
    public String toString() {
        return "AudioMetadata{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", contentType='" + contentType + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", nhostFileId='" + nhostFileId + '\'' +
                ", storageUrl='" + storageUrl + '\'' +
                ", uploadTimestamp=" + uploadTimestamp +
                '}';
    }
}
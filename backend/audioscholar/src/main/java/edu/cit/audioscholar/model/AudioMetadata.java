package edu.cit.audioscholar.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import com.google.cloud.Timestamp;

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
    private ProcessingStatus status;
    private String recordingId;
    private String summaryId;
    private String transcriptText;
    private String tempFilePath;
    private String failureReason;

    public AudioMetadata() {}

    public AudioMetadata(String id, String userId, String fileName, long fileSize,
            String contentType, String title, String description, Timestamp uploadTimestamp,
            String recordingId, ProcessingStatus initialStatus, String tempFilePath) {
        this.id = id;
        this.userId = userId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.title = title != null ? title : "";
        this.description = description != null ? description : "";
        this.uploadTimestamp = uploadTimestamp;
        this.recordingId = recordingId;
        this.status = initialStatus;
        this.tempFilePath = tempFilePath;
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

    public ProcessingStatus getStatus() {
        return status;
    }

    public void setStatus(ProcessingStatus status) {
        this.status = status;
    }

    public String getRecordingId() {
        return recordingId;
    }

    public void setRecordingId(String recordingId) {
        this.recordingId = recordingId;
    }

    public String getSummaryId() {
        return summaryId;
    }

    public void setSummaryId(String summaryId) {
        this.summaryId = summaryId;
    }

    public String getTranscriptText() {
        return transcriptText;
    }

    public void setTranscriptText(String transcriptText) {
        this.transcriptText = transcriptText;
    }

    public String getTempFilePath() {
        return tempFilePath;
    }

    public void setTempFilePath(String tempFilePath) {
        this.tempFilePath = tempFilePath;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }


    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (id != null)
            map.put("id", id);
        if (userId != null)
            map.put("userId", userId);
        if (fileName != null)
            map.put("fileName", fileName);
        map.put("fileSize", fileSize);
        if (contentType != null)
            map.put("contentType", contentType);
        if (title != null)
            map.put("title", title);
        if (description != null)
            map.put("description", description);
        if (nhostFileId != null)
            map.put("nhostFileId", nhostFileId);
        if (storageUrl != null)
            map.put("storageUrl", storageUrl);
        if (uploadTimestamp != null)
            map.put("uploadTimestamp", uploadTimestamp);
        if (status != null)
            map.put("status", status.name());
        if (recordingId != null)
            map.put("recordingId", recordingId);
        if (summaryId != null)
            map.put("summaryId", summaryId);
        if (transcriptText != null)
            map.put("transcriptText", transcriptText);
        if (tempFilePath != null)
            map.put("tempFilePath", tempFilePath);
        if (failureReason != null)
            map.put("failureReason", failureReason);

        return map;
    }

    public static AudioMetadata fromMap(Map<String, Object> map) {
        if (map == null)
            return null;
        AudioMetadata meta = new AudioMetadata();
        meta.setId((String) map.get("id"));
        meta.setUserId((String) map.get("userId"));
        meta.setFileName((String) map.get("fileName"));
        Object size = map.get("fileSize");
        if (size instanceof Number)
            meta.setFileSize(((Number) size).longValue());
        meta.setContentType((String) map.get("contentType"));
        meta.setTitle((String) map.get("title"));
        meta.setDescription((String) map.get("description"));
        meta.setNhostFileId((String) map.get("nhostFileId"));
        meta.setStorageUrl((String) map.get("storageUrl"));
        meta.setUploadTimestamp((Timestamp) map.get("uploadTimestamp"));
        String statusStr = (String) map.get("status");
        if (statusStr != null) {
            try {
                meta.setStatus(ProcessingStatus.valueOf(statusStr));
            } catch (IllegalArgumentException e) {
                meta.setStatus(ProcessingStatus.FAILED);
            }
        }
        meta.setRecordingId((String) map.get("recordingId"));
        meta.setSummaryId((String) map.get("summaryId"));
        meta.setTranscriptText((String) map.get("transcriptText"));
        meta.setTempFilePath((String) map.get("tempFilePath"));
        meta.setFailureReason((String) map.get("failureReason"));
        return meta;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AudioMetadata that = (AudioMetadata) o;
        return Objects.equals(id, that.id) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userId);
    }

    @Override
    public String toString() {
        return "AudioMetadata{" + "id='" + id + '\'' + ", userId='" + userId + '\'' + ", fileName='"
                + fileName + '\'' + ", status=" + status + ", recordingId='" + recordingId + '\''
                + ", tempFilePath='" + tempFilePath + '\'' + ", storageUrl='" + storageUrl + '\''
                + '}';
    }
}

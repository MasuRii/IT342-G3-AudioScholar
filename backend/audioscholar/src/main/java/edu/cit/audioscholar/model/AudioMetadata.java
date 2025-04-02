package edu.cit.audioscholar.model;

public class AudioMetadata {
    private String id;
    private String fileName;
    private long fileSize;
    private long duration; // Duration in seconds

    // Constructor, Getters, and Setters

    public AudioMetadata(String id, String fileName, long fileSize, long duration) {
        this.id = id;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.duration = duration;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }
}

package edu.cit.audioscholar.model;

public class AudioMetadata {
    private String id;
    private String fileName;
    private long fileSize;
    private long duration; // Duration in seconds
    private String title; // Optional title
    private String description; // Optional description
    private String firebaseStorageUrl; // URL of the audio file in Firebase Storage

    // Default constructor (required for Firebase)
    public AudioMetadata() {
    }

    public AudioMetadata(String id, String fileName, long fileSize, long duration, String title, String description, String firebaseStorageUrl) {
        this.id = id;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.duration = duration;
        this.title = title != null ? title : ""; // Use empty string if title is null
        this.description = description != null ? description : ""; // Same for description
        this.firebaseStorageUrl = firebaseStorageUrl;
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

    public String getFirebaseStorageUrl() {
        return firebaseStorageUrl;
    }

    public void setFirebaseStorageUrl(String firebaseStorageUrl) {
        this.firebaseStorageUrl = firebaseStorageUrl;
    }

    @Override
    public String toString() {
        return "AudioMetadata{" +
                "id='" + id + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", duration=" + duration +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", firebaseStorageUrl='" + firebaseStorageUrl + '\'' +
                '}';
    }
}
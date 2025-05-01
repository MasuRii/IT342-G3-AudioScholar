package edu.cit.audioscholar.model;

public enum ProcessingStatus {
    UPLOAD_PENDING,      // Initial state before upload to storage begins
    UPLOADING_TO_STORAGE,// File is being uploaded to cloud storage
    UPLOADED,            // Upload complete, waiting for processing trigger
    PENDING,             // Upload complete, waiting for processing trigger
    PROCESSING,          // Transcription and summarization in progress
    COMPLETED,           // Successfully processed
    FAILED,              // Processing failed due to an error
    PROCESSING_HALTED_UNSUITABLE_CONTENT; // Processing stopped because transcript content was unsuitable (too short, repetitive, etc.)

    // Optional: Add a description if needed
    // private final String description;
    // ProcessingStatus(String description) {
    //     this.description = description;
    // }
    // public String getDescription() {
    //     return description;
    // }
}

package edu.cit.audioscholar.model;

/**
 * Represents the various stages an audio file goes through during processing.
 */
public enum ProcessingStatus {
    UPLOAD_PENDING, // Initial state after request, before Nhost upload starts
    UPLOAD_IN_PROGRESS, // File(s) currently being uploaded to Nhost
    UPLOADED, // Both files successfully uploaded to Nhost
    PROCESSING_QUEUED, // Message sent to respective queues (Transcription, PPTX Conversion)
    TRANSCRIBING, // Audio transcription in progress
    PDF_CONVERTING, // PPTX to PDF conversion in progress
    TRANSCRIPTION_COMPLETE, // Transcription finished successfully
    PDF_CONVERSION_COMPLETE, // PDF conversion finished successfully
    PROCESSING_HALTED_NO_SPEECH, // Transcription detected no speech
    PROCESSING_HALTED_UNSUITABLE_CONTENT, // Transcription detected unsuitable content (too short,
                                          // repetitive etc. - maybe remove if NO_SPEECH is enough?)
    SUMMARIZATION_QUEUED, // Message sent to final summarization queue
    SUMMARIZING, // Combined summarization in progress
    SUMMARY_COMPLETE, // Summary has been generated and saved
    RECOMMENDATIONS_QUEUED, // Message sent to recommendations queue
    GENERATING_RECOMMENDATIONS, // Generating learning recommendations
    COMPLETE, // Process finished successfully, all steps done
    FAILED // An error occurred at some stage
}

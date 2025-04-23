package edu.cit.audioscholar.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import edu.cit.audioscholar.config.RabbitMQConfig;
import edu.cit.audioscholar.dto.AudioProcessingMessage;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.*;

@Service
public class AudioSummarizationListenerService {

    private static final Logger log =
            LoggerFactory.getLogger(AudioSummarizationListenerService.class);

    private final FirebaseService firebaseService;
    private final NhostStorageService nhostStorageService;
    private final GeminiService geminiService;
    private final LearningMaterialRecommenderService learningMaterialRecommenderService;
    private final RecordingService recordingService;
    private final SummaryService summaryService;

    private static final Pattern KEY_POINT_PATTERN =
            Pattern.compile("^\\s*([*\\-]|\\d+\\.)\\s+(.*)", Pattern.MULTILINE);

    public AudioSummarizationListenerService(FirebaseService firebaseService,
            NhostStorageService nhostStorageService, GeminiService geminiService,
            LearningMaterialRecommenderService learningMaterialRecommenderService,
            @Lazy RecordingService recordingService, @Lazy SummaryService summaryService) {
        this.firebaseService = firebaseService;
        this.nhostStorageService = nhostStorageService;
        this.geminiService = geminiService;
        this.learningMaterialRecommenderService = learningMaterialRecommenderService;
        this.recordingService = recordingService;
        this.summaryService = summaryService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void handleAudioProcessingRequest(AudioProcessingMessage message) {
        if (message == null || message.getRecordingId() == null
                || message.getRecordingId().isEmpty() || message.getMetadataId() == null
                || message.getMetadataId().isEmpty()) {
            log.error(
                    "[AMQP Listener] Received invalid or incomplete message from queue '{}'. Ignoring. Message: {}",
                    RabbitMQConfig.QUEUE_NAME, message);
            return;
        }

        String recordingId = message.getRecordingId();
        String metadataId = message.getMetadataId();
        log.info("[AMQP Listener] Received request for recordingId: {}, metadataId: {}",
                recordingId, metadataId);

        AudioMetadata metadata = null;
        Recording recording = null;

        try {
            log.debug("[{}] Fetching AudioMetadata document...", metadataId);
            metadata = firebaseService.getAudioMetadataById(metadataId);
            if (metadata == null) {
                log.error(
                        "[{}] AudioMetadata not found for ID received in message. Cannot process recording {}.",
                        metadataId, recordingId);
                return;
            }
            log.info("[{}] Found metadata. Current status: {}", metadataId, metadata.getStatus());

            ProcessingStatus currentStatus = metadata.getStatus();
            if (currentStatus == ProcessingStatus.COMPLETED
                    || currentStatus == ProcessingStatus.FAILED) {
                log.warn(
                        "[{}] Metadata status is already final ({}). Skipping processing for recording {}.",
                        metadataId, currentStatus, recordingId);
                return;
            }
            if (currentStatus != ProcessingStatus.PENDING
                    && currentStatus != ProcessingStatus.PROCESSING) {
                log.warn(
                        "[{}] Metadata status is not PENDING or PROCESSING (Current: {}). Skipping processing for recording {}.",
                        metadataId, currentStatus, recordingId);
                return;
            }

            log.debug("[{}] Fetching Recording document...", recordingId);
            recording = recordingService.getRecordingById(recordingId);
            if (recording == null) {
                log.error(
                        "[{}] Recording document not found, but metadata {} exists. Cannot process audio.",
                        recordingId, metadataId);
                updateMetadataStatusToFailed(metadataId,
                        "Associated Recording document not found.");
                return;
            }
            log.info("[{}] Found recording.", recordingId);

            if (currentStatus != ProcessingStatus.PROCESSING) {
                log.info("[{}] Updating metadata {} status to PROCESSING.", recordingId,
                        metadataId);
                updateMetadataStatus(metadataId, ProcessingStatus.PROCESSING);
            } else {
                log.info("[{}] Resuming processing (metadata status already PROCESSING).",
                        recordingId);
            }

            String nhostFileId = extractNhostIdFromUrl(recording.getAudioUrl());
            if (nhostFileId == null) {
                log.error(
                        "[{}] Could not extract Nhost File ID from URL: {}. Cannot download audio.",
                        recordingId, recording.getAudioUrl());
                updateMetadataStatusToFailed(metadataId,
                        "Failed to extract Nhost File ID from URL.");
                return;
            }

            log.info("[{}] Downloading audio from Nhost file ID: {}", recordingId, nhostFileId);
            String base64Audio = nhostStorageService.downloadFileAsBase64(nhostFileId);
            log.info("[{}] Successfully downloaded and Base64 encoded audio ({} bytes encoded).",
                    recordingId, base64Audio.length());

            String prompt = createPrompt(recording);
            log.debug("[{}] Generated Gemini prompt: '{}'", recordingId, prompt);

            log.info("[{}] Calling Gemini API for audio summarization...", recordingId);
            String geminiResponseString = geminiService.callGeminiAPIWithAudio(prompt, base64Audio,
                    recording.getFileName());

            log.info("[{}] Received response from GeminiService. Processing...", recordingId);
            handleGeminiResponse(recording, metadataId, geminiResponseString);

            AudioMetadata finalMetadata = firebaseService.getAudioMetadataById(metadataId);
            if (finalMetadata != null && finalMetadata.getStatus() == ProcessingStatus.COMPLETED) {
                try {
                    log.info("[{}] Triggering recommendation generation...", recordingId);
                    List<LearningRecommendation> recommendations =
                            learningMaterialRecommenderService
                                    .generateAndSaveRecommendations(recordingId);
                    if (recommendations.isEmpty()) {
                        log.warn(
                                "[{}] Recommendation generation completed, but no recommendations were generated or saved.",
                                recordingId);
                    } else {
                        log.info("[{}] Successfully generated and saved {} recommendations.",
                                recordingId, recommendations.size());
                    }
                } catch (Exception e) {
                    log.error(
                            "[{}] Failed to generate or save recommendations after successful summarization. Summary is saved, metadata status is COMPLETED.",
                            recordingId, e);
                }
            } else {
                log.info(
                        "[{}] Skipping recommendation generation as metadata status is not COMPLETED (Current: {}).",
                        recordingId,
                        (finalMetadata != null ? finalMetadata.getStatus() : "UNKNOWN/DELETED"));
            }

        } catch (FirestoreInteractionException e) {
            log.error("[{}] Firestore error during processing for metadata {}.", recordingId,
                    metadataId, e);
            updateMetadataStatusToFailed(metadataId,
                    "Firestore interaction failed during processing.");
        } catch (IOException e) {
            log.error("[{}] I/O error (likely Nhost download/encoding) for metadata {}.",
                    recordingId, metadataId, e);
            updateMetadataStatusToFailed(metadataId, "Audio download or encoding failed.");
        } catch (ExecutionException | InterruptedException e) {
            log.error("[{}] Concurrency/Execution error during processing for metadata {}.",
                    recordingId, metadataId, e);
            Thread.currentThread().interrupt();
            updateMetadataStatusToFailed(metadataId, "Concurrency error during processing.");
        } catch (RuntimeException e) {
            log.error("[{}] Runtime error during processing for metadata {}.", recordingId,
                    metadataId, e);
            updateMetadataStatusToFailed(metadataId, "Processing runtime error: " + e.getMessage());
        } catch (Exception e) {
            log.error("[{}] Unexpected error during processing for metadata {}.", recordingId,
                    metadataId, e);
            updateMetadataStatusToFailed(metadataId,
                    "Unexpected processing error: " + e.getMessage());
        }
    }

    private String createPrompt(Recording recording) {
        String prompt =
                "Please summarize the key points and action items from the following audio content";
        if (recording.getTitle() != null && !recording.getTitle().isBlank()) {
            prompt += ", titled '" + recording.getTitle() + "'";
        }
        prompt +=
                ". Structure the summary clearly. Use Markdown formatting, including bullet points (*) or numbered lists for key points/action items.";
        return prompt;
    }

    private void handleGeminiResponse(Recording recording, String metadataId,
            String geminiResponseString) {
        String recordingId = recording.getRecordingId();
        log.debug("[{}] Handling Gemini response string (first 200 chars): {}", recordingId,
                geminiResponseString.substring(0, Math.min(geminiResponseString.length(), 200))
                        + "...");

        try {
            String rawSummaryText = null;
            try {
                com.fasterxml.jackson.databind.ObjectMapper tempMapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode rootNode =
                        tempMapper.readTree(geminiResponseString);
                if (rootNode.has("error")) {
                    String errorTitle = rootNode.path("error").asText("Unknown Error");
                    String errorDetails = rootNode.path("details").asText("No details provided");
                    log.error("[{}] GeminiService returned an error response: {} - {}", recordingId,
                            errorTitle, errorDetails);
                    updateMetadataStatusToFailed(metadataId, "AI service failed: " + errorTitle);
                    return;
                }
                if (rootNode.has("text")) {
                    rawSummaryText = rootNode.path("text").asText();
                } else {
                    log.error(
                            "[{}] GeminiService response JSON did not contain 'text' or 'error' field. Response: {}",
                            recordingId, geminiResponseString);
                    updateMetadataStatusToFailed(metadataId,
                            "Received unexpected response structure from AI service.");
                    return;
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.error("[{}] Failed to parse JSON response from GeminiService: {}. Response: {}",
                        recordingId, e.getMessage(), geminiResponseString);
                updateMetadataStatusToFailed(metadataId, "Failed to parse AI service response.");
                return;
            }

            if (rawSummaryText == null || rawSummaryText.isBlank()) {
                log.error("[{}] Extracted summary text from Gemini response is empty or null.",
                        recordingId);
                updateMetadataStatusToFailed(metadataId, "Received empty summary from AI service.");
                return;
            }

            log.info(
                    "[{}] Successfully extracted raw summary text (length: {}) from Gemini response.",
                    recordingId, rawSummaryText.length());

            Summary structuredSummary = new Summary();
            structuredSummary.setSummaryId(UUID.randomUUID().toString());
            structuredSummary.setRecordingId(recordingId);
            structuredSummary.setFullText(rawSummaryText);

            List<String> keyPoints = new ArrayList<>();
            Matcher matcher = KEY_POINT_PATTERN.matcher(rawSummaryText);
            while (matcher.find()) {
                keyPoints.add(matcher.group(2).trim());
            }
            structuredSummary.setKeyPoints(keyPoints);
            log.info("[{}] Extracted {} potential key points via regex.", recordingId,
                    keyPoints.size());

            List<String> topics = new ArrayList<>();
            if (!rawSummaryText.isBlank()) {
                log.info(
                        "[{}] Calling GeminiService to extract keywords/topics from summary text...",
                        recordingId);
                try {
                    topics = geminiService.extractKeywordsAndTopics(rawSummaryText);
                    log.info("[{}] Extracted {} keywords/topics.", recordingId, topics.size());
                } catch (Exception e) {
                    log.error(
                            "[{}] Failed to extract keywords/topics from GeminiService. Proceeding without topics. Error: {}",
                            recordingId, e.getMessage(), e);
                    topics = new ArrayList<>();
                }
            } else {
                log.warn("[{}] Raw summary text is empty, skipping topic extraction.", recordingId);
            }
            structuredSummary.setTopics(topics);


            structuredSummary.setCondensedSummary(null);

            String formattedMarkdown = formatSummaryAsMarkdown(structuredSummary);
            structuredSummary.setFormattedSummaryText(formattedMarkdown);
            log.info("[{}] Summary formatted successfully.", recordingId);

            log.info(
                    "[{}] Attempting to save summary (ID: {}) with {} key points and {} topics to Firestore...",
                    recordingId, structuredSummary.getSummaryId(),
                    structuredSummary.getKeyPoints().size(), structuredSummary.getTopics().size());

            summaryService.createSummary(structuredSummary);

            log.info("[{}] Summary (ID: {}) saved successfully and linked to recording.",
                    recordingId, structuredSummary.getSummaryId());

            log.info("[{}] Updating metadata {} status to COMPLETED.", recordingId, metadataId);
            updateMetadataStatus(metadataId, ProcessingStatus.COMPLETED);

        } catch (RuntimeException | ExecutionException | InterruptedException e) {
            log.error("[{}] Error during summary post-processing or saving for metadata {}.",
                    recordingId, metadataId, e);
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
            updateMetadataStatusToFailed(metadataId,
                    "Failed during summary post-processing/saving: " + e.getMessage());
        } catch (Exception e) {
            log.error("[{}] Unexpected exception during summary post-processing for metadata {}.",
                    recordingId, metadataId, e);
            updateMetadataStatusToFailed(metadataId,
                    "Unexpected exception during summary post-processing: " + e.getMessage());
        }
    }


    private String formatSummaryAsMarkdown(Summary summary) {
        StringBuilder markdownBuilder = new StringBuilder();

        if (summary.getFullText() != null && !summary.getFullText().isBlank()) {
            markdownBuilder.append(summary.getFullText().trim());
            markdownBuilder.append("\n\n");
        } else {
            log.warn("[{}] Full text for summary ID {} is empty or null during formatting.",
                    summary.getRecordingId(), summary.getSummaryId());
        }

        List<String> keyPoints = summary.getKeyPoints();
        if (keyPoints != null && !keyPoints.isEmpty()) {
            boolean alreadyListed = summary.getFullText() != null
                    && KEY_POINT_PATTERN.matcher(summary.getFullText()).find();

            if (!alreadyListed) {
                markdownBuilder.append("## Key Points\n\n");
                for (String point : keyPoints) {
                    markdownBuilder.append("* ").append(point).append("\n");
                }
                markdownBuilder.append("\n");
            } else {
                log.debug(
                        "[{}] Key points seem to be already included in the full text for summary ID {}. Skipping explicit list generation in formatting.",
                        summary.getRecordingId(), summary.getSummaryId());
            }
        }

        return markdownBuilder.toString().trim();
    }

    private void updateMetadataStatusToFailed(String metadataId, String reason) {
        updateMetadataStatus(metadataId, ProcessingStatus.FAILED, reason);
    }

    private void updateMetadataStatus(String metadataId, ProcessingStatus newStatus,
            String... reasonOptional) {
        if (metadataId == null) {
            log.error("[AMQP Listener] Cannot update metadata status: metadataId is null.");
            return;
        }
        String reason = (reasonOptional.length > 0) ? reasonOptional[0] : null;
        log.info("[{}] Attempting to set metadata status to {}.{}", metadataId, newStatus,
                (reason != null ? " Reason: " + reason : ""));

        try {
            AudioMetadata currentMeta = firebaseService.getAudioMetadataById(metadataId);
            if (currentMeta != null) {
                if (currentMeta.getStatus() == ProcessingStatus.COMPLETED
                        && newStatus == ProcessingStatus.FAILED) {
                    log.warn(
                            "[{}] Metadata status is already COMPLETED. Not overwriting with FAILED due to reason: {}",
                            metadataId, reason);
                    return;
                }
                if (currentMeta.getStatus() == ProcessingStatus.FAILED
                        && (newStatus == ProcessingStatus.PENDING
                                || newStatus == ProcessingStatus.PROCESSING)) {
                    log.warn("[{}] Metadata status is already FAILED. Not overwriting with {}.",
                            metadataId, newStatus);
                    return;
                }

                firebaseService.updateAudioMetadataStatus(metadataId, newStatus);
                log.info("[{}] Metadata status successfully updated to {}.", metadataId, newStatus);
            } else {
                log.error(
                        "[{}] Cannot update metadata status to {} as metadata could not be retrieved (might have been deleted?).",
                        metadataId, newStatus);
            }
        } catch (Exception e) {
            log.error(
                    "[{}] CRITICAL: Failed to update metadata status to {} after processing event. Manual intervention likely required. Error: {}",
                    metadataId, newStatus, e.getMessage(), e);
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
        }
    }

    private String extractNhostIdFromUrl(String url) {
        if (url == null)
            return null;
        try {
            String[] parts = url.split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("files".equalsIgnoreCase(parts[i]) && (i + 1) < parts.length) {
                    String potentialId = parts[i + 1];
                    if (potentialId.matches("[a-zA-Z0-9\\-]+")) {
                        log.debug("Extracted Nhost ID '{}' from URL '{}'", potentialId, url);
                        return potentialId;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Nhost ID from URL '{}': {}", url, e.getMessage());
        }
        log.warn("Could not extract Nhost ID using expected pattern from URL: {}", url);
        return null;
    }
}

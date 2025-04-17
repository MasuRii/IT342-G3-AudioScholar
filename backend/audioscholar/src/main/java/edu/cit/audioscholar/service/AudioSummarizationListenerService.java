package edu.cit.audioscholar.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.cit.audioscholar.config.RabbitMQConfig;
import edu.cit.audioscholar.dto.AudioProcessingMessage;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.LearningRecommendation;
import edu.cit.audioscholar.model.ProcessingStatus;
import edu.cit.audioscholar.model.Summary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class AudioSummarizationListenerService {

    private static final Logger log = LoggerFactory.getLogger(AudioSummarizationListenerService.class);

    private final FirebaseService firebaseService;
    private final NhostStorageService nhostStorageService;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;
    private final LearningMaterialRecommenderService learningMaterialRecommenderService;
    private static final Pattern KEY_POINT_PATTERN = Pattern.compile("^\\s*([*\\-]|\\d+\\.)\\s+(.*)", Pattern.MULTILINE);


    public AudioSummarizationListenerService(FirebaseService firebaseService,
                                             NhostStorageService nhostStorageService,
                                             GeminiService geminiService,
                                             ObjectMapper objectMapper,
                                             LearningMaterialRecommenderService learningMaterialRecommenderService
                                            ) {
        this.firebaseService = firebaseService;
        this.nhostStorageService = nhostStorageService;
        this.geminiService = geminiService;
        this.objectMapper = objectMapper;
        this.learningMaterialRecommenderService = learningMaterialRecommenderService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void handleAudioProcessingRequest(AudioProcessingMessage message) {
        if (message == null || message.getAudioMetadataId() == null || message.getAudioMetadataId().isEmpty()) {
            log.error("[AMQP Listener] Received invalid or empty message from queue '{}'. Ignoring.", RabbitMQConfig.QUEUE_NAME);
            return;
        }

        String metadataId = message.getAudioMetadataId();
        log.info("[AMQP Listener] Received request for metadata ID: {}", metadataId);

        AudioMetadata metadata = null;
        try {
            log.debug("[{}] Fetching AudioMetadata...", metadataId);
            metadata = firebaseService.getAudioMetadataById(metadataId);

            if (metadata == null) {
                log.error("[{}] AudioMetadata not found. Cannot process.", metadataId);
                return;
            }
            log.info("[{}] Found metadata. Current status: {}", metadataId, metadata.getStatus());

            if (metadata.getStatus() == ProcessingStatus.COMPLETED || metadata.getStatus() == ProcessingStatus.FAILED) {
                 log.warn("[{}] Status is already final ({}). Skipping processing.", metadataId, metadata.getStatus());
                 return;
            }
            if (metadata.getStatus() != ProcessingStatus.PENDING && metadata.getStatus() != ProcessingStatus.UPLOADED && metadata.getStatus() != ProcessingStatus.PROCESSING) {
                 log.warn("[{}] Status is not PENDING, UPLOADED, or PROCESSING (Current: {}). Skipping processing.", metadataId, metadata.getStatus());
                 return;
            }

            if (metadata.getStatus() != ProcessingStatus.PROCESSING) {
                log.info("[{}] Updating status to PROCESSING.", metadataId);
                firebaseService.updateAudioMetadataStatus(metadataId, ProcessingStatus.PROCESSING);
            } else {
                 log.info("[{}] Resuming processing (status already PROCESSING).", metadataId);
            }


            log.info("[{}] Downloading audio from Nhost file ID: {}", metadataId, metadata.getNhostFileId());
            String base64Audio = nhostStorageService.downloadFileAsBase64(metadata.getNhostFileId());
            log.info("[{}] Successfully downloaded and Base64 encoded audio ({} bytes encoded).", metadataId, base64Audio.length());

            String prompt = createPrompt(metadata);
            log.debug("[{}] Generated Gemini prompt: '{}'", metadataId, prompt);

            log.info("[{}] Calling Gemini API for audio summarization...", metadataId);
            String geminiResponseString = geminiService.callGeminiAPIWithAudio(
                prompt,
                base64Audio,
                metadata.getFileName()
            );

            log.info("[{}] Received response from GeminiService. Processing...", metadataId);
            handleGeminiResponse(metadata, geminiResponseString);

        } catch (FirestoreInteractionException e) {
            log.error("[{}] Firestore error during processing setup (e.g., initial status update or fetch).", metadataId, e);
            if (metadataId != null) {
                updateStatusToFailed(metadataId, "Firestore interaction failed during processing setup.");
            }
        } catch (IOException e) {
            log.error("[{}] I/O error (likely Nhost download/encoding).", metadataId, e);
            if (metadataId != null) {
                updateStatusToFailed(metadataId, "Audio download or encoding failed.");
            }
        } catch (RuntimeException e) {
            log.error("[{}] Runtime error during processing setup.", metadataId, e);
            if (metadataId != null) {
                updateStatusToFailed(metadataId, "Processing setup runtime error: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("[{}] Unexpected error during processing setup.", metadataId, e);
            if (metadataId != null) {
                updateStatusToFailed(metadataId, "Unexpected processing setup error: " + e.getMessage());
            }
        }
    }

    private String createPrompt(AudioMetadata metadata) {
         String prompt = "Please summarize the key points and action items from the following audio content";
         if (metadata.getTitle() != null && !metadata.getTitle().isBlank()) {
              prompt += ", titled '" + metadata.getTitle() + "'";
         }
         prompt += ". Structure the summary clearly. Use Markdown formatting, including bullet points (*) or numbered lists for key points/action items.";
         return prompt;
    }

    private void handleGeminiResponse(AudioMetadata metadata, String responseString) {
         String metadataId = metadata.getId();
         log.debug("[{}] Handling Gemini response: {}", metadataId, responseString.substring(0, Math.min(responseString.length(), 200)) + "...");

         try {
             JsonNode responseNode = objectMapper.readTree(responseString);

             if (responseNode.has("error")) {
                 String errorTitle = responseNode.path("error").asText("Unknown Error");
                 String errorDetails = responseNode.path("details").asText("");
                 log.error("[{}] Gemini API call failed. Error: '{}', Details: '{}'", metadataId, errorTitle, errorDetails);
                 updateStatusToFailed(metadataId, "Gemini API Error: " + errorTitle);

             } else if (responseNode.has("text")) {
                 String rawSummaryText = responseNode.path("text").asText();
                 log.info("[{}] Successfully received raw summary text from Gemini.", metadataId);
                 log.debug("[{}] Raw summary text: {}", metadataId, rawSummaryText.substring(0, Math.min(rawSummaryText.length(), 200)) + "...");

                 Summary structuredSummary = null;
                 try {
                     log.info("[{}] Structuring summary...", metadataId);
                     structuredSummary = new Summary();
                     structuredSummary.setSummaryId(UUID.randomUUID().toString());
                     structuredSummary.setRecordingId(metadataId);
                     structuredSummary.setFullText(rawSummaryText);
                     List<String> keyPoints = new ArrayList<>();
                     Matcher matcher = KEY_POINT_PATTERN.matcher(rawSummaryText);
                     while (matcher.find()) {
                         keyPoints.add(matcher.group(2).trim());
                     }
                     structuredSummary.setKeyPoints(keyPoints);
                     structuredSummary.setCondensedSummary(null);
                     structuredSummary.setTopics(new ArrayList<>());
                     log.info("[{}] Summary structured. Found {} potential key points.", metadataId, keyPoints.size());

                     log.info("[{}] Formatting summary as Markdown...", metadataId);
                     String formattedMarkdown = formatSummaryAsMarkdown(structuredSummary);
                     structuredSummary.setFormattedSummaryText(formattedMarkdown);
                     log.info("[{}] Summary formatted successfully.", metadataId);
                     log.debug("[{}] Formatted Markdown Summary (first 200 chars): {}", metadataId, formattedMarkdown.substring(0, Math.min(formattedMarkdown.length(), 200)) + "...");
                     log.debug("[{}] Final Structured & Formatted Summary object: {}", metadataId, structuredSummary.toMap());

                     log.info("[{}] Attempting to save summary (ID: {}) to Firestore...", metadataId, structuredSummary.getSummaryId());
                     firebaseService.saveSummary(structuredSummary);
                     log.info("[{}] Summary (ID: {}) saved successfully.", metadataId, structuredSummary.getSummaryId());

                     log.info("[{}] Updating AudioMetadata status to COMPLETED.", metadataId);
                     firebaseService.updateAudioMetadataStatus(metadataId, ProcessingStatus.COMPLETED);
                     log.info("[{}] AudioMetadata status successfully updated to COMPLETED.", metadataId);

                     try {
                         log.info("[{}] Triggering recommendation generation...", metadataId);
                         List<LearningRecommendation> recommendations = learningMaterialRecommenderService.generateAndSaveRecommendations(metadataId);
                         if (recommendations.isEmpty()) {
                             log.warn("[{}] Recommendation generation completed, but no recommendations were generated or saved.", metadataId);
                         } else {
                             log.info("[{}] Successfully generated and saved {} recommendations.", metadataId, recommendations.size());
                         }
                     } catch (Exception e) {
                         log.error("[{}] Failed to generate or save recommendations after successful summarization.", metadataId, e);
                     }

                 } catch (RuntimeException e) {
                     log.error("[{}] Error during summary post-processing (structuring/formatting/saving/status update).", metadataId, e);
                     updateStatusToFailed(metadataId, "Failed during summary post-processing: " + e.getMessage());
                 } catch (Exception e) {
                     log.error("[{}] Unexpected checked exception during summary post-processing.", metadataId, e);
                     updateStatusToFailed(metadataId, "Unexpected checked exception during summary post-processing: " + e.getMessage());
                 }

             } else {
                 log.error("[{}] Received unexpected JSON structure from GeminiService. Response: {}", metadataId, responseString);
                 updateStatusToFailed(metadataId, "Invalid response structure from Gemini service");
             }
         } catch (JsonProcessingException e) {
             log.error("[{}] Failed to parse JSON response from GeminiService. Response: {}", metadataId, responseString, e);
             updateStatusToFailed(metadataId, "Failed to parse Gemini service response");
         }
    }

    private String formatSummaryAsMarkdown(Summary summary) {
        StringBuilder markdownBuilder = new StringBuilder();

        if (summary.getFullText() != null && !summary.getFullText().isBlank()) {
            markdownBuilder.append(summary.getFullText().trim());
            markdownBuilder.append("\n\n");
        } else {
             log.warn("[{}] Full text for summary ID {} is empty or null during formatting.", summary.getRecordingId(), summary.getSummaryId());
        }

        List<String> keyPoints = summary.getKeyPoints();
        if (keyPoints != null && !keyPoints.isEmpty()) {
            boolean alreadyListed = summary.getFullText() != null &&
                                    keyPoints.stream().allMatch(kp -> summary.getFullText().contains(" " + kp));

            if (!alreadyListed) {
                 markdownBuilder.append("## Key Points\n\n");
                 for (String point : keyPoints) {
                     markdownBuilder.append("* ").append(point).append("\n");
                 }
                 markdownBuilder.append("\n");
            } else {
                 log.debug("[{}] Key points seem to be already included in the full text for summary ID {}. Skipping explicit list generation.", summary.getRecordingId(), summary.getSummaryId());
            }
        }
        return markdownBuilder.toString().trim();
    }


    private void updateStatusToFailed(String metadataId, String reason) {
        if (metadataId == null) {
            log.error("[AMQP Listener] Cannot update status to FAILED: metadataId is null.");
            return;
        }
        log.warn("[{}] Attempting to set status to FAILED. Reason: {}", metadataId, reason);
        try {
            AudioMetadata currentMeta = firebaseService.getAudioMetadataById(metadataId);
            if (currentMeta != null && currentMeta.getStatus() != ProcessingStatus.COMPLETED) {
                 firebaseService.updateAudioMetadataStatus(metadataId, ProcessingStatus.FAILED);
                 log.info("[{}] Status successfully updated to FAILED.", metadataId);
            } else if (currentMeta == null) {
                 log.error("[{}] Cannot update status to FAILED as metadata could not be retrieved.", metadataId);
            } else {
                 log.warn("[{}] Status is already COMPLETED. Not updating to FAILED.", metadataId);
            }
        } catch (RuntimeException e) {
            log.error("[{}] CRITICAL: Failed to update status to FAILED after processing error. Manual intervention likely required.", metadataId, e);
        }
    }
}
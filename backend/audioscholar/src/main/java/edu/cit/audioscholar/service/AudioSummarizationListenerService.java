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
import edu.cit.audioscholar.model.ProcessingStatus;

import java.io.IOException;


@Service
public class AudioSummarizationListenerService {

    private static final Logger log = LoggerFactory.getLogger(AudioSummarizationListenerService.class);

    private final FirebaseService firebaseService;
    private final NhostStorageService nhostStorageService;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;


    public AudioSummarizationListenerService(FirebaseService firebaseService,
                                             NhostStorageService nhostStorageService,
                                             GeminiService geminiService,
                                             ObjectMapper objectMapper
) {
        this.firebaseService = firebaseService;
        this.nhostStorageService = nhostStorageService;
        this.geminiService = geminiService;
        this.objectMapper = objectMapper;
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

            if (metadata.getStatus() != ProcessingStatus.PENDING && metadata.getStatus() != ProcessingStatus.UPLOADED) {
                 log.warn("[{}] Status is not PENDING or UPLOADED (Current: {}). Skipping processing.", metadataId, metadata.getStatus());
                 return;
            }

            log.info("[{}] Updating status to PROCESSING.", metadataId);
            firebaseService.updateAudioMetadataStatus(metadataId, ProcessingStatus.PROCESSING);

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
            log.error("[{}] Firestore error during processing.", metadataId, e);
            updateStatusToFailed(metadataId, "Firestore interaction failed during processing.");
        } catch (IOException e) {
            log.error("[{}] I/O error (likely Nhost download/encoding).", metadataId, e);
            updateStatusToFailed(metadataId, "Audio download or encoding failed.");
        } catch (RuntimeException e) {
            log.error("[{}] Runtime error during processing.", metadataId, e);
            updateStatusToFailed(metadataId, "Processing runtime error: " + e.getMessage());
        } catch (Exception e) {
            log.error("[{}] Unexpected error during processing.", metadataId, e);
            updateStatusToFailed(metadataId, "Unexpected processing error: " + e.getMessage());
        }
    }

    private String createPrompt(AudioMetadata metadata) {
         String prompt = "Please summarize the key points and action items from the following audio content";
         if (metadata.getTitle() != null && !metadata.getTitle().isBlank()) {
              prompt += ", titled '" + metadata.getTitle() + "'";
         }
         prompt += ". Structure the summary clearly.";
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

                 try {
                     log.info("[{}] Proceeding with summary structuring, formatting, and saving...", metadataId);



                     String formattedSummary = rawSummaryText;

                     log.warn("[{}] PLACEHOLDER: Summary saving logic not yet implemented.", metadataId);

                     log.info("[{}] Summary processing complete. Updating status to COMPLETED.", metadataId);
                     firebaseService.updateAudioMetadataStatus(metadataId, ProcessingStatus.COMPLETED);
                     log.info("[{}] Status successfully updated to COMPLETED.", metadataId);

                 } catch (Exception e) {
                      log.error("[{}] Error during summary post-processing (structuring/formatting/saving).", metadataId, e);
                      updateStatusToFailed(metadataId, "Failed during summary post-processing: " + e.getMessage());
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

    private void updateStatusToFailed(String metadataId, String reason) {
        if (metadataId == null) {
            log.error("[AMQP Listener] Cannot update status to FAILED: metadataId is null.");
            return;
        }
        log.warn("[{}] Attempting to set status to FAILED. Reason: {}", metadataId, reason);
        try {
            firebaseService.updateAudioMetadataStatus(metadataId, ProcessingStatus.FAILED);
            log.info("[{}] Status successfully updated to FAILED.", metadataId);
        } catch (RuntimeException e) {
            log.error("[{}] CRITICAL: Failed to update status to FAILED after processing error. Manual intervention likely required.", metadataId, e);
        }
    }
}
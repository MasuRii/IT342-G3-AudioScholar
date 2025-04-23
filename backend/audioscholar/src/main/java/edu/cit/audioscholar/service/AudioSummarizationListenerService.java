package edu.cit.audioscholar.service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        private final ObjectMapper objectMapper;

        public AudioSummarizationListenerService(FirebaseService firebaseService,
                        NhostStorageService nhostStorageService, GeminiService geminiService,
                        LearningMaterialRecommenderService learningMaterialRecommenderService,
                        @Lazy RecordingService recordingService,
                        @Lazy SummaryService summaryService, ObjectMapper objectMapper) {
                this.firebaseService = firebaseService;
                this.nhostStorageService = nhostStorageService;
                this.geminiService = geminiService;
                this.learningMaterialRecommenderService = learningMaterialRecommenderService;
                this.recordingService = recordingService;
                this.summaryService = summaryService;
                this.objectMapper = objectMapper;
        }

        @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
        public void handleAudioProcessingRequest(AudioProcessingMessage message) {
                if (message == null || message.getRecordingId() == null
                                || message.getRecordingId().isEmpty()
                                || message.getMetadataId() == null
                                || message.getMetadataId().isEmpty()) {
                        log.error("[AMQP Listener] Received invalid or incomplete message from queue '{}'. Ignoring. Message: {}",
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
                                log.error("[{}] AudioMetadata not found for ID received in message. Cannot process recording {}.",
                                                metadataId, recordingId);
                                return;
                        }
                        log.info("[{}] Found metadata. Current status: {}", metadataId,
                                        metadata.getStatus());

                        ProcessingStatus currentStatus = metadata.getStatus();
                        if (currentStatus == ProcessingStatus.COMPLETED
                                        || currentStatus == ProcessingStatus.FAILED) {
                                log.warn("[{}] Metadata status is already final ({}). Skipping processing for recording {}.",
                                                metadataId, currentStatus, recordingId);
                                return;
                        }
                        if (currentStatus != ProcessingStatus.PENDING
                                        && currentStatus != ProcessingStatus.PROCESSING) {
                                log.warn("[{}] Metadata status is not PENDING or PROCESSING (Current: {}). Skipping processing for recording {}.",
                                                metadataId, currentStatus, recordingId);
                                return;
                        }

                        log.debug("[{}] Fetching Recording document...", recordingId);
                        recording = recordingService.getRecordingById(recordingId);
                        if (recording == null) {
                                log.error("[{}] Recording document not found, but metadata {} exists. Cannot process audio.",
                                                recordingId, metadataId);
                                updateMetadataStatusToFailed(metadataId,
                                                "Associated Recording document not found.");
                                return;
                        }
                        log.info("[{}] Found recording.", recordingId);

                        if (currentStatus != ProcessingStatus.PROCESSING) {
                                log.info("[{}] Updating metadata {} status to PROCESSING.",
                                                recordingId, metadataId);
                                updateMetadataStatus(metadataId, ProcessingStatus.PROCESSING);
                        } else {
                                log.info("[{}] Resuming processing (metadata status already PROCESSING).",
                                                recordingId);
                        }

                        String nhostFileId = extractNhostIdFromUrl(recording.getAudioUrl());
                        if (nhostFileId == null) {
                                log.error("[{}] Could not extract Nhost File ID from URL: {}. Cannot download audio.",
                                                recordingId, recording.getAudioUrl());
                                updateMetadataStatusToFailed(metadataId,
                                                "Failed to extract Nhost File ID from URL.");
                                return;
                        }
                        log.info("[{}] Downloading audio from Nhost file ID: {}", recordingId,
                                        nhostFileId);
                        String base64Audio = nhostStorageService.downloadFileAsBase64(nhostFileId);
                        log.info("[{}] Successfully downloaded and Base64 encoded audio.",
                                        recordingId);

                        String prompt = createPrompt(recording);
                        log.debug("[{}] Generated Gemini prompt for JSON mode: '{}'", recordingId,
                                        prompt);
                        log.info("[{}] Calling Gemini API (JSON Mode) for audio summarization...",
                                        recordingId);
                        String geminiJsonResponse = geminiService.callGeminiAPIWithAudio(prompt,
                                        base64Audio, recording.getFileName());
                        log.info("[{}] Received response from GeminiService (JSON Mode). Processing...",
                                        recordingId);

                        handleGeminiJsonResponse(recording, metadataId, geminiJsonResponse);

                        AudioMetadata finalMetadata =
                                        firebaseService.getAudioMetadataById(metadataId);
                        if (finalMetadata != null && finalMetadata
                                        .getStatus() == ProcessingStatus.COMPLETED) {
                                try {
                                        log.info("[{}] Triggering recommendation generation...",
                                                        recordingId);
                                        List<LearningRecommendation> recommendations =
                                                        learningMaterialRecommenderService
                                                                        .generateAndSaveRecommendations(
                                                                                        recordingId);
                                        if (recommendations.isEmpty()) {
                                                log.warn("[{}] Recommendation generation completed, but no recommendations were generated or saved.",
                                                                recordingId);
                                        } else {
                                                log.info("[{}] Successfully generated and saved {} recommendations.",
                                                                recordingId,
                                                                recommendations.size());
                                        }
                                } catch (Exception e) {
                                        log.error("[{}] Failed to generate or save recommendations after successful summarization. Summary is saved, metadata status is COMPLETED.",
                                                        recordingId, e);
                                }
                        } else {
                                log.info("[{}] Skipping recommendation generation as metadata status is not COMPLETED (Current: {}).",
                                                recordingId,
                                                (finalMetadata != null ? finalMetadata.getStatus()
                                                                : "UNKNOWN/DELETED"));
                        }

                } catch (FirestoreInteractionException e) {
                        log.error("[{}] Firestore error during processing for metadata {}.",
                                        recordingId, metadataId, e);
                        updateMetadataStatusToFailed(metadataId,
                                        "Firestore interaction failed during processing.");
                } catch (IOException e) {
                        log.error("[{}] I/O error (likely Nhost download/encoding) for metadata {}.",
                                        recordingId, metadataId, e);
                        updateMetadataStatusToFailed(metadataId,
                                        "Audio download or encoding failed.");
                } catch (ExecutionException | InterruptedException e) {
                        log.error("[{}] Concurrency/Execution error during processing for metadata {}.",
                                        recordingId, metadataId, e);
                        Thread.currentThread().interrupt();
                        updateMetadataStatusToFailed(metadataId,
                                        "Concurrency error during processing.");
                } catch (RuntimeException e) {
                        log.error("[{}] Runtime error during processing for metadata {}.",
                                        recordingId, metadataId, e);
                        updateMetadataStatusToFailed(metadataId,
                                        "Processing runtime error: " + e.getMessage());
                } catch (Exception e) {
                        log.error("[{}] Unexpected error during processing for metadata {}.",
                                        recordingId, metadataId, e);
                        updateMetadataStatusToFailed(metadataId,
                                        "Unexpected processing error: " + e.getMessage());
                }
        }

        private String createPrompt(Recording recording) {
                String titleClause = "";
                if (recording.getTitle() != null && !recording.getTitle().isBlank()) {
                        titleClause = " titled '" + recording.getTitle() + "'";
                }
                return """
                                Analyze the following audio content%s.
                                Generate a concise, well-structured summary using Markdown in the `summaryText` field.
                                Identify the main key points or action items discussed and list them in the `keyPoints` array.
                                List the 3-5 most important topics or keywords suitable for searching related content in the `topics` array.
                                Identify key terms or jargon used in the audio. For each term, provide a concise definition relevant to the audio's context. Structure this as an array of objects in the `glossary` field, where each object has a `term` (string) and a `definition` (string).
                                Ensure the entire output strictly adheres to the provided JSON schema.
                                """
                                .formatted(titleClause);
        }

        private void handleGeminiJsonResponse(Recording recording, String metadataId,
                        String geminiJsonResponse) {
                String recordingId = recording.getRecordingId();
                log.debug("[{}] Handling direct Gemini JSON response string (first 500 chars): {}",
                                recordingId,
                                geminiJsonResponse.substring(0,
                                                Math.min(geminiJsonResponse.length(), 500))
                                                + "...");

                String summaryText = null;
                List<String> keyPoints = Collections.emptyList();
                List<String> topics = Collections.emptyList();
                List<Map<String, String>> glossary = Collections.emptyList();
                boolean processedSuccessfully = false;

                try {
                        JsonNode responseNode = objectMapper.readTree(geminiJsonResponse);

                        if (responseNode.has("error")) {
                                String errorTitle =
                                                responseNode.path("error").asText("Unknown Error");
                                String errorDetails = responseNode.path("details")
                                                .asText("No details provided");
                                log.error("[{}] GeminiService indicated an error: {} - {}",
                                                recordingId, errorTitle, errorDetails);
                                updateMetadataStatusToFailed(metadataId,
                                                "AI service failed: " + errorTitle);
                                return;
                        }

                        if (responseNode.hasNonNull("summaryText")) {
                                summaryText = responseNode.path("summaryText").asText();
                        } else {
                                log.error("[{}] Gemini JSON response missing required 'summaryText' field. Response: {}",
                                                recordingId, geminiJsonResponse);
                                updateMetadataStatusToFailed(metadataId,
                                                "AI response missing required 'summaryText'.");
                                return;
                        }

                        if (responseNode.hasNonNull("keyPoints")
                                        && responseNode.path("keyPoints").isArray()) {
                                keyPoints = objectMapper.convertValue(
                                                responseNode.path("keyPoints"),
                                                new TypeReference<List<String>>() {});
                        } else {
                                log.warn("[{}] Gemini JSON response missing 'keyPoints' array field or not an array. Proceeding with empty list.",
                                                recordingId);
                        }

                        if (responseNode.hasNonNull("topics")
                                        && responseNode.path("topics").isArray()) {
                                topics = objectMapper.convertValue(responseNode.path("topics"),
                                                new TypeReference<List<String>>() {});
                        } else {
                                log.warn("[{}] Gemini JSON response missing 'topics' array field or not an array. Proceeding with empty list.",
                                                recordingId);
                        }

                        if (responseNode.hasNonNull("glossary")
                                        && responseNode.path("glossary").isArray()) {
                                try {
                                        glossary = objectMapper.convertValue(
                                                        responseNode.path("glossary"),
                                                        new TypeReference<List<Map<String, String>>>() {});
                                        log.info("[{}] Successfully parsed glossary with {} items.",
                                                        recordingId, glossary.size());
                                } catch (IllegalArgumentException e) {
                                        log.warn("[{}] Failed to parse 'glossary' field, likely due to incorrect item structure. Proceeding with empty glossary. Error: {}",
                                                        recordingId, e.getMessage());
                                        glossary = Collections.emptyList();
                                }
                        } else {
                                log.info("[{}] Gemini JSON response missing 'glossary' array field or not an array. Proceeding with empty glossary.",
                                                recordingId);
                        }

                        log.info("[{}] Successfully parsed direct JSON response. Found {} key points, {} topics, and {} glossary items.",
                                        recordingId, keyPoints.size(), topics.size(),
                                        glossary.size());
                        processedSuccessfully = true;

                        Summary structuredSummary = new Summary();
                        structuredSummary.setSummaryId(UUID.randomUUID().toString());
                        structuredSummary.setRecordingId(recordingId);
                        structuredSummary.setFullText(summaryText);
                        structuredSummary.setFormattedSummaryText(summaryText);
                        structuredSummary.setKeyPoints(keyPoints);
                        structuredSummary.setTopics(topics);
                        structuredSummary.setGlossary(glossary);
                        structuredSummary.setCondensedSummary(null);

                        log.info("[{}] Attempting to save summary (ID: {}) with {} key points, {} topics, {} glossary items to Firestore.",
                                        recordingId, structuredSummary.getSummaryId(),
                                        structuredSummary.getKeyPoints().size(),
                                        structuredSummary.getTopics().size(),
                                        structuredSummary.getGlossary().size());

                        summaryService.createSummary(structuredSummary);
                        log.info("[{}] Summary (ID: {}) saved successfully and linked to recording.",
                                        recordingId, structuredSummary.getSummaryId());

                        log.info("[{}] Updating metadata {} status to COMPLETED.", recordingId,
                                        metadataId);
                        updateMetadataStatus(metadataId, ProcessingStatus.COMPLETED);

                } catch (JsonProcessingException e) {
                        log.error("[{}] Failed to parse JSON response received from GeminiService. Error: {}. Response: {}",
                                        recordingId, e.getMessage(),
                                        geminiJsonResponse.substring(0,
                                                        Math.min(geminiJsonResponse.length(), 1000))
                                                        + "...");
                        updateMetadataStatusToFailed(metadataId,
                                        "Failed to parse AI service JSON response.");
                } catch (RuntimeException | ExecutionException | InterruptedException e) {
                        log.error("[{}] Error during summary saving or status update for metadata {}.",
                                        recordingId, metadataId, e);
                        if (e instanceof InterruptedException)
                                Thread.currentThread().interrupt();
                        if (!processedSuccessfully) {
                                updateMetadataStatusToFailed(metadataId,
                                                "Failed during summary processing/saving: "
                                                                + e.getMessage());
                        } else {
                                log.error("[{}] Error occurred *after* successful JSON parsing, possibly during Firestore save or status update. Summary might be partially processed.",
                                                recordingId);
                        }
                } catch (Exception e) {
                        log.error("[{}] Unexpected exception during summary processing for metadata {}.",
                                        recordingId, metadataId, e);
                        if (!processedSuccessfully) {
                                updateMetadataStatusToFailed(metadataId,
                                                "Unexpected exception during summary processing: "
                                                                + e.getMessage());
                        }
                }
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
                        AudioMetadata currentMeta =
                                        firebaseService.getAudioMetadataById(metadataId);
                        if (currentMeta != null) {
                                if (currentMeta.getStatus() == ProcessingStatus.COMPLETED
                                                && newStatus == ProcessingStatus.FAILED) {
                                        log.warn("[{}] Metadata status is already COMPLETED. Not overwriting with FAILED due to reason: {}",
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
                                log.info("[{}] Metadata status successfully updated to {}.",
                                                metadataId, newStatus);
                        } else {
                                log.error("[{}] Cannot update metadata status to {} as metadata could not be retrieved (might have been deleted?).",
                                                metadataId, newStatus);
                        }
                } catch (Exception e) {
                        log.error("[{}] CRITICAL: Failed to update metadata status to {} after processing event. Manual intervention likely required. Error: {}",
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
                                                log.debug("Extracted Nhost ID '{}' from URL '{}'",
                                                                potentialId, url);
                                                return potentialId;
                                        }
                                }
                        }
                } catch (Exception e) {
                        log.error("Failed to parse Nhost ID from URL '{}': {}", url,
                                        e.getMessage());
                }
                log.warn("Could not extract Nhost ID using expected pattern from URL: {}", url);
                return null;
        }
}

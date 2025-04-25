package edu.cit.audioscholar.service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
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

        @RabbitListener(queues = RabbitMQConfig.PROCESSING_QUEUE_NAME)
        public void handleAudioProcessingRequest(AudioProcessingMessage message) {
                if (message == null || message.getRecordingId() == null
                                || message.getRecordingId().isEmpty()
                                || message.getMetadataId() == null
                                || message.getMetadataId().isEmpty()) {
                        log.error("[AMQP Listener] Received invalid or incomplete message from queue '{}'. Ignoring. Message: {}",
                                        RabbitMQConfig.PROCESSING_QUEUE_NAME, message);
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
                                log.error("[{}] AudioMetadata not found for ID. Cannot process recording {}.",
                                                metadataId, recordingId);
                                return;
                        }
                        log.info("[{}] Found metadata. Current status: {}", metadataId,
                                        metadata.getStatus());

                        if (metadata.getStatus() != ProcessingStatus.PENDING) {
                                log.warn("[AMQP Listener] Metadata {} status is not PENDING (it's {}). Skipping summarization processing.",
                                                metadataId, metadata.getStatus());
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

                        log.info("[{}] Updating metadata {} status to PROCESSING.", recordingId,
                                        metadataId);
                        updateMetadataStatus(metadataId, ProcessingStatus.PROCESSING, null);


                        String base64Audio = downloadAudio(recording);
                        if (base64Audio == null) {
                                updateMetadataStatusToFailed(metadataId,
                                                "Failed to download or encode audio.");
                                return;
                        }

                        String transcriptText = performTranscription(recording, base64Audio);
                        if (transcriptText == null) {
                                updateMetadataStatusToFailed(metadataId,
                                                "Audio transcription failed.");
                                return;
                        }

                        if (!saveTranscript(metadataId, transcriptText)) {
                                updateMetadataStatusToFailed(metadataId,
                                                "Failed to save transcript to metadata.");
                                return;
                        }
                        metadata.setTranscriptText(transcriptText);

                        String summarizationJson = performSummarization(recording, transcriptText);
                        if (summarizationJson == null) {
                                updateMetadataStatusToFailed(metadataId,
                                                "Content summarization and analysis failed.");
                                return;
                        }

                        Summary savedSummary = parseAndSaveSummary(recording, metadataId,
                                        summarizationJson);
                        if (savedSummary == null) {
                                AudioMetadata currentMeta =
                                                firebaseService.getAudioMetadataById(metadataId);
                                if (currentMeta != null && currentMeta
                                                .getStatus() != ProcessingStatus.FAILED) {
                                        updateMetadataStatusToFailed(metadataId,
                                                        "Failed to save summary after successful generation.");
                                }
                                return;
                        }

                        finalizeProcessing(metadataId, savedSummary.getSummaryId());
                        triggerRecommendations(recordingId, metadataId);

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

        private String downloadAudio(Recording recording) throws IOException {
                String recordingId = recording.getRecordingId();
                String nhostFileId = extractNhostIdFromUrl(recording.getAudioUrl());
                if (nhostFileId == null) {
                        log.error("[{}] Could not extract Nhost File ID from URL: {}. Cannot download audio.",
                                        recordingId, recording.getAudioUrl());
                        return null;
                }
                log.info("[{}] Downloading audio from Nhost file ID: {}", recordingId, nhostFileId);
                String base64Audio = nhostStorageService.downloadFileAsBase64(nhostFileId);
                log.info("[{}] Successfully downloaded and Base64 encoded audio.", recordingId);
                return base64Audio;
        }

        private String performTranscription(Recording recording, String base64Audio) {
                String recordingId = recording.getRecordingId();
                log.info("[{}] Calling Gemini Transcription API (Flash Model)...", recordingId);
                String transcriptionResult = geminiService.callGeminiTranscriptionAPI(base64Audio,
                                recording.getFileName());
                if (isErrorResponse(transcriptionResult)) {
                        log.error("[{}] Gemini Transcription API failed. Response: {}", recordingId,
                                        transcriptionResult);
                        return null;
                }
                if (transcriptionResult.isBlank()) {
                        log.error("[{}] Gemini Transcription API returned an empty result.",
                                        recordingId);
                        return null;
                }
                log.info("[{}] Transcription successful.", recordingId);
                log.debug("[{}] Transcript (first 100 chars): {}", recordingId,
                                transcriptionResult.substring(0,
                                                Math.min(transcriptionResult.length(), 100))
                                                + "...");
                return transcriptionResult;
        }

        private boolean saveTranscript(String metadataId, String transcriptText) {
                log.info("[{}] Attempting to save transcript text to metadata.", metadataId);
                try {
                        Map<String, Object> updateMap = Map.of("transcriptText", transcriptText);
                        firebaseService.updateDataWithMap(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, updateMap);
                        log.info("[{}] Successfully saved transcript text to metadata.",
                                        metadataId);
                        return true;
                } catch (Exception e) {
                        log.error("[{}] CRITICAL: Failed to save transcript text to metadata. Error: {}",
                                        metadataId, e.getMessage(), e);
                        if (e instanceof InterruptedException)
                                Thread.currentThread().interrupt();
                        return false;
                }
        }

        private String performSummarization(Recording recording, String transcriptText) {
                String recordingId = recording.getRecordingId();
                String prompt = createSummarizationPrompt(recording);
                log.debug("[{}] Generated Gemini Summarization prompt for JSON mode: '{}'",
                                recordingId, prompt);
                log.info("[{}] Calling Gemini Summarization API (Pro Model, JSON Mode)...",
                                recordingId);
                String summarizationResult =
                                geminiService.callGeminiSummarizationAPI(prompt, transcriptText);
                if (isErrorResponse(summarizationResult)) {
                        log.error("[{}] Gemini Summarization API failed. Response: {}", recordingId,
                                        summarizationResult);
                        return null;
                }
                if (summarizationResult.isBlank()) {
                        log.error("[{}] Gemini Summarization API returned an empty result.",
                                        recordingId);
                        return null;
                }
                log.info("[{}] Summarization API call successful. Received JSON response.",
                                recordingId);
                log.debug("[{}] Summarization JSON (first 500 chars): {}", recordingId,
                                summarizationResult.substring(0,
                                                Math.min(summarizationResult.length(), 500))
                                                + "...");
                return summarizationResult;
        }

        private Summary parseAndSaveSummary(Recording recording, String metadataId,
                        String summarizationJson) {
                String recordingId = recording.getRecordingId();
                try {
                        JsonNode responseNode = objectMapper.readTree(summarizationJson);
                        if (responseNode.has("error")) {
                                String errorTitle =
                                                responseNode.path("error").asText("Unknown Error");
                                String errorDetails = responseNode.path("details")
                                                .asText("No details provided");
                                log.error("[{}] Parsed JSON indicates an error (should have been caught earlier): {} - {}",
                                                recordingId, errorTitle, errorDetails);
                                updateMetadataStatusToFailed(metadataId,
                                                "AI service failed: " + errorTitle);
                                return null;
                        }

                        String summaryText = responseNode.path("summaryText").asText(null);
                        List<String> keyPoints =
                                        parseStringList(responseNode, "keyPoints", recordingId);
                        List<String> topics = parseStringList(responseNode, "topics", recordingId);
                        List<Map<String, String>> glossary =
                                        parseGlossary(responseNode, recordingId);

                        if (summaryText == null) {
                                log.error("[{}] Gemini JSON response missing required 'summaryText' field. Response: {}",
                                                recordingId, summarizationJson);
                                updateMetadataStatusToFailed(metadataId,
                                                "AI response missing required 'summaryText'.");
                                return null;
                        }

                        log.info("[{}] Successfully parsed JSON response. Found {} key points, {} topics, and {} glossary items.",
                                        recordingId, keyPoints.size(), topics.size(),
                                        glossary.size());

                        Summary structuredSummary = new Summary();
                        structuredSummary.setSummaryId(UUID.randomUUID().toString());
                        structuredSummary.setRecordingId(recordingId);
                        structuredSummary.setFormattedSummaryText(summaryText);
                        structuredSummary.setKeyPoints(keyPoints);
                        structuredSummary.setTopics(topics);
                        structuredSummary.setGlossary(glossary);

                        log.info("[{}] Attempting to save summary (ID: {}) with {} key points, {} topics, {} glossary items to Firestore.",
                                        recordingId, structuredSummary.getSummaryId(),
                                        keyPoints.size(), topics.size(), glossary.size());
                        Summary savedSummary = summaryService.createSummary(structuredSummary);
                        log.info("[{}] Summary (ID: {}) saved successfully.", recordingId,
                                        savedSummary.getSummaryId());
                        return savedSummary;

                } catch (JsonProcessingException e) {
                        log.error("[{}] Failed to parse JSON response received from Summarization API. Error: {}. Response: {}",
                                        recordingId, e.getMessage(),
                                        summarizationJson.substring(0,
                                                        Math.min(summarizationJson.length(), 1000))
                                                        + "...");
                        updateMetadataStatusToFailed(metadataId,
                                        "Failed to parse AI service JSON response.");
                        return null;
                } catch (ExecutionException | InterruptedException e) {
                        log.error("[{}] Error during summary saving for metadata {}.", recordingId,
                                        metadataId, e);
                        if (e instanceof InterruptedException)
                                Thread.currentThread().interrupt();
                        updateMetadataStatusToFailed(metadataId,
                                        "Failed during summary saving: " + e.getMessage());
                        return null;
                } catch (Exception e) {
                        log.error("[{}] Unexpected exception during summary processing/saving for metadata {}.",
                                        recordingId, metadataId, e);
                        updateMetadataStatusToFailed(metadataId,
                                        "Unexpected exception during summary processing: "
                                                        + e.getMessage());
                        return null;
                }
        }

        private void finalizeProcessing(String metadataId, String summaryId) {
                log.info("[{}] Finalizing processing. Attempting to link summaryId {} and set status to COMPLETED.",
                                metadataId, summaryId);
                try {
                        Map<String, Object> updateMap = Map.of("summaryId", summaryId, "status",
                                        ProcessingStatus.COMPLETED.name());
                        firebaseService.updateDataWithMap(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, updateMap);
                        log.info("[{}] Successfully updated metadata status to COMPLETED and linked summaryId {}.",
                                        metadataId, summaryId);
                } catch (Exception e) {
                        log.error("[{}] CRITICAL: Failed to update metadata status to COMPLETED or link summaryId {} after saving summary. Summary is saved, but metadata might be inconsistent. Error: {}",
                                        metadataId, summaryId, e.getMessage(), e);
                        if (e instanceof InterruptedException)
                                Thread.currentThread().interrupt();
                }
        }

        private void triggerRecommendations(String recordingId, String metadataId) {
                AudioMetadata finalMetadata = null;
                try {
                        finalMetadata = firebaseService.getAudioMetadataById(metadataId);
                } catch (Exception fetchEx) {
                        log.warn("[{}] Failed to re-fetch metadata {} after processing to check status for recommendation trigger. Error: {}",
                                        recordingId, metadataId, fetchEx.getMessage());
                        return;
                }

                if (finalMetadata != null
                                && finalMetadata.getStatus() == ProcessingStatus.COMPLETED) {
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
                                                        recordingId, recommendations.size());
                                }
                        } catch (Exception e) {
                                log.error("[{}] Failed to generate or save recommendations after successful summarization. Summary is saved, metadata status is COMPLETED. Error: {}",
                                                recordingId, e.getMessage(), e);
                        }
                } else {
                        log.info("[{}] Skipping recommendation generation as metadata status is not COMPLETED (Current: {}).",
                                        recordingId,
                                        (finalMetadata != null ? finalMetadata.getStatus()
                                                        : "UNKNOWN/FETCH_FAILED"));
                }
        }

        private String createSummarizationPrompt(Recording recording) {
                String titleClause = "";
                if (recording.getTitle() != null && !recording.getTitle().isBlank()) {
                        titleClause = " titled '" + recording.getTitle() + "'";
                }
                return """
                                Analyze the following lecture transcript%s.
                                Generate a concise, well-structured summary using Markdown in the `summaryText` field. Use headings (##) for main sections and bullet points (* or -) for details. Focus on core arguments, findings, definitions, and conclusions.
                                Identify the main key points or action items discussed and list them as distinct strings in the `keyPoints` array.
                                List the 3-5 most important topics or keywords suitable for searching related content in the `topics` array.
                                Identify important **terms, concepts, acronyms, proper nouns (people, places, organizations mentioned), and technical vocabulary** discussed in the transcript. For each, provide a concise definition relevant to the transcript's context. Structure this as an array of objects in the `glossary` field, where each object has a `term` (string) and a `definition` (string). Aim for comprehensive coverage of potentially unfamiliar items for a learner.
                                Ensure the entire output strictly adheres to the provided JSON schema. Output only the JSON object.
                                """
                                .formatted(titleClause);
        }

        private boolean isErrorResponse(String response) {
                if (response == null || response.isBlank()) {
                        return true;
                }
                if (response.trim().startsWith("{\"error\":")) {
                        try {
                                JsonNode node = objectMapper.readTree(response);
                                return node.has("error");
                        } catch (JsonProcessingException e) {
                                log.warn("Could not parse potential error JSON: {}", response, e);
                                return true;
                        }
                }
                return false;
        }

        private List<String> parseStringList(JsonNode parentNode, String fieldName,
                        String recordingId) {
                if (parentNode.hasNonNull(fieldName) && parentNode.path(fieldName).isArray()) {
                        try {
                                return objectMapper.convertValue(parentNode.path(fieldName),
                                                new TypeReference<List<String>>() {});
                        } catch (IllegalArgumentException e) {
                                log.warn("[{}] Failed to parse '{}' field as List<String>. Proceeding with empty list. Error: {}",
                                                recordingId, fieldName, e.getMessage());
                                return Collections.emptyList();
                        }
                } else {
                        log.warn("[{}] Gemini JSON response missing '{}' array field or not an array. Proceeding with empty list.",
                                        recordingId, fieldName);
                        return Collections.emptyList();
                }
        }

        private List<Map<String, String>> parseGlossary(JsonNode parentNode, String recordingId) {
                if (parentNode.hasNonNull("glossary") && parentNode.path("glossary").isArray()) {
                        try {
                                List<Map<String, String>> glossary = objectMapper.convertValue(
                                                parentNode.path("glossary"),
                                                new TypeReference<List<Map<String, String>>>() {});
                                glossary.removeIf(item -> {
                                        boolean invalid = !item.containsKey("term")
                                                        || !item.containsKey("definition")
                                                        || !(item.get("term") instanceof String)
                                                        || !(item.get("definition") instanceof String);
                                        if (invalid) {
                                                log.warn("[{}] Invalid glossary item structure found: {}. Skipping item.",
                                                                recordingId, item);
                                        }
                                        return invalid;
                                });
                                log.info("[{}] Successfully parsed glossary with {} valid items.",
                                                recordingId, glossary.size());
                                return glossary;
                        } catch (IllegalArgumentException e) {
                                log.warn("[{}] Failed to parse 'glossary' field, likely due to incorrect item structure. Proceeding with empty glossary. Error: {}",
                                                recordingId, e.getMessage());
                                return Collections.emptyList();
                        }
                } else {
                        log.info("[{}] Gemini JSON response missing 'glossary' array field or not an array. Proceeding with empty glossary.",
                                        recordingId);
                        return Collections.emptyList();
                }
        }

        private void updateMetadataStatusToFailed(String metadataId, String reason) {
                updateMetadataStatus(metadataId, ProcessingStatus.FAILED, reason);
        }

        private void updateMetadataStatus(String metadataId, ProcessingStatus newStatus,
                        @Nullable String reason) {
                if (metadataId == null) {
                        log.error("[AMQP Listener] Cannot update metadata status: metadataId is null.");
                        return;
                }
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

                                Map<String, Object> updateMap = new HashMap<>();
                                updateMap.put("status", newStatus.name());
                                if (reason != null) {
                                        updateMap.put("failureReason", reason);
                                } else {
                                        updateMap.put("failureReason", null);
                                }

                                firebaseService.updateDataWithMap(
                                                firebaseService.getAudioMetadataCollectionName(),
                                                metadataId, updateMap);
                                log.info("[{}] Metadata status successfully updated to {}.",
                                                metadataId, newStatus);
                        } else {
                                log.error("[{}] Cannot update metadata status to {} as metadata could not be retrieved (might have been deleted?).",
                                                metadataId, newStatus);
                        }
                } catch (Exception e) {
                        log.error("[{}] CRITICAL: Failed to update metadata status to {}. Manual intervention likely required. Error: {}",
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
                        if (parts.length > 2 && "files".equals(parts[parts.length - 2])) {
                                String potentialId = parts[parts.length - 1];
                                if (potentialId.matches("[a-zA-Z0-9\\-]+")) {
                                        log.debug("Extracted Nhost ID '{}' from URL '{}'",
                                                        potentialId, url);
                                        return potentialId;
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

package edu.cit.audioscholar.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import edu.cit.audioscholar.config.RabbitMQConfig;
import edu.cit.audioscholar.dto.AudioProcessingMessage;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.ProcessingStatus;
import edu.cit.audioscholar.model.Recording;
import edu.cit.audioscholar.model.Summary;

@Service
public class AudioSummarizationListenerService {
        private static final Logger log =
                        LoggerFactory.getLogger(AudioSummarizationListenerService.class);
        private static final String CACHE_METADATA_BY_USER = "audioMetadataByUser";

        private final FirebaseService firebaseService;
        private final NhostStorageService nhostStorageService;
        private final GeminiService geminiService;
        private final LearningMaterialRecommenderService learningMaterialRecommenderService;
        private final RecordingService recordingService;
        private final SummaryService summaryService;
        private final ObjectMapper objectMapper;
        private final CacheManager cacheManager;
        private final Path tempFileDir;

        public AudioSummarizationListenerService(FirebaseService firebaseService,
                        NhostStorageService nhostStorageService, GeminiService geminiService,
                        LearningMaterialRecommenderService learningMaterialRecommenderService,
                        @Lazy RecordingService recordingService,
                        @Lazy SummaryService summaryService, ObjectMapper objectMapper,
                        CacheManager cacheManager,
                        @Value("${app.temp-file-dir}") String tempFileDirStr) {
                this.firebaseService = firebaseService;
                this.nhostStorageService = nhostStorageService;
                this.geminiService = geminiService;
                this.learningMaterialRecommenderService = learningMaterialRecommenderService;
                this.recordingService = recordingService;
                this.summaryService = summaryService;
                this.objectMapper = objectMapper;
                this.cacheManager = cacheManager;
                this.tempFileDir = Paths.get(tempFileDirStr);
                try {
                        Files.createDirectories(this.tempFileDir);
                } catch (IOException e) {
                        log.error("Could not create temporary directory for listener: {}",
                                        this.tempFileDir.toAbsolutePath(), e);
                }
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

                final String recordingId = message.getRecordingId();
                final String metadataId = message.getMetadataId();
                log.info("[AMQP Listener] Received request for recordingId: {}, metadataId: {}",
                                recordingId, metadataId);

                AudioMetadata metadata = null;
                Recording recording = null;
                Path tempAudioFilePath = null;

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

                        tempAudioFilePath = downloadAudioToFile(recording, metadataId);
                        if (tempAudioFilePath == null) {
                                return;
                        }

                        String transcriptText = performTranscription(recording, tempAudioFilePath,
                                        metadataId);
                        if (transcriptText == null) {
                                return;
                        }

                        if (!saveTranscript(metadataId, transcriptText)) {
                                updateMetadataStatusToFailed(metadataId,
                                                "Failed to save transcript to metadata.");
                                return;
                        }
                        metadata.setTranscriptText(transcriptText);

                        String summarizationJson =
                                        performSummarization(recording, transcriptText, metadataId);
                        if (summarizationJson == null) {
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
                        triggerRecommendations(recording.getUserId(), recordingId,
                                        savedSummary.getSummaryId());

                } catch (FirestoreInteractionException e) {
                        log.error("[{}] Firestore error during processing for metadata {}.",
                                        recordingId, metadataId, e);
                        updateMetadataStatusToFailed(metadataId,
                                        "Firestore interaction failed during processing.");
                } catch (ExecutionException | InterruptedException e) {
                        log.error("[{}] Concurrency/Execution error during processing for metadata {}.",
                                        recordingId, metadataId, e);
                        Thread.currentThread().interrupt();
                        updateMetadataStatusToFailed(metadataId,
                                        "Concurrency error during processing.");
                } catch (RuntimeException e) {
                        log.error("[{}] Runtime error during processing for metadata {}.",
                                        recordingId, metadataId, e);
                        AudioMetadata currentMeta =
                                        firebaseService.getAudioMetadataById(metadataId);
                        if (currentMeta != null
                                        && currentMeta.getStatus() != ProcessingStatus.FAILED) {
                                updateMetadataStatusToFailed(metadataId,
                                                "Processing runtime error: " + e.getMessage());
                        }
                } catch (Exception e) {
                        log.error("[{}] Unexpected error during processing for metadata {}.",
                                        recordingId, metadataId, e);
                        AudioMetadata currentMeta =
                                        firebaseService.getAudioMetadataById(metadataId);
                        if (currentMeta != null
                                        && currentMeta.getStatus() != ProcessingStatus.FAILED) {
                                updateMetadataStatusToFailed(metadataId,
                                                "Unexpected processing error: " + e.getMessage());
                        }
                } finally {
                        if (tempAudioFilePath != null) {
                                try {
                                        log.debug("[{}] Deleting temporary audio file: {}",
                                                        recordingId,
                                                        tempAudioFilePath.toAbsolutePath());
                                        Files.deleteIfExists(tempAudioFilePath);
                                        log.info("[{}] Successfully deleted temporary audio file: {}",
                                                        recordingId,
                                                        tempAudioFilePath.toAbsolutePath());
                                } catch (IOException e) {
                                        log.warn("[{}] Failed to delete temporary audio file: {}. Error: {}",
                                                        recordingId,
                                                        tempAudioFilePath.toAbsolutePath(),
                                                        e.getMessage());
                                }
                        }
                }
        }

        private Path downloadAudioToFile(Recording recording, String metadataId) {
                String recordingId = recording.getRecordingId();
                Path tempFilePath = null;
                try {
                        String nhostFileId = extractNhostIdFromUrl(recording.getAudioUrl());
                        if (nhostFileId == null) {
                                log.error("[{}] Could not extract Nhost File ID from URL: {}. Cannot download audio.",
                                                recordingId, recording.getAudioUrl());
                                updateMetadataStatusToFailed(metadataId,
                                                "Invalid Nhost URL in Recording document.");
                                return null;
                        }
                        String fileExtension = Optional.ofNullable(recording.getFileName())
                                        .map(f -> f.contains(".") ? f.substring(f.lastIndexOf("."))
                                                        : ".tmp")
                                        .orElse(".tmp");
                        tempFilePath = Files.createTempFile(tempFileDir,
                                        "audio_" + recordingId + "_", fileExtension);
                        log.info("[{}] Created temporary file path for download: {}", recordingId,
                                        tempFilePath.toAbsolutePath());
                        nhostStorageService.downloadFileToPath(nhostFileId, tempFilePath);
                        log.info("[{}] Successfully downloaded audio from Nhost file ID {} to {}",
                                        recordingId, nhostFileId, tempFilePath.toAbsolutePath());
                        return tempFilePath;
                } catch (IOException e) {
                        log.error("[{}] IOException during audio download/saving for metadata {}. Temp path: {}. Error: {}",
                                        recordingId, metadataId, tempFilePath, e.getMessage(), e);
                        updateMetadataStatusToFailed(metadataId,
                                        "Audio download or saving failed: " + e.getMessage());
                        if (tempFilePath != null) {
                                try {
                                        Files.deleteIfExists(tempFilePath);
                                } catch (IOException ignored) {
                                }
                        }
                        return null;
                } catch (Exception e) {
                        log.error("[{}] Unexpected exception during audio download for metadata {}. Temp path: {}. Error: {}",
                                        recordingId, metadataId, tempFilePath, e.getMessage(), e);
                        updateMetadataStatusToFailed(metadataId,
                                        "Unexpected download error: " + e.getMessage());
                        if (tempFilePath != null) {
                                try {
                                        Files.deleteIfExists(tempFilePath);
                                } catch (IOException ignored) {
                                }
                        }
                        return null;
                }
        }

        private String performTranscription(Recording recording, Path audioFilePath,
                        String metadataId) {
                String recordingId = recording.getRecordingId();
                try {
                        log.info("[{}] Calling Gemini Transcription API (Flash Model) using file: {}",
                                        recordingId, audioFilePath.toAbsolutePath());
                        String transcriptionResult = geminiService.callGeminiTranscriptionAPI(
                                        audioFilePath, recording.getFileName());
                        if (isErrorResponse(transcriptionResult)) {
                                log.error("[{}] Gemini Transcription API failed. Response: {}",
                                                recordingId, transcriptionResult);
                                String errorDetails = "Transcription API Error";
                                try {
                                        JsonNode errorNode =
                                                        objectMapper.readTree(transcriptionResult);
                                        errorDetails = errorNode.path("error")
                                                        .asText("Transcription API Error") + ": "
                                                        + errorNode.path("details").asText(
                                                                        "Details unavailable");
                                } catch (JsonProcessingException ignored) {
                                }
                                updateMetadataStatusToFailed(metadataId, errorDetails);
                                return null;
                        }
                        if (transcriptionResult.isBlank()) {
                                log.error("[{}] Gemini Transcription API returned an empty result.",
                                                recordingId);
                                updateMetadataStatusToFailed(metadataId,
                                                "Audio transcription returned empty result.");
                                return null;
                        }
                        log.info("[{}] Transcription successful.", recordingId);
                        log.debug("[{}] Transcript (first 100 chars): {}", recordingId,
                                        transcriptionResult.substring(0,
                                                        Math.min(transcriptionResult.length(), 100))
                                                        + "...");
                        return transcriptionResult;
                } catch (IOException e) {
                        log.error("[{}] IOException during transcription (reading temp file?) for metadata {}. File: {}. Error: {}",
                                        recordingId, metadataId, audioFilePath.toAbsolutePath(),
                                        e.getMessage(), e);
                        updateMetadataStatusToFailed(metadataId,
                                        "Transcription I/O error: " + e.getMessage());
                        return null;
                } catch (Exception e) {
                        log.error("[{}] Unexpected exception during transcription for metadata {}. File: {}. Error: {}",
                                        recordingId, metadataId, audioFilePath.toAbsolutePath(),
                                        e.getMessage(), e);
                        updateMetadataStatusToFailed(metadataId,
                                        "Unexpected transcription error: " + e.getMessage());
                        return null;
                }
        }

        private String performSummarization(Recording recording, String transcriptText,
                        String metadataId) {
                String recordingId = recording.getRecordingId();
                try {
                        String prompt = createSummarizationPrompt(recording);
                        log.debug("[{}] Generated Gemini Summarization prompt for JSON mode: '{}'",
                                        recordingId, prompt);
                        log.info("[{}] Calling Gemini Summarization API (Pro Model, JSON Mode)...",
                                        recordingId);
                        String summarizationResult = geminiService
                                        .callGeminiSummarizationAPI(prompt, transcriptText);
                        if (isErrorResponse(summarizationResult)) {
                                log.error("[{}] Gemini Summarization API failed. Response: {}",
                                                recordingId, summarizationResult);
                                String errorDetails = "Summarization API Error";
                                try {
                                        JsonNode errorNode =
                                                        objectMapper.readTree(summarizationResult);
                                        errorDetails = errorNode.path("error")
                                                        .asText("Summarization API Error") + ": "
                                                        + errorNode.path("details").asText(
                                                                        "Details unavailable");
                                } catch (JsonProcessingException ignored) {
                                }
                                updateMetadataStatusToFailed(metadataId, errorDetails);
                                return null;
                        }
                        if (summarizationResult.isBlank()) {
                                log.error("[{}] Gemini Summarization API returned an empty result.",
                                                recordingId);
                                updateMetadataStatusToFailed(metadataId,
                                                "Summarization returned empty result.");
                                return null;
                        }
                        log.info("[{}] Summarization API call successful. Received JSON response.",
                                        recordingId);
                        log.debug("[{}] Summarization JSON (first 500 chars): {}", recordingId,
                                        summarizationResult.substring(0,
                                                        Math.min(summarizationResult.length(), 500))
                                                        + "...");
                        return summarizationResult;
                } catch (Exception e) {
                        log.error("[{}] Unexpected exception during summarization for metadata {}. Error: {}",
                                        recordingId, metadataId, e.getMessage(), e);
                        updateMetadataStatusToFailed(metadataId,
                                        "Unexpected summarization error: " + e.getMessage());
                        return null;
                }
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
                log.info("[{}] Finalizing processing: linking summaryId {} and setting status to COMPLETED.",
                                metadataId, summaryId);
                try {
                        Map<String, Object> updateMap = new HashMap<>();
                        updateMap.put("summaryId", summaryId);
                        updateMap.put("status", ProcessingStatus.COMPLETED.name());
                        updateMap.put("failureReason", null);
                        updateMap.put("lastUpdated", Timestamp.now());

                        firebaseService.updateDataWithMap(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, updateMap);
                        log.info("[{}] Successfully updated metadata status to COMPLETED and linked summaryId {}.",
                                        metadataId, summaryId);

                        Cache userMetadataCache = cacheManager.getCache(CACHE_METADATA_BY_USER);
                        if (userMetadataCache != null) {
                                log.info("[{}] Evicting all entries from cache: {}", metadataId,
                                                CACHE_METADATA_BY_USER);
                                userMetadataCache.clear();
                        } else {
                                log.warn("[{}] Cache '{}' not found for eviction.", metadataId,
                                                CACHE_METADATA_BY_USER);
                        }

                } catch (Exception e) {
                        log.error("[{}] CRITICAL: Failed to update metadata status to COMPLETED or link summaryId {} after saving summary. Summary is saved, but metadata might be inconsistent. Error: {}",
                                        metadataId, summaryId, e.getMessage(), e);
                        if (e instanceof InterruptedException)
                                Thread.currentThread().interrupt();
                }
        }

        private void triggerRecommendations(String userId, String recordingId, String summaryId) {
                if (learningMaterialRecommenderService == null) {
                        log.error("[{}] LearningMaterialRecommenderService is null. Cannot trigger recommendations.",
                                        recordingId);
                        updateMetadataStatusToFailed(recordingId,
                                        "Internal server error: Recommendation service unavailable.");
                        return;
                }

                log.info("[{}] Triggering recommendation generation for user {}...", recordingId,
                                userId);
                try {
                        learningMaterialRecommenderService.generateAndSaveRecommendations(userId,
                                        recordingId, summaryId);
                        log.info("[{}] Recommendation generation process triggered successfully.",
                                        recordingId);
                } catch (Exception e) {
                        log.error("[{}] Failed to trigger recommendation generation: {}",
                                        recordingId, e.getMessage(), e);
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
                                                        || !(item.get("definition") instanceof String)
                                                        || ((String) item.get("term")).isBlank()
                                                        || ((String) item.get("definition"))
                                                                        .isBlank();
                                        if (invalid) {
                                                log.warn("[{}] Invalid or incomplete glossary item structure found: {}. Skipping item.",
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
                                if (currentMeta.getStatus() == ProcessingStatus.FAILED
                                                && newStatus != ProcessingStatus.FAILED) {
                                        log.warn("[{}] Metadata status is already FAILED. Not overwriting with {}.",
                                                        metadataId, newStatus);
                                        return;
                                }
                                if (currentMeta.getStatus() == ProcessingStatus.PROCESSING
                                                && newStatus == ProcessingStatus.PENDING) {
                                        log.warn("[{}] Metadata status is already PROCESSING. Not overwriting with PENDING.",
                                                        metadataId);
                                        return;
                                }

                                Map<String, Object> updateMap = new HashMap<>();
                                updateMap.put("status", newStatus.name());
                                if (newStatus == ProcessingStatus.FAILED) {
                                        updateMap.put("failureReason", reason != null ? reason
                                                        : "Unknown failure");
                                } else {
                                        updateMap.put("failureReason", null);
                                }
                                updateMap.put("lastUpdated", Timestamp.now());

                                firebaseService.updateDataWithMap(
                                                firebaseService.getAudioMetadataCollectionName(),
                                                metadataId, updateMap);
                                log.info("[{}] Metadata status successfully updated to {}.",
                                                metadataId, newStatus);

                                Cache userMetadataCache =
                                                cacheManager.getCache(CACHE_METADATA_BY_USER);
                                if (userMetadataCache != null) {
                                        log.info("[{}] Evicting all entries from cache: {}",
                                                        metadataId, CACHE_METADATA_BY_USER);
                                        userMetadataCache.clear();
                                } else {
                                        log.warn("[{}] Cache '{}' not found for eviction.",
                                                        metadataId, CACHE_METADATA_BY_USER);
                                }

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

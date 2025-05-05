package edu.cit.audioscholar.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.FieldValue;
import edu.cit.audioscholar.config.RabbitMQConfig;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.ProcessingStatus;
import edu.cit.audioscholar.model.Recording;
import edu.cit.audioscholar.model.Summary;

@Service
public class SummarizationListenerService {

        private static final Logger log =
                        LoggerFactory.getLogger(SummarizationListenerService.class);
        private static final String CACHE_METADATA_BY_USER = "audioMetadataByUser";

        private final FirebaseService firebaseService;
        private final GeminiService geminiService;
        private final NhostStorageService nhostStorageService;
        private final SummaryService summaryService;
        private final CacheManager cacheManager;
        private final ObjectMapper objectMapper;
        private final Path tempDir;
        private final LearningMaterialRecommenderService recommenderService;
        private final RecordingService recordingService;
        private final RabbitTemplate rabbitTemplate;
        private final Map<String, Long> processedMessageIds = new ConcurrentHashMap<>();
        private final Map<String, Lock> metadataLocks = new ConcurrentHashMap<>();
        private static final long MESSAGE_ID_EXPIRATION_TIME = 10 * 60 * 1000;

        public SummarizationListenerService(FirebaseService firebaseService,
                        GeminiService geminiService, NhostStorageService nhostStorageService,
                        @Lazy SummaryService summaryService, CacheManager cacheManager,
                        ObjectMapper objectMapper,
                        @Value("${app.temp-file-dir:./temp_files}") String tempDirStr,
                        @Lazy LearningMaterialRecommenderService recommenderService,
                        @Lazy RecordingService recordingService, RabbitTemplate rabbitTemplate) {
                this.firebaseService = firebaseService;
                this.geminiService = geminiService;
                this.nhostStorageService = nhostStorageService;
                this.summaryService = summaryService;
                this.cacheManager = cacheManager;
                this.objectMapper = objectMapper;
                this.tempDir = Paths.get(tempDirStr);
                this.recommenderService = recommenderService;
                this.recordingService = recordingService;
                this.rabbitTemplate = rabbitTemplate;
                try {
                        Files.createDirectories(this.tempDir);
                } catch (IOException e) {
                        log.error("Could not create temporary directory for SummarizationListenerService: {}",
                                        this.tempDir.toAbsolutePath(), e);
                }

                Thread cleanupThread = new Thread(() -> {
                        while (!Thread.currentThread().isInterrupted()) {
                                try {
                                        Thread.sleep(60000);
                                        cleanupExpiredMessageIds();
                                } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        break;
                                }
                        }
                });
                cleanupThread.setDaemon(true);
                cleanupThread.setName("MessageIdCleanupThread");
                cleanupThread.start();
        }

        private void cleanupExpiredMessageIds() {
                long currentTime = System.currentTimeMillis();
                processedMessageIds.entrySet().removeIf(entry -> (currentTime
                                - entry.getValue()) > MESSAGE_ID_EXPIRATION_TIME);
        }

        @RabbitListener(queues = RabbitMQConfig.SUMMARIZATION_QUEUE_NAME)
        public void handleSummarizationRequest(Map<String, String> message) {
                if (message == null || message.get("metadataId") == null
                                || message.get("metadataId").isEmpty()) {
                        log.error("[AMQP Listener - Summarization] Received invalid message: {}. Ignoring.",
                                        message);
                        return;
                }

                final String metadataId = message.get("metadataId");
                final String messageId = message.get("messageId");

                if (messageId != null && !messageId.isEmpty()) {
                        if (processedMessageIds.containsKey(messageId)) {
                                log.info("[AMQP Listener - Summarization] Duplicate message detected (ID: {}). Skipping.",
                                                messageId);
                                return;
                        }
                        processedMessageIds.put(messageId, System.currentTimeMillis());
                } else {
                        log.warn("[{}] Message has no messageId for deduplication. Processing anyway but this may cause duplicates.",
                                        metadataId);
                }

                log.info("[AMQP Listener - Summarization] Received request for metadataId: {}, messageId: {}",
                                metadataId, messageId);

                String userId = null;
                Lock lock = null;

                try {
                        lock = metadataLocks.computeIfAbsent(metadataId, k -> new ReentrantLock());
                        lock.lock();
                        log.debug("[{}] Acquired lock for summarization processing", metadataId);

                        Map<String, Object> latestMetadataMap = firebaseService.getData(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId);

                        if (latestMetadataMap == null) {
                                log.error("[{}] AudioMetadata not found for ID. Cannot process summarization.",
                                                metadataId);
                                return;
                        }

                        AudioMetadata metadata = AudioMetadata.fromMap(latestMetadataMap);
                        userId = metadata.getUserId();

                        log.info("[{}] Found metadata. Current status: {}, User: {}", metadataId,
                                        metadata.getStatus(), userId);

                        log.debug("[{}] Metadata details - transcriptText length: {}, transcriptionComplete: {}, "
                                        + "summaryId: {}, audioOnly: {}, status: {}", metadataId,
                                        metadata.getTranscriptText() != null
                                                        ? metadata.getTranscriptText().length()
                                                        : 0,
                                        metadata.isTranscriptionComplete(), metadata.getSummaryId(),
                                        metadata.isAudioOnly(), metadata.getStatus());

                        ProcessingStatus currentStatus = metadata.getStatus();
                        if (currentStatus == ProcessingStatus.SUMMARIZING
                                        || currentStatus == ProcessingStatus.SUMMARY_COMPLETE
                                        || currentStatus == ProcessingStatus.RECOMMENDATIONS_QUEUED
                                        || currentStatus == ProcessingStatus.GENERATING_RECOMMENDATIONS
                                        || currentStatus == ProcessingStatus.COMPLETE) {

                                log.info("[{}] Summarization already in progress or complete (current status: {}). Skipping duplicate processing.",
                                                metadataId, currentStatus);
                                return;
                        }

                        if (currentStatus != ProcessingStatus.SUMMARIZATION_QUEUED) {
                                log.warn("[{}] Metadata status is not SUMMARIZATION_QUEUED (it's {}). Skipping summarization.",
                                                metadataId, metadata.getStatus());
                                return;
                        }

                        String transcript = metadata.getTranscriptText();
                        if (transcript == null || transcript.isBlank()) {
                                for (int retryCount = 1; retryCount <= 3; retryCount++) {
                                        log.warn("[{}] Transcript text is missing in metadata. Retry attempt {} of 3 after delay...",
                                                        metadataId, retryCount);
                                        try {
                                                Thread.sleep(2000);

                                                Map<String, Object> retryMetadataMap =
                                                                firebaseService.getData(
                                                                                firebaseService.getAudioMetadataCollectionName(),
                                                                                metadataId);

                                                if (retryMetadataMap != null) {
                                                        AudioMetadata retryMetadata = AudioMetadata
                                                                        .fromMap(retryMetadataMap);
                                                        transcript = retryMetadata
                                                                        .getTranscriptText();
                                                        if (transcript != null
                                                                        && !transcript.isBlank()) {
                                                                log.info("[{}] Successfully retrieved transcript on retry {}",
                                                                                metadataId,
                                                                                retryCount);
                                                                metadata = retryMetadata;
                                                                break;
                                                        }
                                                }
                                        } catch (InterruptedException e) {
                                                Thread.currentThread().interrupt();
                                                log.error("[{}] Retry interrupted", metadataId);
                                                break;
                                        } catch (Exception e) {
                                                log.error("[{}] Error during retry {}: {}",
                                                                metadataId, retryCount,
                                                                e.getMessage());
                                        }
                                }

                                if (transcript == null || transcript.isBlank()) {
                                        log.error("[{}] Transcript text is still missing after retries. Cannot generate summary.",
                                                        metadataId);
                                        updateMetadataStatus(metadataId, userId,
                                                        ProcessingStatus.FAILED,
                                                        "Missing transcript for summarization after multiple retries");
                                        return;
                                }
                        }

                        String googleFilesApiPdfUri = metadata.getGoogleFilesApiPdfUri();
                        if (googleFilesApiPdfUri != null && !googleFilesApiPdfUri.isBlank()) {
                                log.info("[{}] Found Google Files API URI for PDF, using it directly for summarization: {}",
                                                metadataId, googleFilesApiPdfUri);

                                updateMetadataStatus(metadataId, userId,
                                                ProcessingStatus.SUMMARIZING, null);

                                log.info("[{}] Calling GeminiService to generate summary with PDF context (direct Google Files API)...",
                                                metadataId);
                                String summarizationJson = geminiService
                                                .generateSummaryWithGoogleFileUri(transcript,
                                                                googleFilesApiPdfUri, metadataId);

                                processSummarizationResult(summarizationJson, metadataId, userId,
                                                metadata);
                                return;
                        }

                        if (metadata.isAudioOnly()) {
                                log.info("[{}] Audio-only upload detected. Processing summarization without PDF context.",
                                                metadataId);
                                updateMetadataStatus(metadataId, userId,
                                                ProcessingStatus.SUMMARIZING, null);

                                log.info("[{}] Calling GeminiService to generate transcript-only summary...",
                                                metadataId);
                                String summarizationJson =
                                                geminiService.generateTranscriptOnlySummary(
                                                                transcript, metadataId);

                                processSummarizationResult(summarizationJson, metadataId, userId,
                                                metadata);
                                return;
                        } else if (StringUtils.hasText(metadata.getNhostPptxFileId())) {
                                String pdfUrl = metadata.getGeneratedPdfUrl();
                                if (pdfUrl == null || pdfUrl.isBlank()) {
                                        log.info("[{}] PowerPoint was uploaded but PDF conversion is not complete yet. Delaying summarization.",
                                                        metadataId);

                                        Map<String, Object> statusUpdate = new HashMap<>();
                                        statusUpdate.put("status",
                                                        ProcessingStatus.SUMMARIZATION_QUEUED
                                                                        .name());
                                        statusUpdate.put("lastUpdated", Timestamp.now());
                                        statusUpdate.put("waitingForPdf", true);
                                        firebaseService.updateDataWithMap(firebaseService
                                                        .getAudioMetadataCollectionName(),
                                                        metadataId, statusUpdate);

                                        log.info("[{}] Updated metadata to indicate waiting for PDF completion.",
                                                        metadataId);

                                        log.warn("[{}] PDF conversion not complete. This summarization will need to be retried later when PDF is ready.",
                                                        metadataId);
                                        return;
                                }

                                updateMetadataStatus(metadataId, userId,
                                                ProcessingStatus.SUMMARIZING, null);

                                String pdfNhostId = extractNhostIdFromUrl(pdfUrl);
                                if (pdfNhostId == null) {
                                        log.error("[{}] Could not extract Nhost file ID from PDF URL: {}",
                                                        metadataId, pdfUrl);
                                        updateMetadataStatus(metadataId, userId,
                                                        ProcessingStatus.FAILED,
                                                        "Invalid PDF URL format");
                                        return;
                                }

                                Path tempPdfPath = null;
                                try {
                                        String tempPdfFilename = metadataId + "_context.pdf";
                                        tempPdfPath = tempDir.resolve(tempPdfFilename);
                                        log.info("[{}] Downloading PDF from Nhost (ID: {}) to local file: {}",
                                                        metadataId, pdfNhostId,
                                                        tempPdfPath.getFileName());

                                        nhostStorageService.downloadFileToPath(pdfNhostId,
                                                        tempPdfPath);
                                        log.info("[{}] Successfully downloaded PDF to local file",
                                                        metadataId);

                                        log.info("[{}] Calling GeminiService to generate summary with PDF context...",
                                                        metadataId);
                                        String summarizationJson = geminiService
                                                        .generateSummaryWithPdfContext(transcript,
                                                                        tempPdfPath, metadataId);

                                        processSummarizationResult(summarizationJson, metadataId,
                                                        userId, metadata);
                                } finally {
                                        if (tempPdfPath != null) {
                                                try {
                                                        Files.deleteIfExists(tempPdfPath);
                                                        log.info("[{}] Deleted temporary PDF file: {}",
                                                                        metadataId,
                                                                        tempPdfPath.getFileName());
                                                } catch (IOException e) {
                                                        log.warn("[{}] Failed to delete temporary PDF file: {}. Error: {}",
                                                                        metadataId,
                                                                        tempPdfPath.getFileName(),
                                                                        e.getMessage());
                                                }
                                        }
                                }
                        } else {
                                log.warn("[{}] Neither audio-only flag nor PowerPoint file detected. Treating as audio-only.",
                                                metadataId);

                                updateMetadataStatus(metadataId, userId,
                                                ProcessingStatus.SUMMARIZING, null);
                                String summarizationJson =
                                                geminiService.generateTranscriptOnlySummary(
                                                                transcript, metadataId);
                                processSummarizationResult(summarizationJson, metadataId, userId,
                                                metadata);
                        }

                } catch (FirestoreInteractionException e) {
                        log.error("[{}] Firestore error during summarization processing: {}",
                                        metadataId, e.getMessage(), e);
                } catch (Exception e) {
                        log.error("[{}] Unexpected error during summarization processing: {}",
                                        metadataId, e.getMessage(), e);
                        if (e instanceof InterruptedException)
                                Thread.currentThread().interrupt();
                        if (metadataId != null) {
                                try {
                                        Map<String, Object> currentMetadata =
                                                        firebaseService.getData(firebaseService
                                                                        .getAudioMetadataCollectionName(),
                                                                        metadataId);
                                        if (currentMetadata != null) {
                                                AudioMetadata metadata = AudioMetadata
                                                                .fromMap(currentMetadata);
                                                if (metadata.getStatus() != ProcessingStatus.FAILED) {
                                                        updateMetadataStatus(metadataId, userId,
                                                                        ProcessingStatus.FAILED,
                                                                        "Summarization error: " + e
                                                                                        .getMessage());
                                                }
                                        }
                                } catch (Exception ignored) {
                                        log.error("[{}] Failed to update metadata status after error: {}",
                                                        metadataId, e.getMessage());
                                }
                        }
                } finally {
                        if (lock != null) {
                                lock.unlock();
                                log.debug("[{}] Released lock for summarization processing",
                                                metadataId);
                        }
                }
        }

        private void processSummarizationResult(String summarizationJson, String metadataId,
                        String userId, AudioMetadata metadata) {
                log.info("[{}] Processing summarization result...", metadataId);

                try {
                        if (summarizationJson == null || summarizationJson.isBlank()) {
                                log.error("[{}] Summarization result is null or blank. Cannot proceed.",
                                                metadataId);
                                updateMetadataStatus(metadataId, userId, ProcessingStatus.FAILED,
                                                "Summarization result is empty");
                                return;
                        }

                        if (summarizationJson.contains("\"errorTitle\"")
                                        || summarizationJson.contains("\"errorDetails\"")) {
                                log.error("[{}] Received error in summarization result: {}",
                                                metadataId, summarizationJson);
                                updateMetadataStatus(metadataId, userId, ProcessingStatus.FAILED,
                                                "Error during summarization: " + summarizationJson);
                                return;
                        }

                        Map<String, Object> latestMetadataMap = firebaseService.getData(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId);
                        if (latestMetadataMap != null) {
                                AudioMetadata latestMetadata =
                                                AudioMetadata.fromMap(latestMetadataMap);
                                ProcessingStatus currentStatus = latestMetadata.getStatus();
                                if (currentStatus == ProcessingStatus.SUMMARY_COMPLETE
                                                || currentStatus == ProcessingStatus.RECOMMENDATIONS_QUEUED
                                                || currentStatus == ProcessingStatus.GENERATING_RECOMMENDATIONS
                                                || currentStatus == ProcessingStatus.COMPLETE) {
                                        log.info("[{}] Summary processing has already completed (current status: {}). Skipping duplicate processing.",
                                                        metadataId, currentStatus);
                                        return;
                                }
                        }

                        log.debug("[{}] Attempting to parse summarization result as JSON...",
                                        metadataId);

                        JsonNode rootNode = objectMapper.readTree(summarizationJson);
                        String summaryText = null;
                        List<String> keyPoints = new ArrayList<>();
                        List<String> topics = new ArrayList<>();
                        List<Map<String, String>> glossary = new ArrayList<>();

                        if (rootNode.has("candidates") && rootNode.get("candidates").size() > 0
                                        && rootNode.get("candidates").get(0).has("content")
                                        && rootNode.get("candidates").get(0).get("content")
                                                        .has("parts")
                                        && rootNode.get("candidates").get(0).get("content")
                                                        .get("parts").size() > 0) {

                                JsonNode firstPart = rootNode.get("candidates").get(0)
                                                .get("content").get("parts").get(0);

                                if (firstPart.has("text")) {
                                        String jsonText = firstPart.get("text").asText();
                                        JsonNode innerJson = objectMapper.readTree(jsonText);

                                        if (innerJson.has("summaryText")) {
                                                summaryText = innerJson.get("summaryText").asText();
                                        }

                                        if (innerJson.has("keyPoints")
                                                        && innerJson.get("keyPoints").isArray()) {
                                                for (JsonNode keyPoint : innerJson
                                                                .get("keyPoints")) {
                                                        keyPoints.add(keyPoint.asText());
                                                }
                                        }

                                        if (innerJson.has("topics")
                                                        && innerJson.get("topics").isArray()) {
                                                for (JsonNode topic : innerJson.get("topics")) {
                                                        topics.add(topic.asText());
                                                }
                                        }

                                        if (innerJson.has("glossary")
                                                        && innerJson.get("glossary").isArray()) {
                                                for (JsonNode glossaryItem : innerJson
                                                                .get("glossary")) {
                                                        if (glossaryItem.has("term") && glossaryItem
                                                                        .has("definition")) {
                                                                Map<String, String> item =
                                                                                new HashMap<>();
                                                                item.put("term", glossaryItem
                                                                                .get("term")
                                                                                .asText());
                                                                item.put("definition", glossaryItem
                                                                                .get("definition")
                                                                                .asText());
                                                                glossary.add(item);
                                                        }
                                                }
                                        }
                                } else {
                                        log.warn("[{}] First part does not contain text field",
                                                        metadataId);
                                }
                        } else {
                                log.warn("[{}] Response does not have the expected candidates structure. Attempting direct parsing...",
                                                metadataId);

                                if (rootNode.has("summaryText")) {
                                        summaryText = rootNode.get("summaryText").asText();
                                }

                                if (rootNode.has("keyPoints")
                                                && rootNode.get("keyPoints").isArray()) {
                                        for (JsonNode keyPoint : rootNode.get("keyPoints")) {
                                                keyPoints.add(keyPoint.asText());
                                        }
                                }

                                if (rootNode.has("topics") && rootNode.get("topics").isArray()) {
                                        for (JsonNode topic : rootNode.get("topics")) {
                                                topics.add(topic.asText());
                                        }
                                }

                                if (rootNode.has("glossary")
                                                && rootNode.get("glossary").isArray()) {
                                        for (JsonNode glossaryItem : rootNode.get("glossary")) {
                                                if (glossaryItem.has("term")
                                                                && glossaryItem.has("definition")) {
                                                        Map<String, String> item = new HashMap<>();
                                                        item.put("term", glossaryItem.get("term")
                                                                        .asText());
                                                        item.put("definition", glossaryItem
                                                                        .get("definition")
                                                                        .asText());
                                                        glossary.add(item);
                                                }
                                        }
                                }
                        }

                        if (summaryText == null || summaryText.isBlank()) {
                                log.warn("[{}] Could not extract summary text from structured JSON. Using raw text as fallback.",
                                                metadataId);
                                summaryText = summarizationJson;
                        }

                        log.info("[{}] Successfully extracted summary text (length: {}) and {} key points, {} topics",
                                        metadataId, summaryText.length(), keyPoints.size(),
                                        topics.size());

                        String pdfContextUrl = metadata.getGoogleFilesApiPdfUri();
                        if (pdfContextUrl == null || pdfContextUrl.isBlank()) {
                                pdfContextUrl = metadata.getGeneratedPdfUrl();
                        }

                        Summary summary = createSummary(metadataId, userId, summaryText, keyPoints,
                                        topics, pdfContextUrl, glossary);

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("summaryId", summary.getSummaryId());
                        updates.put("status", ProcessingStatus.SUMMARY_COMPLETE.name());
                        updates.put("lastUpdated", Timestamp.now());
                        firebaseService.updateDataWithMap(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, updates);
                        log.info("[{}] Updated metadata with summaryId and set status to SUMMARY_COMPLETE",
                                        metadataId);

                        updateRecordingWithSummaryId(metadataId, summary.getSummaryId(),
                                        metadataId);

                        triggerRecommendations(metadataId, userId, metadataId,
                                        summary.getSummaryId());

                        invalidateCache(userId);

                } catch (Exception e) {
                        log.error("[{}] Error processing summarization result: {}", metadataId,
                                        e.getMessage(), e);
                        updateMetadataStatus(metadataId, userId, ProcessingStatus.FAILED,
                                        "Error processing summarization result: " + e.getMessage());
                }
        }

        private Summary createSummary(String metadataId, String userId, String summaryText,
                        List<String> keyPoints, List<String> topics, String pdfContextUrl,
                        List<Map<String, String>> glossary) {
                Summary summary = new Summary();
                summary.setSummaryId(UUID.randomUUID().toString());
                summary.setRecordingId(metadataId);
                summary.setFormattedSummaryText(summaryText);
                if (keyPoints != null) {
                        summary.setKeyPoints(keyPoints);
                }
                if (topics != null) {
                        summary.setTopics(topics);
                }
                if (glossary != null) {
                        summary.setGlossary(glossary);
                }
                summary.setCreatedAt(new Date());

                try {
                        log.info("[{}] Saving summary with ID {} to Firestore", metadataId,
                                        summary.getSummaryId());
                        summaryService.createSummary(summary);
                        log.info("[{}] Successfully saved summary with ID {}", metadataId,
                                        summary.getSummaryId());
                } catch (ExecutionException | InterruptedException e) {
                        log.error("[{}] Error saving summary to Firestore: {}", metadataId,
                                        e.getMessage(), e);
                        if (e instanceof InterruptedException) {
                                Thread.currentThread().interrupt();
                        }
                }

                return summary;
        }

        private Summary createSummary(String metadataId, String userId, String summaryText,
                        List<String> keyPoints, List<String> topics, String pdfContextUrl) {
                return createSummary(metadataId, userId, summaryText, keyPoints, topics,
                                pdfContextUrl, null);
        }

        private void updateRecordingWithSummaryId(String recordingId, String summaryId,
                        String metadataId) {
                try {
                        log.info("[{}] Fetching recording {} to update with summaryId {}...",
                                        metadataId, recordingId, summaryId);
                        Recording recording = recordingService.getRecordingById(recordingId);

                        if (recording != null) {
                                recording.setSummaryId(summaryId);
                                recordingService.updateRecording(recording);
                                log.info("[{}] Successfully updated recording {} with summaryId {}",
                                                metadataId, recordingId, summaryId);
                        } else {
                                log.error("[{}] Recording {} not found, can't update with summaryId {}",
                                                metadataId, recordingId, summaryId);
                        }
                } catch (Exception e) {
                        log.error("[{}] Error updating recording {} with summaryId {}: {}",
                                        metadataId, recordingId, summaryId, e.getMessage(), e);
                }
        }

        private void triggerRecommendations(String metadataId, String userId, String recordingId,
                        String summaryId) {
                String recommendationMessageId = UUID.randomUUID().toString();
                Map<String, String> recommendationMessage = new HashMap<>();
                recommendationMessage.put("metadataId", metadataId);
                recommendationMessage.put("messageId", recommendationMessageId);
                recommendationMessage.put("recordingId", recordingId);
                recommendationMessage.put("summaryId", summaryId);
                recommendationMessage.put("userId", userId);

                updateMetadataStatus(metadataId, userId, ProcessingStatus.RECOMMENDATIONS_QUEUED,
                                null);

                try {
                        log.info("[{}] Attempting direct call to recommender service with recordingId {} and summaryId {}",
                                        metadataId, recordingId, summaryId);
                        recommenderService.generateAndSaveRecommendations(userId, recordingId,
                                        summaryId);
                        log.info("[{}] Successfully generated recommendations via direct call.",
                                        metadataId);
                        updateMetadataStatus(metadataId, userId, ProcessingStatus.COMPLETE, null);
                } catch (Exception e) {
                        log.error("[{}] Direct call to recommender failed: {}. Falling back to message queue.",
                                        metadataId, e.getMessage());
                        try {
                                String messageJson = objectMapper
                                                .writeValueAsString(recommendationMessage);
                                rabbitTemplate.convertAndSend(
                                                RabbitMQConfig.PROCESSING_EXCHANGE_NAME,
                                                RabbitMQConfig.RECOMMENDATIONS_ROUTING_KEY,
                                                messageJson);
                                log.info("[{}] Sent message to recommendations queue. Message details: {}",
                                                metadataId, recommendationMessage);
                        } catch (Exception mqEx) {
                                log.error("[{}] CRITICAL: Failed to send message to recommendations queue after direct call failed: {}",
                                                metadataId, mqEx.getMessage(), mqEx);
                                updateMetadataStatus(metadataId, userId, ProcessingStatus.FAILED,
                                                "Failed to queue recommendations");
                        }
                }
                invalidateCache(userId);
        }

        private String extractNhostIdFromUrl(String url) {
                if (url == null || url.isEmpty())
                        return null;

                try {
                        Pattern pattern = Pattern.compile("/files/([a-zA-Z0-9\\-]+)");
                        Matcher matcher = pattern.matcher(url);
                        if (matcher.find()) {
                                String id = matcher.group(1);
                                try {
                                        UUID.fromString(id);
                                        return id;
                                } catch (IllegalArgumentException e) {
                                        log.warn("Found ID segment '{}' in URL '{}' but it's not a valid UUID",
                                                        id, url);
                                        return null;
                                }
                        }
                } catch (Exception e) {
                        log.error("Error extracting Nhost ID from URL '{}': {}", url,
                                        e.getMessage(), e);
                }
                return null;
        }

        private void updateMetadataStatus(String metadataId, String userId, ProcessingStatus status,
                        @Nullable String reason) {
                log.info("[{}] Setting status to {}{}", metadataId, status,
                                (reason != null ? ". Reason: " + reason : ""));
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", status.name());
                updates.put("lastUpdated", Timestamp.now());
                if (reason != null) {
                        updates.put("failureReason", reason);
                } else {
                        updates.put("failureReason", FieldValue.delete());
                }

                try {
                        firebaseService.updateData(firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, updates);

                        try {
                                Map<String, Object> updatedDataMap = firebaseService.getData(
                                                firebaseService.getAudioMetadataCollectionName(),
                                                metadataId);
                                if (updatedDataMap != null) {
                                        AudioMetadata updatedMetadata =
                                                        AudioMetadata.fromMap(updatedDataMap);

                                        Cache byIdCache =
                                                        cacheManager.getCache("audioMetadataById");
                                        if (byIdCache != null) {
                                                byIdCache.put(metadataId, updatedMetadata);
                                                log.debug("[{}] Manually updated cache 'audioMetadataById' with latest status: {}",
                                                                metadataId, status);
                                        } else {
                                                log.warn("Cache 'audioMetadataById' not found during manual update.");
                                        }
                                } else {
                                        log.warn("[{}] Could not fetch updated metadata after status update for cache refresh.",
                                                        metadataId);
                                        Cache byIdCache =
                                                        cacheManager.getCache("audioMetadataById");
                                        if (byIdCache != null) {
                                                byIdCache.evictIfPresent(metadataId);
                                        }
                                }

                                Cache byUserCache = cacheManager.getCache(CACHE_METADATA_BY_USER);
                                if (byUserCache != null) {
                                        byUserCache.clear();
                                        log.debug("[{}] Cleared cache '{}' after status update.",
                                                        metadataId, CACHE_METADATA_BY_USER);
                                } else {
                                        log.warn("Cache '{}' not found during clear operation.",
                                                        CACHE_METADATA_BY_USER);
                                }

                        } catch (Exception cacheEx) {
                                log.error("[{}] Error during manual cache update/eviction after status change: {}",
                                                metadataId, cacheEx.getMessage(), cacheEx);
                        }

                } catch (FirestoreInteractionException e) {
                        log.error("[{}] Failed to update metadata status to {} in Firestore: {}",
                                        metadataId, status, e.getMessage(), e);
                }
        }

        private void invalidateCache(String userId) {
                if (userId == null || userId.isBlank()) {
                        log.warn("Attempted to invalidate cache with null or blank userId.");
                        return;
                }
                try {
                        Cache userCache = cacheManager.getCache(CACHE_METADATA_BY_USER);
                        if (userCache != null) {
                                userCache.clear();
                                log.debug("Invalidated cache '{}' for userId: {}",
                                                CACHE_METADATA_BY_USER, userId);
                        } else {
                                log.warn("Cache '{}' not found during invalidation.",
                                                CACHE_METADATA_BY_USER);
                        }
                } catch (Exception e) {
                        log.error("Error invalidating cache for user {}: {}", userId,
                                        e.getMessage(), e);
                }
        }
}


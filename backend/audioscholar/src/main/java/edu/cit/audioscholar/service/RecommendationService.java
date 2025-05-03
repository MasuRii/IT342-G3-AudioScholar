package edu.cit.audioscholar.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.LearningRecommendation;
import edu.cit.audioscholar.model.ProcessingStatus;

@Service
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    @Autowired
    private FirebaseService firebaseService;

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private LearningMaterialRecommenderService learningMaterialRecommenderService;

    @Autowired
    private ObjectMapper objectMapper;

    public String recommendAndSave(String metadataId, String userId) {
        log.info("[{}] Starting learning materials recommendation for user {}...", metadataId,
                userId);
        try {
            AudioMetadata metadata = firebaseService.getAudioMetadataById(metadataId);
            if (metadata == null) {
                String errorMsg = "Audio metadata not found for ID: " + metadataId;
                log.error("[{}] {}", metadataId, errorMsg);
                return errorResponseToString("Metadata Not Found", errorMsg);
            }

            if (metadata.getStatus() != ProcessingStatus.SUMMARY_COMPLETE) {
                String errorMsg = "Cannot generate recommendations because recording status is "
                        + metadata.getStatus() + ", not SUMMARY_COMPLETE";
                log.error("[{}] {}", metadataId, errorMsg);
                return errorResponseToString("Invalid Status", errorMsg);
            }

            String recordingId = metadata.getRecordingId();
            if (recordingId == null || recordingId.isBlank()) {
                String errorMsg = "Recording ID is missing in metadata";
                log.error("[{}] {}", metadataId, errorMsg);
                return errorResponseToString("Missing Recording", errorMsg);
            }

            List<LearningRecommendation> existingRecommendations =
                    learningMaterialRecommenderService.getRecommendationsByRecordingId(recordingId);
            if (existingRecommendations != null && !existingRecommendations.isEmpty()) {
                log.info(
                        "[{}] Recording already has {} recommendations. Skipping generation to avoid duplicates.",
                        metadataId, existingRecommendations.size());

                if (metadata.getStatus() != ProcessingStatus.COMPLETE) {
                    Map<String, Object> completeUpdates = new HashMap<>();
                    completeUpdates.put("status", ProcessingStatus.COMPLETE.name());
                    completeUpdates.put("lastUpdated", Timestamp.now());
                    firebaseService.updateData(firebaseService.getAudioMetadataCollectionName(),
                            metadataId, completeUpdates);
                    log.info("[{}] Updated metadata status to COMPLETE", metadataId);
                }

                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("count", existingRecommendations.size());
                response.put("message", "Recording already has " + existingRecommendations.size()
                        + " recommendations. Used existing recommendations.");

                String responseJson = objectMapper.writeValueAsString(response);
                return responseJson;
            }

            String summaryText = getSummaryText(metadata);
            if (summaryText == null || summaryText.isBlank()) {
                String errorMsg = "Summary text is not available";
                log.error("[{}] {}", metadataId, errorMsg);
                return errorResponseToString("Missing Summary", errorMsg);
            }

            String transcriptText = metadata.getTranscriptText();
            if (transcriptText == null || transcriptText.isBlank()) {
                log.warn("[{}] Transcript text is not available, proceeding with summary only",
                        metadataId);
            }

            String pdfText = null;
            if (!metadata.isAudioOnly()) {
                pdfText = getPdfText(metadata);
                if (pdfText == null || pdfText.isBlank()) {
                    log.warn(
                            "[{}] PDF text is not available for a non-audio-only recording, proceeding with summary and transcript only",
                            metadataId);
                }
            } else {
                log.info("[{}] Audio-only recording detected, skipping PDF text retrieval",
                        metadataId);
            }

            metadata.setStatus(ProcessingStatus.GENERATING_RECOMMENDATIONS);
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", ProcessingStatus.GENERATING_RECOMMENDATIONS.name());
            updates.put("lastUpdated", Timestamp.now());
            firebaseService.updateData(firebaseService.getAudioMetadataCollectionName(), metadataId,
                    updates);
            log.info("[{}] Updated metadata status to GENERATING_RECOMMENDATIONS", metadataId);

            String recommendationsJson;
            if (metadata.isAudioOnly()) {
                log.info("[{}] Generating recommendations for audio-only recording", metadataId);
                recommendationsJson = geminiService.generateRecommendationsAudioOnly(summaryText,
                        transcriptText, metadataId);
            } else {
                log.info("[{}] Generating recommendations for recording with PDF", metadataId);
                log.warn(
                        "[{}] generateRecommendations method not found, using audio-only version as fallback",
                        metadataId);
                recommendationsJson = geminiService.generateRecommendationsAudioOnly(summaryText,
                        transcriptText, metadataId);
            }

            List<LearningRecommendation> recommendations =
                    parseRecommendationsResponse(recommendationsJson, metadataId);
            if (recommendations.isEmpty()) {
                log.info(
                        "[{}] No recommendations parsed from Gemini response. Using learning material recommender service.",
                        metadataId);
            } else {
                log.info("[{}] Successfully parsed {} recommendations from Gemini response",
                        metadataId, recommendations.size());
            }

            List<LearningRecommendation> savedRecommendations = learningMaterialRecommenderService
                    .generateAndSaveRecommendations(userId, recordingId, metadata.getSummaryId());

            int savedCount = savedRecommendations != null ? savedRecommendations.size() : 0;
            log.info("[{}] Saved {} recommendations to database", metadataId, savedCount);

            Map<String, Object> completeUpdates = new HashMap<>();
            completeUpdates.put("status", ProcessingStatus.COMPLETE.name());
            completeUpdates.put("lastUpdated", Timestamp.now());
            firebaseService.updateData(firebaseService.getAudioMetadataCollectionName(), metadataId,
                    completeUpdates);
            log.info("[{}] Updated metadata status to COMPLETE and set lastUpdated timestamp",
                    metadataId);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("count", savedCount);
            response.put("message",
                    "Successfully generated and saved " + savedCount + " recommendations");

            String responseJson = objectMapper.writeValueAsString(response);
            log.info("[{}] Recommendation process completed successfully", metadataId);
            return responseJson;

        } catch (JsonProcessingException e) {
            String errorMsg = "JSON processing error: " + e.getMessage();
            log.error("[{}] {}", metadataId, errorMsg, e);
            return errorResponseToString("JSON Error", errorMsg);
        } catch (Exception e) {
            String errorMsg = "Unexpected error during recommendation: " + e.getMessage();
            log.error("[{}] {}", metadataId, errorMsg, e);
            return errorResponseToString("Recommendation Error", errorMsg);
        }
    }

    private List<LearningRecommendation> parseRecommendationsResponse(String recommendationsJson,
            String metadataId) {
        List<LearningRecommendation> recommendations = new ArrayList<>();
        try {
            log.info("[{}] Parsing recommendations JSON (length: {})", metadataId,
                    recommendationsJson != null ? recommendationsJson.length() : 0);

            if (recommendationsJson == null || recommendationsJson.isBlank()) {
                log.warn("[{}] Recommendations JSON is null or empty", metadataId);
                return recommendations;
            }

            try {
                if (recommendationsJson.trim().startsWith("[")) {
                    recommendations = objectMapper.readValue(recommendationsJson,
                            objectMapper.getTypeFactory().constructCollectionType(List.class,
                                    LearningRecommendation.class));
                    log.info("[{}] Successfully parsed {} recommendations from JSON array",
                            metadataId, recommendations.size());
                } else {
                    log.warn("[{}] Non-array JSON format received, complex parsing required",
                            metadataId);
                }
            } catch (JsonProcessingException jpe) {
                log.warn("[{}] Could not parse JSON as recommendation array: {}", metadataId,
                        jpe.getMessage());
            }
        } catch (Exception e) {
            log.error("[{}] Error parsing recommendations JSON: {}", metadataId, e.getMessage(), e);
        }
        return recommendations;
    }

    private String getSummaryText(AudioMetadata metadata) {
        if (metadata == null) {
            return null;
        }

        String summaryId = metadata.getSummaryId();
        if (summaryId != null && !summaryId.isBlank()) {
            try {
                Map<String, Object> summaryData = firebaseService.getData("summaries", summaryId);
                if (summaryData != null) {
                    String formattedSummaryText = (String) summaryData.get("formattedSummaryText");
                    if (formattedSummaryText != null && !formattedSummaryText.isBlank()) {
                        log.info("[{}] Retrieved formattedSummaryText from summaries/{}",
                                metadata.getId(), summaryId);
                        return formattedSummaryText;
                    }
                }
            } catch (Exception e) {
                log.warn("[{}] Error retrieving summary document: {}", metadata.getId(),
                        e.getMessage());
            }
        }

        log.warn("[{}] No summary found. Falling back to transcript text.", metadata.getId());
        return metadata.getTranscriptText();
    }

    private String getPdfText(AudioMetadata metadata) {
        if (metadata == null) {
            return null;
        }

        String recordingId = metadata.getRecordingId();
        if (recordingId != null && !recordingId.isBlank()) {
            try {
                Map<String, Object> recordingData =
                        firebaseService.getData("recordings", recordingId);
                if (recordingData != null) {
                    String pdfText = (String) recordingData.get("pdfText");
                    if (pdfText != null && !pdfText.isBlank()) {
                        log.info("[{}] Retrieved pdfText from recordings/{}", metadata.getId(),
                                recordingId);
                        return pdfText;
                    }
                }
            } catch (Exception e) {
                log.warn("[{}] Error retrieving PDF text: {}", metadata.getId(), e.getMessage());
            }
        }

        log.warn("[{}] No PDF text found.", metadata.getId());
        return "";
    }

    private String errorResponseToString(String errorType, String errorMessage) {
        try {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("type", errorType);
            errorResponse.put("message", errorMessage);
            return objectMapper.writeValueAsString(errorResponse);
        } catch (JsonProcessingException e) {
            log.error("Error creating error response JSON: {}", e.getMessage(), e);
            return "{\"status\":\"error\",\"message\":\"" + errorMessage + "\"}";
        }
    }
}

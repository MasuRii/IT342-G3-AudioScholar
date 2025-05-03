package edu.cit.audioscholar.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.google.api.core.ApiFuture;
import com.google.api.services.youtube.model.ResourceId;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.SearchResultSnippet;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.messaging.MulticastMessage;
import edu.cit.audioscholar.dto.AnalysisResults;
import edu.cit.audioscholar.integration.YouTubeAPIClient;
import edu.cit.audioscholar.model.LearningRecommendation;
import edu.cit.audioscholar.model.Recording;

@Service
public class LearningMaterialRecommenderService {
    private static final Logger log =
            LoggerFactory.getLogger(LearningMaterialRecommenderService.class);
    private final LectureContentAnalyzerService lectureContentAnalyzerService;
    private final YouTubeAPIClient youTubeAPIClient;
    private final Firestore firestore;
    private final RecordingService recordingService;
    private final FirebaseService firebaseService;
    private final UserService userService;
    private final String recommendationsCollectionName = "learningRecommendations";
    private static final int MAX_RECOMMENDATIONS_TO_FETCH = 10;
    private static final int SEARCH_RESULTS_POOL_SIZE = 50;
    private static final String EDUCATIONAL_CONTEXT = "lecture educational tutorial";
    private static final Set<String> EDUCATIONAL_DOMAINS =
            Set.of("edu", "education", "academic", "university", "school", "college", "course");
    private static final String TARGET_LANGUAGE = "english";
    private static final Set<String> FOREIGN_LANGUAGE_INDICATORS =
            Set.of("hindi", "urdu", "español", "arabic", "العربية", "中文", "chinese", "русский",
                    "russian", "française", "français", "french", "日本語", "japanese", "한국어",
                    "korean", "deutsch", "german", "italiano", "italian", "português", "portuguese",
                    "bahasa", "indonesian", "malayalam", "tamil", "telugu", "bengali", "marathi");
    private static final Set<String> NON_ENGLISH_REGIONS = Set.of("india", "pakistan", "russia",
            "china", "japan", "korea", "spain", "méxico", "mexico", "brazil", "brasil", "indonesia",
            "malaysia", "thailand", "vietnam", "türkiye", "turkey", "arabic", "saudi", "ukraine",
            "czech", "poland", "hungary", "romania");

    public LearningMaterialRecommenderService(
            LectureContentAnalyzerService lectureContentAnalyzerService,
            YouTubeAPIClient youTubeAPIClient, Firestore firestore,
            RecordingService recordingService, FirebaseService firebaseService,
            UserService userService) {
        this.lectureContentAnalyzerService = lectureContentAnalyzerService;
        this.youTubeAPIClient = youTubeAPIClient;
        this.firestore = firestore;
        this.recordingService = recordingService;
        this.firebaseService = firebaseService;
        this.userService = userService;
    }

    public List<LearningRecommendation> generateAndSaveRecommendations(String userId,
            String recordingId, String summaryId) {
        log.info(
                "Starting recommendation generation and storage for recording ID: {}, user: {}, summary: {}",
                recordingId, userId, summaryId);
        AnalysisResults analysisResults =
                lectureContentAnalyzerService.analyzeLectureContent(recordingId);
        if (!analysisResults.isSuccess()) {
            log.error("Failed to analyze lecture content for recording ID: {}. Error: {}",
                    recordingId, analysisResults.getErrorMessage());
            return Collections.emptyList();
        }
        List<String> keywords = analysisResults.getKeywordsAndTopics();
        if (keywords.isEmpty()) {
            log.info("No keywords found for recording ID: {}. Cannot generate recommendations.",
                    recordingId);
            return Collections.emptyList();
        }
        log.debug("Keywords extracted for recording ID {}: {}", recordingId, keywords);

        List<String> enhancedKeywords = enhanceKeywordsWithEducationalContext(keywords);

        if (!enhancedKeywords.isEmpty()) {
            String firstKeyword = enhancedKeywords.get(0);
            enhancedKeywords.set(0, firstKeyword + " " + TARGET_LANGUAGE);
            if (enhancedKeywords.size() > 2) {
                String secondKeyword = enhancedKeywords.get(2);
                enhancedKeywords.set(2, secondKeyword + " " + TARGET_LANGUAGE);
            }
        }

        log.debug("Enhanced keywords for recording ID {}: {}", recordingId, enhancedKeywords);

        try {
            List<SearchResult> youtubeResults =
                    youTubeAPIClient.searchVideos(enhancedKeywords, SEARCH_RESULTS_POOL_SIZE);
            if (youtubeResults.isEmpty()) {
                log.info(
                        "YouTube search returned no results for keywords related to recording ID: {}",
                        recordingId);
                return Collections.emptyList();
            }
            log.info("Retrieved {} potential recommendations from YouTube for recording ID: {}",
                    youtubeResults.size(), recordingId);

            List<SearchResult> filteredResults =
                    filterAndRankResults(youtubeResults, enhancedKeywords);

            List<SearchResult> languageFilteredResults = filterByLanguage(filteredResults);
            log.info(
                    "Filtered to {} relevant results after language filtering for recording ID: {}",
                    languageFilteredResults.size(), recordingId);

            List<SearchResult> topResults = languageFilteredResults.stream()
                    .limit(MAX_RECOMMENDATIONS_TO_FETCH).collect(Collectors.toList());

            List<LearningRecommendation> recommendations =
                    processYouTubeResults(topResults, recordingId);
            if (recommendations.isEmpty()) {
                log.info("No valid recommendations processed for recording ID: {}", recordingId);
                return Collections.emptyList();
            }
            log.info("Successfully processed {} unique recommendations for recording ID: {}",
                    recommendations.size(), recordingId);
            List<LearningRecommendation> savedRecommendationsWithIds =
                    saveRecommendationsBatch(recommendations);
            if (!savedRecommendationsWithIds.isEmpty()) {
                linkRecommendationsAndNotify(userId, recordingId, summaryId,
                        savedRecommendationsWithIds);
            } else {
                log.warn(
                        "No recommendations were successfully saved for recording ID: {}. Skipping linking step.",
                        recordingId);
            }
            return savedRecommendationsWithIds;
        } catch (Exception e) {
            log.error(
                    "Unexpected error during recommendation generation or saving for recording ID: {}",
                    recordingId, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Collections.emptyList();
        }
    }

    private List<LearningRecommendation> saveRecommendationsBatch(
            List<LearningRecommendation> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return Collections.emptyList();
        }
        String recordingId = recommendations.get(0).getRecordingId();
        List<LearningRecommendation> recommendationsWithIds = new ArrayList<>();
        try {
            WriteBatch batch = firestore.batch();
            for (LearningRecommendation recommendation : recommendations) {
                DocumentReference docRef =
                        firestore.collection(recommendationsCollectionName).document();
                recommendation.setRecommendationId(docRef.getId());
                batch.set(docRef, recommendation);
                recommendationsWithIds.add(recommendation);
            }
            log.info(
                    "Attempting to commit batch write of {} recommendations to Firestore for recording ID: {}",
                    recommendationsWithIds.size(), recordingId);
            ApiFuture<List<WriteResult>> future = batch.commit();
            List<WriteResult> results = future.get();
            log.info(
                    "Successfully committed batch write to Firestore for recording ID: {}. Results count: {}",
                    recordingId, results.size());
            return recommendationsWithIds;
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error committing batch write to Firestore for recording ID: {}", recordingId,
                    e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Unexpected error during native Firestore batch save for recording ID: {}",
                    recordingId, e);
            return Collections.emptyList();
        }
    }

    private void linkRecommendationsAndNotify(String userId, String recordingId, String summaryId,
            List<LearningRecommendation> savedRecommendations) {
        if (savedRecommendations == null || savedRecommendations.isEmpty()) {
            log.warn("No saved recommendations provided to link for recording ID: {}", recordingId);
            return;
        }
        List<String> newRecommendationIds =
                savedRecommendations.stream().map(LearningRecommendation::getRecommendationId)
                        .filter(Objects::nonNull).collect(Collectors.toList());
        if (newRecommendationIds.isEmpty()) {
            log.warn("Extracted recommendation ID list is empty for recording ID: {}. Cannot link.",
                    recordingId);
            return;
        }
        log.info("Attempting to link {} new recommendation(s) to recording ID: {}",
                newRecommendationIds.size(), recordingId);
        boolean linkSuccess = false;
        try {
            if (recordingId == null || recordingId.trim().isEmpty()) {
                log.error("Cannot link recommendations to null or empty recordingId");
                return;
            }

            Recording recording = recordingService.getRecordingById(recordingId);
            if (recording != null) {
                if (recording.getUserId() == null || recording.getUserId().trim().isEmpty()) {
                    log.warn("Recording {} is missing userId. Using passed userId: {}", recordingId,
                            userId);
                    recording.setUserId(userId);
                } else if (!Objects.equals(userId, recording.getUserId())) {
                    log.warn(
                            "User ID mismatch! Passed userId {} does not match recording owner {} for recordingId {}. Using recording owner for notification.",
                            userId, recording.getUserId(), recordingId);
                    userId = recording.getUserId();
                }

                if (recording.getRecordingId() == null
                        || recording.getRecordingId().trim().isEmpty()) {
                    log.warn("Recording object has null/empty recordingId. Setting it to: {}",
                            recordingId);
                    recording.setRecordingId(recordingId);
                }

                List<String> currentIds = recording.getRecommendationIds();
                if (currentIds == null) {
                    currentIds = new ArrayList<>();
                } else {
                    currentIds = new ArrayList<>(currentIds);
                }
                boolean updated = false;
                for (String newId : newRecommendationIds) {
                    if (!currentIds.contains(newId)) {
                        currentIds.add(newId);
                        updated = true;
                    }
                }
                if (updated) {
                    try {
                        recording.setRecommendationIds(currentIds);
                        recordingService.updateRecording(recording);
                        log.info(
                                "Successfully linked {} new recommendations. Total recommendations linked for Recording ID {}: {}",
                                newRecommendationIds.size(), recordingId, currentIds.size());
                        linkSuccess = true;
                    } catch (IllegalArgumentException e) {
                        log.error("Failed to update recording - validation error: {}",
                                e.getMessage());
                        try {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("recommendationIds", currentIds);
                            updates.put("updatedAt", new Date());
                            firestore.collection("recordings").document(recordingId).update(updates)
                                    .get();
                            log.info(
                                    "Successfully updated recommendations using direct Firestore update for recordingId {}",
                                    recordingId);
                            linkSuccess = true;
                        } catch (Exception ex) {
                            log.error("Failed even with direct Firestore update: {}",
                                    ex.getMessage());
                        }
                    }
                } else {
                    log.info(
                            "No *new* recommendations to link. Recording ID {} already contains these recommendation IDs.",
                            recordingId);
                    linkSuccess = true;
                }
            } else {
                log.warn("Recording {} not found. Cannot link recommendations: {}", recordingId,
                        newRecommendationIds);
                try {
                    log.info("Attempting to create minimal recording object for ID: {}",
                            recordingId);
                    Recording newRecording =
                            new Recording(recordingId, userId, "Recording " + recordingId, null);
                    newRecording.setRecommendationIds(newRecommendationIds);
                    recordingService.createRecording(newRecording);
                    log.info(
                            "Successfully created minimal recording with recommendations for ID: {}",
                            recordingId);
                    linkSuccess = true;
                } catch (Exception e) {
                    log.error("Failed to create minimal recording: {}", e.getMessage());
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error fetching or updating recording {} to link recommendations: {}",
                    recordingId, e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            log.error("Unexpected error linking recommendations to recording {}: {}", recordingId,
                    e.getMessage(), e);
        }

        if (linkSuccess) {
            log.info("Proceeding to send FCM notification for user: {}, recording: {}, summary: {}",
                    userId, recordingId, summaryId);

            List<String> tokensToSend = userService.getFcmTokensForUser(userId);
            if (tokensToSend != null && !tokensToSend.isEmpty()) {
                MulticastMessage message = firebaseService.buildProcessingCompleteMessage(userId,
                        recordingId, summaryId);
                if (message != null) {
                    firebaseService.sendFcmMessage(message, tokensToSend, userId);
                    log.info("FCM notification send task initiated for user {}, recording {}.",
                            userId, recordingId);
                } else {
                    log.warn(
                            "FCM message build returned null (likely no tokens found for user {}). Notification not sent for recording {}.",
                            userId, recordingId);
                }
            } else {
                log.warn(
                        "No tokens retrieved for user {} just before sending notification for recording {}. Notification not sent.",
                        userId, recordingId);
            }
        } else {
            log.error("Linking recommendations failed for recording {}. Skipping FCM notification.",
                    recordingId);
        }
    }

    private List<LearningRecommendation> processYouTubeResults(List<SearchResult> youtubeResults,
            String recordingId) {
        if (youtubeResults == null || youtubeResults.isEmpty()) {
            return Collections.emptyList();
        }
        List<LearningRecommendation> recommendations = new ArrayList<>();
        Set<String> addedVideoIds = new HashSet<>();
        for (SearchResult result : youtubeResults) {
            ResourceId resourceId = result.getId();
            SearchResultSnippet snippet = result.getSnippet();
            if (resourceId == null || snippet == null) {
                log.warn("Skipping search result due to missing ID or snippet. Result: {}", result);
                continue;
            }
            String videoId = resourceId.getVideoId();
            String title = snippet.getTitle();
            if (!StringUtils.hasText(videoId) || !StringUtils.hasText(title)) {
                log.warn(
                        "Skipping search result due to missing videoId or title even though ID/Snippet objects exist. VideoId: {}, Title: {}",
                        videoId, title);
                continue;
            }
            if (!addedVideoIds.add(videoId)) {
                log.debug("Skipping duplicate video recommendation. VideoId: {}", videoId);
                continue;
            }
            String description = snippet.getDescription();
            String channelTitle = snippet.getChannelTitle();

            String thumbnailUrl = "https://i.ytimg.com/vi/" + videoId + "/maxresdefault.jpg";

            String fallbackThumbnailUrl = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";

            Map<String, String> thumbnailOptions = new HashMap<>();
            thumbnailOptions.put("maxres", thumbnailUrl);
            thumbnailOptions.put("hq", fallbackThumbnailUrl);
            thumbnailOptions.put("mq", "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg");
            thumbnailOptions.put("sd", "https://i.ytimg.com/vi/" + videoId + "/sddefault.jpg");
            thumbnailOptions.put("default", "https://i.ytimg.com/vi/" + videoId + "/default.jpg");

            log.debug("Using max resolution thumbnail URL for videoId {}: {} (with fallbacks)",
                    videoId, thumbnailUrl);

            int relevanceScore = 0;

            relevanceScore += Math.min(title.length() / 5, 10);

            if (description != null) {
                relevanceScore += Math.min(description.length() / 20, 15);
            }

            boolean isEducational = false;

            if (channelTitle != null) {
                String lowerChannelTitle = channelTitle.toLowerCase();
                for (String eduTerm : EDUCATIONAL_DOMAINS) {
                    if (lowerChannelTitle.contains(eduTerm)) {
                        isEducational = true;
                        relevanceScore += 20;
                        break;
                    }
                }
            }

            if (!isEducational && title != null) {
                String lowerTitle = title.toLowerCase();
                for (String eduTerm : EDUCATIONAL_DOMAINS) {
                    if (lowerTitle.contains(eduTerm)) {
                        isEducational = true;
                        relevanceScore += 15;
                        break;
                    }
                }
            }

            if (title != null) {
                String lowerTitle = title.toLowerCase();
                if (lowerTitle.contains("tutorial") || lowerTitle.contains("course")
                        || lowerTitle.contains("lecture") || lowerTitle.contains("learn")
                        || lowerTitle.contains("lesson")) {
                    relevanceScore += 25;
                    isEducational = true;
                }
            }

            boolean likelyEnglish = true;
            if (title != null) {
                String lowerTitle = title.toLowerCase();

                for (String indicator : FOREIGN_LANGUAGE_INDICATORS) {
                    if (lowerTitle.contains(indicator)) {
                        likelyEnglish = false;
                        break;
                    }
                }

                if (likelyEnglish) {
                    long nonAsciiCount = lowerTitle.chars().filter(c -> c > 127).count();
                    double nonAsciiRatio =
                            lowerTitle.isEmpty() ? 0 : (double) nonAsciiCount / lowerTitle.length();
                    if (nonAsciiRatio > 0.3) {
                        likelyEnglish = false;
                    }
                }
            }

            if (!likelyEnglish) {
                relevanceScore -= 100;
            } else {
                relevanceScore += 10;
            }

            LearningRecommendation recommendation =
                    new LearningRecommendation(videoId, title, description, thumbnailUrl,
                            recordingId, relevanceScore, isEducational, channelTitle);

            recommendation.setFallbackThumbnailUrl(fallbackThumbnailUrl);

            recommendations.add(recommendation);
        }

        recommendations.sort((r1, r2) -> {
            Integer score1 = r1.getRelevanceScore() != null ? r1.getRelevanceScore() : 0;
            Integer score2 = r2.getRelevanceScore() != null ? r2.getRelevanceScore() : 0;
            return score2.compareTo(score1);
        });

        return recommendations;
    }

    public List<LearningRecommendation> getRecommendationsByRecordingId(String recordingId) {
        log.info("Attempting to retrieve recommendations for recording ID: {} using native client",
                recordingId);
        List<LearningRecommendation> recommendations = new ArrayList<>();
        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(recommendationsCollectionName)
                    .whereEqualTo("recordingId", recordingId).get();
            QuerySnapshot querySnapshot = future.get();
            List<QueryDocumentSnapshot> documents = querySnapshot.getDocuments();
            if (documents.isEmpty()) {
                log.info("No recommendations found in Firestore for recording ID: {}", recordingId);
                return Collections.emptyList();
            }
            log.info("Found {} potential recommendation document(s) for recording ID: {}",
                    documents.size(), recordingId);
            for (QueryDocumentSnapshot document : documents) {
                try {
                    LearningRecommendation rec = LearningRecommendation.fromMap(document.getData());
                    if (rec != null) {
                        rec.setRecommendationId(document.getId());
                        recommendations.add(rec);
                        log.debug("Successfully mapped document {} to recommendation: {}",
                                document.getId(), rec);
                    } else {
                        log.warn(
                                "Failed to map Firestore document {} to LearningRecommendation object.",
                                document.getId());
                    }
                } catch (Exception e) {
                    log.error(
                            "Error mapping Firestore document {} to LearningRecommendation for recording ID: {}",
                            document.getId(), recordingId, e);
                }
            }
            log.info("Successfully retrieved and mapped {} recommendations for recording ID: {}",
                    recommendations.size(), recordingId);
            return recommendations;
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error retrieving recommendations from Firestore for recording ID: {}",
                    recordingId, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Unexpected error during native Firestore retrieval for recording ID: {}",
                    recordingId, e);
            return Collections.emptyList();
        }
    }

    public void deleteRecommendationsByRecordingId(String recordingId) {
        if (!StringUtils.hasText(recordingId)) {
            log.warn("Attempted to delete recommendations with null or empty recordingId.");
            return;
        }
        log.warn("Attempting to delete ALL recommendations for recording ID: {}", recordingId);
        CollectionReference recommendationsRef =
                firestore.collection(recommendationsCollectionName);
        Query query = recommendationsRef.whereEqualTo("recordingId", recordingId);
        ApiFuture<QuerySnapshot> future = query.get();
        int deletedCount = 0;
        try {
            QuerySnapshot snapshot = future.get();
            List<QueryDocumentSnapshot> documents = snapshot.getDocuments();
            if (documents.isEmpty()) {
                log.info("No recommendations found for recording ID {} to delete.", recordingId);
                return;
            }
            WriteBatch batch = firestore.batch();
            for (QueryDocumentSnapshot document : documents) {
                log.debug("Adding recommendation {} to delete batch for recordingId {}.",
                        document.getId(), recordingId);
                batch.delete(document.getReference());
                deletedCount++;
            }
            ApiFuture<List<WriteResult>> batchFuture = batch.commit();
            batchFuture.get();
            log.info("Successfully deleted {} recommendations for recording ID: {}", deletedCount,
                    recordingId);
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error deleting recommendations for recording ID {}: {}", recordingId,
                    e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            log.error("Unexpected error deleting recommendations for recording ID {}: {}",
                    recordingId, e.getMessage(), e);
        }
    }

    public boolean deleteRecommendation(String recommendationId) {
        if (!StringUtils.hasText(recommendationId)) {
            log.warn("Attempted to delete recommendation with null or empty ID.");
            return false;
        }
        log.info("Attempting to delete LearningRecommendation with ID: {}", recommendationId);
        try {
            DocumentReference docRef =
                    firestore.collection(recommendationsCollectionName).document(recommendationId);
            ApiFuture<WriteResult> future = docRef.delete();
            future.get();
            log.info("Successfully deleted LearningRecommendation with ID: {}", recommendationId);
            return true;
        } catch (ExecutionException e) {
            log.error("ExecutionException while deleting recommendation {}: {}", recommendationId,
                    e.getMessage(), e);
            return false;
        } catch (InterruptedException e) {
            log.error("InterruptedException while deleting recommendation {}: {}", recommendationId,
                    e.getMessage(), e);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.error("Unexpected error deleting recommendation {}: {}", recommendationId,
                    e.getMessage(), e);
            return false;
        }
    }

    private List<String> enhanceKeywordsWithEducationalContext(List<String> originalKeywords) {
        if (originalKeywords == null || originalKeywords.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> enhancedKeywords = new ArrayList<>();

        for (String keyword : originalKeywords) {
            if (keyword.length() < 3)
                continue;

            enhancedKeywords.add(keyword);

            if (keyword.length() > 5) {
                enhancedKeywords.add(keyword + " " + EDUCATIONAL_CONTEXT);
            }
        }

        if (originalKeywords.size() >= 2) {
            for (int i = 0; i < originalKeywords.size() - 1 && i < 3; i++) {
                String k1 = originalKeywords.get(i);
                for (int j = i + 1; j < originalKeywords.size() && j < i + 3; j++) {
                    String k2 = originalKeywords.get(j);
                    enhancedKeywords.add(k1 + " " + k2 + " " + EDUCATIONAL_CONTEXT);
                }
            }
        }

        return enhancedKeywords;
    }

    private List<SearchResult> filterAndRankResults(List<SearchResult> results,
            List<String> keywords) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        Map<SearchResult, Integer> resultScores = new HashMap<>();

        for (SearchResult result : results) {
            if (result.getSnippet() == null)
                continue;

            int score = 0;
            String title = result.getSnippet().getTitle() != null
                    ? result.getSnippet().getTitle().toLowerCase()
                    : "";
            String description = result.getSnippet().getDescription() != null
                    ? result.getSnippet().getDescription().toLowerCase()
                    : "";
            String channelTitle = result.getSnippet().getChannelTitle() != null
                    ? result.getSnippet().getChannelTitle().toLowerCase()
                    : "";

            for (String keyword : keywords) {
                String lowerKeyword = keyword.toLowerCase();
                if (title.contains(lowerKeyword)) {
                    score += 10;
                }
                if (description.contains(lowerKeyword)) {
                    score += 5;
                }
            }

            for (String eduDomain : EDUCATIONAL_DOMAINS) {
                if (channelTitle.contains(eduDomain)) {
                    score += 15;
                    break;
                }
            }

            for (String eduDomain : EDUCATIONAL_DOMAINS) {
                if (title.contains(eduDomain)) {
                    score += 8;
                    break;
                }
            }

            if (title.length() < 15) {
                score -= 5;
            }
            if (description.length() < 30) {
                score -= 3;
            }

            resultScores.put(result, score);
        }

        return resultScores.entrySet().stream()
                .sorted(Map.Entry.<SearchResult, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey).collect(Collectors.toList());
    }

    private List<SearchResult> filterByLanguage(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        return results.stream().filter(result -> {
            if (result.getSnippet() == null)
                return false;

            String title = result.getSnippet().getTitle() != null
                    ? result.getSnippet().getTitle().toLowerCase()
                    : "";
            String description = result.getSnippet().getDescription() != null
                    ? result.getSnippet().getDescription().toLowerCase()
                    : "";
            String channelTitle = result.getSnippet().getChannelTitle() != null
                    ? result.getSnippet().getChannelTitle().toLowerCase()
                    : "";

            for (String indicator : FOREIGN_LANGUAGE_INDICATORS) {
                if (title.contains(indicator) || channelTitle.contains(indicator)) {
                    log.debug("Filtering out likely non-English video: {} ({})", title, indicator);
                    return false;
                }
            }

            for (String region : NON_ENGLISH_REGIONS) {
                String regex = "\\b" + region + "\\b";
                if (title.matches(".*" + regex + ".*") || channelTitle.matches(".*" + regex + ".*")
                        || description.matches(".*" + regex + ".*")) {
                    log.debug("Filtering out likely non-English region video: {} ({})", title,
                            region);
                    return false;
                }
            }

            long nonAsciiCount = title.chars().filter(c -> c > 127).count();
            double nonAsciiRatio = title.isEmpty() ? 0 : (double) nonAsciiCount / title.length();

            if (nonAsciiRatio > 0.3) {
                log.debug("Filtering out likely non-English video due to high non-ASCII ratio: {}",
                        title);
                return false;
            }

            if (channelTitle.contains("official") && nonAsciiRatio > 0.1) {
                log.debug("Filtering out likely non-English official channel: {}", channelTitle);
                return false;
            }

            return true;
        }).collect(Collectors.toList());
    }
}

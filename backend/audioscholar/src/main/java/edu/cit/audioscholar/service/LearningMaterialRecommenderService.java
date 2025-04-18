package edu.cit.audioscholar.service;

import edu.cit.audioscholar.dto.AnalysisResults;
import edu.cit.audioscholar.integration.YouTubeAPIClient;
import edu.cit.audioscholar.model.LearningRecommendation;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.WriteResult;


import com.google.api.services.youtube.model.ResourceId;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.SearchResultSnippet;
import com.google.api.services.youtube.model.Thumbnail;
import com.google.api.services.youtube.model.ThumbnailDetails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;


@Service
public class LearningMaterialRecommenderService {

    private static final Logger log = LoggerFactory.getLogger(LearningMaterialRecommenderService.class);

    private final LectureContentAnalyzerService lectureContentAnalyzerService;
    private final YouTubeAPIClient youTubeAPIClient;
    private final Firestore firestore;

    private final String recommendationsCollectionName = "learningRecommendations";


    private static final int MAX_RECOMMENDATIONS_TO_FETCH = 10;

    public LearningMaterialRecommenderService(
            LectureContentAnalyzerService lectureContentAnalyzerService,
            YouTubeAPIClient youTubeAPIClient,
            Firestore firestore) {
        this.lectureContentAnalyzerService = lectureContentAnalyzerService;
        this.youTubeAPIClient = youTubeAPIClient;
        this.firestore = firestore;
    }

    public List<LearningRecommendation> generateAndSaveRecommendations(String recordingId) {
        log.info("Starting recommendation generation and storage for recording ID: {}", recordingId);

        AnalysisResults analysisResults = lectureContentAnalyzerService.analyzeLectureContent(recordingId);

        if (!analysisResults.isSuccess()) {
            log.error("Failed to analyze lecture content for recording ID: {}. Error: {}", recordingId, analysisResults.getErrorMessage());
            return Collections.emptyList();
        }

        List<String> keywords = analysisResults.getKeywordsAndTopics();
        if (keywords.isEmpty()) {
            log.info("No keywords found for recording ID: {}. Cannot generate recommendations.", recordingId);
            return Collections.emptyList();
        }

        log.debug("Keywords extracted for recording ID {}: {}", recordingId, keywords);

        try {
            List<SearchResult> youtubeResults = youTubeAPIClient.searchVideos(keywords, MAX_RECOMMENDATIONS_TO_FETCH);

            if (youtubeResults.isEmpty()) {
                log.info("YouTube search returned no results for keywords related to recording ID: {}", recordingId);
                return Collections.emptyList();
            }

            log.info("Retrieved {} potential recommendations from YouTube for recording ID: {}", youtubeResults.size(), recordingId);

            List<LearningRecommendation> recommendations = processYouTubeResults(youtubeResults, recordingId);

            if (recommendations.isEmpty()) {
                log.info("No valid recommendations processed for recording ID: {}", recordingId);
                return Collections.emptyList();
            }

            log.info("Successfully processed {} unique recommendations for recording ID: {}", recommendations.size(), recordingId);

            try {
                WriteBatch batch = firestore.batch();
                List<LearningRecommendation> recommendationsWithIds = new ArrayList<>();

                for (LearningRecommendation recommendation : recommendations) {
                    DocumentReference docRef = firestore.collection(recommendationsCollectionName).document();
                    recommendation.setRecommendationId(docRef.getId());
                    batch.set(docRef, recommendation);
                    recommendationsWithIds.add(recommendation);
                }

                log.info("Attempting to commit batch write of {} recommendations to Firestore for recording ID: {}", recommendationsWithIds.size(), recordingId);
                ApiFuture<List<WriteResult>> future = batch.commit();
                List<WriteResult> results = future.get();
                log.info("Successfully committed batch write to Firestore for recording ID: {}. Results count: {}", recordingId, results.size());

                return recommendationsWithIds;

            } catch (ExecutionException | InterruptedException e) {
                log.error("Error committing batch write to Firestore for recording ID: {}", recordingId, e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return Collections.emptyList();
            } catch (Exception e) {
                 log.error("Unexpected error during native Firestore batch save for recording ID: {}", recordingId, e);
                 return Collections.emptyList();
            }

        } catch (Exception e) {
            log.error("Unexpected error during recommendation generation or saving for recording ID: {}", recordingId, e);
            return Collections.emptyList();
        }
    }

    private List<LearningRecommendation> processYouTubeResults(List<SearchResult> youtubeResults, String recordingId) {
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
                 log.warn("Skipping search result due to missing videoId or title even though ID/Snippet objects exist. VideoId: {}, Title: {}", videoId, title);
                 continue;
            }

            if (!addedVideoIds.add(videoId)) {
                log.debug("Skipping duplicate video recommendation. VideoId: {}", videoId);
                continue;
            }

            String description = snippet.getDescription();
            ThumbnailDetails thumbnails = snippet.getThumbnails();
            String thumbnailUrl = null;

            if (thumbnails != null) {
                Thumbnail defaultThumbnail = thumbnails.getDefault();
                if (defaultThumbnail != null) {
                    thumbnailUrl = defaultThumbnail.getUrl();
                }
            }
            if (thumbnailUrl == null) {
                 log.warn("Could not extract default thumbnail URL for videoId: {}. Setting to null.", videoId);
            }


            LearningRecommendation recommendation = new LearningRecommendation(
                    videoId,
                    title,
                    description,
                    thumbnailUrl,
                    recordingId
            );

            recommendations.add(recommendation);
        }

        return recommendations;
    }

    public List<LearningRecommendation> getRecommendationsByRecordingId(String recordingId) {
        log.info("Attempting to retrieve recommendations for recording ID: {} using native client", recordingId);
        List<LearningRecommendation> recommendations = new ArrayList<>();
        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(recommendationsCollectionName)
                                                        .whereEqualTo("recordingId", recordingId)
                                                        .get();
            QuerySnapshot querySnapshot = future.get();

            List<QueryDocumentSnapshot> documents = querySnapshot.getDocuments();
            if (documents.isEmpty()) {
                 log.info("No recommendations found in Firestore for recording ID: {}", recordingId);
                 return Collections.emptyList();
            }

            log.info("Found {} potential recommendation document(s) for recording ID: {}", documents.size(), recordingId);

            for (QueryDocumentSnapshot document : documents) {
                try {
                    LearningRecommendation rec = LearningRecommendation.fromMap(document.getData());
                    if (rec != null) {
                        rec.setRecommendationId(document.getId());
                        recommendations.add(rec);
                        log.debug("Successfully mapped document {} to recommendation: {}", document.getId(), rec);
                    } else {
                        log.warn("Failed to map Firestore document {} to LearningRecommendation object.", document.getId());
                    }
                } catch (Exception e) {
                     log.error("Error mapping Firestore document {} to LearningRecommendation for recording ID: {}", document.getId(), recordingId, e);
                }
            }

            log.info("Successfully retrieved and mapped {} recommendations for recording ID: {}", recommendations.size(), recordingId);
            return recommendations;

        } catch (ExecutionException | InterruptedException e) {
            log.error("Error retrieving recommendations from Firestore for recording ID: {}", recordingId, e);
            if (e instanceof InterruptedException) {
                 Thread.currentThread().interrupt();
            }
            return Collections.emptyList();
        } catch (Exception e) {
             log.error("Unexpected error during native Firestore retrieval for recording ID: {}", recordingId, e);
             return Collections.emptyList();
        }
    }
}
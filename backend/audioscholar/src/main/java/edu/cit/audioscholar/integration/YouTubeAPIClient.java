package edu.cit.audioscholar.integration;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class YouTubeAPIClient {

    private static final Logger log = LoggerFactory.getLogger(YouTubeAPIClient.class);

    @Value("${youtube.api.key}")
    private String apiKey;

    private static final String APPLICATION_NAME = "AudioScholarApp";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private YouTube youtubeService;

    @PostConstruct
    private void initialize() {
        try {
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            youtubeService = new YouTube.Builder(httpTransport, JSON_FACTORY, null)
                .setApplicationName(APPLICATION_NAME)
                .build();
            log.info("YouTube Data API service initialized successfully.");
        } catch (GeneralSecurityException | IOException e) {
            log.error("Failed to initialize YouTube Data API service", e);
            youtubeService = null;
        }
    }

    public List<SearchResult> searchVideos(List<String> keywords, int maxResults) {
        if (youtubeService == null) {
            log.error("YouTube service is not initialized. Cannot perform search.");
            return Collections.emptyList();
        }
        if (keywords == null || keywords.isEmpty()) {
            log.warn("Keywords list is null or empty. Skipping YouTube search.");
            return Collections.emptyList();
        }
        if (maxResults <= 0) {
            log.warn("maxResults must be positive. Defaulting to 5.");
            maxResults = 5;
        }
        if (maxResults > 50) {
             log.warn("maxResults exceeds YouTube API limit (50). Capping at 50.");
             maxResults = 50;
        }

        String queryString = buildQueryString(keywords);
        log.info("Searching YouTube with query: '{}', maxResults: {}", queryString, maxResults);

        try {
            YouTube.Search.List searchRequest = youtubeService.search()
                .list(List.of("id", "snippet"));

            searchRequest.setKey(apiKey);
            searchRequest.setQ(queryString);
            searchRequest.setType(List.of("video"));
            searchRequest.setMaxResults((long) maxResults);
            searchRequest.setOrder("relevance");
            searchRequest.setFields("items(id/videoId,snippet/title,snippet/description,snippet/thumbnails/default/url)");

            SearchListResponse searchResponse = searchRequest.execute();

            if (searchResponse != null && searchResponse.getItems() != null) {
                List<SearchResult> searchResults = searchResponse.getItems();
                log.info("Successfully retrieved {} video results from YouTube.", searchResults.size());
                return searchResults;
            } else {
                log.info("YouTube search returned no results for query: '{}'", queryString);
                return Collections.emptyList();
            }

        } catch (IOException e) {
            log.error("Error executing YouTube search request for query: '{}'. Message: {}", queryString, e.getMessage(), e);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Unexpected error during YouTube search for query: '{}'", queryString, e);
            return Collections.emptyList();
        }
    }

    private String buildQueryString(List<String> keywords) {
        return keywords.stream()
                       .map(String::trim)
                       .filter(kw -> !kw.isBlank())
                       .collect(Collectors.joining(" "));
    }
}
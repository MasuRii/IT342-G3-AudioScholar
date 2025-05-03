package edu.cit.audioscholar.integration;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import jakarta.annotation.PostConstruct;

@Service
public class YouTubeAPIClient {

    private static final Logger log = LoggerFactory.getLogger(YouTubeAPIClient.class);

    @Value("${youtube.api.key}")
    private String apiKey;

    private static final String APPLICATION_NAME = "AudioScholarApp";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private static final List<String> ENGLISH_REGIONS =
            Arrays.asList("US", "GB", "CA", "AU", "NZ", "IE", "SG");

    private static final String DEFAULT_REGION = "US";

    private YouTube youtubeService;

    @PostConstruct
    private void initialize() {
        try {
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            youtubeService = new YouTube.Builder(httpTransport, JSON_FACTORY, null)
                    .setApplicationName(APPLICATION_NAME).build();
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

        return searchVideosFromMultipleRegions(queryString, maxResults);
    }

    private List<SearchResult> searchVideosFromMultipleRegions(String queryString, int maxResults) {
        int resultsPerRegion = Math.min(maxResults, 20);
        Set<String> uniqueVideoIds = new HashSet<>();
        List<SearchResult> allResults = new ArrayList<>();

        for (String regionCode : ENGLISH_REGIONS) {
            if (allResults.size() >= maxResults)
                break;

            try {
                log.info("Searching YouTube for region: {} with query: '{}'", regionCode,
                        queryString);
                List<SearchResult> regionResults =
                        executeSearch(queryString, resultsPerRegion, regionCode);

                for (SearchResult result : regionResults) {
                    if (result.getId() != null && result.getId().getVideoId() != null) {
                        String videoId = result.getId().getVideoId();
                        if (uniqueVideoIds.add(videoId)) {
                            allResults.add(result);
                            if (allResults.size() >= maxResults)
                                break;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error searching region {}: {}", regionCode, e.getMessage());
            }
        }

        if (allResults.isEmpty()) {
            log.info("No results from region-specific searches. Trying default search.");
            try {
                List<SearchResult> defaultResults = executeSearch(queryString, maxResults, null);
                for (SearchResult result : defaultResults) {
                    if (result.getId() != null && result.getId().getVideoId() != null) {
                        String videoId = result.getId().getVideoId();
                        if (uniqueVideoIds.add(videoId)) {
                            allResults.add(result);
                            if (allResults.size() >= maxResults)
                                break;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error executing default search: {}", e.getMessage(), e);
            }
        }

        log.info("Retrieved {} unique video results across regions", allResults.size());
        return allResults;
    }

    private List<SearchResult> executeSearch(String queryString, int maxResults, String regionCode)
            throws IOException {
        YouTube.Search.List searchRequest = youtubeService.search().list(List.of("id", "snippet"));

        searchRequest.setKey(apiKey);
        searchRequest.setQ(queryString);
        searchRequest.setType(List.of("video"));
        searchRequest.setMaxResults((long) maxResults);
        searchRequest.setOrder("relevance");
        searchRequest.setRelevanceLanguage("en");
        searchRequest.setVideoCaption("closedCaption");

        if (regionCode != null && !regionCode.isEmpty()) {
            searchRequest.setRegionCode(regionCode);
        }

        searchRequest.setFields(
                "items(id/videoId,snippet/title,snippet/description,snippet/channelTitle,snippet/thumbnails)");

        SearchListResponse searchResponse = searchRequest.execute();

        if (searchResponse != null && searchResponse.getItems() != null) {
            List<SearchResult> searchResults = searchResponse.getItems();
            log.info("Successfully retrieved {} video results for region: {}", searchResults.size(),
                    regionCode != null ? regionCode : "default");
            return searchResults;
        } else {
            log.info("YouTube search returned no results for region: {} with query: '{}'",
                    regionCode != null ? regionCode : "default", queryString);
            return Collections.emptyList();
        }
    }

    private String buildQueryString(List<String> keywords) {
        return keywords.stream().map(String::trim).filter(kw -> !kw.isBlank())
                .collect(Collectors.joining(" "));
    }
}

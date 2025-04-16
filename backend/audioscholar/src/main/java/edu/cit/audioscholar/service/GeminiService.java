package edu.cit.audioscholar.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    @Value("${google.ai.api.key}")
    private String apiKey;

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-pro:generateContent";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String callGeminiAPI(String inputText) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", inputText))))
        );

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        String url = API_URL + "?key=" + apiKey;

        log.debug("Calling Gemini API (Text) at URL: {}", API_URL);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, String.class
                );
                log.info("Gemini API (Text) call successful on attempt {}", attempt);
                return processGeminiResponse(response.getBody());

            } catch (HttpServerErrorException | ResourceAccessException e) {
                log.warn("Gemini API (Text) call failed on attempt {}/{}. Error: {}. Retrying...",
                         attempt, MAX_RETRIES, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    log.error("Gemini API (Text) call failed after {} attempts.", MAX_RETRIES, e);
                    return createErrorResponse("API Request Failed after retries", e.getMessage());
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return createErrorResponse("API call interrupted during retry wait", ie.getMessage());
                }
            } catch (HttpClientErrorException e) {
                 log.error("Gemini API (Text) client error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
                 return createErrorResponse("API Client Error: " + e.getStatusCode(), e.getResponseBodyAsString());
            } catch (Exception e) {
                log.error("Unexpected error during Gemini API (Text) call", e);
                return createErrorResponse("Unexpected API Request Failed", e.getMessage());
            }
        }
         return createErrorResponse("API Request Failed", "Max retries reached but no definitive result.");
    }

    public String callGeminiAPIWithAudio(String promptText, String base64Audio, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", promptText);

        Map<String, Object> audioPart = new HashMap<>();
        audioPart.put("inline_data", Map.of(
            "mime_type", getAudioMimeType(fileName),
            "data", base64Audio
        ));

        List<Object> parts = new ArrayList<>();
        parts.add(textPart);
        parts.add(audioPart);

        Map<String, Object> content = Map.of("parts", parts);
        List<Object> contents = new ArrayList<>();
        contents.add(content);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", contents);

        requestBody.put("generationConfig", Map.of(
            "temperature", 0.1,
            "topP", 0.8,
            "topK", 40,
            "maxOutputTokens", 4096
        ));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        String url = API_URL + "?key=" + apiKey;

        log.debug("Calling Gemini API (Audio) at URL: {}", API_URL);
        log.debug("Prompt text length: {}", promptText.length());
        log.debug("Base64 audio data length: {}", base64Audio.length());

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, String.class
                );
                log.info("Gemini API (Audio) call successful on attempt {}", attempt);
                return processGeminiResponse(response.getBody());

            } catch (HttpServerErrorException | ResourceAccessException e) {
                log.warn("Gemini API (Audio) call failed on attempt {}/{}. Error: {}. Retrying...",
                         attempt, MAX_RETRIES, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    log.error("Gemini API (Audio) call failed after {} attempts.", MAX_RETRIES, e);
                    return createErrorResponse("API Request Failed after retries", e.getMessage());
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("API call interrupted during retry wait.", ie);
                    return createErrorResponse("API call interrupted during retry wait", ie.getMessage());
                }
            } catch (HttpClientErrorException e) {
                 log.error("Gemini API (Audio) client error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
                 return createErrorResponse("API Client Error: " + e.getStatusCode(), e.getResponseBodyAsString());
            } catch (Exception e) {
                log.error("Unexpected error during Gemini API (Audio) call on attempt {}", attempt, e);
                return createErrorResponse("Unexpected API Request Failed", e.getMessage());
            }
        }
        return createErrorResponse("API Request Failed", "Max retries reached but no definitive result.");
    }

    private String processGeminiResponse(String rawResponse) {
        try {
            log.debug("Raw Gemini Response: {}", rawResponse);
            JsonNode jsonNode = objectMapper.readTree(rawResponse);

            if (!jsonNode.has("candidates") || !jsonNode.path("candidates").isArray() || jsonNode.path("candidates").isEmpty()) {
                 log.warn("No 'candidates' array found or is empty in Gemini response.");
                 if (jsonNode.has("promptFeedback")) {
                     log.warn("Gemini Response Prompt Feedback: {}", jsonNode.path("promptFeedback").toString());
                     return createErrorResponse("Gemini API Error", "Content blocked or invalid request. Check promptFeedback. Raw: " + rawResponse);
                 }
                 return createErrorResponse("Invalid response structure from Gemini API", "Missing or empty 'candidates' array. Raw: " + rawResponse);
            }

            JsonNode firstCandidate = jsonNode.path("candidates").get(0);
            if (!firstCandidate.has("content") || !firstCandidate.path("content").has("parts") || !firstCandidate.path("content").path("parts").isArray() || firstCandidate.path("content").path("parts").isEmpty()) {
                 log.warn("Missing 'content' or 'parts' in the first candidate.");
                 return createErrorResponse("Invalid response structure", "Missing 'content' or 'parts' in candidate. Raw: " + rawResponse);
            }

            JsonNode firstPart = firstCandidate.path("content").path("parts").get(0);
            if (!firstPart.has("text")) {
                 log.warn("Missing 'text' field in the first part of the candidate's content.");
                 return createErrorResponse("Invalid response structure", "Missing 'text' in content part. Raw: " + rawResponse);
            }

            String summaryText = firstPart.path("text").asText();
            log.info("Successfully extracted summary text from Gemini response.");

            ObjectNode successResponse = objectMapper.createObjectNode();
            successResponse.put("text", summaryText);
            return objectMapper.writeValueAsString(successResponse);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Gemini API JSON response", e);
            return createErrorResponse("JSON Parsing Error", e.getMessage());
        } catch (Exception e) {
             log.error("Unexpected error processing Gemini response", e);
             return createErrorResponse("Response Processing Error", e.getMessage());
        }
    }

    private String createErrorResponse(String errorTitle, String details) {
        ObjectNode errorResponse = objectMapper.createObjectNode();
        errorResponse.put("error", errorTitle);
        errorResponse.put("details", details);
        try {
            return objectMapper.writeValueAsString(errorResponse);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize error response JSON", ex);
            return "{\"error\": \"Failed to serialize error response\", \"details\": \"" + errorTitle.replace("\"", "\\\"") + "\"}";
        }
    }

    private String getAudioMimeType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            log.warn("Filename is null or empty, defaulting MIME type to audio/mpeg");
            return "audio/mpeg";
        }
        String lowercaseFileName = fileName.toLowerCase();
        if (lowercaseFileName.endsWith(".mp3")) {
            return "audio/mp3";
        } else if (lowercaseFileName.endsWith(".wav")) {
            return "audio/wav";
        } else if (lowercaseFileName.endsWith(".m4a")) {
            return "audio/mp4";
        } else if (lowercaseFileName.endsWith(".ogg")) {
            return "audio/ogg";
        } else if (lowercaseFileName.endsWith(".flac")) {
            return "audio/flac";
        } else if (lowercaseFileName.endsWith(".aac")) {
            return "audio/aac";
        } else if (lowercaseFileName.endsWith(".aiff") || lowercaseFileName.endsWith(".aif")) {
             return "audio/aiff";
        }
        log.warn("Unknown audio file extension '{}', defaulting MIME type to audio/mpeg", lowercaseFileName.substring(lowercaseFileName.lastIndexOf('.')));
        return "audio/mpeg";
    }

}
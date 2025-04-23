package edu.cit.audioscholar.service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    @Value("${google.ai.api.key}")
    private String apiKey;

    private static final String API_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String MODEL_NAME = "gemini-2.0-flash";
    private static final String API_URL = API_BASE_URL + MODEL_NAME + ":generateContent";

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final int MAX_OUTPUT_TOKENS_GENERAL = 8192;
    private static final int MAX_OUTPUT_TOKENS_TEXT_ONLY = 2048;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, Object> SUMMARY_RESPONSE_SCHEMA = createSummarySchema();

    private static Map<String, Object> createSummarySchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "OBJECT");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("summaryText", Map.of("type", "STRING", "description",
                "Clear, well-structured summary in Markdown format."));
        properties.put("keyPoints", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"),
                "description", "List of distinct key points or action items."));
        properties.put("topics", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"),
                "description", "List of main topics or keywords (3-5 items)."));
        schema.put("properties", properties);
        schema.put("required", List.of("summaryText", "keyPoints", "topics"));
        return Collections.unmodifiableMap(schema);
    }

    public String callGeminiTextAPI(String promptText) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> textPart = Map.of("text", promptText);
        List<Object> parts = List.of(textPart);
        Map<String, Object> content = Map.of("parts", parts);
        List<Object> contents = List.of(content);

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.5);
        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS_TEXT_ONLY);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", contents);
        requestBody.put("generationConfig", generationConfig);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        String url = API_URL + "?key=" + apiKey;

        log.debug("Calling Gemini API (Text Mode) using model {} at URL: {}", MODEL_NAME, API_URL);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response =
                        restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

                log.info(
                        "Gemini API (Text Mode) call successful on attempt {} using model {}. Status: {}",
                        attempt, MODEL_NAME, response.getStatusCode());

                String responseBody = response.getBody();
                if (responseBody == null || responseBody.isBlank()) {
                    log.warn(
                            "Gemini API (Text Mode) returned successful status ({}) but empty body.",
                            response.getStatusCode());
                    return createErrorResponse("Empty Response",
                            "API returned success status but no content.");
                }

                String extractedText = extractTextFromStandardResponse(responseBody);
                return createTextSuccessResponse(extractedText);

            } catch (HttpServerErrorException | ResourceAccessException e) {
                log.warn(
                        "Gemini API (Text Mode) call failed on attempt {}/{} with retryable error: {}. Retrying...",
                        attempt, MAX_RETRIES, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    log.error("Gemini API (Text Mode) call failed after {} attempts.", MAX_RETRIES,
                            e);
                    return createErrorResponse("API Request Failed (Server/Network)",
                            e.getMessage());
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return createErrorResponse("API call interrupted", ie.getMessage());
                }
            } catch (HttpClientErrorException e) {
                log.error("Gemini API (Text Mode) client error: {} - {}", e.getStatusCode(),
                        e.getResponseBodyAsString(), e);
                String details = parseErrorDetails(e);
                return createErrorResponse("API Client Error: " + e.getStatusCode(), details);
            } catch (RestClientResponseException e) {
                log.error("Gemini API (Text Mode) REST client error: Status {}, Body: {}",
                        e.getStatusCode(), e.getResponseBodyAsString(), e);
                return createErrorResponse("API Request Failed (REST Client)", e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error during Gemini API (Text Mode) call on attempt {}",
                        attempt, e);
                if (attempt == MAX_RETRIES) {
                    return createErrorResponse("Unexpected API Request Failed", e.getMessage());
                }
                return createErrorResponse("Unexpected API Request Failed", e.getMessage());
            }
        }
        return createErrorResponse("API Request Failed", "Max retries reached or unexpected flow.");
    }


    public String callGeminiAPIWithAudio(String promptText, String base64Audio, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> textPart = Map.of("text", promptText);
        Map<String, Object> audioPart = Map.of("inline_data",
                Map.of("mime_type", getAudioMimeType(fileName), "data", base64Audio));
        List<Object> parts = List.of(textPart, audioPart);
        Map<String, Object> content = Map.of("parts", parts);
        List<Object> contents = List.of(content);
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.3);
        generationConfig.put("topP", 0.95);
        generationConfig.put("topK", 40);
        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS_GENERAL);
        generationConfig.put("response_mime_type", "application/json");
        generationConfig.put("response_schema", SUMMARY_RESPONSE_SCHEMA);
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", contents);
        requestBody.put("generationConfig", generationConfig);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        String url = API_URL + "?key=" + apiKey;
        log.debug("Calling Gemini API (Audio, JSON Schema Mode) using model {} at URL: {}",
                MODEL_NAME, API_URL);
        log.debug("Prompt text length: {}", promptText.length());

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response =
                        restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
                log.info(
                        "Gemini API (Audio, JSON Schema Mode) call successful on attempt {} using model {}. Status: {}",
                        attempt, MODEL_NAME, response.getStatusCode());
                String responseBody = response.getBody();
                if (responseBody == null || responseBody.isBlank()) {
                    log.warn(
                            "Gemini API (Audio, JSON Schema Mode) returned successful status ({}) but empty body.",
                            response.getStatusCode());
                    return createErrorResponse("Empty Response",
                            "API returned success status but no content.");
                }

                String extractedJsonText = extractTextFromStandardResponse(responseBody);
                if (extractedJsonText.isBlank()) {
                    log.warn(
                            "Could not extract text content (expected JSON) from Gemini response structure. Body: {}",
                            responseBody);
                    return createErrorResponse("Extraction Error",
                            "Failed to extract content from API response structure.");
                }
                log.debug(
                        "Successfully extracted JSON text from standard response structure (length: {}).",
                        extractedJsonText.length());
                return extractedJsonText;

            } catch (HttpServerErrorException | ResourceAccessException e) {
                log.warn(
                        "Gemini API (Audio, JSON Schema Mode) call failed on attempt {}/{} with retryable error: {}. Retrying...",
                        attempt, MAX_RETRIES, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    log.error("Gemini API (Audio, JSON Schema Mode) call failed after {} attempts.",
                            MAX_RETRIES, e);
                    return createErrorResponse("API Request Failed (Server/Network)",
                            e.getMessage());
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("API call interrupted during retry wait.", ie);
                    return createErrorResponse("API call interrupted", ie.getMessage());
                }
            } catch (HttpClientErrorException e) {
                log.error("Gemini API (Audio, JSON Schema Mode) client error: {} - {}",
                        e.getStatusCode(), e.getResponseBodyAsString(), e);
                String details = parseErrorDetails(e);
                return createErrorResponse("API Client Error: " + e.getStatusCode(), details);
            } catch (RestClientResponseException e) {
                log.error(
                        "Gemini API (Audio, JSON Schema Mode) REST client error: Status {}, Body: {}",
                        e.getStatusCode(), e.getResponseBodyAsString(), e);
                return createErrorResponse("API Request Failed (REST Client)", e.getMessage());
            } catch (Exception e) {
                log.error(
                        "Error during Gemini API (Audio, JSON Schema Mode) call or processing on attempt {}",
                        attempt, e);
                if (attempt == MAX_RETRIES) {
                    return createErrorResponse("Unexpected API Request/Processing Failed",
                            e.getMessage());
                }
                return createErrorResponse("Unexpected API Request/Processing Failed",
                        e.getMessage());
            }
        }
        return createErrorResponse("API Request Failed", "Max retries reached or unexpected flow.");
    }


    private String extractTextFromStandardResponse(String rawResponse)
            throws JsonProcessingException {
        JsonNode jsonNode = objectMapper.readTree(rawResponse);
        if (jsonNode.has("error")) {
            log.error("Gemini API (Text Mode) returned an error object: {}", rawResponse);
            throw new RuntimeException("Gemini API Error: "
                    + jsonNode.path("error").path("message").asText("Unknown error"));
        }
        if (jsonNode.has("promptFeedback") && jsonNode.path("promptFeedback").has("blockReason")) {
            log.warn("Gemini API (Text Mode) request blocked: {}", rawResponse);
            throw new RuntimeException("Gemini API Error: Content Blocked");
        }
        if (jsonNode.has("candidates") && jsonNode.path("candidates").isArray()
                && !jsonNode.path("candidates").isEmpty()) {
            JsonNode firstCandidate = jsonNode.path("candidates").get(0);
            if (firstCandidate.has("content") && firstCandidate.path("content").has("parts")
                    && firstCandidate.path("content").path("parts").isArray()
                    && !firstCandidate.path("content").path("parts").isEmpty()) {
                StringBuilder responseTextBuilder = new StringBuilder();
                for (JsonNode part : firstCandidate.path("content").path("parts")) {
                    if (part.has("text")) {
                        responseTextBuilder.append(part.path("text").asText());
                    }
                }
                return responseTextBuilder.toString();
            }
        }
        log.warn("Could not extract text from Gemini (Text Mode) standard response structure: {}",
                rawResponse);
        return "";
    }

    private String createTextSuccessResponse(String textContent) {
        ObjectNode successResponse = objectMapper.createObjectNode();
        successResponse.put("text", textContent);
        try {
            return objectMapper.writeValueAsString(successResponse);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize text success response JSON", ex);
            return "{\"error\": \"Internal Server Error\", \"details\": \"Failed to serialize success response.\"}";
        }
    }


    private String parseErrorDetails(HttpClientErrorException e) {
        try {
            JsonNode errorNode = objectMapper.readTree(e.getResponseBodyAsString());
            if (errorNode.has("error") && errorNode.path("error").has("message")) {
                return errorNode.path("error").path("message").asText(e.getResponseBodyAsString());
            }
        } catch (JsonProcessingException jsonEx) {
            log.warn("Could not parse Gemini error response body as JSON: {}", jsonEx.getMessage());
        }
        return e.getResponseBodyAsString();
    }

    private String createErrorResponse(String errorTitle, String details) {
        ObjectNode errorResponse = objectMapper.createObjectNode();
        errorResponse.put("error", errorTitle);
        errorResponse.put("details", details != null ? details : "No details available.");
        try {
            return objectMapper.writeValueAsString(errorResponse);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize error response JSON", ex);
            return "{\"error\": \"Internal Server Error\", \"details\": \"Failed to serialize error response details.\"}";
        }
    }

    private String getAudioMimeType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            log.warn("Filename is null or empty, defaulting MIME type to audio/mpeg");
            return "audio/mpeg";
        }
        String lowercaseFileName = fileName.toLowerCase();
        if (lowercaseFileName.endsWith(".flac"))
            return "audio/flac";
        if (lowercaseFileName.endsWith(".wav"))
            return "audio/wav";
        if (lowercaseFileName.endsWith(".mp3"))
            return "audio/mp3";
        if (lowercaseFileName.endsWith(".ogg"))
            return "audio/ogg";
        if (lowercaseFileName.endsWith(".opus"))
            return "audio/opus";
        if (lowercaseFileName.endsWith(".m4a"))
            return "audio/m4a";
        if (lowercaseFileName.endsWith(".mp4"))
            return "audio/mp4";
        if (lowercaseFileName.endsWith(".aac"))
            return "audio/aac";
        if (lowercaseFileName.endsWith(".aiff") || lowercaseFileName.endsWith(".aif"))
            return "audio/aiff";
        if (lowercaseFileName.endsWith(".amr"))
            return "audio/amr";
        log.warn(
                "Unknown or potentially unsupported audio file extension in '{}', defaulting MIME type to audio/mpeg. Verify Gemini API support.",
                fileName);
        return "audio/mpeg";
    }
}

package edu.cit.audioscholar.service;

import java.util.ArrayList;
import java.util.Collections;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    @Value("${google.ai.api.key}")
    private String apiKey;

    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String MODEL_NAME = "gemini-2.0-flash";
    private static final String API_URL = API_BASE_URL + MODEL_NAME + ":generateContent";

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final int MAX_OUTPUT_TOKENS_GENERAL = 8192;
    private static final int MAX_OUTPUT_TOKENS_KEYWORDS = 512;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String callGeminiAPI(String inputText) {
        return callGeminiAPIBase(inputText, MAX_OUTPUT_TOKENS_GENERAL, "Text", API_URL);
    }

    public String callGeminiAPIWithAudio(String promptText, String base64Audio, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> textPart = Map.of("text", promptText);
        Map<String, Object> audioPart = Map.of(
            "inline_data", Map.of(
                "mime_type", getAudioMimeType(fileName),
                "data", base64Audio
            )
        );

        List<Object> parts = List.of(textPart, audioPart);
        Map<String, Object> content = Map.of("parts", parts);
        List<Object> contents = List.of(content);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", contents);
        requestBody.put("generationConfig", Map.of(
            "temperature", 0.3,
            "topP", 0.95,
            "topK", 40,
            "maxOutputTokens", MAX_OUTPUT_TOKENS_GENERAL
        ));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        String url = API_URL + "?key=" + apiKey;

        log.debug("Calling Gemini API (Audio) using model {} at URL: {}", MODEL_NAME, API_URL);
        log.debug("Prompt text length: {}", promptText.length());
        log.debug("Base64 audio data length: {}", base64Audio.length());

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, String.class
                );
                log.info("Gemini API (Audio) call successful on attempt {} using model {}", attempt, MODEL_NAME);
                String responseText = processGeminiResponse(response.getBody());
                return createSuccessResponse(responseText);

            } catch (HttpServerErrorException | ResourceAccessException e) {
                log.warn("Gemini API (Audio) call failed on attempt {}/{} using model {}. Error: {}. Retrying...",
                         attempt, MAX_RETRIES, MODEL_NAME, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    log.error("Gemini API (Audio) call failed after {} attempts using model {}.", MAX_RETRIES, MODEL_NAME, e);
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
                 log.error("Gemini API (Audio) client error using model {}: {} - {}", MODEL_NAME, e.getStatusCode(), e.getResponseBodyAsString(), e);
                 return createErrorResponse("API Client Error: " + e.getStatusCode(), e.getResponseBodyAsString());
            } catch (GeminiApiException e) {
                 log.error("Error processing Gemini API (Audio) response using model {}: {}", MODEL_NAME, e.getMessage());
                 return createErrorResponse(e.getErrorTitle(), e.getDetails());
            } catch (Exception e) {
                log.error("Unexpected error during Gemini API (Audio) call on attempt {} using model {}", attempt, MODEL_NAME, e);
                if (attempt < MAX_RETRIES) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("API call interrupted during retry wait after unexpected error.", ie);
                        return createErrorResponse("API call interrupted during retry wait", ie.getMessage());
                    }
                } else {
                    log.error("Gemini API (Audio) call failed after {} attempts due to unexpected error.", MAX_RETRIES, e);
                    return createErrorResponse("Unexpected API Request Failed", e.getMessage());
                }
            }
        }
        return createErrorResponse("API Request Failed", "Max retries reached but no definitive result or unhandled exception occurred.");
    }

    public List<String> extractKeywordsAndTopics(String summaryText) {
        String prompt = String.format(
            """
            Analyze the following lecture summary. Extract the 3 to 5 most important and concise keywords or short phrases
            that would be effective for searching for related educational videos on platforms like YouTube.
            Prioritize terms that capture the core subject matter.
            Return the results ONLY as a valid JSON array of strings (e.g., ["keyword1", "search phrase 2", "topic 3"]).
            Do not include any introductory text, explanations, markdown formatting, or anything else before or after the JSON array.

            Summary:
            ---
            %s
            ---

            JSON Array Output (3-5 items):
            """,
            summaryText
        );

        log.info("Attempting to extract keywords/topics from summary (length: {} chars) using model {}", summaryText.length(), MODEL_NAME);
        String apiResponseJson = callGeminiAPIBase(prompt, MAX_OUTPUT_TOKENS_KEYWORDS, "Keyword Extraction", API_URL);

        try {
            JsonNode responseNode = objectMapper.readTree(apiResponseJson);
            if (responseNode.has("error")) {
                log.error("Gemini API call for keywords failed using model {}: {} - {}",
                          MODEL_NAME,
                          responseNode.path("error").asText(),
                          responseNode.path("details").asText());
                return Collections.emptyList();
            }

            if (responseNode.has("text")) {
                String rawGeminiOutput = responseNode.path("text").asText();
                log.debug("Raw Gemini output for keywords from model {}: {}", MODEL_NAME, rawGeminiOutput);

                 try {
                     String cleanedOutput = rawGeminiOutput.trim();
                     if (cleanedOutput.startsWith("```json")) {
                         cleanedOutput = cleanedOutput.substring(7).trim();
                     } else if (cleanedOutput.startsWith("```")) {
                         cleanedOutput = cleanedOutput.substring(3).trim();
                     }
                     if (cleanedOutput.endsWith("```")) {
                         cleanedOutput = cleanedOutput.substring(0, cleanedOutput.length() - 3).trim();
                     }

                     if (!cleanedOutput.startsWith("[") || !cleanedOutput.endsWith("]")) {
                         log.warn("Gemini output for keywords from model {} is not a valid JSON array string: {}", MODEL_NAME, cleanedOutput);
                         return parseKeywordsFallback(rawGeminiOutput);
                     }

                     List<String> keywords = objectMapper.readValue(cleanedOutput, new TypeReference<List<String>>() {});
                     log.info("Successfully extracted {} keywords/topics via JSON parsing using model {}.", keywords.size(), MODEL_NAME);
                     keywords.replaceAll(String::trim);
                     keywords.removeIf(String::isBlank);
                     return keywords;

                 } catch (JsonProcessingException jsonEx) {
                     log.error("Failed to parse Gemini keyword response JSON array from model {}: {}. Raw output: {}", MODEL_NAME, jsonEx.getMessage(), rawGeminiOutput);
                     return parseKeywordsFallback(rawGeminiOutput);
                 }
            } else {
                 log.error("Unexpected JSON structure returned from callGeminiAPIBase (keywords) using model {}: {}", MODEL_NAME, apiResponseJson);
                 return Collections.emptyList();
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse the outer JSON response from callGeminiAPIBase (keywords) using model {}: {}", MODEL_NAME, e.getMessage(), e);
            return Collections.emptyList();
        } catch (Exception e) {
             log.error("Unexpected error processing keyword extraction result using model {}: {}", MODEL_NAME, e.getMessage(), e);
             return Collections.emptyList();
        }
    }

    private List<String> parseKeywordsFallback(String rawOutput) {
        log.warn("Attempting fallback keyword parsing for: {}", rawOutput);
        List<String> fallbackKeywords = new ArrayList<>();
        String cleaned = rawOutput.replace("\"", "").replace("[", "").replace("]", "").trim();

        String[] parts = cleaned.split(",");
        if (parts.length > 1) {
             for (String part : parts) {
                 String trimmedPart = part.trim();
                 if (!trimmedPart.isBlank()) {
                     fallbackKeywords.add(trimmedPart);
                 }
             }
        } else {
            parts = cleaned.split("\\r?\\n");
            if (parts.length > 1) {
                for (String part : parts) {
                     String trimmedPart = part.trim();
                     if (trimmedPart.startsWith("-") || trimmedPart.startsWith("*")) {
                         trimmedPart = trimmedPart.substring(1).trim();
                     }
                     if (!trimmedPart.isBlank()) {
                         fallbackKeywords.add(trimmedPart);
                     }
                 }
            } else if (!cleaned.isBlank()) {
                 fallbackKeywords.add(cleaned);
            }
        }

        if (!fallbackKeywords.isEmpty()) {
            log.info("Fallback parsing extracted {} keywords/topics.", fallbackKeywords.size());
        } else {
            log.warn("Fallback keyword parsing failed to extract any keywords from: {}", rawOutput);
        }
        return fallbackKeywords;
    }


    private String callGeminiAPIBase(String inputText, int maxOutputTokens, String contextLog, String apiUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", inputText)))),
            "generationConfig", Map.of(
                 "temperature", 0.2,
                 "topP", 0.8,
                 "topK", 40,
                 "maxOutputTokens", maxOutputTokens
            )
        );

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        String url = apiUrl + "?key=" + apiKey;

        log.debug("Calling Gemini API ({}) using model {} at URL: {}", contextLog, MODEL_NAME, apiUrl);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, String.class
                );
                log.info("Gemini API ({}) call successful on attempt {} using model {}", contextLog, attempt, MODEL_NAME);
                String responseText = processGeminiResponse(response.getBody());
                return createSuccessResponse(responseText);

            } catch (HttpServerErrorException | ResourceAccessException e) {
                log.warn("Gemini API ({}) call failed on attempt {}/{} using model {}. Error: {}. Retrying...",
                         contextLog, attempt, MAX_RETRIES, MODEL_NAME, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    log.error("Gemini API ({}) call failed after {} attempts using model {}.", contextLog, MAX_RETRIES, MODEL_NAME, e);
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
                 log.error("Gemini API ({}) client error using model {}: {} - {}", contextLog, MODEL_NAME, e.getStatusCode(), e.getResponseBodyAsString(), e);
                 return createErrorResponse("API Client Error: " + e.getStatusCode(), e.getResponseBodyAsString());
            } catch (GeminiApiException e) {
                 log.error("Error processing Gemini API ({}) response using model {}: {}", contextLog, MODEL_NAME, e.getMessage());
                 return createErrorResponse(e.getErrorTitle(), e.getDetails());
            } catch (Exception e) {
                log.error("Unexpected error during Gemini API ({}) call on attempt {} using model {}", contextLog, attempt, MODEL_NAME, e);
                if (attempt < MAX_RETRIES) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("API call interrupted during retry wait after unexpected error.", ie);
                        return createErrorResponse("API call interrupted during retry wait", ie.getMessage());
                    }
                } else {
                     log.error("Gemini API ({}) call failed after {} attempts due to unexpected error.", contextLog, MAX_RETRIES, e);
                     return createErrorResponse("Unexpected API Request Failed", e.getMessage());
                }
            }
        }
        return createErrorResponse("API Request Failed", "Max retries reached but no definitive result or unhandled exception occurred.");
    }


    private String processGeminiResponse(String rawResponse) throws GeminiApiException {
        try {
            log.debug("Raw Gemini Response: {}", rawResponse);
            JsonNode jsonNode = objectMapper.readTree(rawResponse);

            if (jsonNode.has("error")) {
                JsonNode errorNode = jsonNode.path("error");
                String errorMessage = errorNode.path("message").asText("Unknown error message");
                String errorStatus = errorNode.path("status").asText("UNKNOWN_STATUS");
                int errorCode = errorNode.path("code").asInt(-1);
                log.error("Gemini API returned an error object. Code: {}, Status: {}, Message: {}", errorCode, errorStatus, errorMessage);
                throw new GeminiApiException("Gemini API Error: " + errorStatus, "Code: " + errorCode + ", Message: " + errorMessage + ". Raw: " + rawResponse);
            }

            if (jsonNode.has("promptFeedback")) {
                JsonNode feedback = jsonNode.path("promptFeedback");
                if (feedback.has("blockReason")) {
                     String reason = feedback.path("blockReason").asText("Unknown");
                     String details = "Reason: " + reason;
                     if (feedback.has("safetyRatings")) {
                         details += ". SafetyRatings: " + feedback.path("safetyRatings").toString();
                     }
                     log.warn("Gemini API request blocked. Reason: {}. Feedback: {}", reason, feedback.toString());
                     throw new GeminiApiException("Gemini API Error: Content Blocked", details + ". Raw: " + rawResponse);
                }
                 if (feedback.has("safetyRatings")) {
                    log.info("Gemini Response Prompt Feedback Safety Ratings: {}", feedback.path("safetyRatings").toString());
                 } else {
                    log.warn("Gemini Response Prompt Feedback present but no blockReason or safetyRatings: {}", feedback.toString());
                 }
            }

            if (!jsonNode.has("candidates") || !jsonNode.path("candidates").isArray() || jsonNode.path("candidates").isEmpty()) {
                 log.warn("No 'candidates' array found or is empty in Gemini response.");
                 String details = "Missing or empty 'candidates' array. Raw: " + rawResponse;
                 if (jsonNode.has("promptFeedback")) {
                    details += " Feedback: " + jsonNode.path("promptFeedback").toString();
                 }
                 if (jsonNode.has("error")) {
                     details += " Top-level error present: " + jsonNode.path("error").toString();
                 }
                 throw new GeminiApiException("Invalid response structure from Gemini API", details);
            }

            JsonNode firstCandidate = jsonNode.path("candidates").get(0);

             if (firstCandidate.has("finishReason")) {
                 String finishReason = firstCandidate.path("finishReason").asText();
                 if (!"STOP".equalsIgnoreCase(finishReason) && !"MAX_TOKENS".equalsIgnoreCase(finishReason)) {
                     log.warn("Gemini response candidate finished with non-standard reason: {}", finishReason);
                     if ("SAFETY".equalsIgnoreCase(finishReason) || "RECITATION".equalsIgnoreCase(finishReason) || "OTHER".equalsIgnoreCase(finishReason)) {
                         String finishMessage = firstCandidate.path("finishMessage").asText("No finish message provided.");
                         throw new GeminiApiException("Gemini API Error: Candidate Finish Reason " + finishReason, finishMessage + ". Raw: " + rawResponse);
                     }
                 }
                 if ("MAX_TOKENS".equalsIgnoreCase(finishReason)) {
                     log.warn("Gemini response may be truncated due to MAX_TOKENS limit.");
                 }
             } else {
                 log.warn("Gemini response candidate is missing a 'finishReason'.");
             }

            if (!firstCandidate.has("content") || !firstCandidate.path("content").has("parts") || !firstCandidate.path("content").path("parts").isArray() || firstCandidate.path("content").path("parts").isEmpty()) {
                 log.warn("Missing 'content' or 'parts' in the first candidate.");
                 String reason = firstCandidate.path("finishReason").asText("UNKNOWN");
                 if (!"STOP".equalsIgnoreCase(reason) && !"MAX_TOKENS".equalsIgnoreCase(reason)) {
                    throw new GeminiApiException("Invalid response structure", "Missing 'content' or 'parts' in candidate. Finish Reason: " + reason + ". Raw: " + rawResponse);
                 } else {
                    log.warn("Candidate content/parts missing, but finish reason was {}. Returning empty text.", reason);
                    return "";
                 }
            }

            StringBuilder responseTextBuilder = new StringBuilder();
            for (JsonNode part : firstCandidate.path("content").path("parts")) {
                 if (part.has("text")) {
                     responseTextBuilder.append(part.path("text").asText());
                 } else {
                     log.warn("Part is missing 'text' field within the candidate's content. Part: {}", part.toString());
                 }
            }

            String responseText = responseTextBuilder.toString();

            if (responseText.isEmpty()) {
                String reason = firstCandidate.path("finishReason").asText("UNKNOWN");
                 log.warn("Extracted text content from Gemini response is empty. Finish Reason: {}", reason);
                 if (!"STOP".equalsIgnoreCase(reason) && !"MAX_TOKENS".equalsIgnoreCase(reason)) {
                      throw new GeminiApiException("Empty Content Received", "The response contained no text content despite parts being present. Finish Reason: " + reason + ". Raw: " + rawResponse);
                 }
            }

            log.info("Successfully extracted text content (length: {}) from Gemini response.", responseText.length());
            return responseText;

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Gemini API JSON response", e);
            throw new GeminiApiException("JSON Parsing Error", e.getMessage() + ". Raw: " + rawResponse);
        } catch (Exception e) {
             if (e instanceof GeminiApiException) {
                 throw (GeminiApiException) e;
             }
             log.error("Unexpected error processing Gemini response", e);
             throw new GeminiApiException("Response Processing Error", e.getMessage() + ". Raw: " + rawResponse);
        }
    }

    private String createSuccessResponse(String textContent) {
         ObjectNode successResponse = objectMapper.createObjectNode();
         successResponse.put("text", textContent);
         try {
             return objectMapper.writeValueAsString(successResponse);
         } catch (JsonProcessingException ex) {
             log.error("Failed to serialize success response JSON", ex);
             return "{\"error\": \"Internal Server Error\", \"details\": \"Failed to serialize success response. Original content was present but could not be formatted.\"}";
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
            return "{\"error\": \"Internal Server Error\", \"details\": \"Failed to serialize error response details.\"}";
        }
    }

    private String getAudioMimeType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            log.warn("Filename is null or empty, defaulting MIME type to audio/mpeg");
            return "audio/mpeg";
        }
        String lowercaseFileName = fileName.toLowerCase();
        if (lowercaseFileName.endsWith(".flac")) return "audio/flac";
        if (lowercaseFileName.endsWith(".wav")) return "audio/wav";
        if (lowercaseFileName.endsWith(".mp3")) return "audio/mp3";
        if (lowercaseFileName.endsWith(".ogg")) return "audio/ogg";
        if (lowercaseFileName.endsWith(".opus")) return "audio/opus";
        if (lowercaseFileName.endsWith(".m4a")) return "audio/m4a";
        if (lowercaseFileName.endsWith(".mp4")) return "audio/mp4";
        if (lowercaseFileName.endsWith(".aac")) return "audio/aac";
        if (lowercaseFileName.endsWith(".aiff") || lowercaseFileName.endsWith(".aif")) return "audio/aiff";
        if (lowercaseFileName.endsWith(".amr")) return "audio/amr";

        log.warn("Unknown or potentially unsupported audio file extension in '{}', defaulting MIME type to audio/mpeg. Verify Gemini API support for this format.", fileName);
        return "audio/mpeg";
    }

    static class GeminiApiException extends Exception {
        private final String errorTitle;
        private final String details;

        public GeminiApiException(String errorTitle, String details) {
            super(errorTitle + ": " + details);
            this.errorTitle = errorTitle;
            this.details = details;
        }

        public String getErrorTitle() {
            return errorTitle;
        }

        public String getDetails() {
            return details;
        }
    }
}
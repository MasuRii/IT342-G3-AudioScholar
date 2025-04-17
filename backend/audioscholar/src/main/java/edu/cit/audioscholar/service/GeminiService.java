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

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-pro:generateContent";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final int MAX_OUTPUT_TOKENS_GENERAL = 4096;
    private static final int MAX_OUTPUT_TOKENS_KEYWORDS = 512;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String callGeminiAPI(String inputText) {
        return callGeminiAPIBase(inputText, MAX_OUTPUT_TOKENS_GENERAL, "Text");
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
            "temperature", 0.1,
            "topP", 0.8,
            "topK", 40,
            "maxOutputTokens", MAX_OUTPUT_TOKENS_GENERAL
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
                String responseText = processGeminiResponse(response.getBody());
                return createSuccessResponse(responseText);

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
            } catch (GeminiApiException e) {
                 log.error("Error processing Gemini API (Audio) response: {}", e.getMessage());
                 return createErrorResponse(e.getErrorTitle(), e.getDetails());
            } catch (Exception e) {
                log.error("Unexpected error during Gemini API (Audio) call on attempt {}", attempt, e);
                if (attempt == MAX_RETRIES) {
                     return createErrorResponse("Unexpected API Request Failed", e.getMessage());
                }
            }
        }
        return createErrorResponse("API Request Failed", "Max retries reached but no definitive result or unhandled exception occurred.");
    }

    public List<String> extractKeywordsAndTopics(String summaryText) {
        String prompt = String.format(
            """
            Analyze the following lecture summary and extract the main keywords and key topics discussed.
            Return the results ONLY as a valid JSON array of strings (e.g., ["keyword1", "topic 2", "key concept 3"]).
            Do not include any introductory text, explanations, markdown formatting, or anything else before or after the JSON array.

            Summary:
            ---
            %s
            ---

            JSON Array Output:
            """,
            summaryText
        );

        log.info("Attempting to extract keywords/topics from summary (length: {} chars)", summaryText.length());
        String apiResponseJson = callGeminiAPIBase(prompt, MAX_OUTPUT_TOKENS_KEYWORDS, "Keyword Extraction");

        try {
            JsonNode responseNode = objectMapper.readTree(apiResponseJson);
            if (responseNode.has("error")) {
                log.error("Gemini API call for keywords failed: {} - {}",
                          responseNode.path("error").asText(),
                          responseNode.path("details").asText());
                return Collections.emptyList();
            }

            if (responseNode.has("text")) {
                String rawGeminiOutput = responseNode.path("text").asText();
                log.debug("Raw Gemini output for keywords: {}", rawGeminiOutput);

                 try {
                     String cleanedOutput = rawGeminiOutput.trim();
                     if (cleanedOutput.startsWith("```json")) {
                         cleanedOutput = cleanedOutput.substring(7);
                     }
                     if (cleanedOutput.startsWith("```")) {
                         cleanedOutput = cleanedOutput.substring(3);
                     }
                     if (cleanedOutput.endsWith("```")) {
                         cleanedOutput = cleanedOutput.substring(0, cleanedOutput.length() - 3);
                     }
                     cleanedOutput = cleanedOutput.trim();

                     if (!cleanedOutput.startsWith("[") || !cleanedOutput.endsWith("]")) {
                         log.error("Gemini output for keywords is not a valid JSON array string: {}", cleanedOutput);
                         return parseKeywordsFallback(rawGeminiOutput);
                     }

                     List<String> keywords = objectMapper.readValue(cleanedOutput, new TypeReference<List<String>>() {});
                     log.info("Successfully extracted {} keywords/topics via JSON parsing.", keywords.size());
                     keywords.replaceAll(String::trim);
                     keywords.removeIf(String::isBlank);
                     return keywords;
                 } catch (JsonProcessingException jsonEx) {
                     log.error("Failed to parse Gemini keyword response JSON array: {}. Raw output: {}", jsonEx.getMessage(), rawGeminiOutput);
                     return parseKeywordsFallback(rawGeminiOutput);
                 }
            } else {
                 log.error("Unexpected JSON structure in Gemini API response for keywords: {}", apiResponseJson);
                 return Collections.emptyList();
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse the outer JSON response from callGeminiAPIBase: {}", e.getMessage(), e);
            return Collections.emptyList();
        } catch (Exception e) {
             log.error("Unexpected error processing keyword extraction result: {}", e.getMessage(), e);
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
            log.warn("Fallback keyword parsing failed to extract any keywords.");
        }
        return fallbackKeywords;
    }



    private String callGeminiAPIBase(String inputText, int maxOutputTokens, String contextLog) {
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
        String url = API_URL + "?key=" + apiKey;

        log.debug("Calling Gemini API ({}) at URL: {}", contextLog, API_URL);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, String.class
                );
                log.info("Gemini API ({}) call successful on attempt {}", contextLog, attempt);
                String responseText = processGeminiResponse(response.getBody());
                return createSuccessResponse(responseText);

            } catch (HttpServerErrorException | ResourceAccessException e) {
                log.warn("Gemini API ({}) call failed on attempt {}/{}. Error: {}. Retrying...",
                         contextLog, attempt, MAX_RETRIES, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    log.error("Gemini API ({}) call failed after {} attempts.", contextLog, MAX_RETRIES, e);
                    return createErrorResponse("API Request Failed after retries", e.getMessage());
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return createErrorResponse("API call interrupted during retry wait", ie.getMessage());
                }
            } catch (HttpClientErrorException e) {
                 log.error("Gemini API ({}) client error: {} - {}", contextLog, e.getStatusCode(), e.getResponseBodyAsString(), e);
                 return createErrorResponse("API Client Error: " + e.getStatusCode(), e.getResponseBodyAsString());
            } catch (GeminiApiException e) {
                 log.error("Error processing Gemini API ({}) response: {}", contextLog, e.getMessage());
                 return createErrorResponse(e.getErrorTitle(), e.getDetails());
            } catch (Exception e) {
                log.error("Unexpected error during Gemini API ({}) call", contextLog, e);
                if (attempt == MAX_RETRIES) {
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

            if (jsonNode.has("promptFeedback")) {
                JsonNode feedback = jsonNode.path("promptFeedback");
                if (feedback.has("blockReason")) {
                     String reason = feedback.path("blockReason").asText("Unknown");
                     log.warn("Gemini API request blocked. Reason: {}. Feedback: {}", reason, feedback.toString());
                     throw new GeminiApiException("Gemini API Error: Content Blocked", "Reason: " + reason + ". Raw: " + rawResponse);
                }
                 log.warn("Gemini Response Prompt Feedback present: {}", feedback.toString());
            }

            if (!jsonNode.has("candidates") || !jsonNode.path("candidates").isArray() || jsonNode.path("candidates").isEmpty()) {
                 log.warn("No 'candidates' array found or is empty in Gemini response.");
                 String details = "Missing or empty 'candidates' array. Raw: " + rawResponse;
                 if (jsonNode.has("promptFeedback")) {
                    details += " Feedback: " + jsonNode.path("promptFeedback").toString();
                 }
                 throw new GeminiApiException("Invalid response structure from Gemini API", details);
            }

            JsonNode firstCandidate = jsonNode.path("candidates").get(0);

             if (firstCandidate.has("finishReason")) {
                 String finishReason = firstCandidate.path("finishReason").asText();
                 if (!"STOP".equalsIgnoreCase(finishReason) && !"MAX_TOKENS".equalsIgnoreCase(finishReason)) {
                     log.warn("Gemini response candidate finished with reason: {}", finishReason);
                 }
                 if ("MAX_TOKENS".equalsIgnoreCase(finishReason)) {
                     log.warn("Gemini response may be truncated due to MAX_TOKENS limit.");
                 }
             }


            if (!firstCandidate.has("content") || !firstCandidate.path("content").has("parts") || !firstCandidate.path("content").path("parts").isArray() || firstCandidate.path("content").path("parts").isEmpty()) {
                 log.warn("Missing 'content' or 'parts' in the first candidate.");
                 throw new GeminiApiException("Invalid response structure", "Missing 'content' or 'parts' in candidate. Raw: " + rawResponse);
            }

            JsonNode firstPart = firstCandidate.path("content").path("parts").get(0);
            if (!firstPart.has("text")) {
                 log.warn("Missing 'text' field in the first part of the candidate's content.");
                  if (jsonNode.has("promptFeedback") && jsonNode.path("promptFeedback").has("blockReason")) {
                       String reason = jsonNode.path("promptFeedback").path("blockReason").asText("Unknown");
                       throw new GeminiApiException("Gemini API Error: Content Blocked (detected in parts)", "Reason: " + reason + ". Raw: " + rawResponse);
                  }
                 throw new GeminiApiException("Invalid response structure", "Missing 'text' in content part. Raw: " + rawResponse);
            }

            String responseText = firstPart.path("text").asText();
            log.info("Successfully extracted text content from Gemini response.");
            return responseText;

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Gemini API JSON response", e);
            throw new GeminiApiException("JSON Parsing Error", e.getMessage());
        } catch (Exception e) {
             log.error("Unexpected error processing Gemini response", e);
             if (e instanceof GeminiApiException) {
                 throw (GeminiApiException) e;
             }
             throw new GeminiApiException("Response Processing Error", e.getMessage());
        }
    }

    private String createSuccessResponse(String textContent) {
         ObjectNode successResponse = objectMapper.createObjectNode();
         successResponse.put("text", textContent);
         try {
             return objectMapper.writeValueAsString(successResponse);
         } catch (JsonProcessingException ex) {
             log.error("Failed to serialize success response JSON", ex);
             return "{\"error\": \"Failed to serialize success response\", \"details\": \"Original content was present but could not be formatted.\"}";
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
            return "{\"error\": \"Failed to serialize error response\", \"details\": \"" + escapeJson(errorTitle) + "\"}";
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
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
        if (lowercaseFileName.endsWith(".m4a") || lowercaseFileName.endsWith(".mp4")) return "audio/mp4";
        if (lowercaseFileName.endsWith(".aac")) return "audio/aac";
        if (lowercaseFileName.endsWith(".aiff") || lowercaseFileName.endsWith(".aif")) return "audio/aiff";
        if (lowercaseFileName.endsWith(".amr")) return "audio/amr";
        if (lowercaseFileName.endsWith(".webm")) return "audio/webm";

        log.warn("Unknown audio file extension in '{}', defaulting MIME type to audio/mpeg", fileName);
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
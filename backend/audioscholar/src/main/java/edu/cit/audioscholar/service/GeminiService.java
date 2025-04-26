package edu.cit.audioscholar.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        private static final String TRANSCRIPTION_MODEL_NAME = "gemini-2.0-flash";
        private static final String SUMMARIZATION_MODEL_NAME = "gemini-2.5-pro-exp-03-25";

        private static final String TRANSCRIPTION_API_URL =
                        API_BASE_URL + TRANSCRIPTION_MODEL_NAME + ":generateContent";
        private static final String SUMMARIZATION_API_URL =
                        API_BASE_URL + SUMMARIZATION_MODEL_NAME + ":generateContent";

        private static final int MAX_RETRIES = 3;
        private static final long RETRY_DELAY_MS = 1000;

        private static final int MAX_OUTPUT_TOKENS_TRANSCRIPTION = 8192;
        private static final int MAX_OUTPUT_TOKENS_SUMMARIZATION = 65536;

        private final RestTemplate restTemplate = new RestTemplate();
        private final ObjectMapper objectMapper = new ObjectMapper();

        private static final Map<String, Object> SUMMARY_RESPONSE_SCHEMA = createSummarySchema();

        private static Map<String, Object> createSummarySchema() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "OBJECT");
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("summaryText", Map.of("type", "STRING", "description",
                                "Clear, well-structured summary in Markdown format."));
                properties.put("keyPoints",
                                Map.of("type", "ARRAY", "items", Map.of("type", "STRING"),
                                                "description",
                                                "List of distinct key points or action items."));
                properties.put("topics", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"),
                                "description", "List of main topics or keywords (3-5 items)."));
                Map<String, Object> glossaryItemProperties = new LinkedHashMap<>();
                glossaryItemProperties.put("term", Map.of("type", "STRING", "description",
                                "The specific term identified from the audio transcript."));
                glossaryItemProperties.put("definition", Map.of("type", "STRING", "description",
                                "A concise definition of the term in the context of the audio transcript."));
                Map<String, Object> glossaryItemSchema = new LinkedHashMap<>();
                glossaryItemSchema.put("type", "OBJECT");
                glossaryItemSchema.put("properties", glossaryItemProperties);
                glossaryItemSchema.put("required", List.of("term", "definition"));
                properties.put("glossary", Map.of("type", "ARRAY", "items", glossaryItemSchema,
                                "description",
                                "List of key terms/concepts and their definitions identified from the audio transcript."));
                schema.put("properties", properties);
                schema.put("required", List.of("summaryText", "keyPoints", "topics", "glossary"));
                return Collections.unmodifiableMap(schema);
        }


        public String callGeminiTranscriptionAPI(Path audioFilePath, String fileName)
                        throws IOException {
                if (audioFilePath == null || !Files.exists(audioFilePath)) {
                        log.error("Audio file path is null or does not exist: {}", audioFilePath);
                        throw new IOException("Audio file path is null or does not exist: "
                                        + audioFilePath);
                }

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                String promptText =
                                "Transcribe the following audio content accurately. Output only the spoken text.";
                Map<String, Object> textPart = Map.of("text", promptText);

                byte[] audioBytes;
                try {
                        log.debug("Reading audio file bytes from: {}",
                                        audioFilePath.toAbsolutePath());
                        audioBytes = Files.readAllBytes(audioFilePath);
                        log.debug("Read {} bytes from audio file.", audioBytes.length);
                } catch (IOException e) {
                        log.error("Failed to read audio file: {}", audioFilePath.toAbsolutePath(),
                                        e);
                        throw e;
                } catch (OutOfMemoryError oom) {
                        log.error("OutOfMemoryError reading audio file ({} bytes) into byte array: {}",
                                        (Files.exists(audioFilePath) ? Files.size(audioFilePath)
                                                        : "unknown size"),
                                        audioFilePath.toAbsolutePath(), oom);
                        return createErrorResponse("Memory Error Reading File",
                                        "File too large to read into memory for Base64 encoding.");
                }

                String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

                String mimeType = getAudioMimeType(fileName);
                Map<String, Object> audioPart = Map.of("inline_data",
                                Map.of("mime_type", mimeType, "data", base64Audio));

                List<Object> parts = List.of(textPart, audioPart);
                Map<String, Object> content = Map.of("parts", parts);
                List<Object> contents = List.of(content);

                Map<String, Object> generationConfig = new HashMap<>();
                generationConfig.put("temperature", 0.2);
                generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS_TRANSCRIPTION);

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("contents", contents);
                requestBody.put("generationConfig", generationConfig);

                HttpEntity<Map<String, Object>> requestEntity =
                                new HttpEntity<>(requestBody, headers);

                String url = TRANSCRIPTION_API_URL + "?key=" + apiKey;
                log.info("Calling Gemini Transcription API (Model: {}) using file: {}",
                                TRANSCRIPTION_MODEL_NAME, audioFilePath.getFileName());

                for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                        try {
                                ResponseEntity<String> response = restTemplate.exchange(url,
                                                HttpMethod.POST, requestEntity, String.class);

                                log.info("Gemini Transcription API call successful on attempt {} using model {}. Status: {}",
                                                attempt, TRANSCRIPTION_MODEL_NAME,
                                                response.getStatusCode());

                                String responseBody = response.getBody();
                                if (responseBody == null || responseBody.isBlank()) {
                                        log.warn("Gemini Transcription API returned successful status ({}) but empty body.",
                                                        response.getStatusCode());
                                        return createErrorResponse("Empty Response",
                                                        "API returned success status but no content.");
                                }

                                String extractedText =
                                                extractTextFromStandardResponse(responseBody);

                                if (extractedText.isBlank()
                                                && !responseBody.contains("promptFeedback")
                                                && !responseBody.contains("finishReason")) {
                                        log.warn("Could not extract text content from Gemini transcription response structure. Body: {}",
                                                        responseBody);
                                        return createErrorResponse("Extraction Error",
                                                        "Failed to extract transcript text from API response structure.");
                                }

                                log.info("Successfully extracted transcript text (length: {}).",
                                                extractedText.length());
                                return extractedText;

                        } catch (HttpServerErrorException | ResourceAccessException e) {
                                log.warn("Gemini Transcription API call failed on attempt {}/{} with retryable error: {}. Retrying...",
                                                attempt, MAX_RETRIES, e.getMessage());
                                if (attempt == MAX_RETRIES) {
                                        log.error("Gemini Transcription API call failed after {} attempts.",
                                                        MAX_RETRIES, e);
                                        return createErrorResponse(
                                                        "API Request Failed (Server/Network)",
                                                        e.getMessage());
                                }
                                try {
                                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempt);
                                } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        log.error("API call interrupted during retry wait.", ie);
                                        return createErrorResponse("API call interrupted",
                                                        ie.getMessage());
                                }
                        } catch (HttpClientErrorException e) {
                                log.error("Gemini Transcription API client error: {} - {}",
                                                e.getStatusCode(), e.getResponseBodyAsString(), e);
                                String details = parseErrorDetails(e);
                                return createErrorResponse("API Client Error: " + e.getStatusCode(),
                                                details);
                        } catch (RestClientResponseException e) {
                                log.error("Gemini Transcription API REST client error: Status {}, Body: {}",
                                                e.getStatusCode(), e.getResponseBodyAsString(), e);
                                return createErrorResponse("API Request Failed (REST Client)",
                                                e.getMessage());
                        } catch (RuntimeException e) {
                                log.error("Runtime error during Gemini Transcription API processing on attempt {}: {}",
                                                attempt, e.getMessage(), e);
                                return createErrorResponse("Transcription API Processing Error",
                                                e.getMessage());
                        } catch (Exception e) {
                                log.error("Unexpected error during Gemini Transcription API call or processing on attempt {}",
                                                attempt, e);
                                return createErrorResponse(
                                                "Transcription API Request/Processing Failed",
                                                e.getMessage());
                        }
                }

                return createErrorResponse("API Request Failed",
                                "Max retries reached or unexpected flow.");
        }


        public String callGeminiSummarizationAPI(String promptText, String transcriptText) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> promptPart = Map.of("text", promptText);
                Map<String, Object> transcriptPart = Map.of("text", transcriptText);
                Map<String, Object> promptContent =
                                Map.of("role", "user", "parts", List.of(promptPart));
                Map<String, Object> transcriptContent =
                                Map.of("role", "user", "parts", List.of(transcriptPart));

                List<Object> contents = List.of(promptContent, transcriptContent);

                Map<String, Object> generationConfig = new HashMap<>();
                generationConfig.put("temperature", 0.4);
                generationConfig.put("topP", 0.95);
                generationConfig.put("topK", 40);
                generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS_SUMMARIZATION);
                generationConfig.put("response_mime_type", "application/json");
                generationConfig.put("response_schema", SUMMARY_RESPONSE_SCHEMA);

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("contents", contents);
                requestBody.put("generationConfig", generationConfig);

                HttpEntity<Map<String, Object>> requestEntity =
                                new HttpEntity<>(requestBody, headers);

                String url = SUMMARIZATION_API_URL + "?key=" + apiKey;
                log.info("Calling Gemini Summarization API (Model: {}, JSON Schema Mode)",
                                SUMMARIZATION_MODEL_NAME);
                log.trace("Summarization prompt text length: {}", promptText.length());
                log.trace("Summarization transcript text length: {}", transcriptText.length());

                for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                        try {
                                ResponseEntity<String> response = restTemplate.exchange(url,
                                                HttpMethod.POST, requestEntity, String.class);

                                log.info("Gemini Summarization API call successful on attempt {} using model {}. Status: {}",
                                                attempt, SUMMARIZATION_MODEL_NAME,
                                                response.getStatusCode());

                                String responseBody = response.getBody();
                                if (responseBody == null || responseBody.isBlank()) {
                                        log.warn("Gemini Summarization API returned successful status ({}) but empty body.",
                                                        response.getStatusCode());
                                        return createErrorResponse("Empty Response",
                                                        "API returned success status but no content.");
                                }

                                String extractedJsonText =
                                                extractTextFromStandardResponse(responseBody);

                                if (extractedJsonText.isBlank()
                                                && !responseBody.contains("promptFeedback")
                                                && !responseBody.contains("finishReason")) {
                                        log.warn("Could not extract JSON content from Gemini summarization response structure. Body: {}",
                                                        responseBody);
                                        return createErrorResponse("Extraction Error",
                                                        "Failed to extract JSON summary from API response structure.");
                                }

                                log.debug("Successfully extracted JSON text from standard response structure (length: {}).",
                                                extractedJsonText.length());
                                return extractedJsonText;

                        } catch (HttpServerErrorException | ResourceAccessException e) {
                                log.warn("Gemini Summarization API call failed on attempt {}/{} with retryable error: {}. Retrying...",
                                                attempt, MAX_RETRIES, e.getMessage());
                                if (attempt == MAX_RETRIES) {
                                        log.error("Gemini Summarization API call failed after {} attempts.",
                                                        MAX_RETRIES, e);
                                        return createErrorResponse(
                                                        "API Request Failed (Server/Network)",
                                                        e.getMessage());
                                }
                                try {
                                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempt);
                                } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        log.error("API call interrupted during retry wait.", ie);
                                        return createErrorResponse("API call interrupted",
                                                        ie.getMessage());
                                }
                        } catch (HttpClientErrorException e) {
                                log.error("Gemini Summarization API client error: {} - {}",
                                                e.getStatusCode(), e.getResponseBodyAsString(), e);
                                String details = parseErrorDetails(e);
                                return createErrorResponse("API Client Error: " + e.getStatusCode(),
                                                details);
                        } catch (RestClientResponseException e) {
                                log.error("Gemini Summarization API REST client error: Status {}, Body: {}",
                                                e.getStatusCode(), e.getResponseBodyAsString(), e);
                                return createErrorResponse("API Request Failed (REST Client)",
                                                e.getMessage());
                        } catch (RuntimeException e) {
                                log.error("Runtime error during Gemini Summarization API processing on attempt {}: {}",
                                                attempt, e.getMessage(), e);
                                return createErrorResponse("Summarization API Processing Error",
                                                e.getMessage());
                        } catch (Exception e) {
                                log.error("Error during Gemini Summarization API call or processing on attempt {}",
                                                attempt, e);
                                return createErrorResponse(
                                                "Summarization API Request/Processing Failed",
                                                e.getMessage());
                        }
                }
                return createErrorResponse("API Request Failed",
                                "Max retries reached or unexpected flow.");
        }



        private String extractTextFromStandardResponse(String rawResponse)
                        throws JsonProcessingException, RuntimeException {
                JsonNode jsonNode = objectMapper.readTree(rawResponse);

                if (jsonNode.has("error")) {
                        String errorMessage = jsonNode.path("error").path("message")
                                        .asText("Unknown API error");
                        log.error("Gemini API returned an error object: {}", rawResponse);
                        throw new RuntimeException("Gemini API Error: " + errorMessage);
                }

                if (jsonNode.has("promptFeedback")
                                && jsonNode.path("promptFeedback").has("blockReason")) {
                        String reason = jsonNode.path("promptFeedback").path("blockReason")
                                        .asText("Unknown");
                        String safetyRatings = jsonNode.path("promptFeedback").path("safetyRatings")
                                        .toString();
                        log.warn("Gemini API request blocked due to prompt feedback. Reason: {}. Safety Ratings: {}",
                                        reason, safetyRatings);
                        throw new RuntimeException(
                                        "Gemini API Error: Content Blocked (Prompt Feedback) - "
                                                        + reason);
                }

                if (jsonNode.has("candidates") && jsonNode.path("candidates").isArray()
                                && !jsonNode.path("candidates").isEmpty()) {
                        JsonNode firstCandidate = jsonNode.path("candidates").get(0);

                        if (firstCandidate.has("finishReason") && !"STOP"
                                        .equals(firstCandidate.path("finishReason").asText())) {
                                String reason = firstCandidate.path("finishReason").asText();
                                log.warn("Gemini generation finished with reason: {}. Output might be incomplete or blocked.",
                                                reason);
                                if ("SAFETY".equals(reason) || "RECITATION".equals(reason)
                                                || "OTHER".equals(reason)) {
                                        String safetyRatings = firstCandidate.path("safetyRatings")
                                                        .toString();
                                        log.error("Gemini API generation blocked. Finish Reason: {}. Safety Ratings: {}",
                                                        reason, safetyRatings);
                                        throw new RuntimeException(
                                                        "Gemini API Error: Content Blocked (Finish Reason) - "
                                                                        + reason);
                                }
                        }

                        if (firstCandidate.has("content")
                                        && firstCandidate.path("content").has("parts")
                                        && firstCandidate.path("content").path("parts").isArray()
                                        && !firstCandidate.path("content").path("parts")
                                                        .isEmpty()) {
                                JsonNode firstPart =
                                                firstCandidate.path("content").path("parts").get(0);
                                if (firstPart.has("text")) {
                                        return firstPart.path("text").asText();
                                } else {
                                        log.warn("First part of Gemini response candidate does not contain 'text' field. Candidate: {}",
                                                        firstCandidate.toString());
                                        return "";
                                }
                        } else {
                                log.warn("First candidate in Gemini response does not contain expected content/parts structure. Candidate: {}",
                                                firstCandidate.toString());
                                return "";
                        }
                }

                log.warn("Could not extract text from Gemini standard response structure (no valid candidates found): {}",
                                rawResponse);
                return "";
        }

        private String parseErrorDetails(HttpClientErrorException e) {
                try {
                        JsonNode errorNode = objectMapper.readTree(e.getResponseBodyAsString());
                        if (errorNode.has("error") && errorNode.path("error").has("message")) {
                                return errorNode.path("error").path("message")
                                                .asText(e.getResponseBodyAsString());
                        }
                } catch (JsonProcessingException jsonEx) {
                        log.warn("Could not parse Gemini error response body as JSON: {}",
                                        jsonEx.getMessage());
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

        public String callSimpleTextAPI(String promptText) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> textPart = Map.of("text", promptText);
                List<Object> parts = List.of(textPart);
                Map<String, Object> content = Map.of("parts", parts);
                List<Object> contents = List.of(content);

                Map<String, Object> generationConfig = new HashMap<>();
                generationConfig.put("temperature", 0.7);
                generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS_TRANSCRIPTION);

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("contents", contents);
                requestBody.put("generationConfig", generationConfig);

                HttpEntity<Map<String, Object>> requestEntity =
                                new HttpEntity<>(requestBody, headers);

                String url = TRANSCRIPTION_API_URL + "?key=" + apiKey;
                log.info("Calling Gemini Simple Text API (Model: {})", TRANSCRIPTION_MODEL_NAME);

                for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                        try {
                                ResponseEntity<String> response = restTemplate.exchange(url,
                                                HttpMethod.POST, requestEntity, String.class);

                                log.info("Gemini Simple Text API call successful on attempt {} using model {}. Status: {}",
                                                attempt, TRANSCRIPTION_MODEL_NAME,
                                                response.getStatusCode());

                                String responseBody = response.getBody();
                                if (responseBody == null || responseBody.isBlank()) {
                                        log.warn("Gemini Simple Text API returned successful status ({}) but empty body.",
                                                        response.getStatusCode());
                                        return createErrorResponse("Empty Response",
                                                        "API returned success status but no content.");
                                }

                                String extractedText =
                                                extractTextFromStandardResponse(responseBody);

                                if (extractedText.isBlank()
                                                && !responseBody.contains("promptFeedback")
                                                && !responseBody.contains("finishReason")) {
                                        log.warn("Could not extract text content from Gemini simple text response structure. Body: {}",
                                                        responseBody);
                                        return createErrorResponse("Extraction Error",
                                                        "Failed to extract text from API response structure.");
                                }

                                log.info("Successfully extracted simple text response (length: {}).",
                                                extractedText.length());
                                return extractedText;

                        } catch (HttpServerErrorException | ResourceAccessException e) {
                                log.warn("Gemini Simple Text API call failed on attempt {}/{} with retryable error: {}. Retrying...",
                                                attempt, MAX_RETRIES, e.getMessage());
                                if (attempt == MAX_RETRIES) {
                                        log.error("Gemini Simple Text API call failed after {} attempts.",
                                                        MAX_RETRIES, e);
                                        return createErrorResponse(
                                                        "API Request Failed (Server/Network)",
                                                        e.getMessage());
                                }
                                try {
                                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempt);
                                } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        log.error("API call interrupted during retry wait.", ie);
                                        return createErrorResponse("API call interrupted",
                                                        ie.getMessage());
                                }
                        } catch (HttpClientErrorException e) {
                                log.error("Gemini Simple Text API client error: {} - {}",
                                                e.getStatusCode(), e.getResponseBodyAsString(), e);
                                String details = parseErrorDetails(e);
                                return createErrorResponse("API Client Error: " + e.getStatusCode(),
                                                details);
                        } catch (RestClientResponseException e) {
                                log.error("Gemini Simple Text API REST client error: Status {}, Body: {}",
                                                e.getStatusCode(), e.getResponseBodyAsString(), e);
                                return createErrorResponse("API Request Failed (REST Client)",
                                                e.getMessage());
                        } catch (RuntimeException e) {
                                log.error("Runtime error during Gemini Simple Text API processing on attempt {}: {}",
                                                attempt, e.getMessage(), e);
                                return createErrorResponse("Simple Text API Processing Error",
                                                e.getMessage());
                        } catch (Exception e) {
                                log.error("Error during Gemini Simple Text API call or processing on attempt {}",
                                                attempt, e);
                                return createErrorResponse(
                                                "Simple Text API Request/Processing Failed",
                                                e.getMessage());
                        }
                }
                return createErrorResponse("API Request Failed",
                                "Max retries reached or unexpected flow.");
        }

        private String getAudioMimeType(String fileName) {
                if (fileName == null || fileName.isEmpty()) {
                        log.warn("Filename is null or empty, defaulting MIME type to audio/mpeg");
                        return "audio/mpeg";
                }

                String lowercaseFileName = fileName.toLowerCase();

                if (lowercaseFileName.endsWith(".mp3"))
                        return "audio/mp3";
                if (lowercaseFileName.endsWith(".m4a"))
                        return "audio/m4a";
                if (lowercaseFileName.endsWith(".mp4"))
                        return "audio/mp4";
                if (lowercaseFileName.endsWith(".wav"))
                        return "audio/wav";
                if (lowercaseFileName.endsWith(".flac"))
                        return "audio/flac";
                if (lowercaseFileName.endsWith(".ogg"))
                        return "audio/ogg";
                if (lowercaseFileName.endsWith(".opus"))
                        return "audio/opus";
                if (lowercaseFileName.endsWith(".aac"))
                        return "audio/aac";
                if (lowercaseFileName.endsWith(".aiff") || lowercaseFileName.endsWith(".aif"))
                        return "audio/aiff";
                if (lowercaseFileName.endsWith(".amr"))
                        return "audio/amr";

                log.warn("Unknown or potentially unsupported audio file extension in '{}', defaulting MIME type to audio/mpeg. Verify Gemini API support.",
                                fileName);
                return "audio/mpeg";
        }
}

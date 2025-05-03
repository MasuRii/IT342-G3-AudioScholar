package edu.cit.audioscholar.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class GeminiService {
        private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

        @Value("${google.ai.api.key}")
        private String apiKey;

        private static final String API_BASE_URL = "https://generativelanguage.googleapis.com";
        private static final String FILES_API_UPLOAD_PATH = "/upload/v1beta/files";
        private static final String FILES_API_BASE_URL = API_BASE_URL;
        private static final String TRANSCRIPTION_MODEL_NAME = "gemini-2.0-flash";
        private static final String SUMMARIZATION_MODEL_NAME = "gemini-2.5-flash-preview-04-17";
        private static final String GENERATE_CONTENT_PATH =
                        "/v1beta/models/{modelName}:generateContent";

        private static final int MAX_RETRIES = 3;
        private static final long RETRY_DELAY_MS = 1000;
        private static final int MAX_OUTPUT_TOKENS_TRANSCRIPTION = 32768;
        private static final int MAX_OUTPUT_TOKENS_SUMMARIZATION = 65536;

        private final RestTemplate restTemplate = new RestTemplate();
        private final ObjectMapper objectMapper = new ObjectMapper();

        private static final Map<String, Object> SUMMARY_RESPONSE_SCHEMA = createSummarySchema();
        private static final Map<String, Object> TRANSCRIPT_RESPONSE_SCHEMA =
                        createTranscriptSchema();
        private static final Map<String, Object> RECOMMENDATIONS_RESPONSE_SCHEMA =
                        createRecommendationsSchema();

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
                schema.put("propertyOrdering",
                                List.of("summaryText", "keyPoints", "topics", "glossary"));
                return Collections.unmodifiableMap(schema);
        }

        private static Map<String, Object> createTranscriptSchema() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "OBJECT");
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("transcript", Map.of("type", "STRING", "description",
                                "The complete transcribed text from the audio file."));
                properties.put("confidenceScore", Map.of("type", "NUMBER", "description",
                                "A confidence score between 0.0 and 1.0 for the overall transcription."));
                schema.put("properties", properties);
                schema.put("required", List.of("transcript"));
                schema.put("propertyOrdering", List.of("transcript", "confidenceScore"));
                return Collections.unmodifiableMap(schema);
        }

        private static Map<String, Object> createRecommendationsSchema() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "ARRAY");

                Map<String, Object> itemProperties = new LinkedHashMap<>();
                itemProperties.put("title", Map.of("type", "STRING", "description",
                                "Descriptive title of the recommended resource"));
                itemProperties.put("description", Map.of("type", "STRING", "description",
                                "Explanation of why this resource is relevant to the lecture content"));
                itemProperties.put("url", Map.of("type", "STRING", "description",
                                "Direct link to the resource"));
                itemProperties.put("type", Map.of("type", "STRING", "description",
                                "Type of resource (Video, Article, Tutorial, Documentation, Tool, Book, Course)"));
                itemProperties.put("audience", Map.of("type", "STRING", "description",
                                "Target audience level (Beginner, Intermediate, Advanced)"));

                Map<String, Object> itemSchema = new LinkedHashMap<>();
                itemSchema.put("type", "OBJECT");
                itemSchema.put("properties", itemProperties);
                itemSchema.put("required",
                                List.of("title", "description", "url", "type", "audience"));
                itemSchema.put("propertyOrdering",
                                List.of("title", "description", "url", "type", "audience"));

                schema.put("items", itemSchema);
                return Collections.unmodifiableMap(schema);
        }

        public String callGeminiTranscriptionAPI(Path audioFilePath, String fileName)
                        throws IOException {
                if (audioFilePath == null || !Files.exists(audioFilePath)) {
                        log.error("Audio file path is null or does not exist: {}", audioFilePath);
                        throw new IOException("Audio file path is null or does not exist: "
                                        + audioFilePath);
                }

                String mimeType = getAudioMimeType(fileName);
                long fileSize = Files.size(audioFilePath);
                String displayName = fileName;

                try {
                        String fileUri = uploadFile(audioFilePath, mimeType, fileSize, displayName);
                        log.info("File uploaded successfully. URI: {}", fileUri);

                        HttpHeaders generateHeaders = new HttpHeaders();
                        generateHeaders.setContentType(MediaType.APPLICATION_JSON);

                        String promptText =
                                        "Transcribe the following audio content accurately. If the audio contains no speech or only silence, output the exact text '[NO SPEECH DETECTED]' in the transcript field. Otherwise, output only the spoken text. Maintain original punctuation, capitalization, and paragraph breaks as best as possible. For numbers, spell them as digits if they represent quantities or measurements, and as words if they are part of natural speech. Include any hesitations, repetitions, or fillers that are meaningful to the content.";
                        Map<String, Object> textPart = Map.of("text", promptText);
                        Map<String, Object> fileDataPart = Map.of("file_data",
                                        Map.of("mime_type", mimeType, "file_uri", fileUri));

                        List<Object> parts = List.of(textPart, fileDataPart);
                        Map<String, Object> content = Map.of("parts", parts);
                        List<Object> contents = List.of(content);

                        Map<String, Object> generationConfig = new HashMap<>();
                        generationConfig.put("temperature", 0.2);
                        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS_TRANSCRIPTION);
                        generationConfig.put("response_mime_type", "application/json");
                        generationConfig.put("response_schema", TRANSCRIPT_RESPONSE_SCHEMA);

                        Map<String, Object> requestBody = new HashMap<>();
                        requestBody.put("contents", contents);
                        requestBody.put("generationConfig", generationConfig);

                        HttpEntity<Map<String, Object>> requestEntity =
                                        new HttpEntity<>(requestBody, generateHeaders);

                        String generateContentUrl = UriComponentsBuilder
                                        .fromUriString(API_BASE_URL + GENERATE_CONTENT_PATH)
                                        .queryParam("key", apiKey)
                                        .buildAndExpand(TRANSCRIPTION_MODEL_NAME).toUriString();

                        log.info("Calling Gemini Transcription API (Model: {}) using file URI: {}",
                                        TRANSCRIPTION_MODEL_NAME, fileUri);

                        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                                try {
                                        ResponseEntity<String> response = restTemplate.exchange(
                                                        generateContentUrl, HttpMethod.POST,
                                                        requestEntity, String.class);
                                        log.info("Gemini Transcription API (generateContent) call successful on attempt {} using model {}. Status: {}",
                                                        attempt, TRANSCRIPTION_MODEL_NAME,
                                                        response.getStatusCode());

                                        String responseBody = response.getBody();
                                        if (responseBody == null || responseBody.isBlank()) {
                                                log.warn("Gemini Transcription API (generateContent) returned successful status ({}) but empty body.",
                                                                response.getStatusCode());
                                                if (attempt == MAX_RETRIES) {
                                                        return createErrorResponse("Empty Response",
                                                                        "API returned success status but no content after retries.");
                                                }
                                                sleepForRetry(attempt);
                                                continue;
                                        }

                                        try {
                                                JsonNode responseNode =
                                                                objectMapper.readTree(responseBody);

                                                if (responseNode.has("candidates")
                                                                && responseNode.path("candidates")
                                                                                .isArray()
                                                                && !responseNode.path("candidates")
                                                                                .isEmpty()) {
                                                        JsonNode firstCandidate = responseNode
                                                                        .path("candidates").get(0);

                                                        if (firstCandidate.has("content")
                                                                        && firstCandidate.path(
                                                                                        "content")
                                                                                        .has("parts")
                                                                        && firstCandidate.path(
                                                                                        "content")
                                                                                        .path("parts")
                                                                                        .isArray()
                                                                        && !firstCandidate.path(
                                                                                        "content")
                                                                                        .path("parts")
                                                                                        .isEmpty()) {

                                                                JsonNode firstPart = firstCandidate
                                                                                .path("content")
                                                                                .path("parts")
                                                                                .get(0);
                                                                if (firstPart.has("text")) {
                                                                        String jsonResponse =
                                                                                        firstPart.path("text")
                                                                                                        .asText();

                                                                        String transcript =
                                                                                        extractTranscriptFromJsonResponse(
                                                                                                        jsonResponse);
                                                                        if (transcript != null) {
                                                                                return transcript;
                                                                        }
                                                                }
                                                        }
                                                }

                                                log.warn("Could not extract structured JSON transcript, falling back to standard extraction.");
                                                String extractedText =
                                                                extractTextFromStandardResponse(
                                                                                responseBody);
                                                log.info("Successfully extracted transcript text (length: {}).",
                                                                extractedText.length());
                                                return extractedText;

                                        } catch (JsonProcessingException e) {
                                                log.warn("Error parsing transcript JSON response, falling back to standard extraction: {}",
                                                                e.getMessage());
                                                String extractedText =
                                                                extractTextFromStandardResponse(
                                                                                responseBody);
                                                log.info("Successfully extracted transcript text (length: {}).",
                                                                extractedText.length());
                                                return extractedText;
                                        }

                                } catch (ApiException e) {
                                        log.error("Gemini Transcription API Error on attempt {}: {}",
                                                        attempt, e.getMessage(), e);
                                        return createErrorResponse("Transcription API Error",
                                                        e.getMessage());
                                } catch (HttpServerErrorException | ResourceAccessException e) {
                                        log.warn("Gemini Transcription API (generateContent) call failed on attempt {}/{} with retryable error: {}. Retrying...",
                                                        attempt, MAX_RETRIES, e.getMessage());
                                        if (attempt == MAX_RETRIES) {
                                                log.error("Gemini Transcription API (generateContent) call failed after {} attempts.",
                                                                MAX_RETRIES, e);
                                                return createErrorResponse(
                                                                "API Request Failed (Server/Network)",
                                                                e.getMessage());
                                        }
                                        sleepForRetry(attempt);
                                } catch (HttpClientErrorException e) {
                                        log.error("Gemini Transcription API (generateContent) client error: {} - {}",
                                                        e.getStatusCode(),
                                                        e.getResponseBodyAsString(), e);
                                        String details = parseErrorDetails(e);
                                        return createErrorResponse(
                                                        "API Client Error: " + e.getStatusCode(),
                                                        details);
                                } catch (RestClientResponseException e) {
                                        log.error("Gemini Transcription API (generateContent) REST client error: Status {}, Body: {}",
                                                        e.getStatusCode(),
                                                        e.getResponseBodyAsString(), e);
                                        return createErrorResponse(
                                                        "API Request Failed (REST Client)",
                                                        e.getMessage());
                                } catch (RuntimeException e) {
                                        log.error("Unexpected runtime error during Gemini Transcription API (generateContent) processing on attempt {}: {}",
                                                        attempt, e.getMessage(), e);
                                        return createErrorResponse("Transcription Processing Error",
                                                        e.getMessage());
                                }
                        }
                        return createErrorResponse("API Request Failed",
                                        "Max retries reached for generateContent or unexpected flow.");

                } catch (IOException e) {
                        log.error("IOException during file handling or upload: {}", e.getMessage(),
                                        e);
                        throw e;
                } catch (ApiException e) {
                        log.error("File Upload API call failed: {}", e.getMessage(), e);
                        return createErrorResponse("File Upload Failed", e.getMessage());
                } catch (Exception e) {
                        log.error("Unexpected error during transcription process: {}",
                                        e.getMessage(), e);
                        return createErrorResponse("Unexpected Transcription Error",
                                        e.getMessage());
                }
        }

        private String uploadFile(Path filePath, String mimeType, long fileSize, String displayName)
                        throws IOException, ApiException {
                String initiateUrl = UriComponentsBuilder
                                .fromUriString(FILES_API_BASE_URL + FILES_API_UPLOAD_PATH)
                                .queryParam("key", apiKey).toUriString();

                HttpHeaders initiateHeaders = new HttpHeaders();
                initiateHeaders.set("X-Goog-Upload-Protocol", "resumable");
                initiateHeaders.set("X-Goog-Upload-Command", "start");
                initiateHeaders.set("X-Goog-Upload-Header-Content-Length",
                                String.valueOf(fileSize));
                initiateHeaders.set("X-Goog-Upload-Header-Content-Type", mimeType);
                initiateHeaders.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> fileMetadata =
                                Map.of("display_name", displayName != null ? displayName
                                                : "audio_file_" + UUID.randomUUID());
                Map<String, Object> initiateBodyMap = Map.of("file", fileMetadata);
                HttpEntity<Map<String, Object>> initiateRequestEntity =
                                new HttpEntity<>(initiateBodyMap, initiateHeaders);
                String uploadUrl;

                log.info("Initiating file upload for: {}", displayName);
                try {
                        ResponseEntity<String> initiateResponse = restTemplate.exchange(initiateUrl,
                                        HttpMethod.POST, initiateRequestEntity, String.class);
                        uploadUrl = initiateResponse.getHeaders().getFirst("X-Goog-Upload-Url");

                        if (uploadUrl == null || uploadUrl.isBlank()) {
                                log.error("Failed to get upload URL from initiation response. Status: {}, Body: {}",
                                                initiateResponse.getStatusCode(),
                                                initiateResponse.getBody());
                                throw new ApiException(
                                                "Failed to get upload URL from initiation response.");
                        }
                        log.info("Upload initiated. Got upload URL.");
                        log.debug("Upload URL: {}", uploadUrl);

                } catch (RestClientException e) {
                        log.error("Error initiating file upload: {}", e.getMessage(), e);
                        throw new ApiException("Error initiating file upload: " + e.getMessage(),
                                        e);
                }

                HttpHeaders uploadHeaders = new HttpHeaders();
                uploadHeaders.setContentType(MediaType.parseMediaType(mimeType));
                uploadHeaders.set("X-Goog-Upload-Offset", "0");
                uploadHeaders.set("X-Goog-Upload-Command", "upload, finalize");

                FileSystemResource fileResource = new FileSystemResource(filePath);
                HttpEntity<FileSystemResource> uploadRequestEntity =
                                new HttpEntity<>(fileResource, uploadHeaders);

                log.info("Uploading file bytes to: {}", uploadUrl);
                try {
                        ResponseEntity<String> uploadResponse = restTemplate.exchange(uploadUrl,
                                        HttpMethod.POST, uploadRequestEntity, String.class);

                        log.info("File upload completed. Status: {}",
                                        uploadResponse.getStatusCode());
                        String responseBody = uploadResponse.getBody();

                        if (responseBody == null) {
                                throw new ApiException(
                                                "Upload completed but received null response body.");
                        }

                        JsonNode responseNode = objectMapper.readTree(responseBody);
                        if (responseNode.has("file") && responseNode.get("file").has("uri")) {
                                String fileUri = responseNode.get("file").get("uri").asText();
                                if (fileUri != null && !fileUri.isBlank()) {
                                        log.debug("Extracted file URI: {}", fileUri);
                                        return fileUri;
                                }
                        }
                        log.error("Upload response did not contain expected file URI. Body: {}",
                                        responseBody);
                        throw new ApiException(
                                        "Upload response did not contain expected file URI.");

                } catch (RestClientException e) {
                        log.error("Error uploading file bytes: {}", e.getMessage(), e);
                        if (e instanceof HttpStatusCodeException) {
                                String errorBody = ((HttpStatusCodeException) e)
                                                .getResponseBodyAsString();
                                log.error("API Error Response Body: {}", errorBody);
                                throw new ApiException("Error uploading file bytes: "
                                                + parseErrorDetailsFromString(errorBody), e);
                        }
                        throw new ApiException("Error uploading file bytes: " + e.getMessage(), e);
                } catch (JsonProcessingException e) {
                        log.error("Error parsing upload response JSON: {}", e.getMessage(), e);
                        throw new ApiException("Error parsing upload response JSON", e);
                }
        }

        public String callGeminiSummarizationAPI(String promptText, String transcriptText) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                String updatedPromptText =
                                promptText + """

                                                YOU MUST RETURN VALID JSON that strictly adheres to the provided schema. Do not include any explanatory text before or after the JSON. The JSON structure must include:
                                                {
                                                  "summaryText": "Your markdown summary here",
                                                  "keyPoints": ["key point 1", "key point 2", ...],
                                                  "topics": ["topic 1", "topic 2", ...],
                                                  "glossary": [
                                                    {"term": "term1", "definition": "definition1"},
                                                    {"term": "term2", "definition": "definition2"},
                                                    ...
                                                  ]
                                                }
                                                """;

                Map<String, Object> promptPart = Map.of("text", updatedPromptText);
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

                String summarizationUrl = UriComponentsBuilder
                                .fromUriString(API_BASE_URL + GENERATE_CONTENT_PATH)
                                .queryParam("key", apiKey).buildAndExpand(SUMMARIZATION_MODEL_NAME)
                                .toUriString();

                log.info("Calling Gemini Summarization API (Model: {}, JSON Schema Mode)",
                                SUMMARIZATION_MODEL_NAME);
                log.trace("Summarization prompt text length: {}", updatedPromptText.length());
                log.trace("Summarization transcript text length: {}", transcriptText.length());

                for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                        try {
                                ResponseEntity<String> response = restTemplate.exchange(
                                                summarizationUrl, HttpMethod.POST, requestEntity,
                                                String.class);
                                log.info("Gemini Summarization API call successful on attempt {} using model {}. Status: {}",
                                                attempt, SUMMARIZATION_MODEL_NAME,
                                                response.getStatusCode());

                                String responseBody = response.getBody();
                                if (responseBody == null || responseBody.isBlank()) {
                                        log.warn("Gemini Summarization API returned successful status ({}) but empty body.",
                                                        response.getStatusCode());
                                        if (attempt == MAX_RETRIES) {
                                                return createErrorResponse("Empty Response",
                                                                "API returned success status but no content after retries.");
                                        }
                                        sleepForRetry(attempt);
                                        continue;
                                }

                                String extractedJsonText =
                                                extractTextFromStandardResponse(responseBody);
                                log.debug("Successfully extracted JSON text from standard response structure (length: {}).",
                                                extractedJsonText.length());
                                return extractedJsonText;

                        } catch (ApiException e) {
                                log.error("Gemini Summarization API Error on attempt {}: {}",
                                                attempt, e.getMessage(), e);
                                return createErrorResponse("Summarization API Error",
                                                e.getMessage());
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
                                sleepForRetry(attempt);
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
                        } catch (JsonProcessingException e) {
                                log.error("Error parsing successful Gemini Summarization response JSON on attempt {}: {}",
                                                attempt, e.getMessage(), e);
                                return createErrorResponse("Response Parsing Error",
                                                e.getMessage());
                        } catch (RuntimeException e) {
                                log.error("Unexpected runtime error during Gemini Summarization API processing on attempt {}: {}",
                                                attempt, e.getMessage(), e);
                                return createErrorResponse("Summarization Processing Error",
                                                e.getMessage());
                        }
                }
                return createErrorResponse("API Request Failed",
                                "Max retries reached for summarization or unexpected flow.");
        }

        public String generateSummaryWithPdfContext(String transcriptText, Path pdfFilePath,
                        String metadataId) {
                log.info("[{}] Starting combined summarization with PDF context.", metadataId);

                if (transcriptText == null || transcriptText.isBlank()) {
                        log.error("[{}] Transcript text is null or blank. Cannot generate summary.",
                                        metadataId);
                        return createErrorResponse("Input Error", "Transcript text is missing.");
                }
                if (pdfFilePath == null || !Files.exists(pdfFilePath)) {
                        log.error("[{}] PDF file path is null or file doesn't exist: {}",
                                        metadataId, pdfFilePath);
                        return createErrorResponse("Input Error",
                                        "PDF file is missing or invalid.");
                }

                String pdfFileUri = null;

                try {
                        log.info("[{}] Using local PDF file: {}", metadataId,
                                        pdfFilePath.getFileName());

                        long pdfSize = Files.size(pdfFilePath);
                        String pdfDisplayName = "context_" + metadataId + ".pdf";
                        log.info("[{}] Uploading PDF ({}) to Google Files API...", metadataId,
                                        pdfDisplayName);

                        pdfFileUri = uploadFile(pdfFilePath, "application/pdf", pdfSize,
                                        pdfDisplayName);
                        log.info("[{}] PDF uploaded successfully to Google Files API. URI: {}",
                                        metadataId, pdfFileUri);

                        HttpHeaders generateHeaders = new HttpHeaders();
                        generateHeaders.setContentType(MediaType.APPLICATION_JSON);

                        String prompt = """
                                        Analyze the provided lecture transcript and the accompanying PDF document.
                                        Generate a comprehensive, concise, well-structured summary incorporating information from BOTH sources, using Markdown in the `summaryText` field. Use headings (##) for main sections and bullet points (* or -) for details. Focus on core arguments, findings, definitions, and conclusions presented in either the transcript or the document.
                                        Identify the main key points or action items discussed across both sources and list them as distinct strings in the `keyPoints` array.
                                        List the 3-5 most important topics or keywords suitable for searching related content based on both sources in the `topics` array.
                                        Identify important **terms, concepts, acronyms, proper nouns (people, places, organizations mentioned), and technical vocabulary** discussed in either the transcript or the document. For each, provide a concise definition relevant to the context. Structure this as an array of objects in the `glossary` field, where each object has a `term` (string) and a `definition` (string). Aim for comprehensive coverage of potentially unfamiliar items for a learner.
                                        Ensure the entire output strictly adheres to the provided JSON schema. Output only the JSON object.
                                        """;

                        Map<String, Object> promptPart = Map.of("text", prompt);
                        Map<String, Object> transcriptPart = Map.of("text", transcriptText);
                        Map<String, Object> pdfPart = Map.of("file_data", Map.of("mime_type",
                                        "application/pdf", "file_uri", pdfFileUri));

                        List<Object> parts = List.of(promptPart, transcriptPart, pdfPart);
                        Map<String, Object> content = Map.of("parts", parts);
                        List<Object> contents = List.of(content);

                        Map<String, Object> generationConfig = new HashMap<>();
                        generationConfig.put("temperature", 0.4);
                        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS_SUMMARIZATION);
                        generationConfig.put("response_mime_type", "application/json");
                        generationConfig.put("response_schema", SUMMARY_RESPONSE_SCHEMA);

                        Map<String, Object> requestBody = new HashMap<>();
                        requestBody.put("contents", contents);
                        requestBody.put("generationConfig", generationConfig);

                        HttpEntity<Map<String, Object>> requestEntity =
                                        new HttpEntity<>(requestBody, generateHeaders);

                        String generateContentUrl = UriComponentsBuilder
                                        .fromUriString(API_BASE_URL + GENERATE_CONTENT_PATH)
                                        .queryParam("key", apiKey)
                                        .buildAndExpand(SUMMARIZATION_MODEL_NAME).toUriString();

                        log.info("[{}] Calling Gemini Summarization API (Model: {}) with transcript and PDF context (URI: {})...",
                                        metadataId, SUMMARIZATION_MODEL_NAME, pdfFileUri);

                        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                                try {
                                        ResponseEntity<String> response = restTemplate.exchange(
                                                        generateContentUrl, HttpMethod.POST,
                                                        requestEntity, String.class);
                                        log.info("[{}] Gemini Summarization API (generateContent with PDF) call successful on attempt {}. Status: {}",
                                                        metadataId, attempt,
                                                        response.getStatusCode());

                                        String responseBody = response.getBody();
                                        if (responseBody == null || responseBody.isBlank()) {
                                                log.warn("[{}] Gemini Summarization API (with PDF) returned successful status ({}) but empty body on attempt {}.",
                                                                metadataId,
                                                                response.getStatusCode(), attempt);
                                                if (attempt == MAX_RETRIES) {
                                                        return createErrorResponse("Empty Response",
                                                                        "API returned success status but no content after retries.");
                                                }
                                                sleepForRetry(attempt);
                                                continue;
                                        }

                                        try {
                                                objectMapper.readTree(responseBody);
                                                log.info("[{}] Successfully received JSON summary response (Length: {}).",
                                                                metadataId, responseBody.length());
                                                return responseBody;
                                        } catch (JsonProcessingException jsonEx) {
                                                log.error("[{}] Gemini Summarization API (with PDF) response was not valid JSON on attempt {}. Body: {}. Error: {}",
                                                                metadataId, attempt,
                                                                responseBody.substring(0, Math.min(
                                                                                responseBody.length(),
                                                                                500)),
                                                                jsonEx.getMessage());
                                                if (attempt == MAX_RETRIES) {
                                                        return createErrorResponse(
                                                                        "Invalid JSON Response",
                                                                        "API response was not valid JSON: "
                                                                                        + jsonEx.getMessage());
                                                }
                                                sleepForRetry(attempt);
                                                continue;
                                        }

                                } catch (HttpServerErrorException | ResourceAccessException e) {
                                        log.warn("[{}] Gemini Summarization API (with PDF) call failed on attempt {}/{} with retryable error: {}. Retrying...",
                                                        metadataId, attempt, MAX_RETRIES,
                                                        e.getMessage());
                                        if (attempt == MAX_RETRIES) {
                                                log.error("[{}] Gemini Summarization API (with PDF) call failed after {} attempts.",
                                                                metadataId, MAX_RETRIES, e);
                                                return createErrorResponse(
                                                                "API Request Failed (Server/Network)",
                                                                e.getMessage());
                                        }
                                        sleepForRetry(attempt);
                                } catch (HttpClientErrorException e) {
                                        log.error("[{}] Gemini Summarization API (with PDF) client error: {} - {}",
                                                        metadataId, e.getStatusCode(),
                                                        e.getResponseBodyAsString(), e);
                                        String details = parseErrorDetails(e);
                                        return createErrorResponse(
                                                        "API Client Error: " + e.getStatusCode(),
                                                        details);
                                } catch (RestClientResponseException e) {
                                        log.error("[{}] Gemini Summarization API (with PDF) REST client error: Status {}, Body: {}",
                                                        metadataId, e.getStatusCode(),
                                                        e.getResponseBodyAsString(), e);
                                        return createErrorResponse(
                                                        "API Request Failed (REST Client)",
                                                        e.getMessage());
                                } catch (RuntimeException e) {
                                        log.error("[{}] Unexpected runtime error during Gemini Summarization (with PDF) processing on attempt {}: {}",
                                                        metadataId, attempt, e.getMessage(), e);
                                        return createErrorResponse("Summarization Processing Error",
                                                        e.getMessage());
                                }
                        }
                        return createErrorResponse("API Request Failed",
                                        "Max retries reached for summarization or unexpected flow.");

                } catch (IOException e) {
                        log.error("[{}] IOException during PDF upload: {}", metadataId,
                                        e.getMessage(), e);
                        return createErrorResponse("File Handling Error",
                                        "Error processing PDF file: " + e.getMessage());
                } catch (ApiException e) {
                        log.error("[{}] Google Files API Upload Error for PDF: {}", metadataId,
                                        e.getMessage(), e);
                        return createErrorResponse("PDF Upload Failed", e.getMessage());
                } catch (Exception e) {
                        log.error("[{}] Unexpected error during combined summarization setup: {}",
                                        metadataId, e.getMessage(), e);
                        return createErrorResponse("Unexpected Summarization Setup Error",
                                        e.getMessage());
                }
        }

        public String generateSummaryWithGoogleFileUri(String transcriptText, String googleFileUri,
                        String metadataId) {
                log.info("[{}] Starting combined summarization with direct Google Files API URI.",
                                metadataId);

                if (transcriptText == null || transcriptText.isBlank()) {
                        log.error("[{}] Transcript text is null or blank. Cannot generate summary.",
                                        metadataId);
                        return createErrorResponse("Input Error", "Transcript text is missing.");
                }
                if (googleFileUri == null || googleFileUri.isBlank()) {
                        log.error("[{}] Google Files API URI is null or blank.", metadataId);
                        return createErrorResponse("Input Error",
                                        "Google Files API URI is missing.");
                }

                try {
                        log.info("[{}] Using Google Files API URI directly: {}", metadataId,
                                        googleFileUri);

                        HttpHeaders generateHeaders = new HttpHeaders();
                        generateHeaders.setContentType(MediaType.APPLICATION_JSON);

                        String prompt = """
                                        Analyze the provided lecture transcript and the accompanying PDF document.
                                        Generate a comprehensive, concise, well-structured summary incorporating information from BOTH sources, using Markdown in the `summaryText` field. Use headings (##) for main sections and bullet points (* or -) for details. Focus on core arguments, findings, definitions, and conclusions presented in either the transcript or the document.
                                        Identify the main key points or action items discussed across both sources and list them as distinct strings in the `keyPoints` array.
                                        List the 3-5 most important topics or keywords suitable for searching related content based on both sources in the `topics` array.
                                        Identify important **terms, concepts, acronyms, proper nouns (people, places, organizations mentioned), and technical vocabulary** discussed in either the transcript or the document. For each, provide a concise definition relevant to the context. Structure this as an array of objects in the `glossary` field, where each object has a `term` (string) and a `definition` (string). Aim for comprehensive coverage of potentially unfamiliar items for a learner.
                                        YOU MUST RETURN VALID JSON that strictly adheres to the provided schema. Do not include any explanatory text before or after the JSON. The JSON structure must include:
                                        {
                                          "summaryText": "Your markdown summary here",
                                          "keyPoints": ["key point 1", "key point 2", ...],
                                          "topics": ["topic 1", "topic 2", ...],
                                          "glossary": [
                                            {"term": "term1", "definition": "definition1"},
                                            {"term": "term2", "definition": "definition2"},
                                            ...
                                          ]
                                        }
                                        """;

                        Map<String, Object> promptPart = Map.of("text", prompt);
                        Map<String, Object> transcriptPart = Map.of("text", transcriptText);
                        Map<String, Object> pdfPart = Map.of("file_data", Map.of("mime_type",
                                        "application/pdf", "file_uri", googleFileUri));

                        List<Object> parts = List.of(promptPart, transcriptPart, pdfPart);
                        Map<String, Object> content = Map.of("parts", parts);
                        List<Object> contents = List.of(content);

                        Map<String, Object> generationConfig = new HashMap<>();
                        generationConfig.put("temperature", 0.4);
                        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS_SUMMARIZATION);
                        generationConfig.put("response_mime_type", "application/json");
                        generationConfig.put("response_schema", SUMMARY_RESPONSE_SCHEMA);

                        Map<String, Object> requestBody = new HashMap<>();
                        requestBody.put("contents", contents);
                        requestBody.put("generationConfig", generationConfig);

                        HttpEntity<Map<String, Object>> requestEntity =
                                        new HttpEntity<>(requestBody, generateHeaders);

                        String generateContentUrl = UriComponentsBuilder
                                        .fromUriString(API_BASE_URL + GENERATE_CONTENT_PATH)
                                        .queryParam("key", apiKey)
                                        .buildAndExpand(SUMMARIZATION_MODEL_NAME).toUriString();

                        log.info("[{}] Calling Gemini Summarization API (Model: {}) with transcript and direct PDF context (URI: {})...",
                                        metadataId, SUMMARIZATION_MODEL_NAME, googleFileUri);

                        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                                try {
                                        ResponseEntity<String> response = restTemplate.exchange(
                                                        generateContentUrl, HttpMethod.POST,
                                                        requestEntity, String.class);
                                        log.info("[{}] Gemini Summarization API (generateContent with direct PDF) call successful on attempt {}. Status: {}",
                                                        metadataId, attempt,
                                                        response.getStatusCode());

                                        String responseBody = response.getBody();
                                        if (responseBody == null || responseBody.isBlank()) {
                                                log.warn("[{}] Gemini Summarization API (with direct PDF) returned successful status ({}) but empty body on attempt {}.",
                                                                metadataId,
                                                                response.getStatusCode(), attempt);
                                                if (attempt == MAX_RETRIES) {
                                                        return createErrorResponse("Empty Response",
                                                                        "API returned success status but no content after retries.");
                                                }
                                                sleepForRetry(attempt);
                                                continue;
                                        }

                                        try {
                                                objectMapper.readTree(responseBody);
                                                log.info("[{}] Successfully received JSON summary response (Length: {}).",
                                                                metadataId, responseBody.length());
                                                return responseBody;
                                        } catch (JsonProcessingException jsonEx) {
                                                log.error("[{}] Gemini Summarization API (with direct PDF) response was not valid JSON on attempt {}. Body: {}. Error: {}",
                                                                metadataId, attempt,
                                                                responseBody.substring(0, Math.min(
                                                                                responseBody.length(),
                                                                                500)),
                                                                jsonEx.getMessage());
                                                if (attempt == MAX_RETRIES) {
                                                        return createErrorResponse(
                                                                        "Invalid JSON Response",
                                                                        "API response was not valid JSON: "
                                                                                        + jsonEx.getMessage());
                                                }
                                                sleepForRetry(attempt);
                                                continue;
                                        }

                                } catch (HttpServerErrorException | ResourceAccessException e) {
                                        log.warn("[{}] Gemini Summarization API (with direct PDF) call failed on attempt {}/{} with retryable error: {}. Retrying...",
                                                        metadataId, attempt, MAX_RETRIES,
                                                        e.getMessage());
                                        if (attempt == MAX_RETRIES) {
                                                log.error("[{}] Gemini Summarization API (with direct PDF) call failed after {} attempts.",
                                                                metadataId, MAX_RETRIES, e);
                                                return createErrorResponse(
                                                                "API Request Failed (Server/Network)",
                                                                e.getMessage());
                                        }
                                        sleepForRetry(attempt);
                                } catch (HttpClientErrorException e) {
                                        log.error("[{}] Gemini Summarization API (with direct PDF) client error: {} - {}",
                                                        metadataId, e.getStatusCode(),
                                                        e.getResponseBodyAsString(), e);
                                        String details = parseErrorDetails(e);
                                        return createErrorResponse(
                                                        "API Client Error: " + e.getStatusCode(),
                                                        details);
                                } catch (RestClientResponseException e) {
                                        log.error("[{}] Gemini Summarization API (with direct PDF) REST client error: Status {}, Body: {}",
                                                        metadataId, e.getStatusCode(),
                                                        e.getResponseBodyAsString(), e);
                                        return createErrorResponse(
                                                        "API Request Failed (REST Client)",
                                                        e.getMessage());
                                } catch (RuntimeException e) {
                                        log.error("[{}] Unexpected runtime error during Gemini Summarization (with direct PDF) processing on attempt {}: {}",
                                                        metadataId, attempt, e.getMessage(), e);
                                        return createErrorResponse("Summarization Processing Error",
                                                        e.getMessage());
                                }
                        }
                        return createErrorResponse("API Request Failed",
                                        "Max retries reached for summarization or unexpected flow.");

                } catch (Exception e) {
                        log.error("[{}] Unexpected error during combined summarization setup: {}",
                                        metadataId, e.getMessage(), e);
                        return createErrorResponse("Unexpected Summarization Setup Error",
                                        e.getMessage());
                }
        }

        private String extractTextFromStandardResponse(String rawResponse)
                        throws JsonProcessingException, ApiException {
                JsonNode jsonNode = objectMapper.readTree(rawResponse);

                if (jsonNode.has("error")) {
                        String errorMessage = jsonNode.path("error").path("message")
                                        .asText("Unknown API error");
                        log.error("Gemini API returned an error object: {}", rawResponse);
                        throw new ApiException("Gemini API Error: " + errorMessage);
                }

                if (jsonNode.has("promptFeedback")
                                && jsonNode.path("promptFeedback").has("blockReason")) {
                        String reason = jsonNode.path("promptFeedback").path("blockReason")
                                        .asText("Unknown");
                        String safetyRatings = jsonNode.path("promptFeedback").path("safetyRatings")
                                        .toString();
                        log.warn("Gemini API request blocked due to prompt feedback. Reason: {}. Safety Ratings: {}",
                                        reason, safetyRatings);
                        throw new ApiException(
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
                                        throw new ApiException(
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
                                        String jsonResponse = firstPart.path("text").asText();
                                        String transcript = extractTranscriptFromJsonResponse(
                                                        jsonResponse);
                                        if (transcript != null) {
                                                return transcript;
                                        }

                                        try {
                                                JsonNode responseNode =
                                                                objectMapper.readTree(jsonResponse);
                                                if (responseNode.has("summaryText")) {
                                                        log.info("Found valid summarization JSON structure with summaryText field");
                                                        return jsonResponse;
                                                }
                                        } catch (JsonProcessingException e) {
                                                log.warn("Could not parse inner JSON response: {}",
                                                                e.getMessage());
                                        }
                                }
                        } else {
                                log.warn("First candidate in Gemini response does not contain expected content/parts structure. Candidate: {}",
                                                firstCandidate.toString());
                                throw new ApiException(
                                                "Invalid response structure: Missing 'content' or 'parts' in candidate.");
                        }
                }

                log.warn("Could not extract text from Gemini standard response structure (no valid candidates found): {}",
                                rawResponse);
                throw new ApiException("No valid candidates found in Gemini response.");
        }

        private String parseErrorDetails(HttpClientErrorException e) {
                return parseErrorDetailsFromString(e.getResponseBodyAsString());
        }

        private String parseErrorDetailsFromString(String responseBodyString) {
                try {
                        JsonNode errorNode = objectMapper.readTree(responseBodyString);
                        if (errorNode.has("error") && errorNode.path("error").has("message")) {
                                return errorNode.path("error").path("message")
                                                .asText(responseBodyString);
                        }
                } catch (JsonProcessingException jsonEx) {
                        log.warn("Could not parse Gemini error response body as JSON: {}",
                                        jsonEx.getMessage());
                }
                return responseBodyString;
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

                String simpleTextUrl = UriComponentsBuilder
                                .fromUriString(API_BASE_URL + GENERATE_CONTENT_PATH)
                                .queryParam("key", apiKey).buildAndExpand(TRANSCRIPTION_MODEL_NAME)
                                .toUriString();

                log.info("Calling Gemini Simple Text API (Model: {})", TRANSCRIPTION_MODEL_NAME);

                for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                        try {
                                ResponseEntity<String> response = restTemplate.exchange(
                                                simpleTextUrl, HttpMethod.POST, requestEntity,
                                                String.class);
                                log.info("Gemini Simple Text API call successful on attempt {} using model {}. Status: {}",
                                                attempt, TRANSCRIPTION_MODEL_NAME,
                                                response.getStatusCode());

                                String responseBody = response.getBody();
                                if (responseBody == null || responseBody.isBlank()) {
                                        log.warn("Gemini Simple Text API returned successful status ({}) but empty body.",
                                                        response.getStatusCode());
                                        if (attempt == MAX_RETRIES) {
                                                return createErrorResponse("Empty Response",
                                                                "API returned success status but no content after retries.");
                                        }
                                        sleepForRetry(attempt);
                                        continue;
                                }

                                String extractedText =
                                                extractTextFromStandardResponse(responseBody);
                                log.info("Successfully extracted simple text response (length: {}).",
                                                extractedText.length());
                                return extractedText;

                        } catch (ApiException e) {
                                log.error("Gemini Simple Text API Error on attempt {}: {}", attempt,
                                                e.getMessage(), e);
                                return createErrorResponse("Simple Text API Error", e.getMessage());
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
                                sleepForRetry(attempt);
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
                        } catch (JsonProcessingException e) {
                                log.error("Error parsing successful Gemini Simple Text response JSON on attempt {}: {}",
                                                attempt, e.getMessage(), e);
                                return createErrorResponse("Response Parsing Error",
                                                e.getMessage());
                        } catch (RuntimeException e) {
                                log.error("Unexpected runtime error during Gemini Simple Text API processing on attempt {}: {}",
                                                attempt, e.getMessage(), e);
                                return createErrorResponse("Simple Text Processing Error",
                                                e.getMessage());
                        }
                }
                return createErrorResponse("API Request Failed",
                                "Max retries reached for simple text or unexpected flow.");
        }

        private String getAudioMimeType(String fileName) {
                if (fileName == null || fileName.isEmpty()) {
                        log.warn("Filename is null or empty, defaulting MIME type to audio/mpeg");
                        return "audio/mpeg";
                }
                String lowercaseFileName = fileName.toLowerCase();
                if (lowercaseFileName.endsWith(".mp3"))
                        return "audio/mp3";
                if (lowercaseFileName.endsWith(".wav"))
                        return "audio/wav";
                if (lowercaseFileName.endsWith(".aiff") || lowercaseFileName.endsWith(".aif"))
                        return "audio/aiff";
                if (lowercaseFileName.endsWith(".flac"))
                        return "audio/flac";
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
                if (lowercaseFileName.endsWith(".amr"))
                        return "audio/amr";
                log.warn("Unknown or potentially unsupported audio file extension in '{}', defaulting MIME type to audio/mpeg. Verify Gemini API support.",
                                fileName);
                return "audio/mpeg";
        }

        private void sleepForRetry(int attempt) {
                try {
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("API call interrupted during retry wait.", ie);
                        throw new RuntimeException("API call interrupted during retry wait.", ie);
                }
        }

        public String generateTranscriptOnlySummary(String transcriptText, String metadataId) {
                log.info("[{}] Starting transcript-only summarization.", metadataId);

                if (transcriptText == null || transcriptText.isBlank()) {
                        log.error("[{}] Transcript text is null or blank. Cannot generate summary.",
                                        metadataId);
                        return createErrorResponse("Input Error", "Transcript text is missing.");
                }

                try {
                        HttpHeaders generateHeaders = new HttpHeaders();
                        generateHeaders.setContentType(MediaType.APPLICATION_JSON);

                        String prompt = """
                                        Analyze the provided lecture transcript carefully.
                                        Generate a comprehensive, concise, well-structured summary in Markdown in the `summaryText` field. Use headings (##) for main sections and bullet points (* or -) for details. Focus on core arguments, findings, definitions, and conclusions presented in the transcript.
                                        Identify the main key points or action items discussed and list them as distinct strings in the `keyPoints` array.
                                        List the 3-5 most important topics or keywords suitable for searching related content in the `topics` array.
                                        Identify important **terms, concepts, acronyms, proper nouns (people, places, organizations mentioned), and technical vocabulary** discussed in the transcript. For each, provide a concise definition relevant to the context. Structure this as an array of objects in the `glossary` field, where each object has a `term` (string) and a `definition` (string). Aim for comprehensive coverage of potentially unfamiliar items for a learner.
                                        Stay strictly within the boundaries of what is explicitly mentioned in the transcript. Do not add external information, assumptions, or hallucinations.
                                        Ensure the entire output strictly adheres to the provided JSON schema. Output only the JSON object.
                                        """;

                        Map<String, Object> promptPart = Map.of("text", prompt);
                        Map<String, Object> transcriptPart = Map.of("text", transcriptText);

                        List<Object> parts = List.of(promptPart, transcriptPart);
                        Map<String, Object> content = Map.of("parts", parts);
                        List<Object> contents = List.of(content);

                        Map<String, Object> generationConfig = new HashMap<>();
                        generationConfig.put("temperature", 0.3);
                        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS_SUMMARIZATION);
                        generationConfig.put("response_mime_type", "application/json");
                        generationConfig.put("response_schema", SUMMARY_RESPONSE_SCHEMA);

                        Map<String, Object> requestBody = new HashMap<>();
                        requestBody.put("contents", contents);
                        requestBody.put("generationConfig", generationConfig);

                        HttpEntity<Map<String, Object>> requestEntity =
                                        new HttpEntity<>(requestBody, generateHeaders);

                        String generateContentUrl = UriComponentsBuilder
                                        .fromUriString(API_BASE_URL + GENERATE_CONTENT_PATH)
                                        .queryParam("key", apiKey)
                                        .buildAndExpand(SUMMARIZATION_MODEL_NAME).toUriString();

                        log.info("[{}] Calling Gemini Summarization API (Model: {}) for transcript-only summary...",
                                        metadataId, SUMMARIZATION_MODEL_NAME);

                        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                                try {
                                        ResponseEntity<String> response = restTemplate.exchange(
                                                        generateContentUrl, HttpMethod.POST,
                                                        requestEntity, String.class);
                                        log.info("[{}] Gemini Summarization API call for transcript-only summary successful on attempt {}. Status: {}",
                                                        metadataId, attempt,
                                                        response.getStatusCode());

                                        String responseBody = response.getBody();
                                        if (responseBody == null || responseBody.isBlank()) {
                                                log.warn("[{}] Gemini Summarization API returned successful status ({}) but empty body on attempt {}.",
                                                                metadataId,
                                                                response.getStatusCode(), attempt);
                                                if (attempt == MAX_RETRIES) {
                                                        return createErrorResponse("Empty Response",
                                                                        "API returned success status but no content after retries.");
                                                }
                                                sleepForRetry(attempt);
                                                continue;
                                        }

                                        try {
                                                String extractedJsonText =
                                                                extractTextFromStandardResponse(
                                                                                responseBody);
                                                log.info("[{}] Successfully received and extracted JSON summary response for transcript-only summary (Length: {}).",
                                                                metadataId,
                                                                extractedJsonText.length());
                                                return extractedJsonText;
                                        } catch (JsonProcessingException jsonEx) {
                                                log.error("[{}] Gemini Summarization API response was not valid JSON on attempt {}. Error: {}",
                                                                metadataId, attempt,
                                                                jsonEx.getMessage());
                                                if (attempt == MAX_RETRIES) {
                                                        return createErrorResponse(
                                                                        "Invalid JSON Response",
                                                                        "API response was not valid JSON: "
                                                                                        + jsonEx.getMessage());
                                                }
                                                sleepForRetry(attempt);
                                                continue;
                                        }

                                } catch (ApiException e) {
                                        log.error("[{}] Gemini Summarization API Error on attempt {}: {}",
                                                        metadataId, attempt, e.getMessage(), e);
                                        return createErrorResponse("Summarization API Error",
                                                        e.getMessage());
                                } catch (HttpServerErrorException | ResourceAccessException e) {
                                        log.warn("[{}] Gemini Summarization API call failed on attempt {}/{} with retryable error: {}. Retrying...",
                                                        metadataId, attempt, MAX_RETRIES,
                                                        e.getMessage());
                                        if (attempt == MAX_RETRIES) {
                                                log.error("[{}] Gemini Summarization API call failed after {} attempts.",
                                                                metadataId, MAX_RETRIES, e);
                                                return createErrorResponse(
                                                                "API Request Failed (Server/Network)",
                                                                e.getMessage());
                                        }
                                        sleepForRetry(attempt);
                                } catch (HttpClientErrorException e) {
                                        log.error("[{}] Gemini Summarization API client error: {} - {}",
                                                        metadataId, e.getStatusCode(),
                                                        e.getResponseBodyAsString(), e);
                                        String details = parseErrorDetails(e);
                                        return createErrorResponse(
                                                        "API Client Error: " + e.getStatusCode(),
                                                        details);
                                } catch (RestClientResponseException e) {
                                        log.error("[{}] Gemini Summarization API REST client error: Status {}, Body: {}",
                                                        metadataId, e.getStatusCode(),
                                                        e.getResponseBodyAsString(), e);
                                        return createErrorResponse(
                                                        "API Request Failed (REST Client)",
                                                        e.getMessage());
                                } catch (RuntimeException e) {
                                        log.error("[{}] Unexpected runtime error during Gemini Summarization processing on attempt {}: {}",
                                                        metadataId, attempt, e.getMessage(), e);
                                        return createErrorResponse("Summarization Processing Error",
                                                        e.getMessage());
                                }
                        }
                        return createErrorResponse("API Request Failed",
                                        "Max retries reached for transcript-only summarization or unexpected flow.");

                } catch (Exception e) {
                        log.error("[{}] Unexpected error during transcript-only summarization: {}",
                                        metadataId, e.getMessage(), e);
                        return createErrorResponse("Summarization Error",
                                        "Unexpected error: " + e.getMessage());
                }
        }

        public String generateRecommendationsAudioOnly(String summaryText, String transcriptText,
                        String metadataId) {
                log.info("[{}] Starting recommendation generation for audio-only recording",
                                metadataId);

                if (summaryText == null || summaryText.isBlank()) {
                        log.error("[{}] Summary text is null or blank. Cannot generate recommendations.",
                                        metadataId);
                        return createErrorResponse("Input Error", "Summary text is missing.");
                }

                try {
                        HttpHeaders generateHeaders = new HttpHeaders();
                        generateHeaders.setContentType(MediaType.APPLICATION_JSON);

                        String prompt = """
                                                You are an expert educational content recommender.

                                                Based on the provided lecture summary and transcript (if available), recommend high-quality learning resources that would complement the lecture content.

                                                For each recommendation:
                                                1. Use the `title` field to provide a descriptive title that clearly indicates the content (e.g. "Introduction to Neural Networks")
                                                2. Use the `description` field to explain why this resource is relevant and how it relates to the lecture (2-3 sentences)
                                                3. Use the `url` field to provide a direct link to the resource (prefer YouTube videos, official documentation, academic papers)
                                                4. Use the `type` field to specify the type (e.g. "Video", "Article", "Tutorial", "Documentation", "Tool", "Book", "Course")
                                                5. Use the `audience` field to specify the target audience level ("Beginner", "Intermediate", "Advanced")

                                                Generate 5-7 diverse, high-quality recommendations that cover different aspects of the material.
                                                Focus on resources that either:
                                                - Clarify complex concepts from the lecture
                                                - Expand on key topics mentioned
                                                - Provide practical applications of the content
                                                - Offer visual explanations for better understanding

                                                Ensure recommendations are closely related to the lecture content. Do not include general or loosely related resources.
                                        YOUR RESPONSE MUST STRICTLY ADHERE TO THE JSON SCHEMA PROVIDED. Return only the array of recommendation objects.
                                        """;

                        Map<String, Object> promptPart = Map.of("text", prompt);
                        Map<String, Object> transcriptPart =
                                        Map.of("text", "LECTURE TRANSCRIPT: " + transcriptText);
                        Map<String, Object> summaryPart =
                                        Map.of("text", "LECTURE SUMMARY: " + summaryText);

                        List<Object> parts = new ArrayList<>();
                        parts.add(promptPart);
                        parts.add(summaryPart);

                        if (transcriptText != null && !transcriptText.isBlank()) {
                                parts.add(transcriptPart);
                                log.info("[{}] Including transcript in recommendation generation request",
                                                metadataId);
                        } else {
                                log.info("[{}] No transcript available, generating recommendations based on summary only",
                                                metadataId);
                        }

                        Map<String, Object> content = Map.of("parts", parts);
                        List<Object> contents = List.of(content);

                        Map<String, Object> generationConfig = new HashMap<>();
                        generationConfig.put("temperature", 0.3);
                        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS_SUMMARIZATION);
                        generationConfig.put("response_mime_type", "application/json");
                        generationConfig.put("response_schema", RECOMMENDATIONS_RESPONSE_SCHEMA);

                        Map<String, Object> requestBody = new HashMap<>();
                        requestBody.put("contents", contents);
                        requestBody.put("generationConfig", generationConfig);

                        HttpEntity<Map<String, Object>> requestEntity =
                                        new HttpEntity<>(requestBody, generateHeaders);

                        String generateContentUrl = UriComponentsBuilder
                                        .fromUriString(API_BASE_URL + GENERATE_CONTENT_PATH)
                                        .queryParam("key", apiKey)
                                        .buildAndExpand(SUMMARIZATION_MODEL_NAME).toUriString();

                        log.info("[{}] Calling Gemini API (Model: {}) for audio-only recommendations with schema...",
                                        metadataId, SUMMARIZATION_MODEL_NAME);

                        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                                try {
                                        ResponseEntity<String> response = restTemplate.exchange(
                                                        generateContentUrl, HttpMethod.POST,
                                                        requestEntity, String.class);
                                        log.info("[{}] Gemini API call for audio-only recommendations successful on attempt {}. Status: {}",
                                                        metadataId, attempt,
                                                        response.getStatusCode());

                                        String responseBody = response.getBody();
                                        if (responseBody == null || responseBody.isBlank()) {
                                                log.warn("[{}] Gemini API returned successful status but empty body on attempt {}.",
                                                                metadataId, attempt);
                                                if (attempt == MAX_RETRIES) {
                                                        return createErrorResponse("Empty Response",
                                                                        "API returned success status but no content after retries.");
                                                }
                                                sleepForRetry(attempt);
                                                continue;
                                        }

                                        try {
                                                String extractedJsonText =
                                                                extractTextFromStandardResponse(
                                                                                responseBody);

                                                objectMapper.readTree(extractedJsonText);

                                                log.info("[{}] Successfully received and extracted JSON recommendations response (Length: {}).",
                                                                metadataId,
                                                                extractedJsonText.length());
                                                return extractedJsonText;
                                        } catch (JsonProcessingException jsonEx) {
                                                log.error("[{}] Gemini API recommendations response was not valid JSON on attempt {}. Error: {}",
                                                                metadataId, attempt,
                                                                jsonEx.getMessage());
                                                if (attempt == MAX_RETRIES) {
                                                        return createErrorResponse(
                                                                        "Invalid JSON Response",
                                                                        "API response was not valid JSON: "
                                                                                        + jsonEx.getMessage());
                                                }
                                                sleepForRetry(attempt);
                                                continue;
                                        }

                                } catch (ApiException e) {
                                        log.error("[{}] Gemini API Error on attempt {}: {}",
                                                        metadataId, attempt, e.getMessage(), e);
                                        return createErrorResponse("API Error", e.getMessage());
                                } catch (HttpServerErrorException | ResourceAccessException e) {
                                        log.warn("[{}] Gemini API call failed on attempt {}/{} with retryable error: {}. Retrying...",
                                                        metadataId, attempt, MAX_RETRIES,
                                                        e.getMessage());
                                        if (attempt == MAX_RETRIES) {
                                                log.error("[{}] Gemini API call failed after {} attempts.",
                                                                metadataId, MAX_RETRIES, e);
                                                return createErrorResponse(
                                                                "API Request Failed (Server/Network)",
                                                                e.getMessage());
                                        }
                                        sleepForRetry(attempt);
                                } catch (HttpClientErrorException e) {
                                        log.error("[{}] Gemini API client error: {} - {}",
                                                        metadataId, e.getStatusCode(),
                                                        e.getResponseBodyAsString(), e);
                                        String details = parseErrorDetails(e);
                                        return createErrorResponse(
                                                        "API Client Error: " + e.getStatusCode(),
                                                        details);
                                } catch (RestClientResponseException e) {
                                        log.error("[{}] Gemini API REST client error: Status {}, Body: {}",
                                                        metadataId, e.getStatusCode(),
                                                        e.getResponseBodyAsString(), e);
                                        return createErrorResponse(
                                                        "API Request Failed (REST Client)",
                                                        e.getMessage());
                                } catch (RuntimeException e) {
                                        log.error("[{}] Unexpected runtime error during Gemini processing on attempt {}: {}",
                                                        metadataId, attempt, e.getMessage(), e);
                                        return createErrorResponse("Processing Error",
                                                        e.getMessage());
                                }
                        }
                        return createErrorResponse("API Request Failed",
                                        "Max retries reached for recommendations or unexpected flow.");

                } catch (Exception e) {
                        log.error("[{}] Unexpected error during recommendation generation: {}",
                                        metadataId, e.getMessage(), e);
                        return createErrorResponse("Recommendation Error",
                                        "Unexpected error: " + e.getMessage());
                }
        }

        private String extractPartialTranscriptFromIncompleteJson(String incompleteJson) {
                try {
                        int transcriptStartIndex = incompleteJson.indexOf("\"transcript\"");
                        if (transcriptStartIndex == -1) {
                                return null;
                        }

                        int contentStartIndex = incompleteJson.indexOf(":", transcriptStartIndex);
                        if (contentStartIndex == -1) {
                                return null;
                        }

                        int textStartIndex = incompleteJson.indexOf("\"", contentStartIndex);
                        if (textStartIndex == -1) {
                                return null;
                        }

                        String partialContent = incompleteJson.substring(textStartIndex + 1);

                        int closingQuoteIndex = findUnescapedQuote(partialContent);
                        if (closingQuoteIndex != -1) {
                                return partialContent.substring(0, closingQuoteIndex);
                        }

                        log.warn("Transcript text was truncated due to MAX_TOKENS. Returning partial transcript.");
                        return partialContent;
                } catch (Exception e) {
                        log.error("Error extracting partial transcript: {}", e.getMessage());
                        return null;
                }
        }

        private int findUnescapedQuote(String text) {
                boolean escaped = false;
                for (int i = 0; i < text.length(); i++) {
                        char c = text.charAt(i);
                        if (c == '\\') {
                                escaped = !escaped;
                        } else if (c == '"' && !escaped) {
                                return i;
                        } else {
                                escaped = false;
                        }
                }
                return -1;
        }

        private String extractTranscriptFromJsonResponse(String jsonResponse) {
                if (jsonResponse == null || jsonResponse.isBlank()) {
                        log.warn("Empty JSON response received");
                        return null;
                }

                try {
                        JsonNode jsonNode = objectMapper.readTree(jsonResponse);
                        if (jsonNode.has("transcript")) {
                                String transcript = jsonNode.path("transcript").asText();
                                log.info("Successfully extracted transcript from JSON response (length: {}).",
                                                transcript.length());
                                return transcript;
                        }
                        // Handle audio-only summarization case which returns a different structure
                        if (jsonNode.has("summaryText")) {
                                log.info("Found summarization response format with 'summaryText' field. Returning entire JSON structure.");
                                return jsonResponse; // Return the entire JSON structure for proper
                                                     // processing in SummarizationListenerService
                        }
                        log.warn("JSON response missing both 'transcript' and 'summaryText' fields");
                } catch (JsonProcessingException e) {
                        // Handle malformed JSON due to MAX_TOKENS truncation
                        log.warn("Error parsing transcript JSON response, attempting to extract partial content: {}",
                                        e.getMessage());

                        // Attempt to salvage partial transcript
                        String partialTranscript =
                                        extractPartialTranscriptFromIncompleteJson(jsonResponse);
                        if (partialTranscript != null && !partialTranscript.isBlank()) {
                                log.info("Successfully extracted partial transcript from truncated response (length: {}).",
                                                partialTranscript.length());
                                return partialTranscript;
                        }
                }

                return null;
        }

        private static class ApiException extends Exception {
                public ApiException(String message) {
                        super(message);
                }

                public ApiException(String message, Throwable cause) {
                        super(message, cause);
                }
        }
}

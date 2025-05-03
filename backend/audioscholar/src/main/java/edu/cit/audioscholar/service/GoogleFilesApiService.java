package edu.cit.audioscholar.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for interacting with Google Files API.
 */
@Service
public class GoogleFilesApiService {
    private static final Logger log = LoggerFactory.getLogger(GoogleFilesApiService.class);
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String FILES_API_UPLOAD_PATH = "/upload/v1beta/files";

    @Value("${google.ai.api.key}")
    private String apiKey;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public GoogleFilesApiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    public static class ApiException extends Exception {
        public ApiException(String message) {
            super(message);
        }
        
        public ApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Uploads a file to Google Files API.
     * 
     * @param filePath The path to the file to upload
     * @param mimeType The MIME type of the file
     * @param fileSize The size of the file in bytes
     * @param displayName A display name for the file
     * @return The Google Files API URI of the uploaded file
     * @throws IOException If there's an error reading the file
     * @throws ApiException If there's an error with the API call
     */
    public String uploadFile(Path filePath, String mimeType, long fileSize, String displayName)
            throws IOException, ApiException {
        String initiateUrl = UriComponentsBuilder
                .fromUriString(API_BASE_URL + FILES_API_UPLOAD_PATH)
                .queryParam("key", apiKey).toUriString();

        HttpHeaders initiateHeaders = new HttpHeaders();
        initiateHeaders.set("X-Goog-Upload-Protocol", "resumable");
        initiateHeaders.set("X-Goog-Upload-Command", "start");
        initiateHeaders.set("X-Goog-Upload-Header-Content-Length",
                        String.valueOf(fileSize));
        initiateHeaders.set("X-Goog-Upload-Header-Content-Type", mimeType);
        initiateHeaders.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> fileMetadata = Map.of("display_name", displayName != null ? displayName : "file_" + UUID.randomUUID());
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

                log.info("File upload completed. Status: {}", uploadResponse.getStatusCode());
                String responseBody = uploadResponse.getBody();

                if (responseBody == null) {
                        throw new ApiException("Upload completed but received null response body.");
                }

                JsonNode responseNode = objectMapper.readTree(responseBody);
                if (responseNode.has("file") && responseNode.get("file").has("uri")) {
                        String fileUri = responseNode.get("file").get("uri").asText();
                        if (fileUri != null && !fileUri.isBlank()) {
                                log.debug("Extracted file URI: {}", fileUri);
                                return fileUri;
                        }
                }
                log.error("Upload response did not contain expected file URI. Body: {}", responseBody);
                throw new ApiException("Upload response did not contain expected file URI.");

        } catch (RestClientException e) {
                log.error("Error uploading file bytes: {}", e.getMessage(), e);
                if (e instanceof HttpStatusCodeException) {
                        String errorBody = ((HttpStatusCodeException) e).getResponseBodyAsString();
                        log.error("API Error Response Body: {}", errorBody);
                        throw new ApiException("Error uploading file bytes: " + parseErrorDetailsFromString(errorBody), e);
                }
                throw new ApiException("Error uploading file bytes: " + e.getMessage(), e);
        } catch (JsonProcessingException e) {
                log.error("Error parsing upload response JSON: {}", e.getMessage(), e);
                throw new ApiException("Error parsing upload response JSON", e);
        }
    }
    
    private String parseErrorDetailsFromString(String responseBodyString) {
        if (responseBodyString == null || responseBodyString.isBlank()) {
            return "Empty error response";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBodyString);
            if (root.has("error")) {
                JsonNode error = root.get("error");
                if (error.has("message")) {
                    return error.get("message").asText();
                }
            }
            return responseBodyString;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse error response JSON: {}", e.getMessage());
            return responseBodyString;
        }
    }
}

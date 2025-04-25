package edu.cit.audioscholar.service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class NhostStorageService {

    private static final Logger LOGGER = Logger.getLogger(NhostStorageService.class.getName());

    private final RestTemplate restTemplate;
    private final String nhostStorageUrl;
    private final String nhostAdminSecret;
    private final ObjectMapper objectMapper;

    public NhostStorageService(RestTemplate restTemplate,
            @Value("${nhost.storage.url}") String nhostStorageUrl,
            @Value("${nhost.storage.admin-secret}") String nhostAdminSecret,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.nhostStorageUrl = nhostStorageUrl.endsWith("/v1/files") ? nhostStorageUrl
                : nhostStorageUrl + "/v1/files";
        this.nhostAdminSecret = nhostAdminSecret;
        this.objectMapper = objectMapper;

        if (this.nhostAdminSecret == null || this.nhostAdminSecret.isEmpty()
                || "${NHOST_ADMIN_SECRET}".equals(this.nhostAdminSecret)) {
            LOGGER.log(Level.WARNING,
                    "Nhost Admin Secret is not set properly. Check NHOST_ADMIN_SECRET environment variable (Needed for uploads).");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NhostFileUploadResponse {
        public String id;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NhostErrorResponse {
        public int status;
        public String message;
        public String error;
    }

    public String uploadFile(File file, String originalFilename, String contentType)
            throws IOException {
        if (file == null || !file.exists() || !file.canRead()) {
            throw new IOException("File is null, does not exist, or cannot be read: "
                    + (file != null ? file.getAbsolutePath() : "null"));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("x-hasura-admin-secret", nhostAdminSecret);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        FileSystemResource resource = new FileSystemResource(file);

        body.add("file", resource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        String filenameToLog =
                StringUtils.hasText(originalFilename) ? originalFilename : file.getName();
        LOGGER.log(Level.INFO, "Uploading file {0} ({1} bytes) from path {2} to Nhost URL: {3}",
                new Object[] {filenameToLog, file.length(), file.getAbsolutePath(),
                        nhostStorageUrl});

        try {
            ResponseEntity<String> rawResponse = restTemplate.exchange(nhostStorageUrl,
                    HttpMethod.POST, requestEntity, String.class);

            return handleNhostResponse(rawResponse);

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            handleNhostError(e);
            return null;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                    "An unexpected error occurred during Nhost file upload processing.", e);
            if (e instanceof RuntimeException && e.getMessage() != null
                    && (e.getMessage().startsWith("Failed to extract file ID") || e.getMessage()
                            .startsWith("Failed to parse Nhost success response"))) {
                throw e;
            }
            throw new RuntimeException(
                    "An unexpected error occurred during Nhost file upload processing.", e);
        }
    }


    @Deprecated
    public String uploadFile(MultipartFile file) throws IOException {
        LOGGER.log(Level.WARNING,
                "Deprecated uploadFile(MultipartFile) called. Use uploadFile(File) instead.");
        throw new UnsupportedOperationException("Use uploadFile(File, String, String) instead.");
    }

    private String handleNhostResponse(ResponseEntity<String> rawResponse) throws IOException {
        if (rawResponse.getStatusCode() == HttpStatus.CREATED && rawResponse.getBody() != null) {
            String responseBody = rawResponse.getBody();
            LOGGER.log(Level.INFO, "Received 201 CREATED response body from Nhost: {0}",
                    responseBody);
            try {
                NhostFileUploadResponse parsedResponse =
                        objectMapper.readValue(responseBody, NhostFileUploadResponse.class);
                if (parsedResponse != null && parsedResponse.id != null
                        && !parsedResponse.id.isEmpty()) {
                    LOGGER.log(Level.INFO, "Successfully parsed Nhost response. File ID: {0}",
                            parsedResponse.id);
                    return parsedResponse.id;
                } else {
                    LOGGER.log(Level.SEVERE,
                            "Parsed Nhost 201 response, but the 'id' field is missing, null, or empty. Parsed object: {0}",
                            parsedResponse);
                    throw new RuntimeException(
                            "Failed to extract file ID from Nhost response structure.");
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE,
                        "Failed to parse Nhost 201 response body into expected structure. Body was: "
                                + responseBody,
                        e);
                throw new RuntimeException("Failed to parse Nhost success response.", e);
            }
        } else {
            LOGGER.log(Level.SEVERE, "Failed to upload file to Nhost. Status: {0}, Body: {1}",
                    new Object[] {rawResponse.getStatusCode(), rawResponse.getBody()});
            throw new RuntimeException(
                    "Failed to upload file to Nhost. Status: " + rawResponse.getStatusCode());
        }
    }

    private void handleNhostError(HttpStatusCodeException e) {
        String errorBody = e.getResponseBodyAsString();
        String errorMessage = "Failed to upload file to Nhost. Status: " + e.getStatusCode();
        try {
            NhostErrorResponse errorResponse =
                    objectMapper.readValue(errorBody, NhostErrorResponse.class);
            errorMessage +=
                    ", Error: " + errorResponse.error + ", Message: " + errorResponse.message;
        } catch (Exception parseException) {
            errorMessage += ", Response Body: " + errorBody;
        }
        LOGGER.log(Level.SEVERE, errorMessage, e);
        throw new RuntimeException(errorMessage, e);
    }



    public String downloadFileAsBase64(String fileId) throws IOException {
        if (fileId == null || fileId.isEmpty()) {
            LOGGER.log(Level.SEVERE, "Cannot download file with null or empty fileId.");
            throw new IllegalArgumentException("File ID cannot be null or empty.");
        }
        String downloadUrl = getPublicUrl(fileId);
        LOGGER.log(Level.INFO, "Attempting to download PUBLIC file from Nhost URL: {0}",
                downloadUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_OCTET_STREAM));

        RequestEntity<Void> requestEntity;
        try {
            requestEntity = RequestEntity.get(new URI(downloadUrl)).headers(headers).build();
        } catch (URISyntaxException e) {
            LOGGER.log(Level.SEVERE, "Invalid URI syntax for download URL: " + downloadUrl, e);
            throw new RuntimeException("Failed to create download URI.", e);
        }

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(requestEntity, byte[].class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                byte[] fileBytes = response.getBody();
                LOGGER.log(Level.INFO,
                        "Successfully downloaded {0} bytes from Nhost for file ID: {1}",
                        new Object[] {fileBytes.length, fileId});
                return Base64.getEncoder().encodeToString(fileBytes);
            } else {
                handleDownloadErrorResponse(response.getStatusCode(), fileId);
                return null;
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            handleDownloadErrorResponse(e.getStatusCode(), fileId);
            return null;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                    "An unexpected error occurred during Nhost file download for file ID: "
                            + fileId,
                    e);
            throw new RuntimeException("An unexpected error occurred during Nhost file download.",
                    e);
        }
    }

    private void handleDownloadErrorResponse(HttpStatusCode statusCode, String fileId) {
        if (statusCode == HttpStatus.FORBIDDEN) {
            LOGGER.log(Level.SEVERE,
                    "Failed to download file from Nhost (Status: 403 FORBIDDEN). Ensure 'public' role has 'select' permission on storage.files table in Nhost for file ID: "
                            + fileId);
            throw new RuntimeException(
                    "Failed to download file from Nhost (403 Forbidden). Check public permissions.");
        } else {
            LOGGER.log(Level.SEVERE,
                    "Failed to download file from Nhost. Status: {0} for file ID: {1}",
                    new Object[] {statusCode, fileId});
            throw new RuntimeException("Failed to download file from Nhost. Status: " + statusCode);
        }
    }


    public String getPublicUrl(String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            LOGGER.log(Level.WARNING, "Generating public URL requested for null or empty fileId.");
            throw new IllegalArgumentException(
                    "File ID cannot be null or empty when generating public URL.");
        }
        String baseUrl = nhostStorageUrl.replace("/v1/files", "");
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return baseUrl + "/v1/files/" + fileId;
    }
}

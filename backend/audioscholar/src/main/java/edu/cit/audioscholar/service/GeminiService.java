package edu.cit.audioscholar.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*") // Allow cross-origin requests
@RestController
public class GeminiService {

    @Value("${google.ai.api.key}") // Load API key from application.properties
    private String apiKey;

    private final String API_URL = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-pro:generateContent";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper(); // JSON parser

    public String callGeminiAPI(String inputText) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
    
        // Request body format
        Map<String, Object> requestBody = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", inputText))))
        );
    
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
    
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                API_URL + "?key=" + apiKey, HttpMethod.POST, requestEntity, String.class
            );

            String rawResponse = response.getBody();
            JsonNode jsonNode = objectMapper.readTree(rawResponse);

            // Validate response structure
            if (!jsonNode.has("candidates") || jsonNode.path("candidates").isEmpty()) {
                // Create error response using ObjectMapper
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("error", "No valid response from Gemini API");
                return objectMapper.writeValueAsString(errorResponse);
            }

            JsonNode contentNode = jsonNode.path("candidates").get(0).path("content").path("parts").get(0).path("text");

            if (contentNode.isMissingNode()) {
                // Create error response using ObjectMapper
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("error", "Unexpected response format");
                return objectMapper.writeValueAsString(errorResponse);
            }

            // Create success response using ObjectMapper
            ObjectNode successResponse = objectMapper.createObjectNode();
            successResponse.put("text", contentNode.asText());
            return objectMapper.writeValueAsString(successResponse); // Properly escaped JSON
        } 
        catch (HttpStatusCodeException e) {
            // Create error response using ObjectMapper
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "API Request Failed");
            errorResponse.put("status", e.getStatusCode().toString());
            errorResponse.put("message", e.getResponseBodyAsString());
            try {
                return objectMapper.writeValueAsString(errorResponse);
            } catch (Exception ex) {
                return "{\"error\": \"Failed to serialize error response\"}";
            }
        } 
        catch (ResourceAccessException e) { // Handles timeout or unreachable server
            // Create error response using ObjectMapper
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "Timeout or Network Issue");
            errorResponse.put("details", "Failed to reach Gemini API. Please check your internet or try again later.");
            try {
                return objectMapper.writeValueAsString(errorResponse);
            } catch (Exception ex) {
                return "{\"error\": \"Failed to serialize error response\"}";
            }
        } 
        catch (Exception e) {
            // Create error response using ObjectMapper
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "Internal Server Error");
            errorResponse.put("details", e.getMessage());
            try {
                return objectMapper.writeValueAsString(errorResponse);
            } catch (Exception ex) {
                return "{\"error\": \"Failed to serialize error response\"}";
            }
        }
    }

    @GetMapping("/test")
    public ResponseEntity<String> testGemini(@RequestParam String prompt) {
        String response = callGeminiAPI(prompt);
        return ResponseEntity.ok(response);
    }
}
package edu.cit.audioscholar.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
                return "{\"error\": \"No valid response from Gemini API\"}";
            }

            JsonNode contentNode = jsonNode.path("candidates").get(0).path("content").path("parts").get(0).path("text");

            if (contentNode.isMissingNode()) {
                return "{\"error\": \"Unexpected response format\"}";
            }

            return "{\"text\": \"" + contentNode.asText() + "\"}";  // Ensure valid JSON response
        } 
        catch (HttpStatusCodeException e) {
            return "{\"error\": \"API Request Failed\", \"status\": \"" + e.getStatusCode() + "\", \"message\": \"" + e.getResponseBodyAsString() + "\"}";
        } 
        catch (ResourceAccessException e) { // Handles timeout or unreachable server
            return "{\"error\": \"Timeout or Network Issue\", \"details\": \"Failed to reach Gemini API. Please check your internet or try again later.\"}";
        } 
        catch (Exception e) {
            return "{\"error\": \"Internal Server Error\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }

    @GetMapping("/test")
    public ResponseEntity<String> testGemini(@RequestParam String prompt) {
        String response = callGeminiAPI(prompt);
        return ResponseEntity.ok(response);
    }
}

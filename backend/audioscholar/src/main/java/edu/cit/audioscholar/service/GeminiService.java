package edu.cit.audioscholar.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.List;

@Service
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
    
        // Correct request body format
        Map<String, Object> requestBody = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", inputText))))
        );
    
        // Create request entity
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
    
        try {
            // Send request
            ResponseEntity<String> response = restTemplate.exchange(API_URL + "?key=" + apiKey, HttpMethod.POST, requestEntity, String.class);
            String rawResponse = response.getBody();
    
            // Convert response to valid JSON
            JsonNode jsonNode = objectMapper.readTree(rawResponse);
    
            // Extract text from the response
            JsonNode contentNode = jsonNode.path("candidates").get(0).path("content").path("parts").get(0).path("text");
            return contentNode.asText();  // This will return just "9 + 10 = 19"
        } catch (Exception e) {
            // Handle errors gracefully
            return "{\"error\": \"Failed to process response\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }

    // REST endpoint for testing Gemini API
    @GetMapping("/test")
    public String testGemini(@RequestParam String prompt) {
        return callGeminiAPI(prompt);
    }
}

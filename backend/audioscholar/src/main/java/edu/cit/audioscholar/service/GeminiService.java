package edu.cit.audioscholar.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
@CrossOrigin(origins = "*")
@RestController
public class GeminiService {

    @Value("${google.ai.api.key}")
    private String apiKey;

    private final String API_URL = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-pro:generateContent";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String callGeminiAPI(String inputText) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
    
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

            if (!jsonNode.has("candidates") || jsonNode.path("candidates").isEmpty()) {
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("error", "No valid response from Gemini API");
                return objectMapper.writeValueAsString(errorResponse);
            }

            JsonNode contentNode = jsonNode.path("candidates").get(0).path("content").path("parts").get(0).path("text");

            if (contentNode.isMissingNode()) {
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("error", "Unexpected response format");
                return objectMapper.writeValueAsString(errorResponse);
            }

            ObjectNode successResponse = objectMapper.createObjectNode();
            successResponse.put("text", contentNode.asText());
            return objectMapper.writeValueAsString(successResponse);
        } 
        catch (Exception e) {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "API Request Failed");
            errorResponse.put("details", e.getMessage());
            try {
                return objectMapper.writeValueAsString(errorResponse);
            } catch (Exception ex) {
                return "{\"error\": \"Failed to serialize error response\"}";
            }
        }
    }
    
    public String callGeminiAPIWithAudio(String promptText, String base64Audio, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Create parts for the multimodal request (text + audio)
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", promptText);
        
        Map<String, Object> audioPart = new HashMap<>();
        audioPart.put("inline_data", Map.of(
            "mime_type", getAudioMimeType(fileName),
            "data", base64Audio
        ));
        
        List<Object> parts = new ArrayList<>();
        parts.add(textPart);
        parts.add(audioPart);
        
        Map<String, Object> content = Map.of("parts", parts);
        List<Object> contents = new ArrayList<>();
        contents.add(content);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", contents);
        
        // Set generation config for better response formatting
        requestBody.put("generationConfig", Map.of(
            "temperature", 0.1,
            "topP", 0.8,
            "topK", 40,
            "maxOutputTokens", 4096
        ));
        
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                API_URL + "?key=" + apiKey, HttpMethod.POST, requestEntity, String.class
            );
    
            String rawResponse = response.getBody();
            JsonNode jsonNode = objectMapper.readTree(rawResponse);
    
            if (!jsonNode.has("candidates") || jsonNode.path("candidates").isEmpty()) {
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("error", "No valid response from Gemini API");
                return objectMapper.writeValueAsString(errorResponse);
            }
    
            JsonNode contentNode = jsonNode.path("candidates").get(0).path("content").path("parts").get(0).path("text");
    
            if (contentNode.isMissingNode()) {
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("error", "Unexpected response format");
                return objectMapper.writeValueAsString(errorResponse);
            }
    
            ObjectNode successResponse = objectMapper.createObjectNode();
            successResponse.put("text", contentNode.asText());
            return objectMapper.writeValueAsString(successResponse);
        } 
        catch (Exception e) {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "API Request Failed");
            errorResponse.put("details", e.getMessage());
            try {
                return objectMapper.writeValueAsString(errorResponse);
            } catch (Exception ex) {
                return "{\"error\": \"Failed to serialize error response\"}";
            }
        }
    }
    
    private String getAudioMimeType(String fileName) {
        String lowercaseFileName = fileName.toLowerCase();
        if (lowercaseFileName.endsWith(".mp3")) {
            return "audio/mp3";
        } else if (lowercaseFileName.endsWith(".wav")) {
            return "audio/wav";
        } else if (lowercaseFileName.endsWith(".m4a")) {
            return "audio/m4a";
        } else if (lowercaseFileName.endsWith(".ogg")) {
            return "audio/ogg";
        } else {
            // Default
            return "audio/mpeg";
        }
    }

    @GetMapping("/test")
    public ResponseEntity<String> testGemini(@RequestParam String prompt) {
        String response = callGeminiAPI(prompt);
        return ResponseEntity.ok(response);
    }
}
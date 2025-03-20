package edu.cit.audioscholar.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.audioscholar.service.GeminiService;

import java.util.Map;

@RestController
@RequestMapping("/api/gemini")
public class GeminiController {

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private ObjectMapper objectMapper; // Ensures proper JSON formatting

    @GetMapping("/test")
    public ResponseEntity<?> testGemini(@RequestParam String prompt) {
        try {
            String rawResponse = geminiService.callGeminiAPI(prompt);
            
            // Ensure the response is properly formatted as JSON
            Map<String, Object> jsonResponse = objectMapper.readValue(rawResponse, Map.class);
            
            return ResponseEntity.ok(jsonResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Invalid JSON Response", "details", e.getMessage()));
        }
    }
}

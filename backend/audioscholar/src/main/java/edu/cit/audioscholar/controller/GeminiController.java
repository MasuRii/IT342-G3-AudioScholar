package edu.cit.audioscholar.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.cit.audioscholar.service.GeminiService;

@RestController
@RequestMapping("/api/gemini")
public class GeminiController {

    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiController(GeminiService geminiService, ObjectMapper objectMapper) {
        this.geminiService = geminiService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/test")
    public ResponseEntity<?> testGemini(@RequestParam String prompt) {
        try {
            String rawResponse = geminiService.callGeminiAPI(prompt);
            Map<String, Object> jsonResponse = objectMapper.readValue(rawResponse, Map.class);
            return ResponseEntity.ok(jsonResponse);
        } catch (JsonProcessingException e) {
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Invalid JSON response received from service", 
                        "details", e.getMessage()
                        ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Error calling Gemini service", 
                        "details", e.getMessage()
                        ));
        }
    }
}
package edu.cit.audioscholar.controller;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.audioscholar.service.GeminiService;

@RestController
@RequestMapping("/api/gemini")
public class GeminiController {

    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    public GeminiController(GeminiService geminiService, ObjectMapper objectMapper) {
        this.geminiService = geminiService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/test")
    public ResponseEntity<?> testGemini(@RequestParam String prompt) {
        try {
            String textResponse = geminiService.callSimpleTextAPI(prompt);

            if (textResponse.trim().startsWith("{\"error\":")) {
                try {
                    Map<String, Object> errorJson = objectMapper.readValue(textResponse,
                            new TypeReference<Map<String, Object>>() {});
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorJson);
                } catch (JsonProcessingException jsonEx) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Gemini service returned an unparsable error",
                                    "details", textResponse));
                }
            }

            return ResponseEntity.ok(Map.of("response", textResponse));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error",
                    "Error processing Gemini request in controller", "details", e.getMessage()));
        }
    }
}

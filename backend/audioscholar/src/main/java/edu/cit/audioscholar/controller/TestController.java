package edu.cit.audioscholar.controller;

import edu.cit.audioscholar.service.FirebaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private FirebaseService firebaseService;

    @PostMapping("/firebase")
    public ResponseEntity<String> testFirebase() {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("testField", "Hello Firebase!");
            data.put("timestamp", System.currentTimeMillis());
            
            String result = firebaseService.saveData("test", "test-document", data);
            return ResponseEntity.ok("Data saved successfully at: " + result);
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
    
    @GetMapping("/firebase")
    public ResponseEntity<?> getTestData() {
        try {
            Object data = firebaseService.getData("test", "test-document");
            return ResponseEntity.ok(data);
        } catch (ExecutionException | InterruptedException e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}
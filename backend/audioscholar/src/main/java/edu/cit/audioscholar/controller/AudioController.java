package edu.cit.audioscholar.controller;

import edu.cit.audioscholar.model.Summary;
import edu.cit.audioscholar.service.AudioProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private final AudioProcessingService audioProcessingService;

    public AudioController(AudioProcessingService audioProcessingService) {
        this.audioProcessingService = audioProcessingService;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadAudio(@RequestParam("file") MultipartFile file) {
        try {
            byte[] audioData = file.getBytes();
            String fileName = file.getOriginalFilename();
            String filePath = audioProcessingService.processAudioFile(audioData, fileName);
            return ResponseEntity.ok("Audio file uploaded successfully: " + filePath);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error processing audio file: " + e.getMessage());
        }
    }
    
    @PostMapping("/summarize")
    public ResponseEntity<?> uploadAndSummarize(@RequestParam("file") MultipartFile file) {
        try {
            byte[] audioData = file.getBytes();
            String fileName = file.getOriginalFilename();
            Summary summary = audioProcessingService.processAndSummarize(audioData, fileName);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error processing and summarizing audio: " + e.getMessage());
        }
    }
}
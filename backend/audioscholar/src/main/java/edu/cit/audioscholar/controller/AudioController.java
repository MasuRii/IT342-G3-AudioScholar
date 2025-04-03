package edu.cit.audioscholar.controller;

import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.Summary;
import edu.cit.audioscholar.service.AudioProcessingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private final AudioProcessingService audioProcessingService;

    public AudioController(AudioProcessingService audioProcessingService) {
        this.audioProcessingService = audioProcessingService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description) {
        try {
            // Log to ensure title and description are received
            System.out.println("Received Title: " + title);   // Log Title
            System.out.println("Received Description: " + description);   // Log Description

            byte[] audioData = file.getBytes();
            String fileName = file.getOriginalFilename();
            
            // Check the values of title and description again
            String audioId = audioProcessingService.processAudioFile(audioData, fileName, title, description);

            URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath().path("/api/audio/{id}")
                .buildAndExpand(audioId).toUri();

            return ResponseEntity.created(location).body(Map.of(
                    "message", "Audio file uploaded successfully",
                    "audioId", audioId
            ));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Error processing audio file",
                    "details", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "An unexpected error occurred during upload",
                    "details", e.getMessage()
            ));
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Error processing and summarizing audio",
                    "details", e.getMessage()
            ));
        }
    }

    @GetMapping
    public ResponseEntity<List<AudioMetadata>> listAllAudio() {
        try {
            List<AudioMetadata> audioMetadataList = audioProcessingService.getAllAudioMetadataList();
            return ResponseEntity.ok(audioMetadataList);
        } catch (ExecutionException e) {
            System.err.println("Error retrieving audio metadata from Firebase: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList()); // Or a more specific error response
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Re-interrupt the current thread
            System.err.println("Thread interrupted while retrieving audio metadata: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList()); // Or a more specific error response
        }
    }

    @DeleteMapping("/{audioId}")
    public ResponseEntity<Void> deleteAudio(@PathVariable String audioId) {
        boolean deleted = audioProcessingService.deleteAudio(audioId);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}

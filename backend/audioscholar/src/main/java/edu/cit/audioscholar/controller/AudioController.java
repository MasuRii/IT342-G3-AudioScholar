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

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private final AudioProcessingService audioProcessingService;

    public AudioController(AudioProcessingService audioProcessingService) {
        this.audioProcessingService = audioProcessingService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadAudio(@RequestParam("file") MultipartFile file) {
        try {
            byte[] audioData = file.getBytes();
            String fileName = file.getOriginalFilename();
            String audioId = audioProcessingService.processAudioFile(audioData, fileName); 

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
        List<AudioMetadata> audioMetadataList = audioProcessingService.getAllAudioMetadataList(); 
        return ResponseEntity.ok(audioMetadataList);
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
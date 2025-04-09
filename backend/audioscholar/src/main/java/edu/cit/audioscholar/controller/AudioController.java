package edu.cit.audioscholar.controller;

import edu.cit.audioscholar.exception.InvalidAudioFileException;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.service.AudioProcessingService;
import edu.cit.audioscholar.service.RecordingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;


@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private static final Logger LOGGER = Logger.getLogger(AudioController.class.getName());
    private final RecordingService recordingService;
    private final AudioProcessingService audioProcessingService;

    public AudioController(RecordingService recordingService, AudioProcessingService audioProcessingService) {
        this.recordingService = recordingService;
        this.audioProcessingService = audioProcessingService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description
    ) {
        String userId = "placeholder_user_id";
        LOGGER.log(Level.INFO, "Using User ID: {0}", userId);

        if (file.isEmpty()) {
            LOGGER.log(Level.WARNING, "Upload attempt failed: File is empty.");
            return ResponseEntity.badRequest().body("File cannot be empty.");
        }

        try {
            LOGGER.log(Level.INFO, "Received upload request for file: {0}", file.getOriginalFilename());
            AudioMetadata savedMetadata = recordingService.uploadAudioFile(file, title, description, userId);
            LOGGER.log(Level.INFO, "Successfully processed upload for file: {0}, Metadata ID: {1}",
                    new Object[]{file.getOriginalFilename(), savedMetadata.getId()});
            return ResponseEntity.status(HttpStatus.CREATED).body(savedMetadata);
        } catch (InvalidAudioFileException e) {
            LOGGER.log(Level.WARNING, "Invalid audio file uploaded: {0}, Reason: {1}", new Object[]{file.getOriginalFilename(), e.getMessage()});
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IOException during file processing/upload: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing file: " + e.getMessage());
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "Error saving metadata to Firestore: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error saving metadata: " + e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "RuntimeException during upload: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error during upload: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during upload: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
        }
    }

    @GetMapping("/metadata")
    public ResponseEntity<?> getAllMetadata() {
        try {
            List<AudioMetadata> metadataList = audioProcessingService.getAllAudioMetadataList();
            return ResponseEntity.ok(metadataList);
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "Error retrieving metadata list: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving metadata.");
        }
    }

    @DeleteMapping("/metadata/{id}")
    public ResponseEntity<?> deleteMetadata(@PathVariable String id) {
        LOGGER.log(Level.INFO, "Received request to delete metadata with ID: {0}", id);
        boolean success = audioProcessingService.deleteAudioMetadata(id);
        if (success) {
             return ResponseEntity.noContent().build();
        } else {
            LOGGER.log(Level.WARNING, "Failed to delete metadata with ID: {0}", id);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete metadata.");
        }
    }
}
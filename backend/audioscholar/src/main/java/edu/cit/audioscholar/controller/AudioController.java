package edu.cit.audioscholar.controller;

import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.service.AudioProcessingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;


@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private static final Logger LOGGER = Logger.getLogger(AudioController.class.getName());
    private final AudioProcessingService audioProcessingService;

    private static final List<String> ALLOWED_AUDIO_TYPES = Arrays.asList(
            "audio/mpeg",
            "audio/mp3",
            "audio/wav",
            "audio/x-wav",
            "audio/aac",
            "audio/ogg",
            "audio/flac",
            "audio/aiff",
            "audio/x-aiff"
    );

    public AudioController(AudioProcessingService audioProcessingService) {
        this.audioProcessingService = audioProcessingService;
    }

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
             LOGGER.log(Level.WARNING, "Upload attempt failed: User not authenticated.");
             return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not authenticated.");
        }
        String userId = authentication.getName();
        LOGGER.log(Level.INFO, "Processing upload for authenticated User ID: {0}", userId);

        if (file.isEmpty()) {
            LOGGER.log(Level.WARNING, "Upload attempt failed for user {0}: File is empty.", userId);
            return ResponseEntity.badRequest().body("File cannot be empty.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AUDIO_TYPES.contains(contentType.toLowerCase())) {
             LOGGER.log(Level.WARNING, "Upload attempt failed for user {0}: Invalid file type '{1}'. Allowed types: {2}",
                     new Object[]{userId, contentType, ALLOWED_AUDIO_TYPES});
             return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                     .body("Invalid file type. Allowed types: " + ALLOWED_AUDIO_TYPES);
        }

        try {
            LOGGER.log(Level.INFO, "Received valid upload request for file: {0} from user: {1}",
                       new Object[]{file.getOriginalFilename(), userId});
            AudioMetadata savedMetadata = audioProcessingService.uploadAndSaveMetadata(file, title, description, userId);
            LOGGER.log(Level.INFO, "Successfully processed upload for file: {0}, Metadata ID: {1}, User ID: {2}",
                    new Object[]{file.getOriginalFilename(), savedMetadata.getId(), userId});
            return ResponseEntity.status(HttpStatus.CREATED).body(savedMetadata);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IOException during file processing/upload for user " + userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing file.");
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "Error saving metadata to Firestore for user " + userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error saving metadata.");
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "RuntimeException during upload for user " + userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred during upload.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during upload for user " + userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
        }
    }


    @GetMapping("/metadata")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyMetadata() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication.getName();

        try {
            List<AudioMetadata> metadataList = audioProcessingService.getAudioMetadataListForUser(userId);
            return ResponseEntity.ok(metadataList);
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "Error retrieving metadata list for user " + userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving metadata.");
        } catch (Exception e) {
             LOGGER.log(Level.SEVERE, "Unexpected error retrieving metadata for user " + userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
        }
    }

    @DeleteMapping("/metadata/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteMetadata(@PathVariable String id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication.getName();

        LOGGER.log(Level.INFO, "User {0} requesting deletion of metadata with ID: {1}", new Object[]{userId, id});

        try {
            AudioMetadata metadata = audioProcessingService.getAudioMetadataById(id);

            if (metadata == null) {
                LOGGER.log(Level.WARNING, "Metadata not found for ID: {0}, requested by user {1}", new Object[]{id, userId});
                return ResponseEntity.notFound().build();
            }

            if (metadata.getUserId() == null || !metadata.getUserId().equals(userId)) {
                 LOGGER.log(Level.WARNING, "User {0} attempted to delete metadata {1} owned by user {2}",
                            new Object[]{userId, id, metadata.getUserId()});
                 return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have permission to delete this resource.");
            }

            boolean success = audioProcessingService.deleteAudioMetadata(id);
            if (success) {
                 LOGGER.log(Level.INFO, "Successfully deleted metadata {0} by user {1}", new Object[]{id, userId});
                 return ResponseEntity.noContent().build();
            } else {
                LOGGER.log(Level.SEVERE, "Failed to delete metadata {0} by user {1} after authorization check.", new Object[]{id, userId});
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete metadata after authorization.");
            }
        } catch (ExecutionException | InterruptedException e) {
             Thread.currentThread().interrupt();
             LOGGER.log(Level.SEVERE, "Error during metadata deletion process for ID " + id + ", requested by user " + userId, e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error during deletion process.");
        } catch (Exception e) {
             LOGGER.log(Level.SEVERE, "Unexpected error during metadata deletion for ID " + id + ", requested by user " + userId, e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred during deletion.");
        }
    }
}
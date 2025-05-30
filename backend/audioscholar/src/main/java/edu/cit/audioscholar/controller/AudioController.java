package edu.cit.audioscholar.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.exception.InvalidAudioFileException;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.service.AudioProcessingService;

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private static final Logger log = LoggerFactory.getLogger(AudioController.class);
    private final AudioProcessingService audioProcessingService;

    private static final Set<String> ALLOWED_AUDIO_TYPES = Set.of("audio/mpeg", "audio/mp3",
            "audio/wav", "audio/x-wav", "audio/aac", "audio/x-aac", "audio/ogg", "audio/flac",
            "audio/x-flac", "audio/aiff", "audio/x-aiff", "audio/vnd.dlna.adts");
    private static final List<String> ALLOWED_POWERPOINT_TYPES = Arrays.asList(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint");
    private static final int DEFAULT_PAGE_SIZE = 20;

    public AudioController(AudioProcessingService audioProcessingService) {
        this.audioProcessingService = audioProcessingService;
    }

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadAudio(@RequestParam("audioFile") MultipartFile audioFile,
            @RequestParam(value = "powerpointFile", required = false) MultipartFile powerpointFile,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description) {
        log.info("Received request to /api/audio/upload with audio and potentially PowerPoint");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Upload attempt failed: User not authenticated.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not authenticated.");
        }
        String userId = authentication.getName();
        log.info("Processing upload for authenticated User ID: {}", userId);

        if (audioFile.isEmpty()) {
            log.warn("Upload attempt failed for user {}: Audio file is empty.", userId);
            return ResponseEntity.badRequest().body("Audio file cannot be empty.");
        }
        String audioContentType = audioFile.getContentType();
        log.debug("Reported audio content type: {}", audioContentType);
        if (audioContentType == null || !isAllowedAudioType(audioContentType)) {
            log.warn(
                    "Upload attempt failed for user {}: Invalid audio file type \'{}\'. Allowed types: {}",
                    userId, audioContentType, ALLOWED_AUDIO_TYPES);
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body("Invalid audio file type. Allowed types: " + ALLOWED_AUDIO_TYPES);
        }

        if (powerpointFile != null && !powerpointFile.isEmpty()) {
            String pptxContentType = powerpointFile.getContentType();
            log.debug("Reported PowerPoint content type: {}", pptxContentType);
            if (pptxContentType == null || !isAllowedPowerpointType(pptxContentType)) {
                log.warn(
                        "Upload attempt failed for user {}: Invalid PowerPoint file type \'{}\'. Allowed types: {}",
                        userId, pptxContentType, ALLOWED_POWERPOINT_TYPES);
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(
                        "Invalid PowerPoint file type. Allowed types: " + ALLOWED_POWERPOINT_TYPES);
            }
            log.info("Valid PowerPoint file provided: {}", powerpointFile.getOriginalFilename());
        } else {
            log.info("No PowerPoint file provided or it is empty.");
            powerpointFile = null;
        }

        Optional<String> optTitle = Optional.ofNullable(title).filter(s -> !s.isBlank());
        Optional<String> optDescription =
                Optional.ofNullable(description).filter(s -> !s.isBlank());

        try {
            log.info("Received valid upload request for audio file: {} from user: {}",
                    audioFile.getOriginalFilename(), userId);

            AudioMetadata initialMetadata = audioProcessingService.queueFilesForUpload(audioFile,
                    powerpointFile, optTitle.orElse(null), optDescription.orElse(null), userId);

            log.info(
                    "Successfully queued file(s) for upload. Metadata ID: {}, Status: {}, User ID: {}",
                    initialMetadata.getId(), initialMetadata.getStatus(), userId);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(initialMetadata);

        } catch (InvalidAudioFileException e) {
            log.warn("Upload failed due to invalid file for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            log.error("IOException during initial file handling/queueing for user {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing file(s).");
        } catch (FirestoreInteractionException e) {
            log.error("Firestore error during initial metadata save for user {}: {}", userId,
                    e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error saving initial request.");
        } catch (RuntimeException e) {
            log.error("RuntimeException during upload queueing for user {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred during upload queueing.");
        }
    }

    private boolean isAllowedAudioType(String contentType) {
        if (contentType == null)
            return false;
        return ALLOWED_AUDIO_TYPES.contains(contentType.toLowerCase());
    }

    private boolean isAllowedPowerpointType(String contentType) {
        if (contentType == null)
            return false;
        return ALLOWED_POWERPOINT_TYPES.contains(contentType.toLowerCase());
    }

    @GetMapping("/metadata")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyMetadata(
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "lastId", required = false) String lastDocumentId) {
        int effectivePageSize = (pageSize != null && pageSize > 0) ? pageSize : DEFAULT_PAGE_SIZE;
        log.info("Received request to /api/audio/metadata with pageSize={}, lastId={}",
                effectivePageSize, lastDocumentId);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication.getName();
        log.info("Fetching metadata for user ID: {}", userId);

        try {
            List<AudioMetadata> metadataList = audioProcessingService
                    .getAudioMetadataListForUser(userId, effectivePageSize, lastDocumentId);
            log.info("Successfully retrieved {} metadata records for user {} (page)",
                    metadataList.size(), userId);
            return ResponseEntity.ok(metadataList);
        } catch (FirestoreInteractionException e) {
            log.error("Firestore error retrieving metadata list for user {}: {}", userId,
                    e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving metadata.");
        } catch (RuntimeException e) {
            log.error("Unexpected runtime error retrieving metadata for user {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred.");
        }
    }

    @DeleteMapping("/metadata/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteMetadata(@PathVariable String id) {
        log.info("Received request to /api/audio/metadata/{}", id);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication.getName();

        log.info("User {} requesting deletion of metadata with ID: {}", userId, id);

        try {
            AudioMetadata metadata = audioProcessingService.getAudioMetadataById(id);

            if (metadata == null) {
                log.warn("Metadata not found for ID: {}, requested by user {}", id, userId);
                return ResponseEntity.notFound().build();
            }

            if (metadata.getUserId() == null || !metadata.getUserId().equals(userId)) {
                log.warn("User {} attempted to delete metadata {} owned by user {}", userId, id,
                        metadata.getUserId() != null ? metadata.getUserId() : "null");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("You do not have permission to delete this resource.");
            }

            boolean success = audioProcessingService.deleteAudioMetadata(id);
            if (success) {
                log.info("Successfully deleted metadata {} by user {}", id, userId);
                return ResponseEntity.noContent().build();
            } else {
                log.error(
                        "Service reported failure to delete metadata {} by user {} after authorization check.",
                        id, userId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to delete metadata.");
            }
        } catch (FirestoreInteractionException e) {
            log.error(
                    "Firestore error during metadata deletion process for ID {}, requested by user {}: {}",
                    id, userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error during deletion process.");
        } catch (RuntimeException e) {
            log.error(
                    "Unexpected runtime error during metadata deletion for ID {}, requested by user {}",
                    id, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred during deletion.");
        }
    }
}

package edu.cit.audioscholar.controller;

import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import edu.cit.audioscholar.dto.SummaryDto;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.ProcessingStatus;
import edu.cit.audioscholar.model.Recording;
import edu.cit.audioscholar.model.Summary;
import edu.cit.audioscholar.service.FirebaseService;
import edu.cit.audioscholar.service.RecordingService;
import edu.cit.audioscholar.service.SummaryService;

@RestController
@RequestMapping("/api")
public class SummaryController {

    private static final Logger log = LoggerFactory.getLogger(SummaryController.class);

    private final SummaryService summaryService;
    private final RecordingService recordingService;
    private final FirebaseService firebaseService;

    public SummaryController(SummaryService summaryService, RecordingService recordingService,
            FirebaseService firebaseService) {
        this.summaryService = summaryService;
        this.recordingService = recordingService;
        this.firebaseService = firebaseService;
    }

    @GetMapping("/summaries/{summaryId}")
    public ResponseEntity<SummaryDto> getSummaryById(@PathVariable String summaryId,
            Authentication authentication) {
        try {
            String currentUserId = getCurrentUserId(authentication);
            log.info("User {} requesting summary with ID: {}", currentUserId, summaryId);

            Summary summary = summaryService.getSummaryById(summaryId);
            if (summary == null) {
                log.warn("Summary not found for ID: {}", summaryId);
                return ResponseEntity.notFound().build();
            }

            authorizeAccessForRecordingInternal(summary.getRecordingId(), currentUserId,
                    "get summary by ID");

            log.info("User {} authorized. Returning summary {}", currentUserId, summaryId);
            return ResponseEntity.ok(SummaryDto.fromModel(summary));

        } catch (AccessDeniedException e) {
            log.warn("Access denied for user {} trying to get summary {}: {}",
                    getCurrentUserId(authentication), summaryId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied to this summary.");
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error retrieving summary {}: {}", summaryId, e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to retrieve summary.");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing getSummaryById for {}: {}", summaryId,
                    e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred.");
        }
    }

    @GetMapping("/recordings/{recordingId}/summary")
    public ResponseEntity<?> getSummaryByRecordingId(@PathVariable String recordingId,
            Authentication authentication) {
        try {
            String currentUserId = getCurrentUserId(authentication);
            log.info("User {} requesting summary for recording ID: {}", currentUserId, recordingId);

            Recording recording = recordingService.getRecordingById(recordingId);

            if (recording != null) {
                log.debug("Recording {} found. Checking ownership and fetching summary.",
                        recordingId);
                if (!currentUserId.equals(recording.getUserId())) {
                    log.warn(
                            "Authorization failed for user {} action 'get summary by recording ID': User does not own recording {}.",
                            currentUserId, recordingId);
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Access denied to this recording's summary.");
                }

                Summary summary = summaryService.getSummaryByRecordingId(recordingId);
                if (summary == null) {
                    log.warn("Summary not found for recording ID: {} (Recording exists)",
                            recordingId);
                    return ResponseEntity.notFound().build();
                }

                log.info("User {} authorized. Returning summary for recording {}", currentUserId,
                        recordingId);
                return ResponseEntity.ok(SummaryDto.fromModel(summary));

            } else {
                log.debug("Recording {} not found. Checking AudioMetadata.", recordingId);
                AudioMetadata metadata = firebaseService.getAudioMetadataByRecordingId(recordingId);

                if (metadata == null) {
                    log.warn("Neither Recording nor AudioMetadata found for recording ID: {}",
                            recordingId);
                    return ResponseEntity.notFound().build();
                }

                if (!currentUserId.equals(metadata.getUserId())) {
                    log.warn(
                            "Authorization failed for user {} action 'get summary by recording ID': User owns metadata {} but not the (missing) recording {}.",
                            currentUserId, metadata.getId(), recordingId);
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Access denied to this recording's summary.");
                }

                ProcessingStatus status = metadata.getStatus();
                log.info("Recording {} not found, but owned metadata {} found with status: {}",
                        recordingId, metadata.getId(), status);

                return switch (status) {
                    case UPLOAD_PENDING, UPLOADING_TO_STORAGE, PENDING, PROCESSING -> ResponseEntity
                            .status(HttpStatus.ACCEPTED).body(status.name());
                    case FAILED -> {
                        log.error(
                                "Processing failed for recording ID {} (Metadata ID {}). Reason: {}",
                                recordingId, metadata.getId(), metadata.getFailureReason());
                        yield ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Processing failed for this recording: "
                                        + metadata.getFailureReason());
                    }
                    case COMPLETED -> {
                        log.error(
                                "Inconsistent State: Metadata {} (recordingId {}) status is COMPLETED, but Recording document not found.",
                                metadata.getId(), recordingId);
                        yield ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                                "Inconsistent server state: Recording data missing despite completed status.");
                    }
                    default -> {
                        log.error(
                                "Unknown ProcessingStatus '{}' found for metadata {} (recordingId {})",
                                status, metadata.getId(), recordingId);
                        yield ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Unknown processing status encountered.");
                    }
                };
            }

        } catch (ResponseStatusException e) {
            throw e;
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error retrieving data for recording {}: {}", recordingId, e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to retrieve recording/summary data.");
        } catch (Exception e) {
            log.error("Unexpected error processing getSummaryByRecordingId for {}: {}", recordingId,
                    e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred.");
        }
    }


    @DeleteMapping("/summaries/{summaryId}")
    public ResponseEntity<Void> deleteSummary(@PathVariable String summaryId,
            Authentication authentication) {
        try {
            String currentUserId = getCurrentUserId(authentication);
            log.info("User {} requesting deletion of summary with ID: {}", currentUserId,
                    summaryId);

            Summary summary = summaryService.getSummaryById(summaryId);
            if (summary == null) {
                log.warn("Attempted to delete non-existent summary ID: {}", summaryId);
                return ResponseEntity.notFound().build();
            }

            authorizeAccessForRecordingInternal(summary.getRecordingId(), currentUserId,
                    "delete summary");

            summaryService.deleteSummary(summaryId);

            log.info("User {} authorized. Deleted summary {}", currentUserId, summaryId);
            return ResponseEntity.noContent().build();

        } catch (AccessDeniedException e) {
            log.warn("Access denied for user {} trying to delete summary {}: {}",
                    getCurrentUserId(authentication), summaryId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied to delete this summary.");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error deleting summary {}: {}", summaryId, e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete summary.");
        } catch (Exception e) {
            log.error("Unexpected error processing deleteSummary for {}: {}", summaryId,
                    e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred.");
        }
    }

    private String getCurrentUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        log.warn("Could not extract user ID from Authentication object. Type: {}",
                authentication != null ? authentication.getClass().getName() : "null");
        if (authentication != null && authentication.getPrincipal() != null) {
            log.warn("Principal type: {}", authentication.getPrincipal().getClass().getName());
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "User ID could not be determined from token.");
    }

    private void authorizeAccessForRecordingInternal(String recordingId, String userId,
            String action)
            throws ResponseStatusException, ExecutionException, InterruptedException {
        if (recordingId == null) {
            log.error("Cannot perform authorization check: recordingId is null during action '{}'",
                    action);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Associated recording ID is missing for authorization check.");
        }

        Recording recording = recordingService.getRecordingById(recordingId);
        if (recording == null) {
            log.error(
                    "Authorization check failed for user {} action '{}': Recording {} not found, but was expected (e.g., for existing summary).",
                    userId, action, recordingId);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Associated recording data not found.");
        }

        if (!userId.equals(recording.getUserId())) {
            log.warn(
                    "Authorization failed for user {} action '{}': User does not own recording {}.",
                    userId, action, recordingId);
            throw new AccessDeniedException("User does not own the associated recording.");
        }

        log.debug("User {} authorized for action '{}' on recording {}", userId, action,
                recordingId);
    }


}

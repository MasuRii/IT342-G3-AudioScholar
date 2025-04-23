package edu.cit.audioscholar.controller;

import edu.cit.audioscholar.dto.SummaryDto;
import edu.cit.audioscholar.model.Recording;
import edu.cit.audioscholar.model.Summary;
import edu.cit.audioscholar.service.RecordingService;
import edu.cit.audioscholar.service.SummaryService;
import edu.cit.audioscholar.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api")
public class SummaryController {

    private static final Logger log = LoggerFactory.getLogger(SummaryController.class);

    private final SummaryService summaryService;
    private final RecordingService recordingService;

    public SummaryController(SummaryService summaryService, RecordingService recordingService, UserService userService) {
        this.summaryService = summaryService;
        this.recordingService = recordingService;
    }

    @GetMapping("/summaries/{summaryId}")
    public ResponseEntity<SummaryDto> getSummaryById(@PathVariable String summaryId, Authentication authentication) {
        try {
            String currentUserId = getCurrentUserId(authentication);
            log.info("User {} requesting summary with ID: {}", currentUserId, summaryId);

            Summary summary = summaryService.getSummaryById(summaryId);
            if (summary == null) {
                log.warn("Summary not found for ID: {}", summaryId);
                return ResponseEntity.notFound().build();
            }

            authorizeAccessForRecording(summary.getRecordingId(), currentUserId, "get summary by ID");

            log.info("User {} authorized. Returning summary {}", currentUserId, summaryId);
            return ResponseEntity.ok(SummaryDto.fromModel(summary));

        } catch (AccessDeniedException e) {
            log.warn("Access denied for user {} trying to get summary {}: {}", getCurrentUserId(authentication), summaryId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to this summary.");
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error retrieving summary {}: {}", summaryId, e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve summary.");
        }
    }

    @GetMapping("/recordings/{recordingId}/summary")
    public ResponseEntity<SummaryDto> getSummaryByRecordingId(@PathVariable String recordingId, Authentication authentication) {
        try {
            String currentUserId = getCurrentUserId(authentication);
            log.info("User {} requesting summary for recording ID: {}", currentUserId, recordingId);

            authorizeAccessForRecording(recordingId, currentUserId, "get summary by recording ID");

            Summary summary = summaryService.getSummaryByRecordingId(recordingId);
            if (summary == null) {
                log.warn("Summary not found for recording ID: {}", recordingId);
                return ResponseEntity.notFound().build();
            }

            log.info("User {} authorized. Returning summary for recording {}", currentUserId, recordingId);
            return ResponseEntity.ok(SummaryDto.fromModel(summary));

        } catch (AccessDeniedException e) {
            log.warn("Access denied for user {} trying to get summary for recording {}: {}", getCurrentUserId(authentication), recordingId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to this recording's summary.");
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error retrieving summary for recording {}: {}", recordingId, e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve summary.");
        }
    }

    @DeleteMapping("/summaries/{summaryId}")
    public ResponseEntity<Void> deleteSummary(@PathVariable String summaryId, Authentication authentication) {
        try {
            String currentUserId = getCurrentUserId(authentication);
            log.info("User {} requesting deletion of summary with ID: {}", currentUserId, summaryId);

            Summary summary = summaryService.getSummaryById(summaryId);
            if (summary == null) {
                log.warn("Attempted to delete non-existent summary ID: {}", summaryId);
                return ResponseEntity.notFound().build();
            }

            authorizeAccessForRecording(summary.getRecordingId(), currentUserId, "delete summary");

            summaryService.deleteSummary(summaryId);
            log.info("User {} authorized. Deleted summary {}", currentUserId, summaryId);
            return ResponseEntity.noContent().build();

        } catch (AccessDeniedException e) {
            log.warn("Access denied for user {} trying to delete summary {}: {}", getCurrentUserId(authentication), summaryId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to delete this summary.");
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error deleting summary {}: {}", summaryId, e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete summary.");
        }
    }


    private String getCurrentUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        log.warn("Could not extract user ID from Authentication object.");
        throw new AccessDeniedException("User ID could not be determined from token.");
    }

    private void authorizeAccessForRecording(String recordingId, String userId, String action)
            throws AccessDeniedException, ExecutionException, InterruptedException {
        if (recordingId == null) {
             log.error("Cannot perform authorization check: recordingId is null during action '{}'", action);
             throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Associated recording ID is missing.");
        }

        Recording recording = recordingService.getRecordingById(recordingId);
        if (recording == null) {
            log.warn("Authorization failed for user {} action '{}': Recording {} not found.", userId, action, recordingId);
            throw new AccessDeniedException("Associated recording not found.");
        }

        if (!userId.equals(recording.getUserId())) {
            log.warn("Authorization failed for user {} action '{}': User does not own recording {}.", userId, action, recordingId);
            throw new AccessDeniedException("User does not own the associated recording.");
        }
         log.debug("User {} authorized for action '{}' on recording {}", userId, action, recordingId);
    }
}
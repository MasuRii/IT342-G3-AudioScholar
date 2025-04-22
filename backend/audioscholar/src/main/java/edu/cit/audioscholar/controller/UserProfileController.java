package edu.cit.audioscholar.controller;

import edu.cit.audioscholar.dto.UpdateUserProfileRequest;
import edu.cit.audioscholar.dto.UserProfileDto;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.User;
import edu.cit.audioscholar.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private static final Logger log = LoggerFactory.getLogger(UserProfileController.class);
    private final UserService userService;

    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_GIF_VALUE
    );
    private static final long MAX_AVATAR_SIZE_BYTES = 5 * 1024 * 1024;


    @Autowired
    public UserProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileDto> getCurrentUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = getUserIdFromAuthentication(authentication);
        log.info("Fetching profile for authenticated user ID: {}", userId);

        User user = userService.getUserById(userId);
        if (user == null) {
            log.warn("User profile not found in Firestore for authenticated user ID: {}", userId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found for ID: " + userId);
        }

        UserProfileDto userProfileDto = UserProfileDto.fromUser(user);
        return ResponseEntity.ok(userProfileDto);
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileDto> updateCurrentUserProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = getUserIdFromAuthentication(authentication);
        log.info("Attempting to update profile text details for user ID: {}", userId);

        try {
            User updatedUser = userService.updateUserProfileDetails(userId, request);
            UserProfileDto updatedUserProfileDto = UserProfileDto.fromUser(updatedUser);
            log.info("Successfully updated profile text details for user ID: {}", userId);
            return ResponseEntity.ok(updatedUserProfileDto);
        } catch (RuntimeException e) {
             if (e.getMessage() != null && e.getMessage().startsWith("User not found")) {
                log.warn("Update failed. User not found for ID: {}", userId, e);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
            }
             if (e instanceof IllegalArgumentException) {
                log.warn("Update failed due to invalid data for user ID {}: {}", userId, e.getMessage());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
            }
            log.error("Error updating profile text details for user ID {}: {}", userId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error updating user profile.", e);
        } catch (Exception e) {
            log.error("Unexpected error updating profile text details for user ID {}: {}", userId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", e);
        }
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadUserAvatar(@RequestParam("avatar") MultipartFile file) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = getUserIdFromAuthentication(authentication);
        log.info("Received request to upload avatar for user ID: {}", userId);

        if (file == null || file.isEmpty()) {
            log.warn("Avatar upload failed for user {}: File is empty.", userId);
            return ResponseEntity.badRequest().body("Avatar file cannot be empty.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            log.warn("Avatar upload failed for user {}: Invalid file type '{}'. Allowed types: {}",
                     userId, contentType, ALLOWED_IMAGE_TYPES);
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                   .body("Invalid file type. Allowed types: " + ALLOWED_IMAGE_TYPES);
        }

        if (file.getSize() > MAX_AVATAR_SIZE_BYTES) {
             log.warn("Avatar upload failed for user {}: File size {} exceeds limit of {} bytes.",
                     userId, file.getSize(), MAX_AVATAR_SIZE_BYTES);
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                   .body("Avatar file size exceeds the limit (" + (MAX_AVATAR_SIZE_BYTES / 1024 / 1024) + "MB).");
        }

        try {
            log.info("Processing valid avatar upload for file: {} from user: {}",
                     file.getOriginalFilename(), userId);

            User updatedUser = userService.updateUserAvatar(userId, file);

            UserProfileDto updatedUserProfileDto = UserProfileDto.fromUser(updatedUser);
            log.info("Successfully uploaded avatar and updated profile for user ID: {}", userId);
            return ResponseEntity.ok(updatedUserProfileDto);

        } catch (IOException e) {
            log.error("IOException during avatar processing/upload for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing avatar file.");
        } catch (FirestoreInteractionException e) {
            log.error("Firestore error during avatar update for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating user profile after avatar upload.");
        } catch (RuntimeException e) {
             if (e.getMessage() != null && e.getMessage().startsWith("User not found")) {
                log.warn("Avatar upload failed. User not found for ID: {}", userId, e);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
             if (e instanceof IllegalArgumentException) {
                log.warn("Avatar upload failed due to invalid data for user ID {}: {}", userId, e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
            }
            log.error("RuntimeException during avatar upload for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred during avatar upload.");
        } catch (Exception e) {
            log.error("Unexpected error during avatar upload for user ID {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
        }
    }


    private String getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Attempt to access user profile endpoint without authentication.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }

        String userId = null;
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            userId = jwt.getSubject();
        } else {
            log.error("Unexpected principal type in UserProfileController: {}. Expected Jwt.",
                      authentication.getPrincipal().getClass().getName());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot determine user ID from authentication principal.");
        }

        if (userId == null || userId.isBlank()) {
            log.error("Could not extract userId (subject) from JWT token for principal name: {}", authentication.getName());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User ID not found in authentication token.");
        }
        return userId;
    }
}
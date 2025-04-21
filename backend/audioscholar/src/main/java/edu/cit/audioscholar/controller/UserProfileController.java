package edu.cit.audioscholar.controller;

import edu.cit.audioscholar.dto.UpdateUserProfileRequest;
import edu.cit.audioscholar.dto.UserProfileDto;
import edu.cit.audioscholar.model.User;
import edu.cit.audioscholar.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private static final Logger log = LoggerFactory.getLogger(UserProfileController.class);

    private final UserService userService;

    @Autowired
    public UserProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
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
    public ResponseEntity<UserProfileDto> updateCurrentUserProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = getUserIdFromAuthentication(authentication);

        log.info("Attempting to update profile for user ID: {}", userId);

        try {
            User updatedUser = userService.updateUserProfileDetails(userId, request);
            UserProfileDto updatedUserProfileDto = UserProfileDto.fromUser(updatedUser);
            log.info("Successfully updated profile for user ID: {}", userId);
            return ResponseEntity.ok(updatedUserProfileDto);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("User not found")) {
                 log.warn("Update failed. User not found for ID: {}", userId, e);
                 throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
            }
            log.error("Error updating profile for user ID {}: {}", userId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error updating user profile.", e);
        } catch (Exception e) {
             log.error("Unexpected error updating profile for user ID {}: {}", userId, e.getMessage(), e);
             throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", e);
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
            log.error("Unexpected principal type: {}. Cannot extract JWT subject.",
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
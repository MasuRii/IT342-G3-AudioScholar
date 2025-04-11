package edu.cit.audioscholar.controller;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import edu.cit.audioscholar.model.User;
import edu.cit.audioscholar.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<?> createUser(@Valid @RequestBody User user) {
        try {
            User createdUser = userService.createUser(user);
            URI location = ServletUriComponentsBuilder
                    .fromCurrentRequest().path("/{id}")
                    .buildAndExpand(createdUser.getUserId()).toUri();
            return ResponseEntity.created(location).body(createdUser);
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Error creating user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating user.");
        } catch (Exception e) {
             logger.error("Unexpected error creating user: {}", e.getMessage(), e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
        }
    }

    @GetMapping("/{userId}")
    @PreAuthorize("#userId == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<?> getUserById(@PathVariable String userId) {
        try {
            User user = userService.getUserById(userId);
            return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build();
        } catch (ExecutionException | InterruptedException e) {
             Thread.currentThread().interrupt();
             logger.error("Error getting user {}: {}", userId, e.getMessage(), e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving user data.");
        } catch (Exception e) {
             logger.error("Unexpected error getting user {}: {}", userId, e.getMessage(), e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
        }
    }

    @PutMapping("/{userId}")
    @PreAuthorize("#userId == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable String userId, @Valid @RequestBody User user) {
        user.setUserId(userId);
        try {
            User updatedUser = userService.updateUser(user);
            return updatedUser != null ? ResponseEntity.ok(updatedUser) : ResponseEntity.notFound().build();
        } catch (ExecutionException | InterruptedException e) {
             Thread.currentThread().interrupt();
             logger.error("Error updating user {}: {}", userId, e.getMessage(), e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating user data.");
        } catch (Exception e) {
             logger.error("Unexpected error updating user {}: {}", userId, e.getMessage(), e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
        }
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("#userId == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        try {
            userService.deleteUser(userId);
            return ResponseEntity.noContent().build();
        } catch (ExecutionException | InterruptedException e) {
             Thread.currentThread().interrupt();
             logger.error("Error deleting user {}: {}", userId, e.getMessage(), e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
             logger.error("Unexpected error deleting user {}: {}", userId, e.getMessage(), e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> findUsers(@RequestParam(value = "email", required = false) String email) {

        try {
            List<User> users;
            if (email != null && !email.isEmpty()) {
                users = userService.getUsersByEmail(email);
            } else {
                 users = List.of();
            }
            return ResponseEntity.ok(users);
        } catch (ExecutionException | InterruptedException e) {
             Thread.currentThread().interrupt();
             logger.error("Error finding users by email [{}]: {}", email, e.getMessage(), e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error searching users.");
        } catch (Exception e) {
             logger.error("Unexpected error finding users [{}]: {}", email, e.getMessage(), e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred.");
        }
    }
}
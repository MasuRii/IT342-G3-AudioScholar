package edu.cit.audioscholar.controller;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.User;
import edu.cit.audioscholar.service.UserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

     private static final Logger logger = LoggerFactory.getLogger(UserController.class);
     private final UserService userService;

     @Autowired
     public UserController(UserService userService) {
          this.userService = userService;
     }

     @PostMapping
     @PreAuthorize("hasRole('ADMIN')")
     public ResponseEntity<?> createUser(@Valid @RequestBody User user) {
          try {
               User createdUser = userService.createUser(user);
               URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                         .buildAndExpand(createdUser.getUserId()).toUri();
               return ResponseEntity.created(location).body(createdUser);
          } catch (FirestoreInteractionException e) {
               logger.error("Error creating user: {}", e.getMessage(), e);
               return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                         .body(e.getMessage() != null ? e.getMessage() : "Error creating user.");
          } catch (IllegalArgumentException e) {
               logger.error("Illegal argument creating user: {}", e.getMessage(), e);
               return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
          } catch (Exception e) {
               logger.error("Unexpected error creating user: {}", e.getMessage(), e);
               return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                         .body("An unexpected error occurred while creating the user.");
          }
     }

     @GetMapping("/{userId}")
     @PreAuthorize("#userId == authentication.name or hasRole('ADMIN')")
     public ResponseEntity<?> getUserById(@PathVariable String userId) {
          try {
               User user = userService.getUserById(userId);
               return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build();
          } catch (FirestoreInteractionException e) {
               logger.error("Error getting user {}: {}", userId, e.getMessage(), e);
               return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                         e.getMessage() != null ? e.getMessage() : "Error retrieving user data.");
          } catch (Exception e) {
               logger.error("Unexpected error getting user {}: {}", userId, e.getMessage(), e);
               return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                         .body("An unexpected error occurred while retrieving the user.");
          }
     }

     @PutMapping("/{userId}")
     @PreAuthorize("#userId == authentication.name or hasRole('ADMIN')")
     public ResponseEntity<?> updateUser(@PathVariable String userId,
               @Valid @RequestBody User user) {
          if (user.getUserId() == null || user.getUserId().isEmpty()) {
               user.setUserId(userId);
          } else if (!user.getUserId().equals(userId)) {
               return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                         .body("User ID in path does not match User ID in request body.");
          }

          try {
               User updatedUser = userService.updateUser(user);
               return ResponseEntity.ok(updatedUser);
          } catch (FirestoreInteractionException e) {
               logger.error("Error updating user {}: {}", userId, e.getMessage(), e);
               return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                         e.getMessage() != null ? e.getMessage() : "Error updating user data.");
          } catch (IllegalArgumentException e) {
               logger.error("Illegal argument updating user {}: {}", userId, e.getMessage(), e);
               return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
          } catch (Exception e) {
               logger.error("Unexpected error updating user {}: {}", userId, e.getMessage(), e);
               return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                         .body("An unexpected error occurred while updating the user.");
          }
     }

     @DeleteMapping("/{userId}")
     @PreAuthorize("#userId == authentication.name or hasRole('ADMIN')")
     public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
          try {
               userService.deleteUser(userId);
               return ResponseEntity.noContent().build();
          } catch (FirestoreInteractionException e) {
               logger.error("Error deleting user {}: {}", userId, e.getMessage(), e);
               return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
          } catch (IllegalArgumentException e) {
               logger.error("Illegal argument deleting user {}: {}", userId, e.getMessage(), e);
               return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
          } catch (Exception e) {
               logger.error("Unexpected error deleting user {}: {}", userId, e.getMessage(), e);
               return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
          }
     }

     @GetMapping
     @PreAuthorize("hasRole('ADMIN')")
     public ResponseEntity<?> findUserByEmail(
               @RequestParam(value = "email", required = true) String email) {
          if (email == null || email.isBlank()) {
               return ResponseEntity.badRequest().body("Email query parameter is required.");
          }
          try {
               User user = userService.findUserByEmail(email);
               if (user != null) {
                    return ResponseEntity.ok(user);
               } else {
                    return ResponseEntity.notFound().build();
               }
          } catch (FirestoreInteractionException e) {
               logger.error("Error finding user by email [{}]: {}", email, e.getMessage(), e);
               return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                         .body(e.getMessage() != null ? e.getMessage() : "Error searching users.");
          } catch (Exception e) {
               logger.error("Unexpected error finding user by email [{}]: {}", email,
                         e.getMessage(), e);
               return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                         .body("An unexpected error occurred while searching users.");
          }
     }
}

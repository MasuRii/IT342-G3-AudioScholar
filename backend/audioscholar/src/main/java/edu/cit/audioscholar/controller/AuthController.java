package edu.cit.audioscholar.controller;

import edu.cit.audioscholar.dto.AuthResponse;
import edu.cit.audioscholar.dto.RegistrationRequest;
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
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.google.firebase.auth.FirebaseAuthException;

import edu.cit.audioscholar.dto.JwtAuthenticationResponse;
import edu.cit.audioscholar.security.JwtTokenProvider;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final JwtTokenProvider tokenProvider;
    private final UserService userService;

    @Autowired
    public AuthController(JwtTokenProvider tokenProvider, UserService userService) {
        this.tokenProvider = tokenProvider;
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegistrationRequest registrationRequest) {
        logger.info("Received registration request for email: {}", registrationRequest.getEmail());
        try {
            User registeredUser = userService.registerNewUser(registrationRequest);
            logger.info("User registered successfully with UID: {}", registeredUser.getUserId());
            return ResponseEntity.status(HttpStatus.CREATED)
                                 .body(new AuthResponse(true, "User registered successfully.", registeredUser.getUserId()));

        } catch (FirebaseAuthException e) {
            logger.error("Firebase registration failed: {}", e.getMessage());
            String message = "Registration failed: " + e.getMessage();
            HttpStatus status = HttpStatus.BAD_REQUEST;
            if ("EMAIL_ALREADY_EXISTS".equals(e.getErrorCode())) {
                message = "Registration failed: Email address is already in use.";
                status = HttpStatus.CONFLICT;
            }
            return ResponseEntity.status(status).body(new AuthResponse(false, message));
        } catch (Exception e) {
            logger.error("An unexpected error occurred during registration: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new AuthResponse(false, "An unexpected error occurred during registration."));
        }
    }


    @GetMapping("/token")
    public ResponseEntity<?> getAuthenticationToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || !(authentication instanceof OAuth2AuthenticationToken)) {
            logger.warn("User requested /api/auth/token without valid OAuth2 authentication.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse(false, "User is not authenticated via OAuth2 session."));
        }

        String jwt = tokenProvider.generateToken(authentication);
        logger.info("Generated JWT token for user {} via /api/auth/token endpoint.", authentication.getName());

        return ResponseEntity.ok(new JwtAuthenticationResponse(jwt));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
         Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
         if (authentication == null || !authentication.isAuthenticated()) {
             return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse(false, "User is not authenticated."));
         }
         return ResponseEntity.ok(authentication.getPrincipal());
    }
}
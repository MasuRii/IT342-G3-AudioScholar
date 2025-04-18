package edu.cit.audioscholar.controller;

import edu.cit.audioscholar.dto.AuthResponse;
import edu.cit.audioscholar.dto.JwtAuthenticationResponse;
import edu.cit.audioscholar.dto.FirebaseTokenRequest;
import edu.cit.audioscholar.dto.RegistrationRequest;
import edu.cit.audioscholar.model.User;
import edu.cit.audioscholar.security.JwtTokenProvider;
import edu.cit.audioscholar.service.FirebaseService;
import edu.cit.audioscholar.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import edu.cit.audioscholar.dto.AuthResponse;
import edu.cit.audioscholar.dto.JwtAuthenticationResponse;
import edu.cit.audioscholar.dto.FirebaseTokenRequest;

import java.util.ArrayList;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final JwtTokenProvider tokenProvider;
    private final UserService userService;
    private final FirebaseService firebaseService;

    @Autowired
    public AuthController(JwtTokenProvider tokenProvider, UserService userService, FirebaseService firebaseService) {
        this.tokenProvider = tokenProvider;
        this.userService = userService;
        this.firebaseService = firebaseService;
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


    @PostMapping("/verify-firebase-token")
    public ResponseEntity<?> verifyTokenAndAuthenticate(@Valid @RequestBody FirebaseTokenRequest tokenRequest) {
        logger.info("Received request to verify Firebase ID token.");
        try {
            FirebaseToken decodedToken = firebaseService.verifyFirebaseIdToken(tokenRequest.getIdToken());
            String uid = decodedToken.getUid();
            String email = decodedToken.getEmail();
            String name = decodedToken.getName();
            logger.info("Firebase ID Token verified successfully for UID: {}, Email: {}", uid, email);

            User user = userService.findOrCreateUserByFirebaseDetails(uid, email, name);
            if (user == null) {
                 logger.error("Failed to find or create local user record for verified Firebase user UID: {}", uid);
                 return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                      .body(new AuthResponse(false, "Failed to process user profile after verification."));
            }
            logger.info("Local user record processed for UID: {}", uid);

            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername(uid)
                    .password("")
                    .authorities(new ArrayList<>())
                    .accountExpired(false)
                    .accountLocked(false)
                    .credentialsExpired(false)
                    .disabled(false)
                    .build();
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
            );

            String jwt = tokenProvider.generateToken(authentication);
            logger.info("Generated API JWT for verified user UID: {}", uid);

            AuthResponse successResponse = new AuthResponse(true, "Token verified successfully.");
            successResponse.setToken(jwt);
            successResponse.setUserId(uid);

            return ResponseEntity.ok(successResponse);

        } catch (FirebaseAuthException e) {
            logger.warn("Firebase ID token verification failed: {}", e.getMessage());
            HttpStatus status = HttpStatus.UNAUTHORIZED;
            String message = "Firebase token verification failed: ";
            if ("ID_TOKEN_EXPIRED".equals(e.getErrorCode())) {
                message += "Token has expired.";
            } else if ("ID_TOKEN_REVOKED".equals(e.getErrorCode())) {
                message += "Token has been revoked.";
            } else {
                message += "Invalid token.";
            }
            return ResponseEntity.status(status).body(new AuthResponse(false, message));
        } catch (IllegalArgumentException e) {
             logger.warn("Illegal argument during token verification: {}", e.getMessage());
             return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AuthResponse(false, e.getMessage()));
        } catch (Exception e) {
            logger.error("An unexpected error occurred during token verification: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new AuthResponse(false, "An unexpected error occurred during token verification."));
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
        logger.info("Generated JWT token for user {} via /api/auth/token endpoint (from OAuth2 session).", authentication.getName());

        return ResponseEntity.ok(new JwtAuthenticationResponse(jwt));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
         Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
         if (authentication == null || !authentication.isAuthenticated()) {
             return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse(false, "User is not authenticated."));
         }
         Object principal = authentication.getPrincipal();
         logger.debug("Fetching /me for principal: {}", principal);
         return ResponseEntity.ok(principal);
    }
}
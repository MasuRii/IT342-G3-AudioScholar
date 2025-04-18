package edu.cit.audioscholar.controller;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;

import edu.cit.audioscholar.dto.AuthResponse;
import edu.cit.audioscholar.dto.FirebaseTokenRequest;
import edu.cit.audioscholar.dto.RegistrationRequest;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.User;
import edu.cit.audioscholar.security.JwtTokenProvider;
import edu.cit.audioscholar.service.FirebaseService;
import edu.cit.audioscholar.service.UserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final FirebaseService firebaseService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(UserService userService, FirebaseService firebaseService, JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.firebaseService = firebaseService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegistrationRequest registrationRequest) {
        log.info("Received registration request for email: {}", registrationRequest.getEmail());
        try {
            User registeredUser = userService.registerNewUser(registrationRequest);
            AuthResponse response = new AuthResponse(true, "User registered successfully.", registeredUser.getUserId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (FirebaseAuthException e) {
            log.error("Firebase Auth error during registration for {}: {}", registrationRequest.getEmail(), e.getMessage());
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new AuthResponse(false, "Email address is already in use."));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new AuthResponse(false, "Registration failed due to Firebase error: " + e.getMessage() + " (Code: " + e.getAuthErrorCode() + ")"));
        } catch (FirestoreInteractionException e) {
             log.error("Firestore error during registration for {}: {}", registrationRequest.getEmail(), e.getMessage(), e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new AuthResponse(false, "Registration failed due to database error."));
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Concurrency error during registration for {}: {}", registrationRequest.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new AuthResponse(false, "Registration process was interrupted."));
        } catch (Exception e) {
            log.error("Unexpected error during registration for {}: {}", registrationRequest.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new AuthResponse(false, "An unexpected error occurred during registration."));
        }
    }


    @PostMapping("/verify-firebase-token")
    public ResponseEntity<?> verifyFirebaseToken(@Valid @RequestBody FirebaseTokenRequest tokenRequest) {
        log.info("Received request to verify Firebase ID token.");
        try {
            FirebaseToken decodedToken = firebaseService.verifyFirebaseIdToken(tokenRequest.getIdToken());
            String uid = decodedToken.getUid();
            String email = decodedToken.getEmail();
            String name = decodedToken.getName();

            log.info("Firebase token verified for UID: {}. Finding or creating user profile.", uid);
            User user = userService.findOrCreateUserByFirebaseDetails(uid, email, name);

            List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            Authentication authentication = new UsernamePasswordAuthenticationToken(user.getUserId(), null, authorities);

            String jwt = jwtTokenProvider.generateToken(authentication);
            log.info("Generated API JWT for user UID: {}", uid);

            AuthResponse response = new AuthResponse(true, "Firebase token verified successfully.");
            response.setToken(jwt);
            response.setUserId(user.getUserId());

            return ResponseEntity.ok(response);

        } catch (FirebaseAuthException e) {
            log.warn("Firebase ID token verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse(false, "Invalid Firebase token: " + e.getMessage()));
        } catch (FirestoreInteractionException e) {
            log.error("Firestore error during user profile lookup/creation after Firebase token verification: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new AuthResponse(false, "Error accessing user profile data."));
        } catch (Exception e) {
            log.error("Unexpected error during Firebase token verification flow: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new AuthResponse(false, "An unexpected error occurred."));
        }
    }

    @PostMapping("/verify-google-token")
    public ResponseEntity<?> verifyGoogleToken(@Valid @RequestBody FirebaseTokenRequest tokenRequest) {
        log.info("Received request to verify Google ID token.");
        log.debug("Received Google Token String for verification: {}", tokenRequest.getIdToken());
        try {
            GoogleIdToken idToken = firebaseService.verifyGoogleIdToken(tokenRequest.getIdToken());
            if (idToken == null) {
                log.warn("Google ID token verification failed (invalid/expired token).");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse(false, "Invalid or expired Google token."));
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String googleUserId = payload.getSubject();
            String email = payload.getEmail();
            boolean emailVerified = payload.getEmailVerified();
            String name = (String) payload.get("name");

            log.info("Google ID token verified for Google User ID (sub): {}, Email: {}", googleUserId, email);

            UserRecord firebaseUserRecord;
            try {
                firebaseUserRecord = FirebaseAuth.getInstance(firebaseService.getFirebaseApp()).getUserByEmail(email);
                log.info("Found existing Firebase user by email {} with UID: {}", email, firebaseUserRecord.getUid());

            } catch (FirebaseAuthException e) {
                if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
                    log.info("No existing Firebase user found for email {}. Creating new Firebase user.", email);
                    UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                            .setEmail(email)
                            .setEmailVerified(emailVerified)
                            .setDisplayName(name)
                            .setDisabled(false);
                    firebaseUserRecord = FirebaseAuth.getInstance(firebaseService.getFirebaseApp()).createUser(createRequest);
                    log.info("Created new Firebase user for email {} with UID: {}", email, firebaseUserRecord.getUid());
                } else {
                    log.error("FirebaseAuthException while finding/creating user for email {}: {}", email, e.getMessage(), e);
                    throw e;
                }
            }

            String firebaseUid = firebaseUserRecord.getUid();
            log.info("Finding or creating Firestore user profile for Firebase UID: {}", firebaseUid);
            User user = userService.findOrCreateUserByFirebaseDetails(firebaseUid, email, name);
            user.setProvider("google");

            List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            Authentication authentication = new UsernamePasswordAuthenticationToken(firebaseUid, null, authorities);

            String jwt = jwtTokenProvider.generateToken(authentication);
            log.info("Generated API JWT for user UID: {} after Google Sign-In.", firebaseUid);

            AuthResponse response = new AuthResponse(true, "Google token verified successfully.");
            response.setToken(jwt);
            response.setUserId(firebaseUid);

            return ResponseEntity.ok(response);

        } catch (GeneralSecurityException | IOException e) {
            log.error("Google ID token verification failed due to security/IO error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse(false, "Google token verification failed."));
        } catch (IllegalArgumentException e) {
             log.warn("Google ID token verification failed: {}", e.getMessage());
             return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AuthResponse(false, e.getMessage()));
        } catch (FirebaseAuthException e) {
             log.error("FirebaseAuthException during Google Sign-In flow for email lookup/creation: {}", e.getMessage(), e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new AuthResponse(false, "Error processing user account: " + e.getMessage()));
        } catch (FirestoreInteractionException e) {
            log.error("Firestore error during user profile lookup/creation after Google token verification: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new AuthResponse(false, "Error accessing user profile data after Google Sign-In."));
        } catch (Exception e) {
            log.error("Unexpected error during Google token verification flow: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new AuthResponse(false, "An unexpected error occurred during Google Sign-In processing."));
        }
    }

}
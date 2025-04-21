package edu.cit.audioscholar.controller;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;

import edu.cit.audioscholar.service.TokenRevocationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import edu.cit.audioscholar.dto.AuthResponse;
import edu.cit.audioscholar.dto.ChangePasswordRequest;
import edu.cit.audioscholar.dto.FirebaseTokenRequest;
import edu.cit.audioscholar.dto.GitHubCodeRequest;
import edu.cit.audioscholar.dto.RegistrationRequest;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.User;
import edu.cit.audioscholar.security.JwtTokenProvider;
import edu.cit.audioscholar.service.FirebaseService;
import edu.cit.audioscholar.service.UserService;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final FirebaseService firebaseService;
    private final JwtTokenProvider jwtTokenProvider;
    private final WebClient webClient;
    private final TokenRevocationService tokenRevocationService;

    @Value("${spring.security.oauth2.client.registration.github.client-id}")
    private String githubClientId;

    @Value("${spring.security.oauth2.client.registration.github.client-secret}")
    private String githubClientSecret;

    @Value("${github.api.url.token:https://github.com/login/oauth/access_token}")
    private String githubTokenUrl;

    @Value("${github.api.url.user:https://api.github.com/user}")
    private String githubUserUrl;

    @Value("${github.api.url.emails:https://api.github.com/user/emails}")
    private String githubEmailsUrl;

    public AuthController(
            UserService userService,
            FirebaseService firebaseService,
            JwtTokenProvider jwtTokenProvider,
            WebClient.Builder webClientBuilder,
            TokenRevocationService tokenRevocationService
    ) {
        this.userService = userService;
        this.firebaseService = firebaseService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.webClient = webClientBuilder.build();
        this.tokenRevocationService = tokenRevocationService;
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
            if (AuthErrorCode.EMAIL_ALREADY_EXISTS.equals(e.getAuthErrorCode())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new AuthResponse(false, "Email address is already in use."));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new AuthResponse(false, "Registration failed due to an authentication service error."));
        } catch (FirestoreInteractionException e) {
            log.error("Firestore error during registration for {}: {}", registrationRequest.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new AuthResponse(false, "Registration failed due to a database error."));
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Concurrency error during registration for {}: {}", registrationRequest.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new AuthResponse(false, "Registration process was interrupted."));
        } catch (Exception e) {
            log.error("Unexpected error during registration for {}: {}", registrationRequest.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new AuthResponse(false, "An unexpected error occurred during registration."));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(HttpServletRequest request) {
        log.info("Received request to logout user.");
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            try {
                tokenRevocationService.revokeToken(token);
                log.info("Token successfully added to denylist (revoked).");
                return ResponseEntity.ok(new AuthResponse(true, "Logout successful."));
            } catch (Exception e) {
                log.error("Error processing logout: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new AuthResponse(false, "Logout processing failed on server."));
            }
        } else {
            log.warn("Logout request received without a valid Bearer token.");
            return ResponseEntity.badRequest().body(new AuthResponse(false, "Authorization token not provided."));
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
            String provider = (String) decodedToken.getClaims().getOrDefault("firebase.sign_in_provider", "firebase");
            String providerId = uid;
            log.info("Firebase token verified for UID: {}. Finding or creating user profile.", uid);
            User user = userService.findOrCreateUserByFirebaseDetails(uid, email, name, provider, providerId);
            List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            Authentication authentication = new UsernamePasswordAuthenticationToken(uid, null, authorities);
            String jwt = jwtTokenProvider.generateToken(authentication);
            log.info("Generated API JWT for user UID {}: {}", uid, jwt);
            AuthResponse response = new AuthResponse(true, "Firebase token verified successfully.");
            response.setToken(jwt);
            response.setUserId(uid);
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
            if (email == null || email.isEmpty()) {
                log.warn("Google token verified, but email is missing for Google User ID: {}", googleUserId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AuthResponse(false, "Email is required from Google profile."));
            }
            log.info("Google ID token verified for Google User ID (sub): {}, Email: {}", googleUserId, email);
            UserRecord firebaseUserRecord;
            try {
                firebaseUserRecord = FirebaseAuth.getInstance(firebaseService.getFirebaseApp()).getUserByEmail(email);
                log.info("Found existing Firebase user by email {} with UID: {}", email, firebaseUserRecord.getUid());
            } catch (FirebaseAuthException e) {
                if (AuthErrorCode.USER_NOT_FOUND.equals(e.getAuthErrorCode())) {
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
            String provider = "google.com";
            String providerId = googleUserId;
            log.info("Finding or creating Firestore user profile for Firebase UID: {}", firebaseUid);
            User user = userService.findOrCreateUserByFirebaseDetails(firebaseUid, email, name, provider, providerId);
            List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            Authentication authentication = new UsernamePasswordAuthenticationToken(firebaseUid, null, authorities);
            String jwt = jwtTokenProvider.generateToken(authentication);
            log.info("Generated API JWT for user UID {} (Google): {}", firebaseUid, jwt);
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

    @PostMapping("/verify-github-code")
    public Mono<ResponseEntity<?>> verifyGitHubCode(@Valid @RequestBody GitHubCodeRequest request) {
        log.info("Received request to verify GitHub code.");
        return webClient.post()
                .uri(githubTokenUrl)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("client_id", githubClientId)
                        .with("client_secret", githubClientSecret)
                        .with("code", request.getCode()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .flatMap(tokenResponse -> {
                    String accessToken = (String) tokenResponse.get("access_token");
                    if (accessToken == null || accessToken.isBlank()) {
                        log.error("GitHub token exchange failed. Response does not contain access_token. Response: {}", tokenResponse);
                        return Mono.error(new RuntimeException("Failed to obtain GitHub access token. The provided code might be invalid or expired."));
                    }
                    log.info("GitHub access token obtained successfully.");
                    return fetchGitHubUserDetails(accessToken);
                })
                .flatMap(githubUser -> {
                    return fetchGitHubPrimaryEmail(githubUser.accessToken())
                            .flatMap(primaryEmail -> {
                                if (primaryEmail == null || primaryEmail.isBlank()) {
                                    log.error("Could not retrieve primary verified email for GitHub user ID: {}", githubUser.id());
                                    return Mono.error(new RuntimeException("Primary verified email not found or not accessible for this GitHub user. Please ensure your primary email on GitHub is verified and public, or grant the 'user:email' scope."));
                                }
                                log.info("Primary GitHub email obtained: {}", primaryEmail);
                                return findOrCreateFirebaseUser(primaryEmail, githubUser.nameOrLogin(), githubUser.avatarUrl())
                                        .flatMap(firebaseUserRecord -> {
                                            String firebaseUid = firebaseUserRecord.getUid();
                                            String provider = "github.com";
                                            String providerId = String.valueOf(githubUser.id());
                                            try {
                                                log.info("Finding or creating Firestore user profile for Firebase UID: {}", firebaseUid);
                                                User appUser = userService.findOrCreateUserByFirebaseDetails(firebaseUid, primaryEmail, githubUser.nameOrLogin(), provider, providerId);
                                                List<SimpleGrantedAuthority> authorities = appUser.getRoles().stream()
                                                        .map(SimpleGrantedAuthority::new)
                                                        .collect(Collectors.toList());
                                                Authentication authentication = new UsernamePasswordAuthenticationToken(firebaseUid, null, authorities);
                                                String jwt = jwtTokenProvider.generateToken(authentication);
                                                log.info("Generated API JWT for user UID {} (GitHub): {}", firebaseUid, jwt);
                                                AuthResponse response = new AuthResponse(true, "GitHub login successful.");
                                                response.setToken(jwt);
                                                response.setUserId(firebaseUid);
                                                return Mono.<ResponseEntity<?>>just(ResponseEntity.ok(response));
                                            } catch (FirestoreInteractionException e) {
                                                log.error("Firestore error during GitHub user profile handling for Firebase UID {}: {}", firebaseUid, e.getMessage(), e);
                                                return Mono.error(e);
                                            }
                                        });
                            });
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("GitHub API call failed: Status {}, Body {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
                    String message = "GitHub API interaction failed.";
                    if (e.getStatusCode().is4xxClientError()) {
                        message = "Invalid request or code provided to GitHub.";
                    } else if (e.getStatusCode().is5xxServerError()) {
                        message = "GitHub server error during authentication.";
                    }
                    return Mono.just(ResponseEntity.status(e.getStatusCode())
                            .body(new AuthResponse(false, message)));
                })
                .onErrorResume(FirebaseAuthException.class, e -> {
                    log.error("FirebaseAuthException during GitHub Sign-In flow: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new AuthResponse(false, "Error processing user account: " + e.getMessage())));
                })
                .onErrorResume(FirestoreInteractionException.class, e -> {
                    log.error("FirestoreInteractionException during GitHub Sign-In flow: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new AuthResponse(false, "Error accessing user profile data after GitHub Sign-In.")));
                })
                .onErrorResume(RuntimeException.class, e -> {
                    log.error("Runtime error during GitHub verification flow: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new AuthResponse(false, e.getMessage())));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Unexpected error during GitHub verification flow: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new AuthResponse(false, "An unexpected error occurred during GitHub Sign-In processing.")));
                });
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = getUserIdFromAuthentication(authentication);

        log.info("Received request to change password for user ID: {}", userId);

        try {
            firebaseService.updateUserPassword(userId, request.getNewPassword());

            log.info("Password successfully changed for user ID: {}", userId);

            return ResponseEntity.ok(new AuthResponse(true, "Password changed successfully."));

        } catch (FirebaseAuthException e) {
            log.error("Failed to change password for user ID {}: {}", userId, e.getMessage());

            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

            if (message.contains("weak password") || message.contains("weak_password")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new AuthResponse(false, "New password is too weak. It must be at least 6 characters long."));
            } else if (message.contains("user not found") || message.contains("user_not_found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new AuthResponse(false, "User not found. Cannot change password."));
            }

            String errorCodeIdentifier = "UNKNOWN";
             if (e.getErrorCode() != null) {
                 errorCodeIdentifier = e.getErrorCode().name();
             } else if (e.getAuthErrorCode() != null) {
                 errorCodeIdentifier = e.getAuthErrorCode().name();
             }

            log.warn("Password change failed for user {}. Error Code: {}, Message: {}", userId, errorCodeIdentifier, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AuthResponse(false, "Failed to change password due to a server error. Please try again later."));

        } catch (IllegalArgumentException e) {
             log.warn("Invalid argument during password change for user ID {}: {}", userId, e.getMessage());
             return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthResponse(false, "Invalid request: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error changing password for user ID {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AuthResponse(false, "An unexpected error occurred while changing the password."));
        }
    }


    private Mono<GitHubUser> fetchGitHubUserDetails(String accessToken) {
        return webClient.get()
                .uri(githubUserUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(userMap -> {
                    Long id = Optional.ofNullable(userMap.get("id")).map(obj -> ((Number) obj).longValue()).orElse(null);
                    String login = (String) userMap.get("login");
                    String name = (String) userMap.get("name");
                    String avatarUrl = (String) userMap.get("avatar_url");
                    if (id == null || login == null) {
                        log.error("Essential GitHub user details (ID or Login) missing in API response: {}", userMap);
                        throw new IllegalStateException("Essential GitHub user details missing from API response.");
                    }
                    log.info("Fetched GitHub user details: ID={}, Login={}, Name={}", id, login, name);
                    return new GitHubUser(id, login, name, avatarUrl, accessToken);
                });
    }

    private Mono<String> fetchGitHubPrimaryEmail(String accessToken) {
         return webClient.get()
                .uri(githubEmailsUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .map(emailsList -> emailsList.stream()
                        .filter(emailMap -> Boolean.TRUE.equals(emailMap.get("primary")) && Boolean.TRUE.equals(emailMap.get("verified")))
                        .map(emailMap -> (String) emailMap.get("email"))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null)
                );
    }

    private record GitHubUser(Long id, String login, String name, String avatarUrl, String accessToken) {
        String nameOrLogin() {
            return (name != null && !name.isBlank()) ? name : login;
        }
    }

    private Mono<UserRecord> findOrCreateFirebaseUser(String email, String name, String pictureUrl) {
         return Mono.fromCallable(() -> {
            try {
                log.debug("Attempting to find Firebase user by email: {}", email);
                return FirebaseAuth.getInstance(firebaseService.getFirebaseApp()).getUserByEmail(email);
            } catch (FirebaseAuthException e) {
                if (AuthErrorCode.USER_NOT_FOUND.equals(e.getAuthErrorCode())) {
                    log.info("No existing Firebase user found for email {}. Creating new Firebase user.", email);
                    UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                            .setEmail(email)
                            .setEmailVerified(true)
                            .setDisplayName(name)
                            .setPhotoUrl(pictureUrl)
                            .setDisabled(false);
                    try {
                        UserRecord newUser = FirebaseAuth.getInstance(firebaseService.getFirebaseApp()).createUser(createRequest);
                        log.info("Successfully created Firebase user for email {} with UID: {}", email, newUser.getUid());
                        return newUser;
                    } catch (FirebaseAuthException createEx) {
                        log.error("Failed to create Firebase user for email {}: {}", email, createEx.getMessage(), createEx);
                        throw new RuntimeException("Firebase user creation failed: " + createEx.getMessage(), createEx);
                    }
                } else {
                    log.error("FirebaseAuthException while finding user for email {}: {}", email, e.getMessage(), e);
                    throw new RuntimeException("Firebase user lookup failed: " + e.getMessage(), e);
                }
            }
        }).onErrorMap(ex -> {
            if (ex instanceof RuntimeException && ex.getCause() instanceof FirebaseAuthException) {
                return (FirebaseAuthException) ex.getCause();
            }
            return ex;
        });
    }

    private String getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Attempt to access protected endpoint without authentication.");
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
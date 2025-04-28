package edu.cit.audioscholar.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.google.cloud.firestore.FieldValue;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import edu.cit.audioscholar.dto.RegistrationRequest;
import edu.cit.audioscholar.dto.UpdateUserProfileRequest;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.User;

@Service
public class UserService {

    private static final String COLLECTION_NAME = "users";
    private static final String USER_CACHE = "usersById";
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final FirebaseService firebaseService;
    private final NhostStorageService nhostStorageService;
    private final Path tempFileDir;

    @Autowired
    public UserService(FirebaseService firebaseService, NhostStorageService nhostStorageService,
            @Value("${app.temp-file-dir}") String tempFileDirStr) {
        this.firebaseService = firebaseService;
        this.nhostStorageService = nhostStorageService;
        this.tempFileDir = Paths.get(tempFileDirStr);
        try {
            Files.createDirectories(this.tempFileDir);
            log.info("User service temporary file directory verified/created at: {}",
                    this.tempFileDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("Could not create temporary file directory for user service: {}",
                    this.tempFileDir.toAbsolutePath(), e);
            throw new RuntimeException(
                    "Failed to initialize temporary file directory for user service", e);
        }
    }

    public User registerNewUser(RegistrationRequest request)
            throws FirebaseAuthException, ExecutionException, InterruptedException {
        log.info("Attempting registration process for email: {}", request.getEmail());
        String displayName = (request.getFirstName() + " " + request.getLastName()).trim();
        if (!StringUtils.hasText(displayName)) {
            displayName = request.getEmail().split("@")[0];
        }
        UserRecord firebaseUserRecord = firebaseService.createFirebaseUser(request.getEmail(),
                request.getPassword(), displayName);
        User newUser = new User();
        newUser.setUserId(firebaseUserRecord.getUid());
        newUser.setEmail(firebaseUserRecord.getEmail());
        newUser.setDisplayName(displayName);
        newUser.setFirstName(request.getFirstName());
        newUser.setLastName(request.getLastName());
        newUser.setProvider("email");
        newUser.setRoles(List.of("ROLE_USER"));
        log.info("Saving user profile to Firestore for UID: {}", firebaseUserRecord.getUid());
        return createUser(newUser);
    }

    @CachePut(value = USER_CACHE, key = "#user.userId")
    public User createUser(User user) throws FirestoreInteractionException {
        if (user.getUserId() == null || user.getUserId().isBlank()) {
            log.error("User ID cannot be null or blank when creating user profile.");
            throw new IllegalArgumentException(
                    "User ID from Firebase Auth is required to create profile.");
        }
        log.info("Saving user profile data for userId: {}", user.getUserId());
        try {
            if (user.getRoles() == null || user.getRoles().isEmpty()) {
                user.setRoles(List.of("ROLE_USER"));
            }
            if (user.getRecordingIds() == null) {
                user.setRecordingIds(List.of());
            }
            if (user.getFavoriteRecordingIds() == null) {
                user.setFavoriteRecordingIds(List.of());
            }
            if (!StringUtils.hasText(user.getFirstName())
                    && !StringUtils.hasText(user.getLastName())
                    && StringUtils.hasText(user.getDisplayName())) {
                String[] names = user.getDisplayName().split(" ", 2);
                user.setFirstName(names[0]);
                if (names.length > 1) {
                    user.setLastName(names[1]);
                }
            }

            Map<String, Object> userMap = user.toMap();
            log.debug("User object map being sent to Firestore during creation for UID {}: {}",
                    user.getUserId(), userMap);
            firebaseService.saveData(COLLECTION_NAME, user.getUserId(), userMap);
            log.info("Successfully created user profile in Firestore for UID: {}",
                    user.getUserId());
            log.debug("User object added/updated in cache '{}' with key: {}", USER_CACHE,
                    user.getUserId());
            return user;
        } catch (Exception e) {
            log.error("Failed to save user {} to Firestore: {}", user.getUserId(), e.getMessage(),
                    e);
            throw new FirestoreInteractionException(
                    "Failed to create user profile in Firestore for UID: " + user.getUserId(), e);
        }
    }

    @Cacheable(value = USER_CACHE, key = "#userId", unless = "#result == null")
    public User getUserById(String userId) throws FirestoreInteractionException {
        if (!StringUtils.hasText(userId)) {
            log.warn("Attempted to fetch user with null or blank ID.");
            return null;
        }
        log.info("Fetching user by ID from Firestore (cache miss): {}", userId);
        try {
            Map<String, Object> data = firebaseService.getData(COLLECTION_NAME, userId);
            if (data == null) {
                log.warn("User not found in Firestore for ID: {}", userId);
                return null;
            }
            User user = User.fromMap(data);
            List<String> tokens = (List<String>) data.get("fcmTokens");
            user.setFcmTokens(tokens != null ? List.copyOf(tokens) : List.of());
            return user;
        } catch (Exception e) {
            log.error("Failed to get user {} from Firestore: {}", userId, e.getMessage(), e);
            throw new FirestoreInteractionException(
                    "Failed to retrieve user profile from Firestore for UID: " + userId, e);
        }
    }

    @CacheEvict(value = USER_CACHE, key = "#uid")
    public User findOrCreateUserByFirebaseDetails(@NonNull String uid, String email, String name,
            String provider, String providerId, @Nullable String photoUrl)
            throws FirestoreInteractionException {
        if (uid.isBlank()) {
            log.error("Cannot find or create user with blank UID.");
            throw new IllegalArgumentException("Firebase UID cannot be blank.");
        }
        log.info("Finding or creating user profile for UID: {} (Cache evicted)", uid);
        User existingUser = getUserById(uid);

        if (existingUser != null) {
            log.info("Found existing user profile for UID: {}", uid);
            boolean needsUpdate = false;
            if (StringUtils.hasText(photoUrl)
                    && !StringUtils.hasText(existingUser.getProfileImageUrl())) {
                log.info(
                        "Updating Firestore profile image URL from provider for existing user {} because Firestore URL was blank. New URL: {}",
                        uid, photoUrl);
                existingUser.setProfileImageUrl(photoUrl);
                needsUpdate = true;
            } else if (StringUtils.hasText(existingUser.getProfileImageUrl())
                    && StringUtils.hasText(photoUrl)
                    && !Objects.equals(photoUrl, existingUser.getProfileImageUrl())) {
                log.debug(
                        "Keeping existing profile image URL for UID {} as it differs from provider URL '{}'.",
                        uid, photoUrl);
            }

            if (StringUtils.hasText(name) && !StringUtils.hasText(existingUser.getDisplayName())) {
                log.info(
                        "Updating Firestore display name from provider for existing user {} because Firestore name was blank. New name: {}",
                        uid, name);
                existingUser.setDisplayName(name);
                if (!StringUtils.hasText(existingUser.getFirstName())
                        || !StringUtils.hasText(existingUser.getLastName())) {
                    String[] names = name.split(" ", 2);
                    existingUser.setFirstName(names[0]);
                    if (names.length > 1) {
                        existingUser.setLastName(names[1]);
                    } else {
                        existingUser.setLastName(null);
                    }
                }
                needsUpdate = true;
            } else if (StringUtils.hasText(existingUser.getDisplayName())
                    && StringUtils.hasText(name)
                    && !Objects.equals(name, existingUser.getDisplayName())) {
                log.debug(
                        "Keeping existing display name '{}' for UID {} as it differs from provider name '{}'.",
                        existingUser.getDisplayName(), uid, name);
            }

            if (needsUpdate) {
                log.info(
                        "Calling updateUser for UID {} due to provider data mismatch or necessary update.",
                        uid);
                return updateUser(existingUser);
            } else {
                log.info(
                        "No provider data mismatch requiring update found for UID {}. Returning existing user.",
                        uid);
                return existingUser;
            }
        } else {
            log.info("No existing user profile found for UID: {}. Creating new profile.", uid);
            User newUser = new User();
            newUser.setUserId(uid);
            newUser.setEmail(email);
            newUser.setDisplayName((StringUtils.hasText(name)) ? name
                    : (email != null ? email.split("@")[0] : "User"));
            if (StringUtils.hasText(newUser.getDisplayName())) {
                String[] names = newUser.getDisplayName().split(" ", 2);
                newUser.setFirstName(names[0]);
                if (names.length > 1) {
                    newUser.setLastName(names[1]);
                }
            }
            newUser.setProvider(provider);
            newUser.setProviderId(providerId);
            newUser.setProfileImageUrl(photoUrl);
            newUser.setRoles(List.of("ROLE_USER"));
            return createUser(newUser);
        }
    }

    @CachePut(value = USER_CACHE, key = "#user.userId")
    public User updateUser(User user) throws FirestoreInteractionException {
        if (user.getUserId() == null || user.getUserId().isBlank()) {
            log.error("User ID cannot be null or blank when updating user profile.");
            throw new IllegalArgumentException("User ID is required to update profile.");
        }
        log.info("Updating user profile (full object) for userId: {}", user.getUserId());
        try {
            if (user.getRoles() == null) {
                user.setRoles(List.of("ROLE_USER"));
            } else if (user.getRoles().isEmpty()) {
                user.setRoles(List.of("ROLE_USER"));
            }
            if (user.getRecordingIds() == null) {
                user.setRecordingIds(List.of());
            }
            if (user.getFavoriteRecordingIds() == null) {
                user.setFavoriteRecordingIds(List.of());
            }

            Map<String, Object> userMap = user.toMap();
            log.debug("User object map being sent to Firestore during update for UID {}: {}",
                    user.getUserId(), userMap);
            log.debug("Profile Image URL in map for UID {}: {}", user.getUserId(),
                    userMap.get("profileImageUrl"));
            firebaseService.saveData(COLLECTION_NAME, user.getUserId(), userMap);
            log.info("Successfully updated user profile in Firestore for UID: {}",
                    user.getUserId());
            log.debug("User object updated in cache '{}' with key: {}", USER_CACHE,
                    user.getUserId());
            return user;
        } catch (Exception e) {
            log.error("Failed to update user {} in Firestore: {}", user.getUserId(), e.getMessage(),
                    e);
            throw new FirestoreInteractionException(
                    "Failed to update user profile in Firestore for UID: " + user.getUserId(), e);
        }
    }

    @CacheEvict(value = USER_CACHE, key = "#userId")
    public User updateUserProfileDetails(String userId, UpdateUserProfileRequest request)
            throws FirestoreInteractionException {
        if (!StringUtils.hasText(userId)) {
            log.error("User ID cannot be null or blank when updating profile details.");
            throw new IllegalArgumentException("User ID is required to update profile details.");
        }
        log.info("Attempting to update profile details for user ID: {} (Cache evicted)", userId);
        User existingUser = getUserById(userId);
        if (existingUser == null) {
            log.warn("Cannot update profile details. User not found in Firestore for ID: {}",
                    userId);
            throw new FirestoreInteractionException(
                    "User not found with ID: " + userId + " for profile update.");
        }

        boolean updated = false;
        if (StringUtils.hasText(request.getFirstName())
                && !Objects.equals(request.getFirstName().trim(), existingUser.getFirstName())) {
            existingUser.setFirstName(request.getFirstName().trim());
            log.debug("Updating firstName for user ID: {}", userId);
            updated = true;
        }
        if (StringUtils.hasText(request.getLastName())
                && !Objects.equals(request.getLastName().trim(), existingUser.getLastName())) {
            existingUser.setLastName(request.getLastName().trim());
            log.debug("Updating lastName for user ID: {}", userId);
            updated = true;
        }
        String oldDisplayName = existingUser.getDisplayName();
        if (StringUtils.hasText(request.getDisplayName())
                && !Objects.equals(request.getDisplayName().trim(), oldDisplayName)) {
            existingUser.setDisplayName(request.getDisplayName().trim());
            log.debug("Updating displayName explicitly for user ID: {}", userId);
            updated = true;
        } else if (!StringUtils.hasText(request.getDisplayName())
                && (updated || !StringUtils.hasText(oldDisplayName))) {
            String potentialDisplayName =
                    (existingUser.getFirstName() + " " + existingUser.getLastName()).trim();
            if (StringUtils.hasText(potentialDisplayName)
                    && !potentialDisplayName.equals(oldDisplayName)) {
                existingUser.setDisplayName(potentialDisplayName);
                log.debug(
                        "Auto-updating displayName based on name change or initial setup for user ID: {}",
                        userId);
                updated = true;
            }
        }

        if (request.getProfileImageUrl() != null && !Objects
                .equals(request.getProfileImageUrl().trim(), existingUser.getProfileImageUrl())) {
            String newUrl = request.getProfileImageUrl().trim();
            existingUser.setProfileImageUrl(newUrl.isEmpty() ? null : newUrl);
            log.debug("Updating profileImageUrl via PUT /me request for user ID: {}. New URL: {}",
                    userId, existingUser.getProfileImageUrl());
            updated = true;
        }

        if (updated) {
            log.info("Saving updated profile details for user ID: {}", userId);
            return updateUser(existingUser);
        } else {
            log.info(
                    "No profile details changed via PUT /me request for user ID: {}. No update performed.",
                    userId);
            return existingUser;
        }
    }

    @CacheEvict(value = USER_CACHE, key = "#userId")
    public User updateUserAvatar(String userId, MultipartFile avatarFile)
            throws IOException, FirestoreInteractionException {
        if (!StringUtils.hasText(userId)) {
            log.error("User ID cannot be null or blank when updating avatar.");
            throw new IllegalArgumentException("User ID is required to update avatar.");
        }
        if (avatarFile == null || avatarFile.isEmpty()) {
            log.warn("Attempted to update avatar for user {} with null or empty file.", userId);
            throw new IllegalArgumentException("Avatar file cannot be empty.");
        }
        log.info("Attempting to update avatar for user ID: {} (Cache evicted)", userId);
        User existingUser = getUserById(userId);
        if (existingUser == null) {
            log.warn("Cannot update avatar. User not found in Firestore for ID: {}", userId);
            throw new FirestoreInteractionException(
                    "User not found with ID: " + userId + " for avatar update.");
        }

        String originalFilename =
                StringUtils.cleanPath(Objects.requireNonNull(avatarFile.getOriginalFilename(),
                        "Original filename cannot be null"));
        String contentType = avatarFile.getContentType();
        String fileExtension = StringUtils.getFilenameExtension(originalFilename);
        String tempFilename = "avatar-" + userId + "-" + UUID.randomUUID()
                + (fileExtension != null ? "." + fileExtension : "");
        Path tempFilePath = this.tempFileDir.resolve(tempFilename);
        File tempFile = null;

        try {
            log.debug("Saving avatar temporarily to: {}", tempFilePath.toAbsolutePath());
            try (InputStream inputStream = avatarFile.getInputStream()) {
                Files.copy(inputStream, tempFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
            tempFile = tempFilePath.toFile();
            log.info("Avatar saved temporarily for user {}: {}", userId,
                    tempFilePath.toAbsolutePath());

            log.info("Uploading new avatar for user {} from temp file {} to Nhost Storage.", userId,
                    tempFilePath.getFileName());
            String nhostFileId =
                    nhostStorageService.uploadFile(tempFile, originalFilename, contentType);
            String publicUrl = nhostStorageService.getPublicUrl(nhostFileId);
            log.info("Avatar uploaded successfully for user {}. Nhost File ID: {}, Public URL: {}",
                    userId, nhostFileId, publicUrl);

            log.debug("Setting profileImageUrl on User object for UID {} before saving. URL: {}",
                    userId, publicUrl);
            existingUser.setProfileImageUrl(publicUrl);

            log.info("Saving updated user profile with new avatar URL for user ID: {}", userId);
            return updateUser(existingUser);

        } catch (IOException e) {
            log.error("IOException during avatar processing (temp save or upload) for user {}: {}",
                    userId, e.getMessage(), e);
            throw new IOException("Failed to process or upload avatar.", e);
        } catch (RuntimeException e) {
            log.error("RuntimeException during Nhost avatar upload/URL generation for user {}: {}",
                    userId, e.getMessage(), e);
            throw new RuntimeException("Failed to process avatar upload with storage service.", e);
        } finally {
            if (tempFilePath != null) {
                try {
                    boolean deleted = Files.deleteIfExists(tempFilePath);
                    if (deleted) {
                        log.debug("Successfully deleted temporary avatar file: {}",
                                tempFilePath.toAbsolutePath());
                    } else {
                        log.debug(
                                "Temporary avatar file not found for deletion (might not have been created): {}",
                                tempFilePath.toAbsolutePath());
                    }
                } catch (IOException e) {
                    log.warn("Could not delete temporary avatar file: {}",
                            tempFilePath.toAbsolutePath(), e);
                }
            }
        }
    }

    @CacheEvict(value = USER_CACHE, key = "#userId")
    public void deleteUser(String userId) throws FirestoreInteractionException {
        if (userId == null || userId.isBlank()) {
            log.error("User ID cannot be null or blank when deleting user profile.");
            throw new IllegalArgumentException("User ID is required to delete profile.");
        }
        log.warn("Deleting user profile from Firestore for userId: {} (Cache evicted)", userId);
        try {
            firebaseService.deleteData(COLLECTION_NAME, userId);
            log.info("Successfully deleted user profile from Firestore for UID: {}", userId);
        } catch (Exception e) {
            log.error("Failed to delete user {} from Firestore: {}", userId, e.getMessage(), e);
            throw new FirestoreInteractionException(
                    "Failed to delete user profile from Firestore for UID: " + userId, e);
        }
    }

    public User findUserByEmail(String email) throws FirestoreInteractionException {
        if (email == null || email.isBlank()) {
            log.warn("Attempted to find user with null or blank email.");
            return null;
        }
        log.debug("Querying user by email (not cached): {}", email);
        try {
            List<Map<String, Object>> results =
                    firebaseService.queryCollection(COLLECTION_NAME, "email", email);
            if (results.isEmpty()) {
                log.info("No user found with email: {}", email);
                return null;
            }
            if (results.size() > 1) {
                log.warn(
                        "Multiple users found with the same email: {}. Returning the first one found.",
                        email);
            }
            return User.fromMap(results.get(0));
        } catch (Exception e) {
            log.error("Failed to find user by email {} from Firestore: {}", email, e.getMessage(),
                    e);
            throw new FirestoreInteractionException(
                    "Failed to find user by email from Firestore: " + email, e);
        }
    }

    public User addFcmToken(String userId, String fcmToken)
            throws FirestoreInteractionException, IllegalArgumentException {
        log.info("Attempting to add FCM token for user ID: {}", userId);
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(fcmToken)) {
            log.error("User ID and FCM token cannot be blank.");
            throw new IllegalArgumentException("User ID and FCM token must be provided.");
        }

        User user = getUserById(userId);
        if (user == null) {
            log.error("User not found with ID: {} while trying to add FCM token.", userId);
            throw new IllegalArgumentException("User not found with ID: " + userId);
        }

        List<String> currentTokens = user.getFcmTokens();
        if (currentTokens == null) {
            log.warn("User {} had a null fcmTokens list in Firestore. Initializing a new list.",
                    userId);
            currentTokens = new java.util.ArrayList<>();
        }
        if (!(currentTokens instanceof java.util.ArrayList)) {
            currentTokens = new java.util.ArrayList<>(currentTokens);
        }

        if (!currentTokens.contains(fcmToken)) {
            log.debug("Adding new FCM token for user ID: {}", userId);
            currentTokens.add(fcmToken);
            user.setFcmTokens(currentTokens);
            return updateUser(user);
        } else {
            log.debug("FCM token already exists for user ID: {}. No update needed.", userId);
            return user;
        }
    }

    public List<String> getFcmTokensForUser(String userId) throws FirestoreInteractionException {
        log.debug("Attempting to retrieve FCM tokens for user ID: {}", userId);
        if (!StringUtils.hasText(userId)) {
            log.warn("Attempted to get FCM tokens with null or blank user ID.");
            return List.of();
        }

        User user = getUserById(userId);
        if (user == null) {
            log.warn("User not found with ID: {} when trying to retrieve FCM tokens.", userId);
            return List.of();
        }

        List<String> tokens = user.getFcmTokens();
        log.info("Retrieved {} FCM token(s) for user ID: {}", tokens.size(), userId);
        return tokens;
    }

    public void removeFcmTokens(String userId, List<String> tokensToRemove) {
        if (userId == null || userId.isBlank() || tokensToRemove == null
                || tokensToRemove.isEmpty()) {
            log.warn(
                    "Attempted to remove FCM tokens with invalid userId or empty token list. userId={}, tokens={}",
                    userId, tokensToRemove);
            return;
        }

        try {
            log.info("Attempting to remove {} stale FCM token(s) for user: {}",
                    tokensToRemove.size(), userId);
            firebaseService.updateDataWithMap(COLLECTION_NAME, userId,
                    Map.of("fcmTokens", FieldValue.arrayRemove(tokensToRemove.toArray())));
            log.info("Successfully removed {} stale FCM token(s) for user: {}",
                    tokensToRemove.size(), userId);
        } catch (FirestoreInteractionException e) {
            log.error("Failed to remove stale FCM tokens for user {}: {}", userId, e.getMessage(),
                    e);
        } catch (Exception e) {
            log.error("Unexpected error removing stale FCM tokens for user {}: {}", userId,
                    e.getMessage(), e);
        }
    }
}

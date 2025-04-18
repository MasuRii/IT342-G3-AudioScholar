package edu.cit.audioscholar.service;

import edu.cit.audioscholar.dto.RegistrationRequest;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class UserService {
    private static final String COLLECTION_NAME = "users";
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final FirebaseService firebaseService;

    @Autowired
    public UserService(FirebaseService firebaseService) {
        this.firebaseService = firebaseService;
    }

    public User registerNewUser(RegistrationRequest request) throws FirebaseAuthException, ExecutionException, InterruptedException {
        log.info("Attempting registration process for email: {}", request.getEmail());


        String displayName = request.getFirstName() + " " + request.getLastName();
        UserRecord firebaseUserRecord = firebaseService.createFirebaseUser(
                request.getEmail(),
                request.getPassword(),
                displayName
        );

        User newUser = new User();
        newUser.setUserId(firebaseUserRecord.getUid());
        newUser.setEmail(firebaseUserRecord.getEmail());
        newUser.setDisplayName(displayName);
        newUser.setProvider("email");
        newUser.setRoles(List.of("ROLE_USER"));

        log.info("Saving user profile to Firestore for UID: {}", firebaseUserRecord.getUid());
        return createUser(newUser);
    }


    public User createUser(User user) throws FirestoreInteractionException {
        if (user.getUserId() == null || user.getUserId().isBlank()) {
             log.error("User ID cannot be null or blank when creating user profile.");
             throw new IllegalArgumentException("User ID from Firebase Auth is required to create profile.");
        }
        log.info("Saving user profile data for userId: {}", user.getUserId());
        try {
            firebaseService.saveData(COLLECTION_NAME, user.getUserId(), user.toMap());
            return user;
        } catch (Exception e) {
             log.error("Failed to save user {} to Firestore: {}", user.getUserId(), e.getMessage(), e);
             throw new FirestoreInteractionException("Failed to create user profile in Firestore for UID: " + user.getUserId(), e);
        }
    }

    public User getUserById(String userId) throws FirestoreInteractionException {
        log.debug("Fetching user by ID: {}", userId);
        try {
            Map<String, Object> data = firebaseService.getData(COLLECTION_NAME, userId);
            if (data == null) {
                log.warn("User not found in Firestore for ID: {}", userId);
                return null;
            }
            return User.fromMap(data);
        } catch (Exception e) {
            log.error("Failed to get user {} from Firestore: {}", userId, e.getMessage(), e);
            throw new FirestoreInteractionException("Failed to retrieve user profile from Firestore for UID: " + userId, e);
        }
    }

    public User findOrCreateUserByFirebaseDetails(@NonNull String uid, String email, String name) throws FirestoreInteractionException {
        if (uid.isBlank()) {
            log.error("Cannot find or create user with blank UID.");
            throw new IllegalArgumentException("Firebase UID cannot be blank.");
        }

        log.info("Finding or creating user profile for UID: {}", uid);
        User existingUser = getUserById(uid);

        if (existingUser != null) {
            log.info("Found existing user profile for UID: {}", uid);
            return existingUser;
        } else {
            log.info("No existing user profile found for UID: {}. Creating new profile.", uid);
            User newUser = new User();
            newUser.setUserId(uid);
            newUser.setEmail(email);
            newUser.setDisplayName(name);
            newUser.setRoles(List.of("ROLE_USER"));

            return createUser(newUser);
        }
    }


    public User updateUser(User user) throws FirestoreInteractionException {
        if (user.getUserId() == null || user.getUserId().isBlank()) {
             log.error("User ID cannot be null or blank when updating user profile.");
             throw new IllegalArgumentException("User ID is required to update profile.");
        }
        log.info("Updating user profile for userId: {}", user.getUserId());
         try {
            firebaseService.updateData(COLLECTION_NAME, user.getUserId(), user.toMap());
            return user;
        } catch (Exception e) {
            log.error("Failed to update user {} in Firestore: {}", user.getUserId(), e.getMessage(), e);
            throw new FirestoreInteractionException("Failed to update user profile in Firestore for UID: " + user.getUserId(), e);
        }
    }

    public void deleteUser(String userId) throws FirestoreInteractionException {
         if (userId == null || userId.isBlank()) {
             log.error("User ID cannot be null or blank when deleting user profile.");
             throw new IllegalArgumentException("User ID is required to delete profile.");
        }
        log.warn("Deleting user profile from Firestore for userId: {}", userId);
         try {
            firebaseService.deleteData(COLLECTION_NAME, userId);
        } catch (Exception e) {
            log.error("Failed to delete user {} from Firestore: {}", userId, e.getMessage(), e);
            throw new FirestoreInteractionException("Failed to delete user profile from Firestore for UID: " + userId, e);
        }
    }

    public User findUserByEmail(String email) throws FirestoreInteractionException {
        if (email == null || email.isBlank()) {
            return null;
        }
        log.debug("Querying user by email: {}", email);
        try {
            List<Map<String, Object>> results = firebaseService.queryCollection(COLLECTION_NAME, "email", email);
            if (results.isEmpty()) {
                log.info("No user found with email: {}", email);
                return null;
            }
            if (results.size() > 1) {
                log.warn("Multiple users found with the same email: {}. Returning the first one.", email);
            }
            return User.fromMap(results.get(0));
        } catch (Exception e) {
             log.error("Failed to find user by email {} from Firestore: {}", email, e.getMessage(), e);
             throw new FirestoreInteractionException("Failed to find user by email from Firestore: " + email, e);
        }
    }

}
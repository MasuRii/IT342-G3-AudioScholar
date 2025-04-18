package edu.cit.audioscholar.service;

import edu.cit.audioscholar.dto.RegistrationRequest;
import edu.cit.audioscholar.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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


    public User createUser(User user) throws ExecutionException, InterruptedException {
        if (user.getUserId() == null || user.getUserId().isBlank()) {
             log.error("User ID cannot be null or blank when creating user profile.");
             throw new IllegalArgumentException("User ID from Firebase Auth is required to create profile.");
        }
        log.info("Saving user profile data for userId: {}", user.getUserId());
        firebaseService.saveData(COLLECTION_NAME, user.getUserId(), user.toMap());
        return user;
    }

    public User getUserById(String userId) throws ExecutionException, InterruptedException {
        log.debug("Fetching user by ID: {}", userId);
        Map<String, Object> data = firebaseService.getData(COLLECTION_NAME, userId);
        if (data == null) {
            log.warn("User not found in Firestore for ID: {}", userId);
            return null;
        }
        return User.fromMap(data);
    }

    public User updateUser(User user) throws ExecutionException, InterruptedException {
        log.info("Updating user profile for userId: {}", user.getUserId());
        firebaseService.updateData(COLLECTION_NAME, user.getUserId(), user.toMap());
        return user;
    }

    public void deleteUser(String userId) throws ExecutionException, InterruptedException {
        log.warn("Deleting user profile from Firestore for userId: {}", userId);
        firebaseService.deleteData(COLLECTION_NAME, userId);
    }

    public List<User> getUsersByEmail(String email) throws ExecutionException, InterruptedException {
        log.debug("Querying users by email: {}", email);
        List<Map<String, Object>> results = firebaseService.queryCollection(COLLECTION_NAME, "email", email);
        return results.stream().map(User::fromMap).collect(Collectors.toList());
    }
}
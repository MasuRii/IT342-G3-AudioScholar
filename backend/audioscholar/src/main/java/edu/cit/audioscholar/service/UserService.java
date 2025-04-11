package edu.cit.audioscholar.service;

import edu.cit.audioscholar.model.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class UserService {
    private static final String COLLECTION_NAME = "users";
    
    private final FirebaseService firebaseService;
    
    public UserService(FirebaseService firebaseService) {
        this.firebaseService = firebaseService;
    }
    
    public User createUser(User user) throws ExecutionException, InterruptedException {
        if (user.getUserId() == null) {
            user.setUserId(UUID.randomUUID().toString());
        }
        firebaseService.saveData(COLLECTION_NAME, user.getUserId(), user.toMap());
        return user;
    }
    
    public User getUserById(String userId) throws ExecutionException, InterruptedException {
        Map<String, Object> data = firebaseService.getData(COLLECTION_NAME, userId);
        return data != null ? User.fromMap(data) : null;
    }
    
    public User updateUser(User user) throws ExecutionException, InterruptedException {
        firebaseService.updateData(COLLECTION_NAME, user.getUserId(), user.toMap());
        return user;
    }
    
    public void deleteUser(String userId) throws ExecutionException, InterruptedException {
        firebaseService.deleteData(COLLECTION_NAME, userId);
    }
    
    public List<User> getUsersByEmail(String email) throws ExecutionException, InterruptedException {
        List<Map<String, Object>> results = firebaseService.queryCollection(COLLECTION_NAME, "email", email);
        return results.stream().map(User::fromMap).collect(Collectors.toList());
    }
}
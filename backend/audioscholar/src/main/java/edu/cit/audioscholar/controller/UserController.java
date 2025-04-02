package edu.cit.audioscholar.controller;

import edu.cit.audioscholar.model.User;
import edu.cit.audioscholar.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ✅ Create User (POST)
    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) throws ExecutionException, InterruptedException {
        User createdUser = userService.createUser(user);
        return ResponseEntity.status(201).body(createdUser); // ✅ 201 Created
    }

    // ✅ Get User by ID (GET)
    @GetMapping("/{userId}")
    public ResponseEntity<User> getUserById(@PathVariable String userId) throws ExecutionException, InterruptedException {
        User user = userService.getUserById(userId);
        return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build(); // ✅ 200 OK or 404 Not Found
    }

    // ✅ Update User (PUT)
    @PutMapping("/{userId}")
    public ResponseEntity<User> updateUser(@PathVariable String userId, @RequestBody User user) throws ExecutionException, InterruptedException {
        user.setUserId(userId); // Ensure ID is correctly set
        User updatedUser = userService.updateUser(user);
        return ResponseEntity.ok(updatedUser); // ✅ 200 OK
    }

    // ✅ Delete User (DELETE)
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) throws ExecutionException, InterruptedException {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build(); // ✅ 204 No Content
    }

    // ✅ Get Users by Email (GET)
    @GetMapping("/email/{email}")
    public ResponseEntity<List<User>> getUsersByEmail(@PathVariable String email) throws ExecutionException, InterruptedException {
        List<User> users = userService.getUsersByEmail(email);
        return users.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(users); // ✅ 200 OK or 404 Not Found
    }
}
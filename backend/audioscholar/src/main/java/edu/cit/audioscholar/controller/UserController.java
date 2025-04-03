package edu.cit.audioscholar.controller;

import edu.cit.audioscholar.model.User;
import edu.cit.audioscholar.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) throws ExecutionException, InterruptedException {
        User createdUser = userService.createUser(user);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}")
                .buildAndExpand(createdUser.getUserId()).toUri();
        return ResponseEntity.created(location).body(createdUser);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<User> getUserById(@PathVariable String userId) throws ExecutionException, InterruptedException {
        User user = userService.getUserById(userId);
        return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build();
    }

    @PutMapping("/{userId}")
    public ResponseEntity<User> updateUser(@PathVariable String userId, @RequestBody User user) throws ExecutionException, InterruptedException {
        user.setUserId(userId); 
        User updatedUser = userService.updateUser(user);
        return updatedUser != null ? ResponseEntity.ok(updatedUser) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) throws ExecutionException, InterruptedException {
        userService.deleteUser(userId); 
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<User>> findUsers(@RequestParam(value = "email", required = false) String email) 
            throws ExecutionException, InterruptedException {
        
        List<User> users;
        if (email != null && !email.isEmpty()) {
            users = userService.getUsersByEmail(email);
        } else {
             users = List.of();
        }
        
        return ResponseEntity.ok(users); 
    }
}
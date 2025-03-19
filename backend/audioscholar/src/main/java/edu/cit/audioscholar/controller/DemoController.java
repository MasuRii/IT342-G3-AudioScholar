package edu.cit.audioscholar.controller;

import edu.cit.audioscholar.model.Recording;
import edu.cit.audioscholar.model.Summary;
import edu.cit.audioscholar.model.User;
import edu.cit.audioscholar.service.RecordingService;
import edu.cit.audioscholar.service.SummaryService;
import edu.cit.audioscholar.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final UserService userService;
    private final RecordingService recordingService;
    private final SummaryService summaryService;

    public DemoController(UserService userService, RecordingService recordingService, SummaryService summaryService) {
        this.userService = userService;
        this.recordingService = recordingService;
        this.summaryService = summaryService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> getStatus() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "AudioScholar API is running");
        status.put("message", "Firebase models are ready to be tested");
        return ResponseEntity.ok(status);
    }

    // Changed to GetMapping for easier testing in browser
    @GetMapping("/create-test-data")
    public ResponseEntity<Map<String, Object>> createTestData() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Create a test user
            String userId = UUID.randomUUID().toString();
            User user = new User(userId, "test@audioscholar.com", "Test User");
            user = userService.createUser(user);
            response.put("user", user.toMap());
            
            // Create a test recording
            String recordingId = UUID.randomUUID().toString();
            Recording recording = new Recording(recordingId, userId, "Test Lecture Recording", 
                    "https://storage.audioscholar.com/test-audio.mp3");
            recording.setDuration("10:30");
            recording = recordingService.createRecording(recording);
            response.put("recording", recording.toMap());
            
            // Create a test summary
            String summaryId = UUID.randomUUID().toString();
            Summary summary = new Summary(summaryId, recordingId, 
                    "This is the full transcription of the test lecture. It contains detailed information about the topic.");
            summary.setCondensedSummary("This is a condensed summary of the lecture.");
            summary.setKeyPoints(Arrays.asList(
                    "First key point about the lecture",
                    "Second important concept discussed",
                    "Final takeaway from the lecture"
            ));
            summary.setTopics(Arrays.asList("Computer Science", "AI", "Education"));
            summary = summaryService.createSummary(summary);
            response.put("summary", summary.toMap());
            
            response.put("status", "success");
            response.put("message", "Test data created successfully");
            
        } catch (ExecutionException | InterruptedException e) {
            response.put("status", "error");
            response.put("message", "Failed to create test data: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            User user = userService.getUserById(userId);
            if (user != null) {
                response.put("user", user.toMap());
                response.put("status", "success");
            } else {
                response.put("status", "error");
                response.put("message", "User not found");
                return ResponseEntity.status(404).body(response);
            }
        } catch (ExecutionException | InterruptedException e) {
            response.put("status", "error");
            response.put("message", "Failed to retrieve user: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/recording/{recordingId}")
    public ResponseEntity<Map<String, Object>> getRecording(@PathVariable String recordingId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Recording recording = recordingService.getRecordingById(recordingId);
            if (recording != null) {
                response.put("recording", recording.toMap());
                response.put("status", "success");
            } else {
                response.put("status", "error");
                response.put("message", "Recording not found");
                return ResponseEntity.status(404).body(response);
            }
        } catch (ExecutionException | InterruptedException e) {
            response.put("status", "error");
            response.put("message", "Failed to retrieve recording: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/summary/{summaryId}")
    public ResponseEntity<Map<String, Object>> getSummary(@PathVariable String summaryId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Summary summary = summaryService.getSummaryById(summaryId);
            if (summary != null) {
                response.put("summary", summary.toMap());
                response.put("status", "success");
            } else {
                response.put("status", "error");
                response.put("message", "Summary not found");
                return ResponseEntity.status(404).body(response);
            }
        } catch (ExecutionException | InterruptedException e) {
            response.put("status", "error");
            response.put("message", "Failed to retrieve summary: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/user/{userId}/recordings")
    public ResponseEntity<Map<String, Object>> getUserRecordings(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            var recordings = recordingService.getRecordingsByUserId(userId);
            response.put("recordings", recordings);
            response.put("count", recordings.size());
            response.put("status", "success");
        } catch (ExecutionException | InterruptedException e) {
            response.put("status", "error");
            response.put("message", "Failed to retrieve user recordings: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    // Changed to GetMapping for easier testing in browser
    @GetMapping("/cleanup-test-data/{userId}")
    public ResponseEntity<Map<String, Object>> cleanupTestData(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get user's recordings
            var recordings = recordingService.getRecordingsByUserId(userId);
            
            // Delete summaries associated with recordings
            for (Recording recording : recordings) {
                if (recording.getSummaryId() != null) {
                    summaryService.deleteSummary(recording.getSummaryId());
                }
                recordingService.deleteRecording(recording.getRecordingId());
            }
            
            // Delete user
            userService.deleteUser(userId);
            
            response.put("status", "success");
            response.put("message", "Test data cleaned up successfully");
            
        } catch (ExecutionException | InterruptedException e) {
            response.put("status", "error");
            response.put("message", "Failed to clean up test data: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    // Add a simple home page for testing
    @GetMapping("/")
    public String homePage() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>AudioScholar API Test</title>
                <style>
                    body { font-family: Arial, sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; }
                    h1 { color: #333; }
                    .btn { 
                        display: inline-block; 
                        padding: 10px 15px; 
                        background-color: #4CAF50; 
                        color: white; 
                        text-decoration: none; 
                        border-radius: 4px;
                        margin: 5px;
                    }
                    .btn:hover { background-color: #3e8e41; }
                    pre { background-color: #f5f5f5; padding: 10px; border-radius: 4px; overflow-x: auto; }
                    #result { margin-top: 20px; }
                </style>
            </head>
            <body>
                <h1>AudioScholar API Test</h1>
                <p>Use the buttons below to test the API endpoints:</p>
                
                <a href="/api/demo/status" class="btn">Check Status</a>
                <a href="/api/demo/create-test-data" class="btn">Create Test Data</a>
                
                <div id="result">
                    <h2>Results:</h2>
                    <pre id="resultContent">Click a button to see results</pre>
                </div>
                
                <script>
                    document.querySelectorAll('.btn').forEach(btn => {
                        btn.addEventListener('click', async (e) => {
                            e.preventDefault();
                            const url = btn.getAttribute('href');
                            try {
                                const response = await fetch(url);
                                const data = await response.json();
                                document.getElementById('resultContent').textContent = JSON.stringify(data, null, 2);
                                
                                // If we created test data, add buttons to view the created entities
                                if (url === '/api/demo/create-test-data' && data.status === 'success') {
                                    const userId = data.user.userId;
                                    const recordingId = data.recording.recordingId;
                                    const summaryId = data.summary.summaryId;
                                    
                                    const btnsDiv = document.createElement('div');
                                    btnsDiv.innerHTML = `
                                        <a href="/api/demo/user/${userId}" class="btn">View User</a>
                                        <a href="/api/demo/recording/${recordingId}" class="btn">View Recording</a>
                                        <a href="/api/demo/summary/${summaryId}" class="btn">View Summary</a>
                                        <a href="/api/demo/user/${userId}/recordings" class="btn">View User Recordings</a>
                                        <a href="/api/demo/cleanup-test-data/${userId}" class="btn">Clean Up Test Data</a>
                                    `;
                                    
                                    document.body.appendChild(btnsDiv);
                                    
                                    btnsDiv.querySelectorAll('.btn').forEach(newBtn => {
                                        newBtn.addEventListener('click', async (e) => {
                                            e.preventDefault();
                                            const newUrl = newBtn.getAttribute('href');
                                            try {
                                                const newResponse = await fetch(newUrl);
                                                const newData = await newResponse.json();
                                                document.getElementById('resultContent').textContent = JSON.stringify(newData, null, 2);
                                            } catch (error) {
                                                document.getElementById('resultContent').textContent = error.toString();
                                            }
                                        });
                                    });
                                }
                            } catch (error) {
                                document.getElementById('resultContent').textContent = error.toString();
                            }
                        });
                    });
                </script>
            </body>
            </html>
        """;
    }
}
package edu.cit.audioscholar.service;

import edu.cit.audioscholar.exception.InvalidAudioFileException;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.Recording;
import com.google.cloud.Timestamp;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
public class RecordingService {
    private static final String RECORDINGS_COLLECTION = "recordings";
    private static final String METADATA_COLLECTION = "audio_metadata";
    private static final long MAX_FILE_SIZE_BYTES = 500 * 1024 * 1024; // 500MB
    private static final List<String> SUPPORTED_FORMATS = Arrays.asList("audio/wav", "audio/mpeg", "audio/aiff", "audio/aac", "audio/ogg", "audio/flac");

    private final FirebaseService firebaseService;
    private final UserService userService;
    private final NhostStorageService nhostStorageService;

    public RecordingService(FirebaseService firebaseService, UserService userService, NhostStorageService nhostStorageService) {
        this.firebaseService = firebaseService;
        this.userService = userService;
        this.nhostStorageService = nhostStorageService;
    }

    public AudioMetadata uploadAudioFile(MultipartFile file, String title, String description, String userId)
            throws IOException, ExecutionException, InterruptedException, InvalidAudioFileException {

        // 1. Server-side Validation
        validateAudioFile(file);

        // 2. Store the audio file (using NhostStorageService)
        String fileUrl = nhostStorageService.uploadFile(file); // Using the method that accepts only MultipartFile

        // 3. Create AudioMetadata
        AudioMetadata audioMetadata = new AudioMetadata();
        audioMetadata.setId(UUID.randomUUID().toString());
        audioMetadata.setUserId(userId);
        audioMetadata.setStorageUrl(fileUrl); // Use storageUrl as per your AudioMetadata model
        audioMetadata.setFileName(file.getOriginalFilename());
        audioMetadata.setFileSize(file.getSize());
        audioMetadata.setContentType(file.getContentType()); // Use contentType
        audioMetadata.setTitle(title);
        audioMetadata.setDescription(description);
        audioMetadata.setUploadTimestamp(Timestamp.of(new Date()));

        // 4. Save AudioMetadata to Firestore (This will trigger Module 2 processing)
        firebaseService.saveData(METADATA_COLLECTION, audioMetadata.getId(), audioMetadata.toMap());

        // Optionally, you might want to create a Recording entity as well, depending on your data model
        Recording recording = new Recording();
        recording.setRecordingId(UUID.randomUUID().toString());
        recording.setUserId(userId);
        recording.setAudioUrl(fileUrl); // Use audioUrl as per your Recording model
        recording.setFileName(file.getOriginalFilename()); // We need to add a setter for fileName in Recording
        createRecording(recording); // Assuming createRecording handles saving to Firestore

        return audioMetadata;
    }

    private void validateAudioFile(MultipartFile file) throws InvalidAudioFileException {
        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidAudioFileException("File size exceeds the maximum allowed size of 500MB.");
        }

        // Validate file format
        if (!SUPPORTED_FORMATS.contains(file.getContentType())) {
            throw new InvalidAudioFileException("Unsupported audio format. Supported formats are: " + String.join(", ", SUPPORTED_FORMATS));
        }
    }

    public Recording createRecording(Recording recording) throws ExecutionException, InterruptedException {
        // Generate ID if not provided
        if (recording.getRecordingId() == null) {
            recording.setRecordingId(UUID.randomUUID().toString());
        }

        // Set timestamps
        recording.setCreatedAt(new Date());
        recording.setUpdatedAt(new Date());

        // Save to Firestore
        firebaseService.saveData(RECORDINGS_COLLECTION, recording.getRecordingId(), recording.toMap());

        // Update user's recordings list
        var user = userService.getUserById(recording.getUserId());
        if (user != null) {
            user.getRecordingIds().add(recording.getRecordingId());
            userService.updateUser(user);
        }

        return recording;
    }

    public Recording getRecordingById(String recordingId) throws ExecutionException, InterruptedException {
        java.util.Map<String, Object> data = firebaseService.getData(RECORDINGS_COLLECTION, recordingId);
        return data != null ? Recording.fromMap(data) : null;
    }

    public Recording updateRecording(Recording recording) throws ExecutionException, InterruptedException {
        recording.setUpdatedAt(new Date());
        firebaseService.updateData(RECORDINGS_COLLECTION, recording.getRecordingId(), recording.toMap());
        return recording;
    }

    public void deleteRecording(String recordingId) throws ExecutionException, InterruptedException {
        Recording recording = getRecordingById(recordingId);
        if (recording != null) {
            // Remove from user's recordings list
            var user = userService.getUserById(recording.getUserId());
            if (user != null) {
                user.getRecordingIds().remove(recordingId);
                userService.updateUser(user);
            }

            // Delete the recording
            firebaseService.deleteData(RECORDINGS_COLLECTION, recordingId);
        }
    }

    public List<Recording> getRecordingsByUserId(String userId) throws ExecutionException, InterruptedException {
        List<java.util.Map<String, Object>> results = firebaseService.queryCollection(RECORDINGS_COLLECTION, "userId", userId);
        return results.stream().map(Recording::fromMap).collect(java.util.stream.Collectors.toList());
    }
}
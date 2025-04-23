package edu.cit.audioscholar.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.google.cloud.Timestamp;
import edu.cit.audioscholar.exception.InvalidAudioFileException;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.Recording;

@Service
public class RecordingService {
    private static final Logger log = LoggerFactory.getLogger(RecordingService.class);
    private static final String RECORDINGS_COLLECTION = "recordings";
    private static final String METADATA_COLLECTION = "audio_metadata";
    private static final long MAX_FILE_SIZE_BYTES = 500 * 1024 * 1024;
    private static final List<String> SUPPORTED_FORMATS =
            Arrays.asList("audio/wav", "audio/mpeg", "audio/aiff", "audio/aac", "audio/ogg",
                    "audio/flac", "audio/mp3", "audio/x-wav", "audio/x-aiff");

    private final FirebaseService firebaseService;
    private final UserService userService;
    private final NhostStorageService nhostStorageService;
    private final SummaryService summaryService;

    public RecordingService(FirebaseService firebaseService, UserService userService,
            NhostStorageService nhostStorageService, @Lazy SummaryService summaryService) {
        this.firebaseService = firebaseService;
        this.userService = userService;
        this.nhostStorageService = nhostStorageService;
        this.summaryService = summaryService;
    }

    public AudioMetadata uploadAudioFile(MultipartFile file, String title, String description,
            String userId, String recordingId) throws IOException, ExecutionException,
            InterruptedException, InvalidAudioFileException {

        if (!StringUtils.hasText(recordingId)) {
            log.error("Recording ID cannot be null or blank when calling uploadAudioFile.");
            throw new IllegalArgumentException("Recording ID must be provided.");
        }

        validateAudioFile(file);

        log.info("Uploading file '{}' to Nhost storage for user {}", file.getOriginalFilename(),
                userId);
        String nhostFileId = nhostStorageService.uploadFile(file);
        String fileUrl = nhostStorageService.getPublicUrl(nhostFileId);
        log.info("File '{}' uploaded to Nhost, ID: {}, URL: {}", file.getOriginalFilename(),
                nhostFileId, fileUrl);

        AudioMetadata audioMetadata = new AudioMetadata();
        audioMetadata.setId(UUID.randomUUID().toString());
        audioMetadata.setUserId(userId);
        audioMetadata.setStorageUrl(fileUrl);
        audioMetadata.setNhostFileId(nhostFileId);
        audioMetadata.setFileName(file.getOriginalFilename());
        audioMetadata.setFileSize(file.getSize());
        audioMetadata.setContentType(file.getContentType());
        audioMetadata.setTitle(StringUtils.hasText(title) ? title : file.getOriginalFilename());
        audioMetadata.setDescription(description);
        audioMetadata.setUploadTimestamp(Timestamp.of(new Date()));
        audioMetadata.setRecordingId(recordingId);

        log.info("Saving AudioMetadata (ID: {}) with RecordingId: {} to Firestore collection '{}'",
                audioMetadata.getId(), audioMetadata.getRecordingId(), METADATA_COLLECTION);
        firebaseService.saveData(METADATA_COLLECTION, audioMetadata.getId(), audioMetadata.toMap());
        log.info("Successfully saved AudioMetadata (ID: {})", audioMetadata.getId());

        Recording recording = new Recording();
        recording.setRecordingId(recordingId);
        recording.setUserId(userId);
        recording.setAudioUrl(fileUrl);
        recording.setFileName(file.getOriginalFilename());
        recording.setTitle(audioMetadata.getTitle());

        log.info("Creating Recording document with pre-generated ID: {}", recordingId);
        createRecording(recording);
        log.info("Successfully initiated creation of Recording document (ID: {})", recordingId);

        return audioMetadata;
    }

    private void validateAudioFile(MultipartFile file) throws InvalidAudioFileException {
        if (file == null || file.isEmpty()) {
            throw new InvalidAudioFileException("Uploaded file cannot be null or empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidAudioFileException(
                    "File size (" + file.getSize() + " bytes) exceeds the maximum allowed size of "
                            + MAX_FILE_SIZE_BYTES + " bytes.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_FORMATS.contains(contentType.toLowerCase())) {
            log.warn("Unsupported audio format detected: '{}'. Supported formats are: {}",
                    contentType, SUPPORTED_FORMATS);
            throw new InvalidAudioFileException("Unsupported audio format: '" + contentType
                    + "'. Supported formats are: " + String.join(", ", SUPPORTED_FORMATS));
        }
        log.debug("Audio file validation passed for: {}", file.getOriginalFilename());
    }

    public Recording createRecording(Recording recording)
            throws ExecutionException, InterruptedException {
        if (recording.getRecordingId() == null || recording.getRecordingId().isBlank()) {
            log.error("Attempted to create recording with null or blank ID.");
            throw new IllegalArgumentException("Recording ID cannot be null or blank.");
        }
        if (recording.getUserId() == null || recording.getUserId().isBlank()) {
            log.error(
                    "Attempted to create recording with null or blank User ID for Recording ID: {}",
                    recording.getRecordingId());
            throw new IllegalArgumentException(
                    "User ID cannot be null or blank when creating a recording.");
        }
        Date now = new Date();
        if (recording.getCreatedAt() == null) {
            recording.setCreatedAt(now);
        }
        recording.setUpdatedAt(now);
        log.info("Saving Recording (ID: {}) to Firestore collection '{}' for user {}",
                recording.getRecordingId(), RECORDINGS_COLLECTION, recording.getUserId());
        firebaseService.saveData(RECORDINGS_COLLECTION, recording.getRecordingId(),
                recording.toMap());
        log.info("Successfully saved Recording (ID: {}) to Firestore.", recording.getRecordingId());
        var user = userService.getUserById(recording.getUserId());
        if (user != null) {
            if (user.getRecordingIds() == null) {
                user.setRecordingIds(new java.util.ArrayList<>());
            }
            if (!user.getRecordingIds().contains(recording.getRecordingId())) {
                user.getRecordingIds().add(recording.getRecordingId());
                log.info("Adding recording ID {} to user {}'s list and updating user.",
                        recording.getRecordingId(), user.getUserId());
                userService.updateUser(user);
            } else {
                log.warn("Recording ID {} already present in user {}'s list. Skipping user update.",
                        recording.getRecordingId(), user.getUserId());
            }
        } else {
            log.warn(
                    "User {} not found when trying to link recording {}. Recording saved, but not linked in user document.",
                    recording.getUserId(), recording.getRecordingId());
        }
        return recording;
    }

    public Recording getRecordingById(String recordingId)
            throws ExecutionException, InterruptedException {
        if (!StringUtils.hasText(recordingId)) {
            log.warn("getRecordingById called with null or blank ID.");
            return null;
        }
        log.debug("Fetching recording by ID: {}", recordingId);
        java.util.Map<String, Object> data =
                firebaseService.getData(RECORDINGS_COLLECTION, recordingId);
        if (data == null) {
            log.warn("No document found in collection '{}' for ID: {}", RECORDINGS_COLLECTION,
                    recordingId);
            return null;
        }
        log.debug("Data found for recording ID {}, converting from map.", recordingId);
        return Recording.fromMap(data);
    }

    public Recording updateRecording(Recording recording)
            throws ExecutionException, InterruptedException {
        if (recording == null || !StringUtils.hasText(recording.getRecordingId())) {
            log.error("Attempted to update recording with null object or null/blank ID.");
            throw new IllegalArgumentException(
                    "Cannot update recording without a valid object and ID.");
        }
        recording.setUpdatedAt(new Date());
        log.info("Updating Recording (ID: {}) in Firestore.", recording.getRecordingId());
        firebaseService.updateData(RECORDINGS_COLLECTION, recording.getRecordingId(),
                recording.toMap());
        log.info("Successfully updated Recording (ID: {}).", recording.getRecordingId());
        return recording;
    }

    public void deleteRecording(String recordingId)
            throws ExecutionException, InterruptedException {
        if (!StringUtils.hasText(recordingId)) {
            log.warn("deleteRecording called with null or blank ID.");
            return;
        }
        log.info("Attempting to delete recording with ID: {}", recordingId);
        Recording recording = getRecordingById(recordingId);
        if (recording != null) {
            String userId = recording.getUserId();
            String summaryId = recording.getSummaryId();
            String audioUrl = recording.getAudioUrl();
            if (StringUtils.hasText(userId)) {
                var user = userService.getUserById(userId);
                if (user != null && user.getRecordingIds() != null) {
                    boolean removed = user.getRecordingIds().remove(recordingId);
                    if (removed) {
                        log.info("Removed recording ID {} from user {}'s list. Updating user.",
                                recordingId, userId);
                        userService.updateUser(user);
                    } else {
                        log.warn("Recording ID {} not found in user {}'s list during deletion.",
                                recordingId, userId);
                    }
                } else if (user == null) {
                    log.warn(
                            "User {} not found during recording {} deletion. Cannot unlink from user.",
                            userId, recordingId);
                }
            } else {
                log.warn("Recording {} has no associated userId. Cannot unlink from user.",
                        recordingId);
            }
            if (StringUtils.hasText(summaryId)) {
                log.info(
                        "Recording {} has an associated summary ID {}. Attempting to delete summary.",
                        recordingId, summaryId);
                try {
                    summaryService.deleteSummary(summaryId);
                    log.info("Successfully deleted summary {} associated with recording {}",
                            summaryId, recordingId);
                } catch (Exception e) {
                    log.error("Error deleting summary {} associated with recording {}: {}",
                            summaryId, recordingId, e.getMessage(), e);
                }
            }
            log.info("Deleting Recording document (ID: {}) from Firestore.", recordingId);
            firebaseService.deleteData(RECORDINGS_COLLECTION, recordingId);
            log.info("Successfully deleted Recording document (ID: {}).", recordingId);
            if (StringUtils.hasText(audioUrl)) {
                try {
                    String nhostFileId = extractNhostIdFromUrl(audioUrl);
                    if (StringUtils.hasText(nhostFileId)) {
                        log.info("Attempting to delete Nhost file {} associated with recording {}",
                                nhostFileId, recordingId);
                        log.warn(
                                "Nhost file deletion skipped for file ID {} as deleteFile method is not available in NhostStorageService.",
                                nhostFileId);
                    } else {
                        log.warn(
                                "Could not extract Nhost file ID from URL '{}' for recording {}. Cannot delete from storage.",
                                audioUrl, recordingId);
                    }
                } catch (Exception e) {
                    log.error(
                            "Error during Nhost file ID extraction or deletion attempt for recording {}: {}",
                            recordingId, e.getMessage(), e);
                }
            }
        } else {
            log.warn("Recording with ID {} not found. Cannot delete.", recordingId);
        }
    }

    public List<Recording> getRecordingsByUserId(String userId)
            throws ExecutionException, InterruptedException {
        if (!StringUtils.hasText(userId)) {
            log.warn("getRecordingsByUserId called with null or blank userId.");
            return List.of();
        }
        log.debug("Fetching recordings for user ID: {}", userId);
        List<java.util.Map<String, Object>> results =
                firebaseService.queryCollection(RECORDINGS_COLLECTION, "userId", userId);
        log.debug("Found {} recording documents for user {}", results.size(), userId);
        return results.stream().map(Recording::fromMap).collect(Collectors.toList());
    }

    private String extractNhostIdFromUrl(String url) {
        if (url == null)
            return null;
        try {
            String[] parts = url.split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("files".equalsIgnoreCase(parts[i]) && (i + 1) < parts.length) {
                    String potentialId = parts[i + 1];
                    if (potentialId.matches("[a-zA-Z0-9\\-]+")) {
                        log.trace("Extracted Nhost ID '{}' from URL '{}'", potentialId, url);
                        return potentialId;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Nhost ID from URL '{}': {}", url, e.getMessage());
        }
        log.warn("Could not extract Nhost ID using expected pattern from URL: {}", url);
        return null;
    }
}

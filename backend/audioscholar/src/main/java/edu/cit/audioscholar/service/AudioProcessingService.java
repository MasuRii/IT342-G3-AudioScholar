package edu.cit.audioscholar.service;

import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.Summary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
public class AudioProcessingService {

    private static final String UPLOAD_DIR_STRING = "src/main/resources/uploads";
    private static final Path UPLOAD_DIR = Paths.get(UPLOAD_DIR_STRING);

    @Autowired
    private GeminiService geminiService;

    @Autowired(required = false)
    private FirebaseService firebaseService;

    @Autowired(required = false)
    private RecordingService recordingService;

    @Autowired(required = false)
    private SummaryService summaryService;

    // Removed the in-memory map

    public String processAudioFile(byte[] audioData, String originalFileName, String title, String description) throws IOException, ExecutionException, InterruptedException {
        Files.createDirectories(UPLOAD_DIR);

        // Log the title and description
        System.out.println("Processing Audio File: ");
        System.out.println("Title: " + title);  // Log Title
        System.out.println("Description: " + description);  // Log Description

        String uniqueFileName = UUID.randomUUID().toString() + "_" + originalFileName;
        Path filePath = UPLOAD_DIR.resolve(uniqueFileName);

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(audioData);
        }

        title = (title == null) ? "" : title;
        description = (description == null) ? "" : description;

        String audioId = UUID.randomUUID().toString();
        AudioMetadata metadata = new AudioMetadata(
                audioId,
                uniqueFileName,
                audioData.length,
                getAudioDuration(audioData),
                title,
                description
        );
        firebaseService.saveAudioMetadata(metadata); // Save to Firebase

        // Log the metadata to ensure values are correct
        System.out.println("Metadata Saved to Firebase: " + metadata);

        return metadata.getId(); // Return the generated ID
    }

    public List<AudioMetadata> getAllAudioMetadataList() throws ExecutionException, InterruptedException {
        return firebaseService.getAllAudioMetadata(); // Retrieve all from Firebase
    }


    public boolean deleteAudio(String audioId) {
        try {
            AudioMetadata metadata = null;
            // Need to fetch metadata to get filename for local deletion
            for (AudioMetadata am : firebaseService.getAllAudioMetadata()) {
                if (am.getId().equals(audioId)) {
                    metadata = am;
                    break;
                }
            }
            if (metadata != null) {
                Path filePath = UPLOAD_DIR.resolve(metadata.getFileName());
                Files.deleteIfExists(filePath); // Delete from local storage
                firebaseService.deleteData("audio_metadata", audioId); // Delete from Firebase
                return true;
            }
            return false;
        } catch (IOException | ExecutionException | InterruptedException e) {
            System.err.println("Error deleting audio: " + e.getMessage());
            return false;
        }
    }

    private long getAudioDuration(byte[] audioData) {
        return 0;
    }

    public Summary processAndSummarize(byte[] audioData, String fileName) throws Exception {
        String audioId = processAudioFile(audioData, fileName, "", "");

        // No need to fetch from in-memory map anymore
        AudioMetadata metadata = null;
        for (AudioMetadata am : firebaseService.getAllAudioMetadata()) {
            if (am.getId().equals(audioId)) {
                metadata = am;
                break;
            }
        }
        if (metadata == null) {
            throw new IllegalStateException("Metadata not found in Firebase after processing file with ID: " + audioId);
        }

        String base64Audio = Base64.getEncoder().encodeToString(audioData);
        String aiResponse = callGeminiWithAudio(base64Audio, metadata.getFileName());

        Summary summary = createSummaryFromResponse(aiResponse);
        summary.setRecordingId(audioId);

        return summary;
    }

    private String callGeminiWithAudio(String base64Audio, String fileName) {
        String prompt = "Please analyze this audio and provide the following:" +
                "\n1. Full transcript" +
                "\n2. A concise summary (2-3 paragraphs)" +
                "\n3. 5-7 key points" +
                "\n4. Main topics discussed" +
                "\nFormat your response as JSON with these fields: transcript, summary, keyPoints (as array), topics (as array)";

        return geminiService.callGeminiAPIWithAudio(prompt, base64Audio, fileName);
    }

    private Summary createSummaryFromResponse(String aiResponse) throws Exception {
        System.out.println("Received AI Response (needs parsing): " + aiResponse);
        return new Summary();
    }
}
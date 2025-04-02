package edu.cit.audioscholar.service;

import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.Summary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@Service
public class AudioProcessingService {

    private static final String UPLOAD_DIR = "src/main/resources/uploads";

    @Autowired
    private GeminiService geminiService;

    @Autowired(required = false)
    private FirebaseService firebaseService;

    @Autowired(required = false)
    private RecordingService recordingService;

    @Autowired(required = false)
    private SummaryService summaryService;

    // In-memory storage for audio metadata (this can be changed to a database)
    private final Map<String, AudioMetadata> audioMetadataMap = new HashMap<>();

    /**
     * Process audio file - saves it locally and returns the file path
     */
    public String processAudioFile(byte[] audioData, String fileName) throws IOException {
        Files.createDirectories(Paths.get(UPLOAD_DIR));

        // Create a unique filename to prevent collisions
        String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
        File file = new File(UPLOAD_DIR, uniqueFileName);

        // Write file to the target directory
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(audioData);
        }

        // Store metadata
        AudioMetadata metadata = new AudioMetadata(
                UUID.randomUUID().toString(),
                file.getName(),
                audioData.length,
                getAudioDuration(audioData) // Placeholder method to calculate duration
        );
        audioMetadataMap.put(metadata.getId(), metadata);

        return file.getAbsolutePath();
    }

    /**
     * Get all audio metadata
     */
    public Map<String, AudioMetadata> getAllAudioMetadata() {
        return audioMetadataMap;
    }

    /**
     * Delete audio by ID
     */
    public boolean deleteAudio(String audioId) {
        AudioMetadata metadata = audioMetadataMap.remove(audioId);
        if (metadata != null) {
            File file = new File(UPLOAD_DIR, metadata.getFileName());
            return file.delete(); // Delete the file from storage
        }
        return false;
    }

    /**
     * Get audio duration (just a placeholder, replace with actual implementation)
     */
    private long getAudioDuration(byte[] audioData) {
        // Here, you can add logic to calculate actual audio duration.
        return audioData.length / 1000; // Dummy calculation
    }

    public Summary processAndSummarize(byte[] audioData, String fileName) throws Exception {
        // First, save the file locally
        String filePath = processAudioFile(audioData, fileName);

        // Generate a summary using Gemini
        String base64Audio = Base64.getEncoder().encodeToString(audioData);
        String aiResponse = callGeminiWithAudio(base64Audio, fileName);

        // Create and populate the Summary object
        return createSummaryFromResponse(aiResponse);
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
        // Summarization logic...
        return new Summary(); // Placeholder
    }
}

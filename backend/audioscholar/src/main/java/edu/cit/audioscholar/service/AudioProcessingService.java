package edu.cit.audioscholar.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class AudioProcessingService {

    private static final String UPLOAD_DIR = "src/main/resources/uploads"; // Target directory

    @Autowired
    private GeminiService geminiService; // Injecting Gemini API service

    public void processAudioFile(byte[] audioData, String fileName) {
        try {
            // Ensure the uploads directory exists
            Files.createDirectories(Paths.get(UPLOAD_DIR));

            // Create the file path inside resources/uploads/
            File file = new File(UPLOAD_DIR, fileName);

            // Write file to the target directory
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(audioData);
            }

            System.out.println("Audio file saved to: " + file.getAbsolutePath());

            // Read the saved file content (for now, just a placeholder text)
            String fileContent = "This is a test transcript for " + fileName; // Replace this with actual transcript

            // Call Gemini API with the file content
            String aiResponse = geminiService.callGeminiAPI("Summarize this: " + fileContent);
            
            System.out.println("Gemini AI Response: " + aiResponse); // Log AI response

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

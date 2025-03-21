package edu.cit.audioscholar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.audioscholar.model.Recording;
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
    
    // We'll use these services if they're needed, without assuming specific methods
    @Autowired(required = false)
    private FirebaseService firebaseService;
    
    @Autowired(required = false)
    private RecordingService recordingService;
    
    @Autowired(required = false)
    private SummaryService summaryService;

    /**
     * Process audio file - saves it locally and returns the path
     */
    public String processAudioFile(byte[] audioData, String fileName) throws IOException {
        // Ensure the uploads directory exists
        Files.createDirectories(Paths.get(UPLOAD_DIR));

        // Create a unique filename to prevent collisions
        String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
        File file = new File(UPLOAD_DIR, uniqueFileName);

        // Write file to the target directory
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(audioData);
        }
        
        System.out.println("Audio file saved to: " + file.getAbsolutePath());
        return file.getAbsolutePath();
    }
    
    /**
     * Process audio and generate a summary using Gemini
     */
    public Summary processAndSummarize(byte[] audioData, String fileName) throws Exception {
        // First, save the file locally
        String filePath = processAudioFile(audioData, fileName);
        
        // Generate a summary using Gemini
        String base64Audio = Base64.getEncoder().encodeToString(audioData);
        String aiResponse = callGeminiWithAudio(base64Audio, fileName);
        
        // Create and populate the Summary object
        Summary summary = createSummaryFromResponse(aiResponse);
        
        return summary;
    }
    
    /**
     * Call Gemini API with audio data
     */
    private String callGeminiWithAudio(String base64Audio, String fileName) {
        // Create a prompt for Gemini that requests transcript, summary, key points, and topics
        String prompt = "Please analyze this audio and provide the following:" +
                        "\n1. Full transcript" +
                        "\n2. A concise summary (2-3 paragraphs)" +
                        "\n3. 5-7 key points" +
                        "\n4. Main topics discussed" +
                        "\nFormat your response as JSON with these fields: transcript, summary, keyPoints (as array), topics (as array)";
        
        // Call Gemini API with the audio data
        return geminiService.callGeminiAPIWithAudio(prompt, base64Audio, fileName);
    }
    
    /**
     * Create a Summary object from the Gemini API response
     */
    private Summary createSummaryFromResponse(String aiResponse) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode responseNode = mapper.readTree(aiResponse);
        
        if (responseNode.has("error")) {
            throw new Exception("Error from AI service: " + responseNode.get("error").asText());
        }
        
        String summaryId = UUID.randomUUID().toString();
        String recordingId = UUID.randomUUID().toString(); // This would normally come from the Recording
        Summary summary = new Summary(summaryId, recordingId, "");
        
        // Extract the text content
        String textContent = responseNode.has("text") ? responseNode.get("text").asText() : "";
        
        try {
            // Try to parse as JSON
            JsonNode contentJson = mapper.readTree(textContent);
            
            // Extract fields from JSON
            if (contentJson.has("transcript")) {
                summary.setFullText(contentJson.get("transcript").asText());
            }
            
            if (contentJson.has("summary")) {
                summary.setCondensedSummary(contentJson.get("summary").asText());
            }
            
            if (contentJson.has("keyPoints") && contentJson.get("keyPoints").isArray()) {
                List<String> keyPoints = new ArrayList<>();
                contentJson.get("keyPoints").forEach(point -> keyPoints.add(point.asText()));
                summary.setKeyPoints(keyPoints);
            }
            
            if (contentJson.has("topics") && contentJson.get("topics").isArray()) {
                List<String> topics = new ArrayList<>();
                contentJson.get("topics").forEach(topic -> topics.add(topic.asText()));
                summary.setTopics(topics);
            }
        } catch (Exception e) {
            // If parsing as JSON fails, use a fallback strategy
            // This handles cases where Gemini doesn't return properly formatted JSON
            if (summary.getFullText() == null || summary.getFullText().isEmpty()) {
                summary.setFullText(textContent);
            }
            
            if (summary.getCondensedSummary() == null || summary.getCondensedSummary().isEmpty()) {
                // Try to extract a summary from the text
                String[] paragraphs = textContent.split("\n\n");
                if (paragraphs.length > 0) {
                    summary.setCondensedSummary(paragraphs[0]);
                } else {
                    summary.setCondensedSummary("Summary not available");
                }
            }
        }
        
        return summary;
    }
}
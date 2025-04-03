package edu.cit.audioscholar.service;

import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.Summary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private final Map<String, AudioMetadata> audioMetadataMap = new HashMap<>(); 

    public String processAudioFile(byte[] audioData, String originalFileName) throws IOException {
        Files.createDirectories(UPLOAD_DIR);

        String uniqueFileName = UUID.randomUUID().toString() + "_" + originalFileName;
        Path filePath = UPLOAD_DIR.resolve(uniqueFileName);

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(audioData);
        }

        String audioId = UUID.randomUUID().toString(); 
        AudioMetadata metadata = new AudioMetadata(
                audioId,
                uniqueFileName,
                audioData.length,
                getAudioDuration(audioData)
        );
        audioMetadataMap.put(metadata.getId(), metadata);

        return audioId; 
    }

    public Map<String, AudioMetadata> getAllAudioMetadataMap() {
        return Collections.unmodifiableMap(audioMetadataMap);
    }

    public List<AudioMetadata> getAllAudioMetadataList() {
        List<AudioMetadata> fileMetadataList = new ArrayList<>();

        if (!Files.isDirectory(UPLOAD_DIR) || !Files.isReadable(UPLOAD_DIR)) {
            System.err.println("Upload directory does not exist or is not readable: " + UPLOAD_DIR_STRING);
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.list(UPLOAD_DIR)) {
            paths.filter(Files::isRegularFile)
                 .forEach(filePath -> {
                     try {
                         String fileName = filePath.getFileName().toString();
                         long fileSize = Files.size(filePath);
                         long duration = 0; 

                         AudioMetadata metadata = new AudioMetadata(
                                 fileName,
                                 fileName,
                                 fileSize,
                                 duration
                         );
                         fileMetadataList.add(metadata);
                     } catch (IOException e) {
                         System.err.println("Error reading metadata for file: " + filePath + " - " + e.getMessage());
                     }
                 });
        } catch (IOException e) {
            System.err.println("Error listing files in directory: " + UPLOAD_DIR_STRING + " - " + e.getMessage());
            return Collections.emptyList(); 
        }

        return fileMetadataList;
    }


    public boolean deleteAudio(String audioId) {
        AudioMetadata metadata = audioMetadataMap.remove(audioId);
        if (metadata != null) {
            Path filePath = UPLOAD_DIR.resolve(metadata.getFileName()); 
            try {
                return Files.deleteIfExists(filePath);
            } catch (IOException e) {
                System.err.println("Error deleting file: " + filePath + " - " + e.getMessage());
                return false; 
            }
        }
        return false; 
    }

    private long getAudioDuration(byte[] audioData) {
        return 0;
    }

    public Summary processAndSummarize(byte[] audioData, String fileName) throws Exception {
        String audioId = processAudioFile(audioData, fileName); 
        
        AudioMetadata metadata = audioMetadataMap.get(audioId);
        if (metadata == null) {
            throw new IllegalStateException("Metadata not found after processing file with ID: " + audioId);
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
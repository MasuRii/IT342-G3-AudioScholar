package edu.cit.audioscholar.service;

import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.Summary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.google.cloud.Timestamp;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;


@Service
public class AudioProcessingService {

    private static final Logger LOGGER = Logger.getLogger(AudioProcessingService.class.getName());

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private FirebaseService firebaseService;

    @Autowired
    private NhostStorageService nhostStorageService;

    @Autowired(required = false)
    private RecordingService recordingService;

    @Autowired(required = false)
    private SummaryService summaryService;


    public AudioMetadata uploadAndSaveMetadata(MultipartFile file, String title, String description, String userId)
            throws IOException, ExecutionException, InterruptedException {

        LOGGER.log(Level.INFO, "Starting upload process for file: {0}, Title: {1}, User: {2}",
                   new Object[]{file.getOriginalFilename(), title, userId});

        String nhostFileId = nhostStorageService.uploadFile(file);
        LOGGER.log(Level.INFO, "File uploaded to Nhost, ID: {0}", nhostFileId);

        String storageUrl = nhostStorageService.getPublicUrl(nhostFileId);
        LOGGER.log(Level.INFO, "Constructed Nhost public URL: {0}", storageUrl);

        AudioMetadata metadata = new AudioMetadata();
        metadata.setUserId(userId);
        metadata.setFileName(file.getOriginalFilename());
        metadata.setFileSize(file.getSize());
        metadata.setContentType(file.getContentType());
        metadata.setTitle(title != null ? title : "");
        metadata.setDescription(description != null ? description : "");
        metadata.setNhostFileId(nhostFileId);
        metadata.setStorageUrl(storageUrl);
        metadata.setUploadTimestamp(Timestamp.now());

        AudioMetadata savedMetadata = firebaseService.saveAudioMetadata(metadata);
        LOGGER.log(Level.INFO, "AudioMetadata saved to Firestore with ID: {0}", savedMetadata.getId());

        return savedMetadata;
    }


    public List<AudioMetadata> getAllAudioMetadataList() throws ExecutionException, InterruptedException {
        return firebaseService.getAllAudioMetadata();
    }


    public boolean deleteAudioMetadata(String metadataId) {
        try {
            firebaseService.deleteData(firebaseService.getAudioMetadataCollectionName(), metadataId);
            LOGGER.log(Level.INFO, "Deleted AudioMetadata from Firestore with ID: {0}", metadataId);
            return true;
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.SEVERE, "Error deleting audio metadata from Firestore: " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
             LOGGER.log(Level.SEVERE, "Unexpected error deleting audio metadata: " + e.getMessage(), e);
            return false;
        }
    }


    public Summary processAndSummarize(byte[] audioData, MultipartFile fileInfo, String userId) throws Exception {
        LOGGER.log(Level.INFO, "Starting processAndSummarize for file: {0}", fileInfo.getOriginalFilename());

        AudioMetadata metadata = uploadAndSaveMetadata(fileInfo, "", "", userId);

        if (metadata == null || metadata.getId() == null) {
            throw new IllegalStateException("Metadata not saved correctly in Firestore for file: " + fileInfo.getOriginalFilename());
        }
        LOGGER.log(Level.INFO, "Metadata created with ID: {0}", metadata.getId());

        String base64Audio = Base64.getEncoder().encodeToString(audioData);
        String aiResponse = callGeminiWithAudio(base64Audio, metadata.getFileName());

        Summary summary = createSummaryFromResponse(aiResponse);
        summary.setRecordingId(metadata.getId());


        LOGGER.log(Level.INFO, "Successfully processed and summarized audio for metadata ID: {0}", metadata.getId());
        return summary;
    }

    private String callGeminiWithAudio(String base64Audio, String fileName) {
         LOGGER.log(Level.INFO, "Calling Gemini for file: {0}", fileName);
        String prompt = "Please analyze this audio and provide the following:" +
                "\n1. Full transcript" +
                "\n2. A concise summary (2-3 paragraphs)" +
                "\n3. 5-7 key points" +
                "\n4. Main topics discussed" +
                "\nFormat your response as JSON with these fields: transcript, summary, keyPoints (as array), topics (as array)";

        return geminiService.callGeminiAPIWithAudio(prompt, base64Audio, fileName);
    }

    private Summary createSummaryFromResponse(String aiResponse) throws Exception {
        LOGGER.log(Level.INFO, "Parsing Gemini response.");
        System.out.println("Received AI Response (needs parsing): " + aiResponse);
        Summary summary = new Summary();
        return summary;
    }
}
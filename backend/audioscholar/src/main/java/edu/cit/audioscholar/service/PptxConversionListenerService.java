package edu.cit.audioscholar.service;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import edu.cit.audioscholar.config.RabbitMQConfig;
import edu.cit.audioscholar.dto.AudioProcessingMessage;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.ProcessingStatus;

@Service
public class PptxConversionListenerService {

        private static final Logger logger =
                        LoggerFactory.getLogger(PptxConversionListenerService.class);

        private final FirebaseService firebaseService;
        private final NhostStorageService nhostStorageService;
        private final GoogleFilesApiService googleFilesApiService;
        private final PptxToPdfConverter pptxToPdfConverter;
        private final RabbitTemplate rabbitTemplate;
        private final ObjectMapper objectMapper;
        private final Path tempFileDir;

        public PptxConversionListenerService(FirebaseService firebaseService,
                        NhostStorageService nhostStorageService,
                        GoogleFilesApiService googleFilesApiService,
                        PptxToPdfConverter pptxToPdfConverter, RabbitTemplate rabbitTemplate,
                        ObjectMapper objectMapper,
                        @Value("${app.temp-file-dir}") String tempFileDirStr) {
                this.firebaseService = firebaseService;
                this.nhostStorageService = nhostStorageService;
                this.googleFilesApiService = googleFilesApiService;
                this.pptxToPdfConverter = pptxToPdfConverter;
                this.rabbitTemplate = rabbitTemplate;
                this.objectMapper = objectMapper;
                this.tempFileDir = Paths.get(tempFileDirStr);
                try {
                        Files.createDirectories(this.tempFileDir);
                } catch (IOException e) {
                        logger.error("Could not create temporary directory: {}", this.tempFileDir,
                                        e);
                        throw new RuntimeException("Failed to initialize temporary directory", e);
                }
        }

        @RabbitListener(queues = RabbitMQConfig.PPTX_CONVERSION_QUEUE_NAME)
        public void handlePptxConversion(AudioProcessingMessage messageDto) {
                String metadataId = messageDto.getMetadataId();
                logger.info("Processing PPTX conversion for metadata ID: {}", metadataId);

                try {
                        Map<String, Object> metadataMap = firebaseService.getData(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId);

                        if (metadataMap == null) {
                                logger.error("Cannot find metadata for ID: {}. Abandoning PPTX conversion.",
                                                metadataId);
                                return;
                        }

                        AudioMetadata metadata = AudioMetadata.fromMap(metadataMap);
                        ProcessingStatus currentStatus = metadata.getStatus();

                        if (currentStatus == ProcessingStatus.SUMMARY_COMPLETE
                                        || currentStatus == ProcessingStatus.SUMMARIZING
                                        || currentStatus == ProcessingStatus.SUMMARIZATION_QUEUED) {
                                logger.info("Skipping PDF conversion as summarization is already in progress or complete (status: {})",
                                                currentStatus);
                                return;
                        }

                        updateStatus(metadataId, ProcessingStatus.PDF_CONVERTING, null);

                        metadataMap = firebaseService.getData(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId);
                        metadata = AudioMetadata.fromMap(metadataMap);

                        String nhostPptxFileId = metadata.getNhostPptxFileId();
                        if (nhostPptxFileId == null || nhostPptxFileId.isBlank()) {
                                logger.error("No PPTX file ID found in metadata. Cannot proceed with conversion.");
                                updateStatus(metadataId, ProcessingStatus.FAILED,
                                                "No PPTX file ID available");
                                return;
                        }

                        logger.info("Downloading PPTX file: {}", nhostPptxFileId);
                        UUID downloadId = UUID.randomUUID();
                        Path tempPptxPath = tempFileDir.resolve(
                                        "pptx_download_" + metadataId + "_" + downloadId + ".pptx");
                        logger.debug("Temporary download path: {}", tempPptxPath);

                        try {
                                nhostStorageService.downloadFileToPath(nhostPptxFileId,
                                                tempPptxPath);
                                logger.info("PPTX file {} downloaded temporarily to {}",
                                                nhostPptxFileId, tempPptxPath);

                                logger.info("Starting PPTX to PDF conversion...");
                                ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
                                try {
                                        pptxToPdfConverter.convert(
                                                        new FileInputStream(tempPptxPath.toFile()),
                                                        pdfOutputStream);
                                        byte[] pdfBytes = pdfOutputStream.toByteArray();
                                        logger.info("PPTX to PDF conversion successful. PDF size: {} bytes",
                                                        pdfBytes.length);

                                        String originalPptxFileName =
                                                        metadata.getOriginalPptxFileName();
                                        String pdfFileName =
                                                        generatePdfFileName(originalPptxFileName);
                                        logger.info("Preparing to upload generated PDF as: {}",
                                                        pdfFileName);

                                        UUID pdfUploadId = UUID.randomUUID();
                                        Path tempPdfPath = tempFileDir.resolve("pdf_upload_"
                                                        + metadataId + "_" + pdfUploadId + ".pdf");
                                        Files.write(tempPdfPath, pdfBytes);
                                        logger.info("Generated PDF saved temporarily to {}",
                                                        tempPdfPath);

                                        logger.info("Uploading PDF directly to Google Files API as: {}",
                                                        pdfFileName);
                                        String fileUri = googleFilesApiService.uploadFile(
                                                        tempPdfPath, "application/pdf",
                                                        pdfBytes.length, pdfFileName);
                                        logger.info("PDF file uploaded successfully to Google Files API. URI: {}",
                                                        fileUri);

                                        Map<String, Object> updates = new HashMap<>();
                                        updates.put("googleFilesApiPdfUri", fileUri);
                                        updates.put("pdfConversionComplete", true);
                                        updates.put("status",
                                                        ProcessingStatus.PDF_CONVERSION_COMPLETE
                                                                        .name());
                                        updates.put("lastUpdated", Timestamp.now());

                                        firebaseService.updateDataWithMap(firebaseService
                                                        .getAudioMetadataCollectionName(),
                                                        metadataId, updates);
                                        logger.info("AudioMetadata updated with PDF details and status PDF_CONVERSION_COMPLETE for ID: {}",
                                                        metadataId);

                                        metadataMap = firebaseService.getData(firebaseService
                                                        .getAudioMetadataCollectionName(),
                                                        metadataId);
                                        metadata = AudioMetadata.fromMap(metadataMap);

                                        logger.info("PDF conversion complete for ID: {}. Waiting for transcription (Current status: {}, Transcription complete flag: {}).",
                                                        metadataId, metadata.getStatus(),
                                                        metadata.isTranscriptionComplete());

                                        boolean transcriptionDone =
                                                        metadata.isTranscriptionComplete();
                                        boolean pdfDone = metadata.isPdfConversionComplete();
                                        boolean isAudioOnly = metadata.isAudioOnly();

                                        logger.debug("Completion status check for {}: TranscriptionDone={}, PdfConversionDone={}, AudioOnly={}",
                                                        metadataId, transcriptionDone, pdfDone,
                                                        isAudioOnly);

                                        boolean readyForSummarization = transcriptionDone
                                                        && (pdfDone || isAudioOnly);

                                        if (readyForSummarization && metadata
                                                        .getStatus() != ProcessingStatus.SUMMARIZATION_QUEUED
                                                        && metadata.getStatus() != ProcessingStatus.SUMMARIZING
                                                        && metadata.getStatus() != ProcessingStatus.SUMMARY_COMPLETE) {

                                                updates = new HashMap<>();
                                                updates.put("status",
                                                                ProcessingStatus.SUMMARIZATION_QUEUED
                                                                                .name());
                                                updates.put("lastUpdated", Timestamp.now());
                                                firebaseService.updateDataWithMap(firebaseService
                                                                .getAudioMetadataCollectionName(),
                                                                metadataId, updates);
                                                logger.info("Updated status to SUMMARIZATION_QUEUED for metadata ID: {}",
                                                                metadataId);

                                                Map<String, String> message = new HashMap<>();
                                                message.put("metadataId", metadataId);
                                                message.put("messageId",
                                                                UUID.randomUUID().toString());

                                                rabbitTemplate.convertAndSend(
                                                                RabbitMQConfig.PROCESSING_EXCHANGE_NAME,
                                                                RabbitMQConfig.SUMMARIZATION_ROUTING_KEY,
                                                                message);
                                                logger.info("Sent message to summarization queue for metadata ID: {}",
                                                                metadataId);
                                        } else {
                                                logger.info("Conditions not yet met for summarization or already in progress (Transcription: {}, PDF: {}, AudioOnly: {}, Status: {}). Waiting for other processes.",
                                                                transcriptionDone, pdfDone,
                                                                isAudioOnly, metadata.getStatus());

                                                if (pdfDone && !transcriptionDone && !isAudioOnly
                                                                && metadata.getStatus() != ProcessingStatus.TRANSCRIBING) {
                                                        logger.info("PDF conversion is complete but transcription is not yet started or may be stalled. Attempting to trigger/retry transcription process for ID: {}",
                                                                        metadataId);
                                                        AudioProcessingMessage transcriptionMessage =
                                                                        new AudioProcessingMessage();
                                                        transcriptionMessage
                                                                        .setMetadataId(metadataId);
                                                        transcriptionMessage.setUserId(
                                                                        metadata.getUserId());

                                                        rabbitTemplate.convertAndSend(
                                                                        RabbitMQConfig.PROCESSING_EXCHANGE_NAME,
                                                                        RabbitMQConfig.TRANSCRIPTION_ROUTING_KEY,
                                                                        transcriptionMessage);
                                                        logger.info("Sent retry message to transcription queue for metadata ID: {}",
                                                                        metadataId);
                                                }
                                        }

                                        Files.deleteIfExists(tempPptxPath);
                                        logger.debug("Cleaned up temporary downloaded PPTX file: {}",
                                                        tempPptxPath);
                                        Files.deleteIfExists(tempPdfPath);
                                        logger.debug("Cleaned up temporary generated PDF file: {}",
                                                        tempPdfPath);

                                } catch (Exception e) {
                                        logger.error("Error during PPTX to PDF conversion: {}",
                                                        e.getMessage(), e);
                                        throw e;
                                }
                        } catch (Exception e) {
                                logger.error("Error during PPTX to PDF conversion: {}",
                                                e.getMessage(), e);
                                updateStatus(metadataId, ProcessingStatus.FAILED,
                                                "Error converting PPTX to PDF: " + e.getMessage());
                                try {
                                        Files.deleteIfExists(tempPptxPath);
                                } catch (IOException cleanupError) {
                                        logger.warn("Failed to delete temporary PPTX file: {}",
                                                        cleanupError.getMessage());
                                }
                        }
                } catch (Exception e) {
                        logger.error("Unexpected error in PPTX conversion process: {}",
                                        e.getMessage(), e);
                        updateStatus(metadataId, ProcessingStatus.FAILED,
                                        "Unexpected error in PPTX conversion process: "
                                                        + e.getMessage());
                }
        }

        private void updateStatus(String metadataId, ProcessingStatus status,
                        String failureReason) {
                try {
                        Map<String, Object> statusUpdate = new HashMap<>();
                        statusUpdate.put("status", status.name());
                        statusUpdate.put("lastUpdated", Timestamp.now());
                        if (failureReason != null) {
                                statusUpdate.put("failureReason", failureReason);
                        } else {
                                if (status != ProcessingStatus.FAILED) {
                                        statusUpdate.put("failureReason", null);
                                }
                        }
                        firebaseService.updateData(firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, statusUpdate);
                        logger.info("Updated status for metadata {} to {}", metadataId,
                                        status.name());
                } catch (Exception e) {
                        logger.error("Failed to update status to {} for metadata ID {}: {}",
                                        status.name(), metadataId, e.getMessage(), e);
                }
        }


        private String generatePdfFileName(String originalPptxFileName) {
                if (originalPptxFileName == null || originalPptxFileName.isEmpty()) {
                        return "converted_presentation.pdf";
                }
                String baseName = originalPptxFileName;
                int lastDotIndex = originalPptxFileName.lastIndexOf('.');
                if (lastDotIndex > 0 && originalPptxFileName.substring(lastDotIndex)
                                .equalsIgnoreCase(".pptx")) {
                        baseName = originalPptxFileName.substring(0, lastDotIndex);
                }
                return baseName + ".pdf";
        }
}

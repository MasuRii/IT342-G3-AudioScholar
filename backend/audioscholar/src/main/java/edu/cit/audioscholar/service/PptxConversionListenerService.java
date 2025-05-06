package edu.cit.audioscholar.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
        private final ConvertApiService convertApiService;
        private final RabbitTemplate rabbitTemplate;
        private final ObjectMapper objectMapper;

        public PptxConversionListenerService(FirebaseService firebaseService,
                        NhostStorageService nhostStorageService,
                        ConvertApiService convertApiService, RabbitTemplate rabbitTemplate,
                        ObjectMapper objectMapper) {
                this.firebaseService = firebaseService;
                this.nhostStorageService = nhostStorageService;
                this.convertApiService = convertApiService;
                this.rabbitTemplate = rabbitTemplate;
                this.objectMapper = objectMapper;
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

                        updateStatus(metadataId, ProcessingStatus.PDF_CONVERTING_API, null);

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

                        String pptxUrl = nhostStorageService.getPublicUrl(nhostPptxFileId);
                        logger.info("Retrieved public URL for PPTX: {}", pptxUrl);

                        Map<String, Object> pptxUrlUpdate = new HashMap<>();
                        pptxUrlUpdate.put("pptxNhostUrl", pptxUrl);
                        pptxUrlUpdate.put("lastUpdated", Timestamp.now());
                        firebaseService.updateDataWithMap(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, pptxUrlUpdate);
                        logger.info("Updated AudioMetadata with PPTX URL for ID: {}", metadataId);

                        logger.info("Starting PPTX to PDF conversion using ConvertAPI for file: {}",
                                        pptxUrl);
                        String pdfUrl = convertApiService.convertPptxUrlToPdfUrl(pptxUrl);
                        logger.info("PPTX to PDF conversion successful. PDF URL: {}", pdfUrl);

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("convertApiPdfUrl", pdfUrl);
                        updates.put("generatedPdfUrl", pdfUrl);
                        updates.put("pdfConversionComplete", true);
                        updates.put("status", ProcessingStatus.PDF_CONVERSION_COMPLETE.name());
                        updates.put("lastUpdated", Timestamp.now());

                        firebaseService.updateDataWithMap(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, updates);
                        logger.info("AudioMetadata updated with PDF details and status PDF_CONVERSION_COMPLETE for ID: {}",
                                        metadataId);

                        metadataMap = firebaseService.getData(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId);
                        metadata = AudioMetadata.fromMap(metadataMap);

                        logger.info("PDF conversion complete for ID: {}. Waiting for transcription (Current status: {}, Transcription complete flag: {}).",
                                        metadataId, metadata.getStatus(),
                                        metadata.isTranscriptionComplete());

                        boolean transcriptionDone = metadata.isTranscriptionComplete();
                        boolean pdfDone = metadata.isPdfConversionComplete();
                        boolean isAudioOnly = metadata.isAudioOnly();

                        logger.debug("Completion status check for {}: TranscriptionDone={}, PdfConversionDone={}, AudioOnly={}",
                                        metadataId, transcriptionDone, pdfDone, isAudioOnly);

                        boolean readyForSummarization =
                                        transcriptionDone && (pdfDone || isAudioOnly);

                        if (readyForSummarization && metadata
                                        .getStatus() != ProcessingStatus.SUMMARIZATION_QUEUED
                                        && metadata.getStatus() != ProcessingStatus.SUMMARIZING
                                        && metadata.getStatus() != ProcessingStatus.SUMMARY_COMPLETE) {

                                updates = new HashMap<>();
                                updates.put("status", ProcessingStatus.SUMMARIZATION_QUEUED.name());
                                updates.put("lastUpdated", Timestamp.now());
                                firebaseService.updateDataWithMap(
                                                firebaseService.getAudioMetadataCollectionName(),
                                                metadataId, updates);
                                logger.info("Updated status to SUMMARIZATION_QUEUED for metadata ID: {}",
                                                metadataId);

                                Map<String, String> message = new HashMap<>();
                                message.put("metadataId", metadataId);
                                message.put("messageId", UUID.randomUUID().toString());

                                rabbitTemplate.convertAndSend(
                                                RabbitMQConfig.PROCESSING_EXCHANGE_NAME,
                                                RabbitMQConfig.SUMMARIZATION_ROUTING_KEY, message);
                                logger.info("Sent message to summarization queue for metadata ID: {}",
                                                metadataId);
                        } else {
                                logger.info("Conditions not yet met for summarization or already in progress (Transcription: {}, PDF: {}, AudioOnly: {}, Status: {}). Waiting for other processes.",
                                                transcriptionDone, pdfDone, isAudioOnly,
                                                metadata.getStatus());

                                if (pdfDone && !transcriptionDone && !isAudioOnly && metadata
                                                .getStatus() != ProcessingStatus.TRANSCRIBING) {
                                        logger.info("PDF conversion is complete but transcription is not yet started or may be stalled. Attempting to trigger/retry transcription process for ID: {}",
                                                        metadataId);
                                        AudioProcessingMessage transcriptionMessage =
                                                        new AudioProcessingMessage();
                                        transcriptionMessage.setMetadataId(metadataId);
                                        transcriptionMessage.setUserId(metadata.getUserId());

                                        rabbitTemplate.convertAndSend(
                                                        RabbitMQConfig.PROCESSING_EXCHANGE_NAME,
                                                        RabbitMQConfig.TRANSCRIPTION_ROUTING_KEY,
                                                        transcriptionMessage);
                                        logger.info("Sent retry message to transcription queue for metadata ID: {}",
                                                        metadataId);
                                }
                        }
                } catch (Exception e) {
                        logger.error("Error during PPTX to PDF conversion: {}", e.getMessage(), e);
                        updateStatus(metadataId, ProcessingStatus.FAILED,
                                        "Error converting PPTX to PDF: " + e.getMessage());
                }
        }

        private void updateStatus(String metadataId, ProcessingStatus status,
                        String failureReason) {
                try {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("status", status.name());
                        updates.put("lastUpdated", Timestamp.now());

                        if (failureReason != null) {
                                updates.put("failureReason", failureReason);
                        } else if (status != ProcessingStatus.FAILED) {
                                updates.put("failureReason", null);
                        }

                        firebaseService.updateDataWithMap(
                                        firebaseService.getAudioMetadataCollectionName(),
                                        metadataId, updates);
                        logger.info("Updated status to {} for metadata ID: {}", status, metadataId);
                } catch (Exception e) {
                        logger.error("Failed to update status for metadata ID: {}", metadataId, e);
                }
        }

        private String generatePdfFileName(String originalPptxFileName) {
                if (originalPptxFileName == null || originalPptxFileName.isBlank()) {
                        return "converted_presentation.pdf";
                }

                String baseName = originalPptxFileName;
                if (baseName.toLowerCase().endsWith(".pptx")
                                || baseName.toLowerCase().endsWith(".ppt")) {
                        baseName = baseName.substring(0, baseName.lastIndexOf('.'));
                }

                return baseName + ".pdf";
        }
}

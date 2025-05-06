package edu.cit.audioscholar.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.convertapi.client.ConversionResult;
import com.convertapi.client.ConvertApi;
import com.convertapi.client.Param;

@Service
public class ConvertApiService {

    private static final Logger logger = LoggerFactory.getLogger(ConvertApiService.class);
    private static final int TIMEOUT_SECONDS = 120;

    public String convertPptxUrlToPdfUrl(String pptxUrl) throws Exception {
        if (pptxUrl == null || pptxUrl.isBlank()) {
            throw new IllegalArgumentException("PPTX URL cannot be null or empty");
        }

        logger.info("Starting PPTX to PDF conversion for file: {}", pptxUrl);

        try {
            Param fileParam = new Param("File", pptxUrl);
            Param storeFileParam = new Param("StoreFile", "true");

            String format = pptxUrl.toLowerCase().endsWith(".pptx") ? "pptx" : "ppt";

            CompletableFuture<ConversionResult> future =
                    ConvertApi.convert(format, "pdf", fileParam, storeFileParam);

            ConversionResult result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            logger.info("ConvertAPI conversion completed successfully. Conversion cost: {}",
                    result.conversionCost());

            String pdfUrl = result.getFile(0).getUrl();
            logger.info("PDF conversion successful. PDF URL: {}", pdfUrl);

            return pdfUrl;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("PPTX to PDF conversion was interrupted", e);
            throw new Exception("PPTX to PDF conversion was interrupted", e);
        } catch (ExecutionException e) {
            logger.error("Error during PPTX to PDF conversion", e.getCause());
            throw new Exception("Error during PPTX to PDF conversion: " + e.getCause().getMessage(),
                    e.getCause());
        } catch (TimeoutException e) {
            logger.error("PPTX to PDF conversion timed out after {} seconds", TIMEOUT_SECONDS, e);
            throw new Exception(
                    "PPTX to PDF conversion timed out after " + TIMEOUT_SECONDS + " seconds", e);
        } catch (Exception e) {
            logger.error("Unexpected error during PPTX to PDF conversion", e);
            throw new Exception("Unexpected error during PPTX to PDF conversion: " + e.getMessage(),
                    e);
        }
    }
}

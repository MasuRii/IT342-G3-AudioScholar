package edu.cit.audioscholar.service;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PptxToPdfConverter {

    private static final Logger logger = LoggerFactory.getLogger(PptxToPdfConverter.class);

    public void convert(InputStream pptxInputStream, OutputStream pdfOutputStream)
            throws Exception {
        if (pptxInputStream == null || pdfOutputStream == null) {
            throw new IllegalArgumentException("Input and output streams cannot be null");
        }

        long startTime = System.currentTimeMillis();
        logger.info("Starting PPTX to PDF conversion...");

        try (XMLSlideShow ppt = new XMLSlideShow(pptxInputStream);
                PDDocument pdf = new PDDocument()) {

            Dimension pageSize = ppt.getPageSize();
            float pdfWidth = (float) pageSize.getWidth();
            float pdfHeight = (float) pageSize.getHeight();
            logger.debug("PPTX page size (points): width={}, height={}", pdfWidth, pdfHeight);

            int slideCount = 0;
            for (XSLFSlide slide : ppt.getSlides()) {
                slideCount++;
                logger.debug("Processing slide {}...", slideCount);

                PDRectangle pdfPageSize = new PDRectangle(pdfWidth, pdfHeight);
                PDPage pdfPage = new PDPage(pdfPageSize);
                pdf.addPage(pdfPage);

                BufferedImage slideImage = new BufferedImage((int) pdfWidth, (int) pdfHeight,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = slideImage.createGraphics();

                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, (int) pdfWidth, (int) pdfHeight);

                slide.draw(graphics);

                graphics.dispose();

                PDImageXObject pdImage = LosslessFactory.createFromImage(pdf, slideImage);

                try (PDPageContentStream contentStream = new PDPageContentStream(pdf, pdfPage)) {
                    contentStream.drawImage(pdImage, 0, 0, pdfWidth, pdfHeight);
                }
                logger.debug("Finished processing slide {}.", slideCount);
            }

            logger.info("Saving converted PDF with {} pages...", slideCount);
            pdf.save(pdfOutputStream);
            logger.info("PDF saving complete.");

        } catch (IOException e) {
            logger.error("IOException during PPTX to PDF conversion: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during PPTX to PDF conversion: {}", e.getMessage(), e);
            throw new Exception("PPTX conversion failed: " + e.getMessage(), e);
        } finally {
            long endTime = System.currentTimeMillis();
            logger.info("PPTX to PDF conversion finished in {} ms.", (endTime - startTime));
        }
    }
}

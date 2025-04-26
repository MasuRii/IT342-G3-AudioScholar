package edu.cit.audioscholar.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    private static final String GAC_ENV_VAR = "GOOGLE_APPLICATION_CREDENTIALS";

    @Bean
    FirebaseApp firebaseApp() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            InputStream serviceAccountStream = getCredentialsStream();

            if (serviceAccountStream == null) {
                throw new IOException("Could not find Firebase service account credentials via "
                        + GAC_ENV_VAR
                        + " environment variable or classpath:firebase-service-account.json");
            }

            FirebaseOptions options;
            try (InputStream stream = serviceAccountStream) {
                options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(stream))
                        .setDatabaseUrl("https://audioscholar-39b22-default-rtdb.firebaseio.com")
                        .build();
                logger.info("Successfully configured FirebaseApp.");
            } catch (IOException e) {
                logger.error("Error processing Firebase credentials stream.", e);
                throw e;
            }

            return FirebaseApp.initializeApp(options);
        } else {
            logger.info("FirebaseApp already initialized. Returning existing instance.");
            return FirebaseApp.getInstance();
        }
    }

    private InputStream getCredentialsStream() throws IOException {
        String credentialsPath = System.getenv(GAC_ENV_VAR);
        String source;

        if (credentialsPath != null && !credentialsPath.isEmpty()) {
            source = "environment variable " + GAC_ENV_VAR + " (" + credentialsPath + ")";
            try {
                logger.info("Attempting to load Firebase credentials from {}", source);
                return new FileInputStream(credentialsPath);
            } catch (IOException e) {
                logger.warn("Failed to load credentials from {}: {}", source, e.getMessage());
            }
        }

        String classpathResource = "firebase-service-account.json";
        source = "classpath:" + classpathResource;
        try {
            logger.info("Attempting to load Firebase credentials from {}", source);
            InputStream stream = new ClassPathResource(classpathResource).getInputStream();
            if (stream != null) {
                logger.info("Successfully found credentials in {}", source);
                return stream;
            } else {
                logger.warn("Credentials not found in {}", source);
            }
        } catch (IOException e) {
            logger.warn("Failed to load credentials from {}: {}", source, e.getMessage());
        }

        logger.error(
                "Could not locate Firebase credentials via environment variable or classpath.");
        return null;
    }
}

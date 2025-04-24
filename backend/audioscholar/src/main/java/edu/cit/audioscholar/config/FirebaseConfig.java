package edu.cit.audioscholar.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

@Configuration
public class FirebaseConfig {

    @Value("${FIREBASE_CREDENTIALS_PATH:#{null}}")
    private String firebaseCredentialsPath;

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    FirebaseApp firebaseApp() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            InputStream serviceAccountStream = null;
            String credentialsSource;

            if (firebaseCredentialsPath != null && !firebaseCredentialsPath.isEmpty()) {
                try {
                    logger.info("Loading Firebase credentials from path: {}",
                            firebaseCredentialsPath);
                    serviceAccountStream = new FileInputStream(firebaseCredentialsPath);
                    credentialsSource = "file path environment variable";
                } catch (IOException e) {
                    logger.error("Failed to load Firebase credentials from path: {}",
                            firebaseCredentialsPath, e);
                    throw e;
                }
            } else {
                String classpathResource = "firebase-service-account.json";
                logger.info(
                        "Firebase credentials path environment variable not set. Loading from classpath: {}",
                        classpathResource);
                try {
                    serviceAccountStream =
                            new ClassPathResource(classpathResource).getInputStream();
                    credentialsSource = "classpath";
                } catch (IOException e) {
                    logger.error("Failed to load Firebase credentials from classpath: {}",
                            classpathResource, e);
                    throw new IOException(
                            "Failed to load Firebase credentials from classpath. Ensure '"
                                    + classpathResource + "' is in src/main/resources.",
                            e);
                }
            }

            if (serviceAccountStream == null) {
                throw new IOException("Could not initialize Firebase service account stream.");
            }

            FirebaseOptions options;
            try (InputStream stream = serviceAccountStream) {
                options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(stream))
                        .setDatabaseUrl("https://audioscholar-39b22-default-rtdb.firebaseio.com")
                        .build();
                logger.info("Successfully loaded Firebase credentials from {}", credentialsSource);
            } catch (IOException e) {
                logger.error("Error processing Firebase credentials stream from {}",
                        credentialsSource, e);
                throw e;
            }


            return FirebaseApp.initializeApp(options);
        } else {
            logger.info("FirebaseApp already initialized. Returning existing instance.");
            return FirebaseApp.getInstance();
        }
    }
}

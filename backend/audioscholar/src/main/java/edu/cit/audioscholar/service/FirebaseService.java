package edu.cit.audioscholar.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.UserRecord.UpdateRequest;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.AudioMetadata;
import edu.cit.audioscholar.model.LearningRecommendation;
import edu.cit.audioscholar.model.ProcessingStatus;

@Service
public class FirebaseService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseService.class);

    private Firestore firestore;
    private FirebaseAuth firebaseAuth;
    private FirebaseMessaging firebaseMessaging;

    private final String audioMetadataCollectionName;
    private final String recommendationsCollectionName;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final FirebaseApp firebaseApp;
    private final UserService userService;

    @Value("${google.oauth.web.client.id}")
    private String webClientIdFromTokenAud;
    @Value("${google.oauth.android.client.id}")
    private String androidClientId;

    @Value("classpath:firebase-service-account.json")
    private Resource serviceAccountResource;

    private final CacheManager cacheManager;
    private static final String CACHE_METADATA_BY_USER = "audioMetadataByUser";
    private static final String CACHE_METADATA_BY_ID = "audioMetadataById";

    public FirebaseService(
            @Value("${firebase.firestore.collection.audiometadata}") String audioMetadataCollectionName,
            @Value("${firebase.firestore.collection.recommendations}") String recommendationsCollectionName,
            FirebaseApp firebaseApp, @Lazy UserService userService, CacheManager cacheManager) {
        this.audioMetadataCollectionName = audioMetadataCollectionName;
        this.recommendationsCollectionName = recommendationsCollectionName;
        this.firebaseApp = firebaseApp;
        this.userService = userService;
        this.cacheManager = cacheManager;
    }

    @PostConstruct
    private void initializeFirebase() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options =
                        FirebaseOptions.builder()
                                .setCredentials(com.google.auth.oauth2.GoogleCredentials
                                        .fromStream(serviceAccountResource.getInputStream()))
                                .build();
                FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK initialized successfully.");
            } else {
                log.info("Firebase Admin SDK already initialized.");
            }
            this.firestore = FirestoreClient.getFirestore(this.firebaseApp);
            this.firebaseAuth = FirebaseAuth.getInstance(this.firebaseApp);
            this.firebaseMessaging = FirebaseMessaging.getInstance(this.firebaseApp);

        } catch (IOException e) {
            log.error("Failed to initialize Firebase Admin SDK", e);
            throw new IllegalStateException("Failed to initialize Firebase Admin SDK", e);
        }
    }

    private FirebaseAuth getFirebaseAuth() {
        return this.firebaseAuth;
    }

    public String getAudioMetadataCollectionName() {
        return audioMetadataCollectionName;
    }

    private Firestore getFirestore() {
        return this.firestore;
    }

    public FirebaseToken verifyFirebaseIdToken(String idToken) throws FirebaseAuthException {
        if (idToken == null || idToken.isBlank()) {
            log.warn("Attempted to verify a null or blank Firebase ID token.");
            throw new IllegalArgumentException("ID token cannot be null or blank.");
        }
        log.debug("Attempting to verify Firebase ID token.");
        try {
            boolean checkRevoked = true;
            FirebaseToken decodedToken = getFirebaseAuth().verifyIdToken(idToken, checkRevoked);
            log.info("Successfully verified Firebase ID token for UID: {}", decodedToken.getUid());
            return decodedToken;
        } catch (FirebaseAuthException e) {
            log.error("Firebase ID token verification failed: {}", e.getMessage());
            throw e;
        }
    }

    public FirebaseApp getFirebaseApp() {
        return firebaseApp;
    }

    public AudioMetadata getAudioMetadataByRecordingId(String recordingId)
            throws FirestoreInteractionException {
        if (!StringUtils.hasText(recordingId)) {
            log.warn("Attempted to get AudioMetadata with blank recordingId.");
            return null;
        }
        log.debug("Querying {} collection for recordingId: {}", audioMetadataCollectionName,
                recordingId);
        try {
            Firestore firestore = getFirestore();
            CollectionReference colRef = firestore.collection(audioMetadataCollectionName);
            Query query = colRef.whereEqualTo("recordingId", recordingId).limit(1);

            ApiFuture<QuerySnapshot> future = query.get();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();

            if (documents.isEmpty()) {
                log.warn("No AudioMetadata document found with recordingId: {}", recordingId);
                return null;
            } else {
                if (documents.size() > 1) {
                    log.warn(
                            "Multiple AudioMetadata documents found for recordingId: {}. Returning the first one.",
                            recordingId);
                }
                DocumentSnapshot document = documents.get(0);
                AudioMetadata metadata = fromDocumentSnapshot(document);
                if (metadata != null) {
                    log.info("Retrieved AudioMetadata document {} with recordingId: {}",
                            metadata.getId(), recordingId);
                } else {
                    log.error("Mapping failed for document {} found via recordingId: {}",
                            document.getId(), recordingId);
                }
                return metadata;
            }
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error querying AudioMetadata by recordingId: {}", recordingId, e);
            throw new FirestoreInteractionException(
                    "Failed to retrieve AudioMetadata by recordingId " + recordingId, e);
        } catch (Exception e) {
            log.error("Unexpected error querying AudioMetadata by recordingId: {}", recordingId, e);
            throw new FirestoreInteractionException(
                    "Unexpected error retrieving AudioMetadata by recordingId " + recordingId, e);
        }
    }

    public GoogleIdToken verifyGoogleIdToken(String googleIdTokenString)
            throws GeneralSecurityException, IOException, IllegalArgumentException {
        if (googleIdTokenString == null || googleIdTokenString.isBlank()) {
            log.warn("Attempted to verify a null or blank Google ID token.");
            throw new IllegalArgumentException("Google ID token cannot be null or blank.");
        }
        List<String> audiences = new ArrayList<>();
        if (StringUtils.hasText(webClientIdFromTokenAud)) {
            audiences.add(webClientIdFromTokenAud);
        }
        if (StringUtils.hasText(androidClientId)) {
            audiences.add(androidClientId);
        }
        if (audiences.isEmpty()) {
            log.error(
                    "No Google OAuth Client IDs configured for audience verification. Check application.properties");
            throw new IllegalStateException(
                    "Missing Google OAuth Client ID configuration for token verification.");
        }
        log.debug(
                "Attempting to verify Google ID token using GoogleIdTokenVerifier. Expected audience(s): {}",
                audiences);
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(),
                GsonFactory.getDefaultInstance()).setAudience(audiences).build();
        GoogleIdToken idToken = verifier.verify(googleIdTokenString);
        if (idToken != null) {
            GoogleIdToken.Payload payload = idToken.getPayload();
            String userId = payload.getSubject();
            log.info("Successfully verified Google ID token for Google User ID (sub): {}", userId);
            return idToken;
        } else {
            log.warn(
                    "Google ID token verification failed (verifier.verify() returned null). Token might be invalid, expired, or signature check failed.");
            return null;
        }
    }

    public UserRecord createFirebaseUser(String email, String password, String displayName)
            throws FirebaseAuthException {
        UserRecord.CreateRequest request =
                new UserRecord.CreateRequest().setEmail(email).setPassword(password)
                        .setDisplayName(displayName).setEmailVerified(false).setDisabled(false);
        log.info("Attempting to create Firebase Auth user for email: {}", email);
        try {
            UserRecord userRecord = getFirebaseAuth().createUser(request);
            log.info("Successfully created Firebase Auth user: {} (UID: {})", userRecord.getEmail(),
                    userRecord.getUid());
            return userRecord;
        } catch (FirebaseAuthException e) {
            log.error("Failed to create Firebase Auth user for email {}: {}", email,
                    e.getMessage());
            throw e;
        }
    }

    public void updateUserPassword(String uid, String newPassword) throws FirebaseAuthException {
        if (!StringUtils.hasText(uid)) {
            log.error("Cannot update password for blank UID.");
            throw new IllegalArgumentException("User ID (UID) cannot be blank.");
        }
        if (!StringUtils.hasText(newPassword)) {
            log.error("Cannot update password to a blank value for UID: {}", uid);
            throw new IllegalArgumentException("New password cannot be blank.");
        }
        log.info("Attempting to update password for Firebase user UID: {}", uid);
        try {
            UpdateRequest request = new UpdateRequest(uid).setPassword(newPassword);
            getFirebaseAuth().updateUser(request);
            log.info("Successfully updated password for Firebase user UID: {}", uid);
        } catch (FirebaseAuthException e) {
            log.error("Failed to update password for Firebase user UID {}: {}", uid,
                    e.getMessage());
            throw e;
        }
    }

    public String saveData(String collection, String document, Object dataPojo) {
        if (dataPojo == null) {
            log.error("Attempted to save null data object to {}/{}", collection, document);
            throw new IllegalArgumentException("Data object to save cannot be null.");
        }
        try {
            Firestore firestore = getFirestore();
            ApiFuture<WriteResult> future =
                    firestore.collection(collection).document(document).set(dataPojo);
            String updateTime = future.get().getUpdateTime().toString();
            log.info("Data of type {} saved to {}/{} at {}", dataPojo.getClass().getSimpleName(),
                    collection, document, updateTime);
            return updateTime;
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error saving data object of type {} to {}/{}",
                    dataPojo.getClass().getSimpleName(), collection, document, e);
            throw new FirestoreInteractionException("Error saving data to Firestore", e);
        } catch (Exception e) {
            log.error("Unexpected error saving data object of type {} to {}/{}",
                    dataPojo.getClass().getSimpleName(), collection, document, e);
            throw new FirestoreInteractionException("Unexpected error saving data to Firestore", e);
        }
    }

    public Map<String, Object> getData(String collection, String document) {
        try {
            DocumentReference docRef = getFirestore().collection(collection).document(document);
            DocumentSnapshot snapshot = docRef.get().get();

            if (snapshot != null && snapshot.exists()) {
                Map<String, Object> data = snapshot.getData();
                log.debug("Data retrieved from {}/{}", collection, document);
                return data;
            } else {
                log.debug("No document found at {}/{}", collection, document);
                return null;
            }
        } catch (Exception e) {
            log.error("Error getting data from Firestore at {}/{}: {}", collection, document,
                    e.getMessage());
            throw new FirestoreInteractionException(
                    "Failed to get document " + collection + "/" + document, e);
        }
    }

    public String updateData(String collection, String document, Object dataPojo) {
        if (dataPojo == null) {
            log.error("Attempted to update with null data object for {}/{}", collection, document);
            throw new IllegalArgumentException("Data object for update cannot be null.");
        }
        try {
            Firestore firestore = getFirestore();
            ApiFuture<WriteResult> future = firestore.collection(collection).document(document)
                    .set(dataPojo, SetOptions.merge());
            String updateTime = future.get().getUpdateTime().toString();
            log.info("Data of type {} updated (merged) for {}/{} at {}",
                    dataPojo.getClass().getSimpleName(), collection, document, updateTime);
            return updateTime;
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error updating (merging) data object of type {} for {}/{}",
                    dataPojo.getClass().getSimpleName(), collection, document, e);
            throw new FirestoreInteractionException("Error updating data in Firestore", e);
        } catch (Exception e) {
            log.error("Unexpected error updating (merging) data object of type {} for {}/{}",
                    dataPojo.getClass().getSimpleName(), collection, document, e);
            throw new FirestoreInteractionException("Unexpected error updating data in Firestore",
                    e);
        }
    }

    public String updateDataWithMap(String collection, String document, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            log.warn("Attempted to update data with null or empty map for {}/{}", collection,
                    document);
            return "No update performed (empty map)";
        }
        try {
            Firestore firestore = getFirestore();
            ApiFuture<WriteResult> future =
                    firestore.collection(collection).document(document).update(data);
            String updateTime = future.get().getUpdateTime().toString();
            log.info("Data updated via Map for {}/{} at {}", collection, document, updateTime);
            return updateTime;
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error updating data via Map for {}/{}", collection, document, e);
            throw new FirestoreInteractionException("Error updating data in Firestore", e);
        }
    }

    public String deleteData(String collection, String document) {
        try {
            Firestore firestore = getFirestore();
            ApiFuture<WriteResult> future =
                    firestore.collection(collection).document(document).delete();
            String updateTime = future.get().getUpdateTime().toString();
            log.info("Data deleted from {}/{} at {}", collection, document, updateTime);
            return updateTime;
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error deleting data from {}/{}", collection, document, e);
            throw new FirestoreInteractionException("Error deleting data from Firestore", e);
        }
    }

    public List<Map<String, Object>> queryCollection(String collection, String field,
            Object value) {
        try {
            Firestore firestore = getFirestore();
            ApiFuture<QuerySnapshot> future =
                    firestore.collection(collection).whereEqualTo(field, value).get();
            List<Map<String, Object>> results = new ArrayList<>();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            for (QueryDocumentSnapshot document : documents) {
                results.add(document.getData());
            }
            log.info("Query on collection '{}' where '{}' == '{}' returned {} results.", collection,
                    field, value, results.size());
            return results;
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error querying collection '{}' where '{}' == '{}'", collection, field, value,
                    e);
            throw new FirestoreInteractionException("Error querying collection in Firestore", e);
        }
    }

    public void updateAudioMetadataStatus(String metadataId, ProcessingStatus status)
            throws FirestoreInteractionException {
        if (!StringUtils.hasText(metadataId) || status == null) {
            log.error(
                    "Cannot update status with blank metadataId or null status. ID: {}, Status: {}",
                    metadataId, status);
            throw new IllegalArgumentException(
                    "Metadata ID and Status cannot be null/blank for update.");
        }
        log.info("Attempting to update status to {} for metadata ID: {}", status, metadataId);
        try {
            Firestore firestore = getFirestore();
            DocumentReference docRef =
                    firestore.collection(audioMetadataCollectionName).document(metadataId);
            ApiFuture<WriteResult> future = docRef.update("status", status.name());
            future.get();
            log.info("Successfully updated status to {} for metadata ID: {}", status, metadataId);
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error updating status for metadata ID {}: {}", metadataId, e.getMessage(),
                    e);
            throw new FirestoreInteractionException(
                    "Failed to update status for metadata " + metadataId, e);
        } catch (Exception e) {
            log.error("Unexpected error updating status for metadata ID {}: {}", metadataId,
                    e.getMessage(), e);
            throw new FirestoreInteractionException(
                    "Unexpected error updating status for metadata " + metadataId, e);
        }
    }

    public List<AudioMetadata> getAllAudioMetadata() {
        log.warn(
                "Executing getAllAudioMetadata - Fetching all documents from {}. Consider pagination for large datasets.",
                audioMetadataCollectionName);
        try {
            Firestore firestore = getFirestore();
            ApiFuture<QuerySnapshot> future =
                    firestore.collection(audioMetadataCollectionName).get();
            List<AudioMetadata> audioMetadataList = new ArrayList<>();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            for (QueryDocumentSnapshot document : documents) {
                try {
                    AudioMetadata metadata = fromDocumentSnapshot(document);
                    if (metadata != null) {
                        audioMetadataList.add(metadata);
                    }
                } catch (Exception e) {
                    log.error("Failed to process document {} in getAllAudioMetadata: {}",
                            document.getId(), e.getMessage(), e);
                }
            }
            log.info("Retrieved {} total AudioMetadata documents from Firestore.",
                    audioMetadataList.size());
            return audioMetadataList;
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error retrieving all AudioMetadata documents", e);
            throw new FirestoreInteractionException("Failed to retrieve all AudioMetadata", e);
        }
    }

    @Cacheable(value = CACHE_METADATA_BY_USER, key = "#userId", condition = "#userId != null")
    public List<AudioMetadata> getAudioMetadataByUserId(String userId, int pageSize,
            String lastDocumentId) {
        if (!StringUtils.hasText(userId)) {
            log.warn("Attempted to get AudioMetadata with blank userId.");
            return Collections.emptyList();
        }
        log.info(
                "Retrieving AudioMetadata for user ID: {}, page size: {}, starting after document ID: {}",
                userId, pageSize, lastDocumentId == null ? "N/A" : lastDocumentId);
        List<AudioMetadata> userMetadataList = new ArrayList<>();
        try {
            Firestore firestore = getFirestore();
            CollectionReference colRef = firestore.collection(audioMetadataCollectionName);
            Query query = colRef.whereEqualTo("userId", userId)
                    .orderBy("uploadTimestamp", Query.Direction.DESCENDING)
                    .limit(pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE);

            if (StringUtils.hasText(lastDocumentId)) {
                DocumentSnapshot lastSnapshot = null;
                try {
                    ApiFuture<DocumentSnapshot> lastSnapshotFuture =
                            colRef.document(lastDocumentId).get();
                    lastSnapshot = lastSnapshotFuture.get();
                } catch (ExecutionException | InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error(
                            "Error fetching pagination document snapshot for ID: {} used in startAfter. Aborting pagination.",
                            lastDocumentId, e);
                    throw new FirestoreInteractionException(
                            "Failed to fetch pagination cursor document " + lastDocumentId, e);
                }

                if (lastSnapshot != null && lastSnapshot.exists()) {
                    query = query.startAfter(lastSnapshot);
                    log.debug("Pagination query starting after document: {}", lastDocumentId);
                } else {
                    log.warn(
                            "lastDocumentId '{}' provided for pagination but document not found or fetch failed. Fetching first page.",
                            lastDocumentId);
                    query = colRef.whereEqualTo("userId", userId)
                            .orderBy("uploadTimestamp", Query.Direction.DESCENDING)
                            .limit(pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE);
                }
            }

            ApiFuture<QuerySnapshot> future = query.get();
            List<QueryDocumentSnapshot> documents;
            try {
                documents = future.get().getDocuments();
            } catch (ExecutionException | InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Firestore query execution failed for user ID: {}", userId, e);
                if (e.getCause() instanceof NullPointerException
                        && e.getCause().getMessage() != null && e.getCause().getMessage().contains(
                                "Cannot read the array length because \"value\" is null")) {
                    log.error(
                            "Caught the specific NullPointerException during query execution, likely an internal Firestore client issue with data mapping or query processing.");
                }
                throw new FirestoreInteractionException(
                        "Firestore query execution failed for user " + userId, e);
            } catch (Exception e) {
                log.error("Unexpected error during Firestore query execution for user ID: {}",
                        userId, e);
                throw new FirestoreInteractionException(
                        "Unexpected error during query execution for user " + userId, e);
            }

            log.debug("Query executed successfully, processing {} documents for user {}",
                    documents.size(), userId);
            for (QueryDocumentSnapshot document : documents) {
                try {
                    AudioMetadata metadata = fromDocumentSnapshot(document);
                    if (metadata != null) {
                        userMetadataList.add(metadata);
                    } else {
                        log.warn("Document {} resulted in null AudioMetadata object after mapping.",
                                document.getId());
                    }
                } catch (Exception e) {
                    log.error(
                            "Failed to process document {} for user {} in getAudioMetadataByUserId: {}",
                            document.getId(), userId, e.getMessage(), e);
                }
            }
            log.info("Successfully retrieved {} AudioMetadata documents for user ID: {} (page)",
                    userMetadataList.size(), userId);
            return userMetadataList;
        } catch (FirestoreInteractionException e) {
            log.error("Firestore interaction failed while retrieving metadata for user ID: {}",
                    userId, e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error retrieving paginated AudioMetadata for user ID: {}", userId,
                    e);
            throw new FirestoreInteractionException(
                    "Unexpected error retrieving paginated AudioMetadata for user " + userId, e);
        }
    }

    @Cacheable(value = CACHE_METADATA_BY_ID, key = "#metadataId", unless = "#result == null")
    public AudioMetadata getAudioMetadataById(String metadataId) {
        if (!StringUtils.hasText(metadataId)) {
            log.warn("Attempted to get AudioMetadata with blank ID.");
            return null;
        }
        log.debug("Retrieving AudioMetadata document by ID: {}", metadataId);
        try {
            Firestore firestore = getFirestore();
            DocumentReference docRef =
                    firestore.collection(audioMetadataCollectionName).document(metadataId);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot document = future.get();

            if (document.exists()) {
                AudioMetadata metadata = fromDocumentSnapshot(document);
                if (metadata != null) {
                    log.info("Retrieved AudioMetadata document with ID: {}", metadataId);
                } else {
                    log.warn("Mapping failed for existing document ID: {}", metadataId);
                }
                return metadata;
            } else {
                log.warn("No AudioMetadata document found with ID: {}", metadataId);
                return null;
            }
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error retrieving AudioMetadata document by ID: {}", metadataId, e);
            throw new FirestoreInteractionException(
                    "Failed to retrieve AudioMetadata by ID " + metadataId, e);
        } catch (Exception e) {
            log.error("Unexpected error retrieving AudioMetadata document by ID: {}", metadataId,
                    e);
            throw new FirestoreInteractionException(
                    "Unexpected error retrieving AudioMetadata by ID " + metadataId, e);
        }
    }

    public void saveLearningRecommendations(List<LearningRecommendation> recommendations)
            throws FirestoreInteractionException {
        if (recommendations == null || recommendations.isEmpty()) {
            log.warn("Attempted to save an empty or null list of recommendations.");
            return;
        }
        String recordingId = recommendations.stream().map(LearningRecommendation::getRecordingId)
                .filter(Objects::nonNull).findFirst().orElse("UNKNOWN");

        log.info("[{}] Attempting to save {} recommendations to collection: {}", recordingId,
                recommendations.size(), recommendationsCollectionName);
        try {
            Firestore firestore = getFirestore();
            WriteBatch batch = firestore.batch();
            CollectionReference colRef = firestore.collection(recommendationsCollectionName);

            for (LearningRecommendation recommendation : recommendations) {
                DocumentReference docRef = colRef.document();
                batch.set(docRef, recommendation);
            }

            ApiFuture<List<WriteResult>> future = batch.commit();
            future.get();

            log.info("[{}] Successfully saved {} recommendations to Firestore.", recordingId,
                    recommendations.size());
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[{}] Error saving batch of recommendations to Firestore.", recordingId, e);
            throw new FirestoreInteractionException(
                    "Failed to save batch of recommendations for recording " + recordingId, e);
        } catch (Exception e) {
            log.error("[{}] Unexpected error saving batch of recommendations.", recordingId, e);
            throw new FirestoreInteractionException(
                    "Unexpected error saving recommendations for recording " + recordingId, e);
        }
    }

    public List<LearningRecommendation> getLearningRecommendationsByRecordingId(String recordingId)
            throws FirestoreInteractionException {
        if (!StringUtils.hasText(recordingId)) {
            log.error("Cannot retrieve recommendations with a blank recordingId.");
            throw new IllegalArgumentException("Recording ID cannot be blank.");
        }
        log.info("Retrieving recommendations for recording ID: {} from collection: {}", recordingId,
                recommendationsCollectionName);
        List<LearningRecommendation> recommendationList = new ArrayList<>();
        try {
            Firestore firestore = getFirestore();
            CollectionReference colRef = firestore.collection(recommendationsCollectionName);
            Query query = colRef.whereEqualTo("recordingId", recordingId).orderBy("createdAt",
                    Query.Direction.ASCENDING);

            ApiFuture<QuerySnapshot> future = query.get();
            QuerySnapshot querySnapshot = future.get();
            List<QueryDocumentSnapshot> documents = querySnapshot.getDocuments();

            for (QueryDocumentSnapshot document : documents) {
                try {
                    LearningRecommendation recommendation =
                            document.toObject(LearningRecommendation.class);
                    recommendationList.add(recommendation);
                } catch (Exception e) {
                    log.error(
                            "Error mapping Firestore document {} to LearningRecommendation object for recordingId {}: {}",
                            document.getId(), recordingId, e.getMessage(), e);
                }
            }
            log.info("Retrieved {} recommendations for recording ID: {}", recommendationList.size(),
                    recordingId);
            return recommendationList;
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error retrieving recommendations for recording ID: {}", recordingId, e);
            throw new FirestoreInteractionException(
                    "Failed to retrieve recommendations for recording " + recordingId, e);
        } catch (Exception e) {
            log.error("Unexpected error retrieving recommendations for recording ID: {}",
                    recordingId, e);
            throw new FirestoreInteractionException(
                    "Unexpected error retrieving recommendations for recording " + recordingId, e);
        }
    }

    private AudioMetadata fromDocumentSnapshot(DocumentSnapshot document) {
        if (document == null || !document.exists()) {
            log.warn("Attempted to map null or non-existent document snapshot.");
            return null;
        }
        AudioMetadata metadata = new AudioMetadata();
        metadata.setId(document.getId());
        try {
            Map<String, Object> data = document.getData();
            if (data == null) {
                log.warn("Document snapshot data is null for ID: {}", document.getId());
                return null;
            }

            metadata.setUserId(getString(data, "userId", document.getId()));
            metadata.setFileName(getString(data, "fileName", document.getId()));
            metadata.setFileSize(getLong(data, "fileSize", document.getId()));
            metadata.setContentType(getString(data, "contentType", document.getId()));
            metadata.setTitle(getString(data, "title", document.getId()));
            metadata.setDescription(getString(data, "description", document.getId()));
            metadata.setNhostFileId(getString(data, "nhostFileId", document.getId()));
            metadata.setStorageUrl(getString(data, "storageUrl", document.getId()));
            metadata.setAudioUrl(getString(data, "audioUrl", document.getId()));
            metadata.setUploadTimestamp(getTimestamp(data, "uploadTimestamp", document.getId()));
            metadata.setRecordingId(getString(data, "recordingId", document.getId()));
            metadata.setSummaryId(getString(data, "summaryId", document.getId()));
            metadata.setTranscriptText(getString(data, "transcriptText", document.getId()));
            metadata.setDurationSeconds(getInteger(data, "durationSeconds", document.getId()));
            metadata.setLastUpdated(getTimestamp(data, "lastUpdated", document.getId()));
            metadata.setFailureReason(getString(data, "failureReason", document.getId()));

            String statusStr = getString(data, "status", document.getId());
            if (StringUtils.hasText(statusStr)) {
                try {
                    metadata.setStatus(ProcessingStatus.valueOf(statusStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    log.warn(
                            "Invalid status value '{}' found in Firestore for document ID: {}. Setting status to UPLOADED.",
                            statusStr, document.getId());
                    metadata.setStatus(ProcessingStatus.UPLOADED);
                }
            } else {
                log.debug(
                        "Status field missing or blank for document ID: {}. Defaulting to UPLOADED.",
                        document.getId());
                metadata.setStatus(ProcessingStatus.UPLOADED);
            }

            metadata.setTranscriptionComplete(
                    getBoolean(data, "transcriptionComplete", document.getId(), false));
            metadata.setPdfConversionComplete(
                    getBoolean(data, "pdfConversionComplete", document.getId(), false));
            metadata.setAudioOnly(getBoolean(data, "audioOnly", document.getId(), false));
            metadata.setAudioUploadComplete(
                    getBoolean(data, "audioUploadComplete", document.getId(), false));
            metadata.setWaitingForPdf(getBoolean(data, "waitingForPdf", document.getId(), false));

            metadata.setOriginalPptxFileName(
                    getString(data, "originalPptxFileName", document.getId()));
            metadata.setPptxFileSize(getLong(data, "pptxFileSize", document.getId()));
            metadata.setPptxContentType(getString(data, "pptxContentType", document.getId()));
            metadata.setNhostPptxFileId(getString(data, "nhostPptxFileId", document.getId()));
            metadata.setGeneratedPdfNhostFileId(
                    getString(data, "generatedPdfNhostFileId", document.getId()));
            metadata.setGeneratedPdfUrl(getString(data, "generatedPdfUrl", document.getId()));
            metadata.setGoogleFilesApiPdfUri(
                    getString(data, "googleFilesApiPdfUri", document.getId()));
            metadata.setGptSummary(getString(data, "gptSummary", document.getId()));

            return metadata;
        } catch (Exception e) {
            log.error(
                    "Critical error mapping Firestore document data to AudioMetadata for ID: {}. Error: {}",
                    document.getId(), e.getMessage(), e);
            return null;
        }
    }

    private String getString(Map<String, Object> data, String key, String docId) {
        Object value = data.get(key);
        if (value instanceof String) {
            return (String) value;
        } else if (value != null) {
            log.trace(
                    "Field '{}' was not a String for document ID: {}. Type: {}. Returning toString().",
                    key, docId, value.getClass().getName());
            return value.toString();
        }
        log.trace("Field '{}' not found or null for document ID: {}", key, docId);
        return null;
    }

    private long getLong(Map<String, Object> data, String key, String docId) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value != null) {
            log.warn("Field '{}' was not a Number for document ID: {}. Type: {}. Returning 0.", key,
                    docId, value.getClass().getName());
        } else {
            log.trace("Field '{}' not found or null for document ID: {}", key, docId);
        }
        return 0L;
    }

    private Timestamp getTimestamp(Map<String, Object> data, String key, String docId) {
        Object value = data.get(key);
        if (value instanceof Timestamp) {
            return (Timestamp) value;
        } else if (value != null) {
            log.warn(
                    "Field '{}' was not a Timestamp for document ID: {}. Type: {}. Returning null.",
                    key, docId, value.getClass().getName());
        } else {
            log.trace("Field '{}' not found or null for document ID: {}", key, docId);
        }
        return null;
    }

    private Integer getInteger(Map<String, Object> data, String key, String docId) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value != null) {
            log.warn("Field '{}' was not a Number for document ID: {}. Type: {}. Returning null.",
                    key, docId, value.getClass().getName());
        } else {
            log.trace("Field '{}' not found or null for document ID: {}", key, docId);
        }
        return null;
    }

    private boolean getBoolean(Map<String, Object> data, String key, String docId,
            boolean defaultValue) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof Number) {
            long numValue = ((Number) value).longValue();
            return numValue != 0;
        } else if (value instanceof String) {
            String strValue = (String) value;
            return "true".equalsIgnoreCase(strValue) || "1".equals(strValue);
        } else if (value != null) {
            log.warn(
                    "Field '{}' was not a Boolean, Number, or String for document ID: {}. Type: {}. Returning default value.",
                    key, docId, value.getClass().getName());
        } else {
            log.trace("Field '{}' not found or null for document ID: {}", key, docId);
        }
        return defaultValue;
    }

    public MulticastMessage buildProcessingCompleteMessage(String userId, String recordingId,
            String summaryId) {
        log.info(
                "Building FCM processing complete message for userId: {}, recordingId: {}, summaryId: {}",
                userId, recordingId, summaryId);

        List<String> tokens;
        try {
            tokens = userService.getFcmTokensForUser(userId);
        } catch (FirestoreInteractionException e) {
            log.error("Failed to retrieve FCM tokens for user {} due to Firestore error: {}",
                    userId, e.getMessage(), e);
            return null;
        }

        if (tokens == null || tokens.isEmpty()) {
            log.warn("No FCM tokens found for user {}. Cannot build notification message.", userId);
            return null;
        }
        log.debug("Found {} tokens for user {}", tokens.size(), userId);

        Map<String, String> dataPayload = Map.of("type", "processingComplete", "recordingId",
                recordingId, "summaryId", summaryId);

        AndroidConfig androidConfig =
                AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build();

        Map<String, String> apnsHeaders =
                Map.of("apns-priority", "5", "apns-push-type", "background");
        Aps aps = Aps.builder().setContentAvailable(true).build();

        ApnsConfig apnsConfig = ApnsConfig.builder().putAllHeaders(apnsHeaders).setAps(aps).build();

        MulticastMessage message =
                MulticastMessage.builder().putAllData(dataPayload).setAndroidConfig(androidConfig)
                        .setApnsConfig(apnsConfig).addAllTokens(tokens).build();

        log.info("Successfully built FCM MulticastMessage for {} tokens, user {}, recording {}",
                tokens.size(), userId, recordingId);
        return message;
    }

    public void sendFcmMessage(MulticastMessage message, List<String> tokens, String userId) {
        if (message == null || tokens == null || tokens.isEmpty() || userId == null
                || userId.isBlank()) {
            log.warn("Cannot send FCM message: message, token list, or userId is null or empty.");
            return;
        }
        FirebaseMessaging messagingInstance = this.firebaseMessaging;
        log.info("Attempting to send FCM multicast message to {} recipients for user {}.",
                tokens.size(), userId);
        List<String> unregisteredTokens = new ArrayList<>();
        try {
            BatchResponse response = messagingInstance.sendEachForMulticast(message);
            int successCount = response.getSuccessCount();
            int failureCount = response.getFailureCount();
            log.info("FCM multicast send completed for user {}. Success: {}, Failure: {}", userId,
                    successCount, failureCount);

            if (failureCount > 0) {
                log.warn("Some FCM messages failed to send for user {}. Failures: {}", userId,
                        failureCount);
                List<SendResponse> responses = response.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    if (!responses.get(i).isSuccessful()) {
                        if (i < tokens.size()) {
                            String failedToken = tokens.get(i);
                            FirebaseMessagingException e = responses.get(i).getException();
                            if (e != null) {
                                log.warn(
                                        "FCM message failed for token {} (user {}): ErrorCode={}, Message={}",
                                        failedToken, userId, e.getMessagingErrorCode(),
                                        e.getMessage());
                                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                                    unregisteredTokens.add(failedToken);
                                    log.info(
                                            "Identified unregistered token {} for user {} for cleanup.",
                                            failedToken, userId);
                                }
                            } else {
                                log.warn(
                                        "FCM message failed for token {} (user {}), but no exception details available.",
                                        failedToken, userId);
                            }
                        } else {
                            log.error(
                                    "Index {} out of bounds for token list size {} while processing failures for user {}. Cannot log failed token.",
                                    i, tokens.size(), userId);
                        }
                    }
                }
            }
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send FCM multicast message for user {}: ErrorCode={}, Message={}",
                    userId, e.getMessagingErrorCode(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending FCM multicast message for user {}.", userId, e);
        }

        if (!unregisteredTokens.isEmpty()) {
            log.info("Initiating cleanup for {} unregistered FCM tokens for user {}.",
                    unregisteredTokens.size(), userId);
            try {
                userService.removeFcmTokens(userId, unregisteredTokens);
            } catch (Exception e) {
                log.error("Error during FCM token cleanup for user {}: {}", userId, e.getMessage(),
                        e);
            }
        }
    }

    @CacheEvict(value = CACHE_METADATA_BY_ID, key = "#metadataId",
            condition = "#metadataId != null")
    public void updateAudioMetadataStatusAndReason(String metadataId, @Nullable String userId,
            ProcessingStatus status, String reason)
            throws ExecutionException, InterruptedException {
        if (metadataId == null || status == null) {
            log.error("Cannot update metadata status: metadataId or status is null.");
            throw new IllegalArgumentException("Metadata ID and Status cannot be null.");
        }

        DocumentReference docRef =
                getFirestore().collection(audioMetadataCollectionName).document(metadataId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status.name());
        updates.put("lastUpdated", Timestamp.now());

        if (status == ProcessingStatus.FAILED
                || status == ProcessingStatus.PROCESSING_HALTED_UNSUITABLE_CONTENT) {
            updates.put("failureReason", reason != null ? reason : "No specific reason provided.");
        } else {
            updates.put("failureReason", null);
        }

        log.info(
                "Updating Firestore document {} in collection {} for user {} with status: {}, reason: '{}'",
                metadataId, audioMetadataCollectionName, userId != null ? userId : "<unknown>",
                status, reason);
        ApiFuture<WriteResult> writeResult = docRef.update(updates);

        try {
            WriteResult result = writeResult.get();
            log.info(
                    "Successfully updated Firestore document {} at {}. Attempting manual cache eviction.",
                    metadataId, result.getUpdateTime());

            if (userId != null) {
                try {
                    Cache userListCache = cacheManager.getCache(CACHE_METADATA_BY_USER);
                    if (userListCache != null) {
                        log.info("Manually evicting user list cache entry for user {} in cache {}",
                                userId, CACHE_METADATA_BY_USER);
                        userListCache.evict(userId);
                    } else {
                        log.warn("Cache '{}' not found for manual user list eviction.",
                                CACHE_METADATA_BY_USER);
                    }
                } catch (Exception e) {
                    log.error("Error during manual user list cache eviction for user {}: {}",
                            userId, e.getMessage(), e);
                }
            } else {
                log.warn(
                        "Cannot manually evict user list cache for metadata {} because userId was null.",
                        metadataId);
            }

        } catch (ExecutionException | InterruptedException e) {
            log.error(
                    "Firestore update failed for document {}. Cache eviction will not be attempted. Error: {}",
                    metadataId, e.getMessage(), e);
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
            throw e;
        }
    }
}

package edu.cit.audioscholar.service;

import com.google.cloud.Timestamp; // Import Firestore Timestamp
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.security.JwtTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);
    private static final String DENYLIST_COLLECTION = "jwt_denylist";

    private final FirebaseService firebaseService;
    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    public TokenRevocationService(FirebaseService firebaseService, JwtTokenProvider jwtTokenProvider) {
        this.firebaseService = firebaseService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Adds the given JWT's ID (jti) to the denylist in Firestore.
     * The entry will implicitly expire based on Firestore cleanup rules or manual cleanup.
     * We store the original expiry time for potential manual cleanup.
     *
     * @param token The JWT to revoke.
     */
    public void revokeToken(String token) {
        try {
            String jti = jwtTokenProvider.getJtiFromJWT(token);
            Date expirationDate = jwtTokenProvider.getExpirationDateFromJWT(token);
            long nowMillis = System.currentTimeMillis();
            long expirationMillis = expirationDate.getTime();

            // Optional: Check if token is already expired before adding to denylist
            if (expirationMillis <= nowMillis) {
                log.debug("Token with jti {} is already expired. No need to add to denylist.", jti);
                return;
            }

            // Store JTI as document ID, include expiry for potential cleanup
            Map<String, Object> denylistEntry = new HashMap<>();
            denylistEntry.put("expiryTimestamp", Timestamp.of(expirationDate)); // Store as Firestore Timestamp

            log.info("Adding token jti {} to denylist with expiry {}", jti, expirationDate);
            firebaseService.saveData(DENYLIST_COLLECTION, jti, denylistEntry);

        } catch (ExpiredJwtException e) {
            // If the token is already expired when trying to revoke, it's effectively revoked.
            log.debug("Attempted to revoke an already expired token (jti: {}).", e.getClaims().getId());
        } catch (FirestoreInteractionException e) {
            log.error("Firestore error while adding token jti to denylist: {}", e.getMessage(), e);
            // Depending on requirements, you might re-throw or handle differently
        } catch (Exception e) {
            log.error("Unexpected error while revoking token: {}", e.getMessage(), e);
            // Handle unexpected JWT parsing errors (Malformed, Signature etc.)
        }
    }

    /**
     * Checks if a JWT's ID (jti) exists in the Firestore denylist.
     *
     * @param token The JWT to check.
     * @return true if the token's jti is found in the denylist, false otherwise.
     */
    public boolean isTokenRevoked(String token) {
        try {
            String jti = jwtTokenProvider.getJtiFromJWT(token);
            log.debug("Checking denylist for token jti: {}", jti);

            Map<String, Object> data = firebaseService.getData(DENYLIST_COLLECTION, jti);
            boolean revoked = data != null;

            if (revoked) {
                log.warn("Token with jti {} found in denylist. Access denied.", jti);
            } else {
                log.debug("Token with jti {} not found in denylist. Access allowed (pending other checks).", jti);
            }
            return revoked;

        } catch (ExpiredJwtException e) {
            // An expired token is implicitly invalid, so it's not "revoked" in the sense of being in the denylist.
            // The standard JWT validation handles expiration.
            log.debug("Token is already expired (jti: {}), check is effectively false (handled by standard validation).", e.getClaims().getId());
            return false;
        } catch (FirestoreInteractionException e) {
            log.error("Firestore error while checking denylist for token: {}", e.getMessage(), e);
            // Fail-safe: If we can't check the denylist, should we deny access?
            // Returning false allows access if DB fails, true denies access. Choose based on security posture.
            // Let's deny access if the check fails.
            return true;
        } catch (Exception e) {
            log.error("Unexpected error while checking token revocation status: {}", e.getMessage(), e);
            // Fail-safe: Deny access on unexpected errors during check.
            return true;
        }
    }

    // --- Optional: Cleanup Method ---
    // This would need to be triggered periodically (e.g., scheduled task, Cloud Function)
    /*
    public void cleanupExpiredDenylistEntries() {
        log.info("Starting cleanup of expired JWT denylist entries...");
        try {
            Timestamp now = Timestamp.now();
            // Query Firestore for documents where expiryTimestamp <= now
            // This requires creating an index on expiryTimestamp in Firestore
            // Example using Query (adapt based on FirebaseService capabilities):
            // Query query = firebaseService.getFirestore().collection(DENYLIST_COLLECTION)
            //     .whereLessThanOrEqualTo("expiryTimestamp", now);
            // ApiFuture<QuerySnapshot> querySnapshot = query.get();
            // List<QueryDocumentSnapshot> documents = querySnapshot.get().getDocuments();

            // For simplicity, let's assume FirebaseService can provide a way to query
            // List<Map<String, Object>> expiredEntries = firebaseService.queryCollectionWhereLessThanOrEqual(
            //      DENYLIST_COLLECTION, "expiryTimestamp", now);

            // int count = 0;
            // for (Map<String, Object> entry : expiredEntries) {
            //     String jti = (String) entry.get("jti"); // Assuming jti is stored if not used as ID
            //     // Or get the document ID if jti is the ID
            //     // String docId = ...
            //     if (jti != null) { // or docId
            //         firebaseService.deleteData(DENYLIST_COLLECTION, jti); // or docId
            //         count++;
            //     }
            // }
            // log.info("Finished cleanup. Removed {} expired denylist entries.", count);

        } catch (Exception e) {
             log.error("Error during denylist cleanup: {}", e.getMessage(), e);
        }
    }
    */
}
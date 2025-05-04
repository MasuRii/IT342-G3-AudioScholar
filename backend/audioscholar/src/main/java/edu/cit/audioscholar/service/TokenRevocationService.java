package edu.cit.audioscholar.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import com.google.cloud.Timestamp;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.security.JwtTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);
    private static final String DENYLIST_COLLECTION = "jwt_denylist";
    private static final String CACHE_NAME = "jwtDenylistCache";

    private final FirebaseService firebaseService;
    private final JwtTokenProvider jwtTokenProvider;
    private final CacheManager cacheManager;

    @Autowired
    public TokenRevocationService(FirebaseService firebaseService,
            JwtTokenProvider jwtTokenProvider, CacheManager cacheManager) {
        this.firebaseService = firebaseService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.cacheManager = cacheManager;
    }

    public void revokeToken(String token) {
        String jti = null;
        try {
            jti = jwtTokenProvider.getJtiFromJWT(token);
            Date expirationDate = jwtTokenProvider.getExpirationDateFromJWT(token);
            long nowMillis = System.currentTimeMillis();
            long expirationMillis = expirationDate.getTime();

            if (expirationMillis <= nowMillis) {
                log.debug("Token with jti {} is already expired. No need to add to denylist.", jti);
                return;
            }

            Map<String, Object> denylistEntry = new HashMap<>();
            denylistEntry.put("expiryTimestamp", Timestamp.of(expirationDate));

            log.info("Adding token jti {} to denylist with expiry {}", jti, expirationDate);
            firebaseService.saveData(DENYLIST_COLLECTION, jti, denylistEntry);

            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) {
                cache.put(jti, Boolean.TRUE);
                log.debug("Added jti {} to cache '{}'", jti, CACHE_NAME);
            } else {
                log.warn("Cache '{}' not found. Could not cache revoked token jti: {}", CACHE_NAME,
                        jti);
            }

        } catch (ExpiredJwtException e) {
            log.debug("Attempted to revoke an already expired token (jti: {}).",
                    e.getClaims().getId());
        } catch (FirestoreInteractionException e) {
            log.error("Firestore error while adding token jti {} to denylist: {}", jti,
                    e.getMessage(), e);
        } catch (JwtException e) {
            log.error("Error processing JWT during revocation: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while revoking token (jti: {}): {}", jti, e.getMessage(),
                    e);
        }
    }

    public boolean isTokenRevoked(String token) {
        try {
            String tokenId = jwtTokenProvider.getJtiFromJWT(token);
            log.debug("Checking denylist for token jti: {}", tokenId);

            Cache jwtDenylistCache = cacheManager.getCache(CACHE_NAME);
            if (jwtDenylistCache != null) {
                Boolean cachedResult = jwtDenylistCache.get(tokenId, Boolean.class);
                if (cachedResult != null) {
                    log.debug("Token jti {} found in cache '{}'. Revoked: {}", tokenId, CACHE_NAME,
                            cachedResult);
                    return cachedResult;
                }
                log.debug("Token jti {} not found in cache '{}'. Checking Firestore.", tokenId,
                        CACHE_NAME);
            }

            try {
                Map<String, Object> denylistEntry =
                        firebaseService.getData(DENYLIST_COLLECTION, tokenId);

                if (denylistEntry == null) {
                    // Document doesn't exist in Firestore
                    if (jwtDenylistCache != null) {
                        jwtDenylistCache.put(tokenId, Boolean.FALSE);
                    }
                    log.debug(
                            "Token with jti {} not found in Firestore denylist. Access allowed (pending other checks).",
                            tokenId);
                    return false;
                } else {
                    // Document exists in Firestore
                    if (jwtDenylistCache != null) {
                        jwtDenylistCache.put(tokenId, Boolean.TRUE);
                    }
                    log.debug("Token with jti {} found in Firestore denylist. Access denied.",
                            tokenId);
                    return true;
                }
            } catch (FirestoreInteractionException e) {
                if (jwtDenylistCache != null) {
                    jwtDenylistCache.put(tokenId, Boolean.FALSE);
                }
                log.error(
                        "Error checking Firestore denylist for token jti {}: {}. Allowing access.",
                        tokenId, e.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("Error checking token revocation status: {}", e.getMessage(), e);
            return true;
        }
    }
}

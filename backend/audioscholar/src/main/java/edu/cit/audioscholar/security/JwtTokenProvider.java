package edu.cit.audioscholar.security;

import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${app.jwt.secret}")
    private String jwtSecretString;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    private SecretKey jwtSecretKey;

    @PostConstruct
    public void init() {
        if (jwtSecretString == null || jwtSecretString.isBlank() || jwtSecretString.length() < 43) {
            logger.warn("JWT Secret ('app.jwt.secret') is not configured, is blank, or too short (requires >= 43 Base64 chars for HS256)! Using a default temporary key. PLEASE CONFIGURE a strong secret.");
            this.jwtSecretKey = Jwts.SIG.HS256.key().build();
        } else {
            try {
                byte[] keyBytes = Decoders.BASE64.decode(jwtSecretString);
                this.jwtSecretKey = Keys.hmacShaKeyFor(keyBytes);
                logger.info("Successfully loaded JWT secret key from configuration.");
            } catch (IllegalArgumentException e) {
                logger.error("Invalid Base64 encoding for JWT secret ('app.jwt.secret'). Using a default temporary key. PLEASE FIX the configuration.", e);
                this.jwtSecretKey = Jwts.SIG.HS256.key().build();
            }
        }
    }


    public SecretKey getJwtSecretKey() {
        if (this.jwtSecretKey == null) {
            init();
            if (this.jwtSecretKey == null) {
                throw new IllegalStateException("JWT Secret Key is null after initialization attempt.");
            }
        }
        return jwtSecretKey;
    }

    public String generateToken(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        String username = authentication.getName();
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        logger.debug("Generating token for user: {}, Authorities: {}", username, authorities);

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);
        String jti = UUID.randomUUID().toString();

        String displayName = null;
        if (principal instanceof OAuth2User) {
            OAuth2User oauth2User = (OAuth2User) principal;
            displayName = oauth2User.getAttribute("name");
            if (displayName == null && oauth2User.getAttribute("login") != null) {
                displayName = oauth2User.getAttribute("login");
            }
        }

        return Jwts.builder()
                .id(jti)
                .subject(username)
                .claim("roles", authorities)
                .claim("name", displayName != null ? displayName : "")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getJwtSecretKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String getUserIdFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getJwtSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public String getJtiFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getJwtSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getId();
    }

    public Date getExpirationDateFromJWT(String token) {
         Claims claims = Jwts.parser()
                .verifyWith(getJwtSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getExpiration();
    }


    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(getJwtSecretKey()).build().parseSignedClaims(authToken);
            return true;
        } catch (SignatureException ex) {
            logger.error("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            logger.debug("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string is empty or invalid: {}", ex.getMessage());
        } catch (Exception e) {
             logger.error("Unexpected error validating JWT token: {}", e.getMessage(), e);
        }
        return false;
    }

    public Claims getClaimsFromJWT(String token) {
         return Jwts.parser()
                .verifyWith(getJwtSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
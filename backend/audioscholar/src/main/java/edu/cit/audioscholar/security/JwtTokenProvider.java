package edu.cit.audioscholar.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.security.core.GrantedAuthority;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.stream.Collectors;

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
        if (jwtSecretString == null || jwtSecretString.length() < 32) {
             logger.warn("JWT Secret is not configured or too short in application properties! Using a default temporary key. PLEASE CONFIGURE a strong 'app.jwt.secret'.");
             this.jwtSecretKey = Jwts.SIG.HS256.key().build();
        } else {
            byte[] keyBytes = Decoders.BASE64.decode(jwtSecretString);
            this.jwtSecretKey = Keys.hmacShaKeyFor(keyBytes);
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

        String displayName = null;
        if (principal instanceof OAuth2User) {
             displayName = ((OAuth2User) principal).getAttribute("name");
             if (displayName == null && ((OAuth2User) principal).getAttribute("login") != null) {
                 displayName = ((OAuth2User) principal).getAttribute("login");
             }
        }


        return Jwts.builder()
                .subject(username)
                .claim("roles", authorities)
                .claim("name", displayName)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(jwtSecretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String getUsernameFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(jwtSecretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(jwtSecretKey).build().parseSignedClaims(authToken);
            return true;
        } catch (SignatureException ex) {
            logger.error("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            logger.error("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    public Claims getClaimsFromJWT(String token) {
         return Jwts.parser()
                 .verifyWith(jwtSecretKey)
                 .build()
                 .parseSignedClaims(token)
                 .getPayload();
    }
}
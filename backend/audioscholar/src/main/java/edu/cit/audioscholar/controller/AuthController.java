package edu.cit.audioscholar.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.cit.audioscholar.dto.JwtAuthenticationResponse;
import edu.cit.audioscholar.security.JwtTokenProvider;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final JwtTokenProvider tokenProvider;

    public AuthController(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @GetMapping("/token")
    public ResponseEntity<?> getAuthenticationToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || !(authentication instanceof OAuth2AuthenticationToken)) {
            logger.warn("User requested /api/auth/token without valid OAuth2 authentication.");
            return ResponseEntity.status(401).body("User is not authenticated via OAuth2 session.");
        }

        String jwt = tokenProvider.generateToken(authentication);
        logger.info("Generated JWT token for user {} via /api/auth/token endpoint.", authentication.getName());

        return ResponseEntity.ok(new JwtAuthenticationResponse(jwt));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
         Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
         if (authentication == null || !authentication.isAuthenticated()) {
             return ResponseEntity.status(401).body("User is not authenticated.");
         }
         return ResponseEntity.ok(authentication.getPrincipal());
    }
}
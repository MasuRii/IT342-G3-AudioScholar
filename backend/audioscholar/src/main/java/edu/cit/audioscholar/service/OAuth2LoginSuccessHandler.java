package edu.cit.audioscholar.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import edu.cit.audioscholar.model.User;
import edu.cit.audioscholar.security.JwtTokenProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final UserService userService;
    private final JwtTokenProvider tokenProvider;
    private final SavedRequestAwareAuthenticationSuccessHandler delegate = new SavedRequestAwareAuthenticationSuccessHandler();

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    public OAuth2LoginSuccessHandler(UserService userService, JwtTokenProvider tokenProvider) {
        this.userService = userService;
        this.tokenProvider = tokenProvider;
        this.delegate.setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
             logger.warn("Unsupported authentication type: {}", authentication.getClass().getName());
             this.delegate.onAuthenticationSuccess(request, response, authentication);
             return;
        }

        OAuth2User oauth2User = token.getPrincipal();
        String provider = token.getAuthorizedClientRegistrationId();

        Map<String, Object> attributes = oauth2User.getAttributes();
        String email = null;
        String name = null;
        String providerId = null;
        String pictureUrl = null;

        try {
             if ("google".equals(provider)) {
                email = (String) attributes.get("email");
                name = (String) attributes.get("name");
                providerId = (String) attributes.get("sub");
                pictureUrl = (String) attributes.get("picture");
            } else if ("github".equals(provider)) {
                email = (String) attributes.get("email");
                name = (String) attributes.get("name");
                if (name == null) { name = (String) attributes.get("login"); }
                Object idObject = attributes.get("id");
                providerId = (idObject != null) ? String.valueOf(idObject) : null;
                pictureUrl = (String) attributes.get("avatar_url");
            }

            if (email == null || providerId == null) {
                logger.error("Could not extract required user info (email/providerId) from provider '{}'. Attributes: {}", provider, attributes);
                response.sendRedirect("/login?error=provider_info_missing");
                return;
            }

            logger.info("OAuth2 login success. Provider: {}, ProviderId: {}, Email: {}, Name: {}", provider, providerId, email, name);

            processUserLogin(email, name, pictureUrl, provider, providerId);

            String jwt = tokenProvider.generateToken(authentication);
            logger.info("Generated JWT for user {}: [JWT Logged]", email);

             logger.info("OAuth2 login successful. Using default redirect. Frontend should call /api/auth/token.");
             this.delegate.onAuthenticationSuccess(request, response, authentication);


        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error processing user login for email {}: {}", email, e.getMessage(), e);
            Thread.currentThread().interrupt();
            response.sendRedirect("/login?error=internal_error");
        } catch (Exception e) {
            logger.error("Unexpected error during OAuth2 success handling for email {}: {}", email, e.getMessage(), e);
             response.sendRedirect("/login?error=unexpected_error");
        }
    }

    private void processUserLogin(String email, String name, String pictureUrl, String provider, String providerId)
            throws ExecutionException, InterruptedException {

        List<User> existingUsers = userService.getUsersByEmail(email);
        User user = null;
        boolean isNewUser = false;

        if (existingUsers != null && !existingUsers.isEmpty()) {
             user = existingUsers.stream()
                    .filter(u -> provider.equals(u.getProvider()))
                    .findFirst()
                    .orElse(existingUsers.get(0));
            logger.info("Found existing user with email {}. User ID: {}", email, user.getUserId());

            boolean needsUpdate = false;
            if (name != null && !name.equals(user.getDisplayName())) { user.setDisplayName(name); needsUpdate = true; }
            if (pictureUrl != null && !pictureUrl.equals(user.getProfileImageUrl())) { user.setProfileImageUrl(pictureUrl); needsUpdate = true; }
            if (user.getProvider() == null || !provider.equals(user.getProvider())) { user.setProvider(provider); needsUpdate = true; }
            if (user.getProviderId() == null || !providerId.equals(user.getProviderId())) { user.setProviderId(providerId); needsUpdate = true; }

            if (user.getRoles() == null || user.getRoles().isEmpty()) {
                logger.info("Assigning default role ROLE_USER to existing user {}", user.getUserId());
                user.setRoles(Collections.singletonList("ROLE_USER"));
                needsUpdate = true;
            }

            if (needsUpdate) {
                logger.info("Updating user details for User ID: {}", user.getUserId());
                userService.updateUser(user);
            }
        } else {
            isNewUser = true;
            logger.info("Creating new user for email {}", email);
            user = new User();
            if (user.getUserId() == null) { user.setUserId(UUID.randomUUID().toString()); }
            user.setEmail(email);
            user.setDisplayName(name);
            user.setProfileImageUrl(pictureUrl);
            user.setProvider(provider);
            user.setProviderId(providerId);
            user.setRecordingIds(new ArrayList<>());
            user.setFavoriteRecordingIds(new ArrayList<>());

            logger.info("Assigning default role ROLE_USER to new user {}", user.getUserId());
            user.setRoles(Collections.singletonList("ROLE_USER"));

            userService.createUser(user);
            logger.info("Created new user with User ID: {}", user.getUserId());
        }

    }


}
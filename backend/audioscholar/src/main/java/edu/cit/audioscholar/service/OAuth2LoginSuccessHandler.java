package edu.cit.audioscholar.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import edu.cit.audioscholar.exception.FirestoreInteractionException;
import edu.cit.audioscholar.model.User;
import edu.cit.audioscholar.security.JwtTokenProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final UserService userService;
    private final SavedRequestAwareAuthenticationSuccessHandler delegate =
            new SavedRequestAwareAuthenticationSuccessHandler();


    @Autowired
    public OAuth2LoginSuccessHandler(UserService userService, JwtTokenProvider tokenProvider) {
        this.userService = userService;
        this.delegate.setDefaultTargetUrl("/");
        this.delegate.setAlwaysUseDefaultTargetUrl(false);
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
            if ("google".equalsIgnoreCase(provider)) {
                email = (String) attributes.get("email");
                name = (String) attributes.get("name");
                providerId = (String) attributes.get("sub");
                pictureUrl = (String) attributes.get("picture");
            } else if ("github".equalsIgnoreCase(provider)) {
                email = (String) attributes.get("email");
                name = (String) attributes.get("name");
                if (name == null || name.isBlank()) {
                    name = (String) attributes.get("login");
                }
                Object idObject = attributes.get("id");
                providerId = (idObject != null) ? String.valueOf(idObject) : null;
                pictureUrl = (String) attributes.get("avatar_url");
            }

            if (providerId == null) {
                logger.error(
                        "Could not extract required providerId from provider '{}'. Attributes: {}",
                        provider, attributes);
                response.sendRedirect("/login?error=provider_info_missing");
                return;
            }
            if (email == null) {
                logger.warn(
                        "Email not provided by OAuth2 provider '{}' for providerId {}. Proceeding without email.",
                        provider, providerId);
            }


            logger.info("OAuth2 login success. Provider: {}, ProviderId: {}, Email: {}, Name: {}",
                    provider, providerId, email, name);

            processUserLogin(email, name, pictureUrl, provider, providerId);


            logger.info(
                    "OAuth2 login successful. Redirecting using SavedRequestAwareAuthenticationSuccessHandler. Frontend should call /api/auth/token if JWT is needed.");
            this.delegate.onAuthenticationSuccess(request, response, authentication);


        } catch (FirestoreInteractionException e) {
            logger.error("Error processing user login for email {}: {}", email, e.getMessage(), e);
            response.sendRedirect("/login?error=profile_error");
        } catch (Exception e) {
            logger.error("Unexpected error during OAuth2 success handling for email {}: {}", email,
                    e.getMessage(), e);
            response.sendRedirect("/login?error=unexpected_error");
        }
    }

    private void processUserLogin(String email, String name, String pictureUrl, String provider,
            String providerId) throws FirestoreInteractionException {


        User user = userService.findUserByEmail(email);

        if (user != null) {
            logger.info("Found existing user with email {}. User ID: {}", email, user.getUserId());

            boolean needsUpdate = false;
            if (name != null && !name.equals(user.getDisplayName())) {
                user.setDisplayName(name);
                needsUpdate = true;
            }
            if (pictureUrl != null && !pictureUrl.equals(user.getProfileImageUrl())) {
                user.setProfileImageUrl(pictureUrl);
                needsUpdate = true;
            }
            if (user.getProvider() == null || !provider.equals(user.getProvider())) {
                logger.warn("Updating provider for user {} from {} to {}", user.getUserId(),
                        user.getProvider(), provider);
                user.setProvider(provider);
                needsUpdate = true;
            }
            if (user.getProviderId() == null || !providerId.equals(user.getProviderId())) {
                logger.warn("Updating providerId for user {}", user.getUserId());
                user.setProviderId(providerId);
                needsUpdate = true;
            }

            if (user.getRoles() == null || user.getRoles().isEmpty()) {
                logger.info("Assigning default role ROLE_USER to existing user {}",
                        user.getUserId());
                user.setRoles(Collections.singletonList("ROLE_USER"));
                needsUpdate = true;
            }

            if (needsUpdate) {
                logger.info("Updating user details for User ID: {}", user.getUserId());
                userService.updateUser(user);
            }
        } else {
            logger.info("Creating new user via OAuth. Email: {}, Provider: {}", email, provider);
            user = new User();
            String internalUid = UUID.randomUUID().toString();
            user.setUserId(internalUid);
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

package edu.cit.audioscholar.security;

import edu.cit.audioscholar.service.TokenRevocationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtDenylistFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtDenylistFilter.class);

    private final TokenRevocationService tokenRevocationService;

    public JwtDenylistFilter(TokenRevocationService tokenRevocationService) {
        this.tokenRevocationService = tokenRevocationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            String token = extractJwtFromRequest(request);

            if (token != null) {
                log.debug("Checking if authenticated token is revoked for path: {}", request.getRequestURI());
                if (tokenRevocationService.isTokenRevoked(token)) {
                    log.warn("Access denied: Token has been revoked (found in denylist).");
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Token has been revoked.\"}");
                    response.setContentType("application/json");
                    return;
                } else {
                     log.debug("Token is valid (not revoked). Proceeding.");
                }
            } else {
                 log.debug("User is authenticated but no Bearer token found in request? This might indicate session-based auth leaking through.");
            }
        } else {
             log.debug("No authenticated user found in context, skipping denylist check for path: {}", request.getRequestURI());
        }


        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
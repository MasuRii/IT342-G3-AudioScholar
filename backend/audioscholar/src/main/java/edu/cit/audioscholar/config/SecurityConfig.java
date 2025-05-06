package edu.cit.audioscholar.config;

import java.util.Arrays;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import edu.cit.audioscholar.security.JwtDenylistFilter;
import edu.cit.audioscholar.security.JwtTokenProvider;
import edu.cit.audioscholar.service.OAuth2LoginSuccessHandler;
import edu.cit.audioscholar.service.TokenRevocationService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

        @Autowired
        private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

        @Autowired
        private JwtTokenProvider tokenProvider;

        @Autowired
        private TokenRevocationService tokenRevocationService;

        @Bean
        JwtDecoder jwtDecoder() {
                SecretKey secretKey = tokenProvider.getJwtSecretKey();
                if (secretKey == null) {
                        throw new IllegalStateException(
                                        "JWT Secret Key cannot be null. Check JwtTokenProvider initialization and configuration.");
                }
                return NimbusJwtDecoder.withSecretKey(secretKey).build();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(List.of("http://localhost:8100",
                                "https://localhost:8100", "capacitor://localhost",
                                "http://localhost", "https://localhost", "http://localhost:5173",
                                "https://localhost:5173", "http://localhost:8080",
                                "https://localhost:8080",
                                "https://it342-g3-audioscholar.onrender.com"));
                configuration.setAllowedMethods(
                                Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
                configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control",
                                "Content-Type", "X-Requested-With", "Accept", "X-CSRF-TOKEN"));
                configuration.setAllowCredentials(true);
                configuration.setExposedHeaders(List.of("Authorization"));
                configuration.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        @Bean
        @Order(1)
        SecurityFilterChain statefulFilterChain(HttpSecurity http) throws Exception {
                RequestMatcher statefulEndpoints = new OrRequestMatcher(
                                AntPathRequestMatcher.antMatcher("/"),
                                AntPathRequestMatcher.antMatcher("/images/**"),
                                AntPathRequestMatcher.antMatcher("/css/**"),
                                AntPathRequestMatcher.antMatcher("/favicon.ico"),
                                AntPathRequestMatcher.antMatcher("/login/**"),
                                AntPathRequestMatcher.antMatcher("/oauth2/**"),
                                AntPathRequestMatcher.antMatcher("/error"),
                                AntPathRequestMatcher.antMatcher("/api/auth/token"));

                http.securityMatcher(statefulEndpoints).authorizeHttpRequests(authz -> authz
                                .requestMatchers(AntPathRequestMatcher.antMatcher("/"),
                                                AntPathRequestMatcher.antMatcher("/images/**"),
                                                AntPathRequestMatcher.antMatcher("/css/**"),
                                                AntPathRequestMatcher.antMatcher("/favicon.ico"),
                                                AntPathRequestMatcher.antMatcher("/login/**"),
                                                AntPathRequestMatcher.antMatcher("/oauth2/**"),
                                                AntPathRequestMatcher.antMatcher("/error"))
                                .permitAll()
                                .requestMatchers(
                                                AntPathRequestMatcher.antMatcher("/api/auth/token"))
                                .authenticated().anyRequest().denyAll())
                                .oauth2Login(oauth2 -> oauth2
                                                .successHandler(oAuth2LoginSuccessHandler))
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(AbstractHttpConfigurer::disable);

                return http.build();
        }

        @Bean
        @Order(2)
        SecurityFilterChain statelessFilterChain(HttpSecurity http) throws Exception {
                JwtDenylistFilter jwtDenylistFilter = new JwtDenylistFilter(tokenRevocationService);

                http.securityMatcher("/api/**").authorizeHttpRequests(authz -> authz
                                .requestMatchers(HttpMethod.POST, "/api/auth/register",
                                                "/api/auth/verify-firebase-token",
                                                "/api/auth/verify-google-token",
                                                "/api/auth/verify-github-code")
                                .permitAll().requestMatchers(HttpMethod.POST, "/api/auth/logout")
                                .authenticated()
                                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/**"))
                                .authenticated().anyRequest().denyAll())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt.decoder(jwtDecoder())))
                                .sessionManagement(session -> session.sessionCreationPolicy(
                                                SessionCreationPolicy.STATELESS))
                                .addFilterAfter(jwtDenylistFilter,
                                                BearerTokenAuthenticationFilter.class)
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(AbstractHttpConfigurer::disable);

                return http.build();
        }
}

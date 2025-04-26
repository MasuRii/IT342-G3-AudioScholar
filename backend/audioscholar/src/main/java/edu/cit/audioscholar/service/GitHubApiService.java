package edu.cit.audioscholar.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class GitHubApiService {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiService.class);
    private static final String GITHUB_CACHE = "githubApiResults";

    private final WebClient webClient;

    @Value("${github.api.url.user:https://api.github.com/user}")
    private String githubUserUrl;

    @Value("${github.api.url.emails:https://api.github.com/user/emails}")
    private String githubEmailsUrl;

    @Autowired
    public GitHubApiService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.github.com").build();
    }

    public record GitHubUser(Long id, String login, String name, String avatarUrl) {
        public String nameOrLogin() {
            return (name != null && !name.isBlank()) ? name : login;
        }
    }


    @Cacheable(value = GITHUB_CACHE, key = "#accessToken", unless = "#result == null")
    public Mono<GitHubUser> fetchUserDetails(String accessToken) {
        Instant start = Instant.now();
        log.info("Fetching GitHub user details from API (cache miss) for token starting with: {}",
                accessToken.substring(0, Math.min(accessToken.length(), 8)));
        return webClient.get().uri(githubUserUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON).retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(userMap -> {
                    Long id = Optional.ofNullable(userMap.get("id"))
                            .map(obj -> ((Number) obj).longValue()).orElse(null);
                    String login = (String) userMap.get("login");
                    String name = (String) userMap.get("name");
                    String avatarUrl = (String) userMap.get("avatar_url");

                    if (id == null || login == null) {
                        log.error(
                                "Essential GitHub user details (ID or Login) missing in API response: {}",
                                userMap);
                        throw new IllegalStateException(
                                "Essential GitHub user details missing from API response.");
                    }
                    Instant end = Instant.now();
                    log.info(
                            "Fetched GitHub user details from API: ID={}, Login={}, Name={}. Duration: {}ms",
                            id, login, name, Duration.between(start, end).toMillis());
                    return new GitHubUser(id, login, name, avatarUrl);
                })
                .doOnError(e -> log.error(
                        "Failed to fetch GitHub user details from API. Duration: {}ms. Error: {}",
                        Duration.between(start, Instant.now()).toMillis(), e.getMessage()));
    }

    @Cacheable(value = GITHUB_CACHE, key = "#accessToken + '-primaryEmail'",
            unless = "#result == null")
    public Mono<String> fetchPrimaryEmail(String accessToken) {
        Instant start = Instant.now();
        log.info("Fetching GitHub primary email from API (cache miss) for token starting with: {}",
                accessToken.substring(0, Math.min(accessToken.length(), 8)));
        return webClient.get().uri(githubEmailsUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON).retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .map(emailsList -> {
                    String primaryEmail = emailsList.stream()
                            .filter(emailMap -> Boolean.TRUE.equals(emailMap.get("primary"))
                                    && Boolean.TRUE.equals(emailMap.get("verified")))
                            .map(emailMap -> (String) emailMap.get("email"))
                            .filter(Objects::nonNull).findFirst().orElse(null);
                    Instant end = Instant.now();
                    if (primaryEmail != null) {
                        log.info("Fetched GitHub primary email from API. Duration: {}ms",
                                Duration.between(start, end).toMillis());
                    } else {
                        log.warn(
                                "Could not find primary verified email in GitHub API response. Duration: {}ms",
                                Duration.between(start, end).toMillis());
                    }
                    return primaryEmail;
                })
                .doOnError(e -> log.error(
                        "Failed to fetch GitHub emails from API. Duration: {}ms. Error: {}",
                        Duration.between(start, Instant.now()).toMillis(), e.getMessage()));
    }
}

package edu.cit.audioscholar.service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import edu.cit.audioscholar.dto.Monitor;
import edu.cit.audioscholar.dto.UptimeRobotResponse;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
public class UptimeRobotService {

        private static final Logger log = LoggerFactory.getLogger(UptimeRobotService.class);
        private final WebClient.Builder webClientBuilder;

        @Value("${uptimerobot.api.key}")
        private String apiKey;

        @Value("${uptimerobot.api.base-url}")
        private String apiBaseUrl;

        private static final String GET_MONITORS_PATH = "/getMonitors";
        private static final String UPTIME_RATIO_DAYS = "7";
        private static final Duration API_TIMEOUT = Duration.ofSeconds(30);
        private static final int MAX_RETRIES = 2;
        private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

        public UptimeRobotService(WebClient.Builder webClientBuilder) {
                this.webClientBuilder = webClientBuilder;
                log.debug("UptimeRobotService constructed. Will use configured apiBaseUrl and apiKey.");
        }

        @Cacheable("uptimeRobotMonitors")
        public List<Monitor> getMonitors() {
                log.info("Fetching monitors from UptimeRobot API (Cache key: uptimeRobotMonitors), Timeout: {}, Retries: {}",
                                API_TIMEOUT, MAX_RETRIES);

                if (apiKey == null || apiKey.isEmpty()
                                || apiKey.equals("YOUR_READ_ONLY_API_KEY_HERE")) {
                        log.error("UptimeRobot API Key is missing or not configured in application.properties.");
                        return Collections.emptyList();
                }
                if (apiBaseUrl == null || apiBaseUrl.isEmpty()) {
                        log.error("UptimeRobot API Base URL is missing or not configured correctly in application.properties.");
                        return Collections.emptyList();
                }

                MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
                formData.add("api_key", apiKey);
                formData.add("format", "json");
                formData.add("custom_uptime_ratios", UPTIME_RATIO_DAYS);

                try {
                        WebClient client = this.webClientBuilder.baseUrl(this.apiBaseUrl)
                                        .defaultHeader(HttpHeaders.CONTENT_TYPE,
                                                        MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                                        .defaultHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
                                        .build();

                        UptimeRobotResponse response = client.post().uri(GET_MONITORS_PATH)
                                        .body(BodyInserters.fromFormData(formData)).retrieve()
                                        .bodyToMono(UptimeRobotResponse.class).timeout(API_TIMEOUT)
                                        .onErrorResume(TimeoutException.class, ex -> {
                                                log.warn("UptimeRobot API call timed out after {}. Returning empty list.",
                                                                API_TIMEOUT);
                                                return Mono.justOrEmpty(null);
                                        })
                                        .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY).filter(
                                                        throwable -> !(throwable instanceof TimeoutException))
                                                        .onRetryExhaustedThrow((retryBackoffSpec,
                                                                        retrySignal) -> {
                                                                log.error("Retries exhausted for UptimeRobot API call. Last error: {}",
                                                                                retrySignal.failure()
                                                                                                .getMessage());
                                                                return retrySignal.failure();
                                                        }))
                                        .block();

                        if (response != null && "ok".equalsIgnoreCase(response.getStat())) {
                                log.debug("Successfully retrieved {} monitors.",
                                                response.getMonitors() != null
                                                                ? response.getMonitors().size()
                                                                : 0);
                                return response.getMonitors() != null ? response.getMonitors()
                                                : Collections.emptyList();
                        } else if (response == null) {
                                log.warn("UptimeRobot API call did not return a response (likely due to timeout).");
                                return Collections.emptyList();
                        } else {
                                log.error("Failed to get monitors from UptimeRobot. Status: {}, Response: {}",
                                                response.getStat(), response);
                                return Collections.emptyList();
                        }
                } catch (Exception e) {
                        log.error("Error calling UptimeRobot API at {}{}: {}", apiBaseUrl,
                                        GET_MONITORS_PATH, e.getMessage(), e);
                        return Collections.emptyList();
                }
        }
}

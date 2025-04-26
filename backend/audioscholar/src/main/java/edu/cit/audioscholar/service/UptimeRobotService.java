package edu.cit.audioscholar.service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
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

@Service
public class UptimeRobotService {

    private static final Logger log = LoggerFactory.getLogger(UptimeRobotService.class);
    private final WebClient.Builder webClientBuilder;

    @Value("${uptimerobot.api.key}")
    private String apiKey;

    @Value("${uptimerobot.api.base-url:https://api.uptimerobot.com/v2}")
    private String apiBaseUrl;

    private static final String GET_MONITORS_PATH = "/getMonitors";
    private static final String UPTIME_RATIO_DAYS = "7";

    public UptimeRobotService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
        log.debug("UptimeRobotService constructed. Initial apiBaseUrl='{}', apiKey configured='{}'",
                apiBaseUrl, (apiKey != null && !apiKey.isEmpty()
                        && !apiKey.equals("YOUR_READ_ONLY_API_KEY_HERE")));
    }

    @Cacheable("uptimeRobotMonitors")
    public List<Monitor> getMonitors() {
        log.info("Fetching monitors from UptimeRobot API (Cache key: uptimeRobotMonitors)");

        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_READ_ONLY_API_KEY_HERE")) {
            log.error(
                    "UptimeRobot API Key is missing or not configured in application.properties.");
            return Collections.emptyList();
        }
        if (apiBaseUrl == null || apiBaseUrl.isEmpty()) {
            log.error(
                    "UptimeRobot API Base URL is missing or not configured correctly when trying to fetch monitors.");
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
                    .defaultHeader(HttpHeaders.CACHE_CONTROL, "no-cache").build();

            UptimeRobotResponse response = client.post().uri(GET_MONITORS_PATH)
                    .body(BodyInserters.fromFormData(formData)).retrieve()
                    .bodyToMono(UptimeRobotResponse.class).timeout(Duration.ofSeconds(10)).block();

            if (response != null && "ok".equalsIgnoreCase(response.getStat())) {
                log.debug("Successfully retrieved {} monitors.",
                        response.getMonitors() != null ? response.getMonitors().size() : 0);
                return response.getMonitors() != null ? response.getMonitors()
                        : Collections.emptyList();
            } else {
                log.error("Failed to get monitors from UptimeRobot. Status: {}, Response: {}",
                        response != null ? response.getStat() : "null", response);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error calling UptimeRobot API at {}/{}: {}", apiBaseUrl, GET_MONITORS_PATH,
                    e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}

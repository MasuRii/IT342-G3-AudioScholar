package edu.cit.audioscholar.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.Map;
import java.util.stream.Collectors;

public class DotenvConfig implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, org.springframework.boot.SpringApplication application) {
        try {
            Dotenv dotenv = Dotenv.configure().load();
            Map<String, String> envVars = dotenv.entries().stream()
                .collect(Collectors.toMap(DotenvEntry::getKey, DotenvEntry::getValue));
            PropertySource<?> propertySource = new MapPropertySource("dotenv", (Map<String, Object>) (Map<?, ?>) envVars);
            environment.getPropertySources().addFirst(propertySource);
        } catch (Exception e) {
            // If .env file doesn't exist or can't be loaded, continue without it
            // This allows the application to run without .env if not present
        }
    }
}
package ru.syncroom.games.service.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Optional adapter for local/free inference services.
 *
 * Expected HTTP contracts:
 * - POST guessUrl body {"imageBase64":"..."} -> {"text":"..."}
 * - POST drawUrl  body {"prompt":"..."}      -> {"imageBase64":"data:image/png;base64,..."}
 */
@Component
@RequiredArgsConstructor
public class LocalRussianInferenceGateway implements GarticInferenceGateway {

    private final RestTemplate restTemplate;

    @Value("${bots.inference.enabled:false}")
    private boolean enabled;

    @Value("${bots.inference.draw-url:}")
    private String drawUrl;

    @Value("${bots.inference.guess-url:}")
    private String guessUrl;

    @Override
    public Optional<String> draw(String phrase) {
        if (!enabled || drawUrl == null || drawUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("prompt", phrase), headers);
            Map<?, ?> response = restTemplate.postForObject(drawUrl, entity, Map.class);
            if (response == null) {
                return Optional.empty();
            }
            Object value = response.get("imageBase64");
            if (value == null) {
                return Optional.empty();
            }
            String image = String.valueOf(value);
            return image.isBlank() ? Optional.empty() : Optional.of(image);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> guess(String imageBase64) {
        if (!enabled || guessUrl == null || guessUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("imageBase64", imageBase64), headers);
            Map<?, ?> response = restTemplate.postForObject(guessUrl, entity, Map.class);
            if (response == null) {
                return Optional.empty();
            }
            Object value = response.get("text");
            if (value == null) {
                return Optional.empty();
            }
            String guess = String.valueOf(value).trim();
            return guess.isBlank() ? Optional.empty() : Optional.of(guess);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}

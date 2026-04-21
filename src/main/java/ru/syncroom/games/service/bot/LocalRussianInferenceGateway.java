package ru.syncroom.games.service.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
@Slf4j
public class LocalRussianInferenceGateway implements GarticInferenceGateway {

    private final RestTemplate restTemplate;

    public LocalRussianInferenceGateway(@Qualifier("inferenceRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${bots.inference.enabled:false}")
    private boolean enabled;

    @Value("${bots.inference.draw-url:}")
    private String drawUrl;

    @Value("${bots.inference.guess-url:}")
    private String guessUrl;

    @Value("${bots.inference.text-url:}")
    private String textUrl;

    @Override
    public Optional<String> draw(String phrase) {
        if (!enabled || drawUrl == null || drawUrl.isBlank()) {
            return Optional.empty();
        }
        int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("prompt", phrase), headers);
                Map<?, ?> response = restTemplate.postForObject(drawUrl, entity, Map.class);
                if (response == null) {
                    log.warn("Bot draw got null response: attempt={}, url={}", attempt, drawUrl);
                    continue;
                }
                Object value = response.get("imageBase64");
                if (value == null) {
                    log.warn("Bot draw response without imageBase64: attempt={}, keys={}", attempt, response.keySet());
                    continue;
                }
                String image = String.valueOf(value);
                if (image.isBlank()) {
                    log.warn("Bot draw returned blank image: attempt={}", attempt);
                    continue;
                }
                return Optional.of(image);
            } catch (Exception e) {
                log.warn("Bot draw failed: attempt={}, phrase='{}', reason={}",
                        attempt, preview(phrase), e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(250L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return Optional.empty();
                    }
                }
            }
        }
        return Optional.empty();
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
        } catch (Exception e) {
            log.warn("Bot guess failed: reason={}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> generatePhrase() {
        return generateText("Придумай короткую смешную фразу для игры Gartic Phone на русском языке. " +
                "Только одна фраза, без пояснений, максимум 8 слов.");
    }

    @Override
    public Optional<String> generateQuiplashAnswer(String promptText) {
        String basePrompt = (promptText == null || promptText.isBlank()) ? "любой странный вопрос" : promptText;
        return generateText("Дай короткий смешной ответ для Quiplash на вопрос: \"" + basePrompt + "\". " +
                "Только ответ без кавычек и пояснений, максимум 12 слов.");
    }

    private Optional<String> generateText(String prompt) {
        if (!enabled || textUrl == null || textUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("prompt", prompt), headers);
            Map<?, ?> response = restTemplate.postForObject(textUrl, entity, Map.class);
            if (response == null) {
                return Optional.empty();
            }
            Object value = response.get("text");
            if (value == null) {
                return Optional.empty();
            }
            String text = String.valueOf(value).trim();
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            log.warn("Bot text generation failed: reason={}", e.getMessage());
            return Optional.empty();
        }
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() > 60 ? t.substring(0, 60) + "..." : t;
    }
}

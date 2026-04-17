package ru.syncroom.games.service.bot;

import java.util.Optional;

public interface GarticInferenceGateway {
    Optional<String> draw(String phrase);

    Optional<String> guess(String imageBase64);
}

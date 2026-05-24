package ru.syncroom.games.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Bot catalog")
class BotCatalogTest {

    @ParameterizedTest
    @CsvSource({
            "GARTIC_DRAWER, GARTIC_PHONE",
            "GARTIC_BOT, GARTIC_PHONE",
            "QUIPLASH_BOT, QUIPLASH",
            "QUIPLASH_JOKER, QUIPLASH",
            "QUIPLASH_VOTER, QUIPLASH"
    })
    @DisplayName("supportedGameTypesFor — детерминированное сопоставление botType → игра")
    void supportedGameTypesFor_mapsBotTypeToGame(String botType, String expectedGame) {
        assertEquals(List.of(expectedGame), GameService.supportedGameTypesFor(botType));
    }

    @Test
    @DisplayName("supportedGameTypesFor — неизвестный тип → пустой список")
    void supportedGameTypesFor_unknownType() {
        assertEquals(List.of(), GameService.supportedGameTypesFor("CHESS_BOT"));
    }
}

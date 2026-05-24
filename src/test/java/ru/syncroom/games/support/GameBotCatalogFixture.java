package ru.syncroom.games.support;

import ru.syncroom.games.domain.BotUser;
import ru.syncroom.games.repository.BotUserRepository;

import java.util.List;

/**
 * Каталог игровых ботов как после Flyway V14 + V16 + V27 (для тестов без Flyway).
 */
public final class GameBotCatalogFixture {

    private GameBotCatalogFixture() {
    }

    public static void seedIfEmpty(BotUserRepository repository) {
        if (repository.count() > 0) {
            return;
        }
        repository.saveAll(List.of(
                bot("DrawBot", "GARTIC_DRAWER", "/static/bots/drawbot.png"),
                bot("WordSmith", "GARTIC_WRITER", "/static/bots/wordsmith.png"),
                bot("SketchGuess", "GARTIC_GUESSER", "/static/bots/sketchguess.png"),
                bot("GarticMate", "GARTIC_BOT", "/static/bots/drawbot.png"),
                bot("QuipMate", "QUIPLASH_BOT", "/static/bots/quipmaster.png"),
                bot("QuipJoker", "QUIPLASH_JOKER", "/static/bots/quipmaster.png"),
                bot("QuipVoter", "QUIPLASH_VOTER", "/static/bots/quipmaster.png")
        ));
    }

    private static BotUser bot(String name, String type, String avatarUrl) {
        return BotUser.builder()
                .name(name)
                .botType(type)
                .avatarUrl(avatarUrl)
                .isActive(true)
                .build();
    }
}

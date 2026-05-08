package ru.syncroom.rooms.seatbot;

import java.util.List;
import java.util.Locale;

/**
 * Default seat bots created when a room is provisioned (matches client ordering by seat x, y).
 */
public enum DefaultSeatBotsByContext {
    STUDY(List.of("STUDY_HELPER", "WORK_FOCUS_BUDDY")),
    WORK(List.of("WORK_FOCUS_BUDDY")),
    SPORT(List.of("SPORT_CHEERLEADER")),
    LEISURE(List.of());

    private final List<String> botTypes;

    DefaultSeatBotsByContext(List<String> botTypes) {
        this.botTypes = List.copyOf(botTypes);
    }

    public List<String> getBotTypes() {
        return botTypes;
    }

    public static List<String> forContext(String context) {
        if (context == null || context.isBlank()) {
            return List.of();
        }
        return switch (context.trim().toLowerCase(Locale.ROOT)) {
            case "study" -> STUDY.botTypes;
            case "work" -> WORK.botTypes;
            case "sport" -> SPORT.botTypes;
            case "leisure" -> LEISURE.botTypes;
            default -> List.of();
        };
    }
}

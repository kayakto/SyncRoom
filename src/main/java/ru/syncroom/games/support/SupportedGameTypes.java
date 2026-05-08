package ru.syncroom.games.support;

import java.util.List;
import java.util.Set;

public final class SupportedGameTypes {

    public static final String QUIPLASH = "QUIPLASH";
    public static final String GARTIC_PHONE = "GARTIC_PHONE";

    private static final Set<String> ALL = Set.of(QUIPLASH, GARTIC_PHONE);

    private SupportedGameTypes() {
    }

    public static boolean isSupported(String gameType) {
        return gameType != null && ALL.contains(gameType);
    }

    public static List<String> allOrdered() {
        return List.of(QUIPLASH, GARTIC_PHONE);
    }

    public static String normalizePathSegment(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase().replace('-', '_');
    }
}

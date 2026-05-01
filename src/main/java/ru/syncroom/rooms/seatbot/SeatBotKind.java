package ru.syncroom.rooms.seatbot;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum SeatBotKind {
    WORK_FOCUS_BUDDY(
            "WORK_FOCUS_BUDDY",
            "Lofi Cat",
            "Тихий компаньон, реагирует на фазы помодоро",
            List.of("WORK", "STUDY"),
            true, false, false
    ),
    STUDY_HELPER(
            "STUDY_HELPER",
            "Учебник Андрюша",
            "Постит цели и лайкает чужие",
            List.of("STUDY"),
            true, true, true
    ),
    SPORT_CHEERLEADER(
            "SPORT_CHEERLEADER",
            "Тренер Витя",
            "Лайкает закрытые таски",
            List.of("SPORT"),
            false, false, true
    );

    private final String typeKey;
    private final String displayName;
    private final String description;
    private final List<String> supportedContextsUpper;
    private final boolean reactsToPomodoro;
    private final boolean createsGoals;
    private final boolean likesGoals;

    public boolean supportsRoomContext(String roomContext) {
        if (roomContext == null) {
            return false;
        }
        String u = roomContext.trim().toUpperCase(Locale.ROOT);
        return supportedContextsUpper.contains(u);
    }

    public static SeatBotKind fromTypeKey(String key) {
        if (key == null) {
            return null;
        }
        String k = key.trim();
        for (SeatBotKind v : values()) {
            if (v.typeKey.equalsIgnoreCase(k)) {
                return v;
            }
        }
        return null;
    }
}

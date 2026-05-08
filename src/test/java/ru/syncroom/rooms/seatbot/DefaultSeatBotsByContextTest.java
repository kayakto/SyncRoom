package ru.syncroom.rooms.seatbot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DefaultSeatBotsByContext")
class DefaultSeatBotsByContextTest {

    @Test
    @DisplayName("STUDY — два типа в фиксированном порядке")
    void studyOrder() {
        assertEquals(List.of("STUDY_HELPER", "WORK_FOCUS_BUDDY"), DefaultSeatBotsByContext.forContext("study"));
        assertEquals(List.of("STUDY_HELPER", "WORK_FOCUS_BUDDY"), DefaultSeatBotsByContext.forContext("STUDY"));
    }

    @Test
    @DisplayName("WORK / SPORT / LEISURE")
    void otherContexts() {
        assertEquals(List.of("WORK_FOCUS_BUDDY"), DefaultSeatBotsByContext.forContext("work"));
        assertEquals(List.of("SPORT_CHEERLEADER"), DefaultSeatBotsByContext.forContext("sport"));
        assertTrue(DefaultSeatBotsByContext.forContext("leisure").isEmpty());
    }

    static Stream<Arguments> blankOrUnknownContexts() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of("   "),
                Arguments.of("unknown"),
                Arguments.of("LEISURE_EXTRA"));
    }

    @ParameterizedTest
    @MethodSource("blankOrUnknownContexts")
    @DisplayName("пустой / неизвестный контекст → пустой список")
    void emptyForUnknown(String raw) {
        assertTrue(DefaultSeatBotsByContext.forContext(raw).isEmpty());
    }

    @Test
    @DisplayName("getBotTypes на enum-константах")
    void enumLists() {
        assertEquals(2, DefaultSeatBotsByContext.STUDY.getBotTypes().size());
        assertEquals(1, DefaultSeatBotsByContext.WORK.getBotTypes().size());
        assertEquals(1, DefaultSeatBotsByContext.SPORT.getBotTypes().size());
        assertTrue(DefaultSeatBotsByContext.LEISURE.getBotTypes().isEmpty());
    }
}

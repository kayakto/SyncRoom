package ru.syncroom.rooms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.seat-bots")
public class SeatBotProperties {

    /**
     * Максимум seat-ботов в одной комнате (MVP).
     */
    private int maxPerRoom = 3;

    /**
     * Вероятность за фазу WORK, что STUDY_HELPER поставит лайк чужой открытой цели (0.0–1.0).
     */
    private double studyHelperWorkLikeProbability = 0.3;
}

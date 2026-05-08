package ru.syncroom.games.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.syncroom.games.dto.GameTypeConfigDto;
import ru.syncroom.games.support.SupportedGameTypes;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "GamesConfig", description = "Публичные настройки типов игр")
@RestController
public class GamesConfigController {

    @GetMapping("/api/games/config")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Лимиты игроков и оценка длительности по типу игры")
    public Map<String, GameTypeConfigDto> gameConfig() {
        Map<String, GameTypeConfigDto> m = new LinkedHashMap<>();
        m.put(SupportedGameTypes.QUIPLASH, GameTypeConfigDto.builder()
                .minPlayers(3)
                .maxPlayers(8)
                .estimatedDurationMin(10)
                .build());
        m.put(SupportedGameTypes.GARTIC_PHONE, GameTypeConfigDto.builder()
                .minPlayers(4)
                .maxPlayers(8)
                .estimatedDurationMin(15)
                .build());
        return m;
    }
}

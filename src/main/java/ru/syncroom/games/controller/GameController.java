package ru.syncroom.games.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.syncroom.games.dto.CreateGameRequest;
import ru.syncroom.games.dto.GameResponse;
import ru.syncroom.games.service.GameService;
import ru.syncroom.users.domain.User;

import java.util.UUID;

/**
 * REST API мини-игр в комнате.
 * <p>
 * WebSocket: подписка {@code /topic/game/{gameId}} — события лобби и игры
 * ({@code PLAYER_READY}, {@code PLAYER_UNREADY}, {@code PLAYER_LEFT}, {@code GAME_STARTED},
 * {@code GAME_FINISHED}, {@code PLAYER_KICKED} и др.).
 */
@Tag(name = "Games", description = "Мини-игры в комнате: лобби, готовность, старт; синхронизация по WebSocket")
@RestController
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @Operation(
            summary = "Создать игру",
            description = "Создаёт сессию в комнате (лобби). Только участник комнаты; в комнате не должно быть другой незавершённой игры. " +
                    "Клиент подписывается на /topic/game/{gameId} для событий."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Игра создана",
                    content = @Content(schema = @Schema(implementation = GameResponse.class))),
            @ApiResponse(responseCode = "400", description = "Пользователь не в комнате или уже есть активная игра"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Комната не найдена")
    })
    @PostMapping("/api/rooms/{roomId}/games")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "Bearer Authentication")
    public GameResponse createGame(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateGameRequest request
    ) {
        return gameService.createGame(roomId, currentUser.getId(), request.getGameType());
    }

    @Operation(
            summary = "Текущая игра в комнате",
            description = "Возвращает активную (незавершённую) сессию для комнаты."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Игра найдена",
                    content = @Content(schema = @Schema(implementation = GameResponse.class))),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Нет активной игры")
    })
    @GetMapping("/api/rooms/{roomId}/games/current")
    @SecurityRequirement(name = "Bearer Authentication")
    public GameResponse getCurrent(@PathVariable UUID roomId) {
        return gameService.getCurrent(roomId);
    }

    @Operation(
            summary = "Готов к игре",
            description = "Отмечает участника готовым в лобби; при необходимости добавляет его в список игроков."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Готовность обновлена"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Игра или пользователь не найдены")
    })
    @PostMapping("/api/games/{gameId}/ready")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirement(name = "Bearer Authentication")
    public void ready(
            @PathVariable UUID gameId,
            @AuthenticationPrincipal User currentUser
    ) {
        gameService.markReady(gameId, currentUser.getId());
    }

    @Operation(
            summary = "Не готов",
            description = "Снимает готовность в лобби (только пока игра в статусе LOBBY)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Готовность снята"),
            @ApiResponse(responseCode = "400", description = "Игра не в фазе лобби"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Игра или игрок не найдены")
    })
    @PostMapping("/api/games/{gameId}/unready")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirement(name = "Bearer Authentication")
    public void unready(
            @PathVariable UUID gameId,
            @AuthenticationPrincipal User currentUser
    ) {
        gameService.markUnready(gameId, currentUser.getId());
    }

    @Operation(
            summary = "Покинуть лобби",
            description = "Выход из лобби: игрок удаляется из сессии. Доступно только в LOBBY."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Игрок вышел из лобби"),
            @ApiResponse(responseCode = "400", description = "Игра уже началась или завершена"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Игра или игрок не найдены")
    })
    @PostMapping("/api/games/{gameId}/leave")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirement(name = "Bearer Authentication")
    public void leaveLobby(
            @PathVariable UUID gameId,
            @AuthenticationPrincipal User currentUser
    ) {
        gameService.leaveLobby(gameId, currentUser.getId());
    }

    @Operation(
            summary = "Запустить игру",
            description = "Переход из лобби в игру: требуется минимум три готовых игрока; неготовые удаляются с событием PLAYER_KICKED."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Игра запущена"),
            @ApiResponse(responseCode = "400", description = "Недостаточно готовых игроков"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Игра не найдена")
    })
    @PostMapping("/api/games/{gameId}/start")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirement(name = "Bearer Authentication")
    public void start(@PathVariable UUID gameId) {
        gameService.startGame(gameId);
    }
}

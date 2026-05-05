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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.syncroom.games.dto.AddBotRequest;
import ru.syncroom.games.dto.BotInfoResponse;
import ru.syncroom.games.dto.CreateGameRequest;
import ru.syncroom.games.dto.GarticDrawingUploadResponse;
import ru.syncroom.games.dto.GameResponse;
import ru.syncroom.games.service.GameService;
import ru.syncroom.games.service.GarticDrawingServeResult;
import ru.syncroom.users.domain.User;

import java.net.URI;
import java.util.List;
import java.util.Map;
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

    @Operation(
            summary = "Остановить игру",
            description = "Принудительно завершает текущую игру в комнате. Доступно участнику комнаты (обычно хосту теста)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Игра остановлена"),
            @ApiResponse(responseCode = "400", description = "Пользователь не участник комнаты"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Игра не найдена")
    })
    @PostMapping("/api/games/{gameId}/stop")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirement(name = "Bearer Authentication")
    public void stop(
            @PathVariable UUID gameId,
            @AuthenticationPrincipal User currentUser
    ) {
        gameService.stopGame(gameId, currentUser.getId());
    }

    @Operation(
            summary = "Загрузить PNG для Gartic Phone",
            description = "Принимает файл PNG (multipart field `file`). Возвращает drawingAssetId и imageUrl. " +
                    "В WebSocket SUBMIT_DRAWING передайте {\"drawingAssetId\": \"...\"} без base64. " +
                    "Скачать картинку: GET imageUrl с тем же JWT."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Файл сохранён",
                    content = @Content(schema = @Schema(implementation = GarticDrawingUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Не PNG, слишком большой файл или игра не в фазе рисования"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Игра не найдена")
    })
    @PostMapping(value = "/api/games/{gameId}/gartic/drawings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "Bearer Authentication")
    public GarticDrawingUploadResponse uploadGarticDrawing(
            @PathVariable UUID gameId,
            @AuthenticationPrincipal User currentUser,
            @RequestPart("file") MultipartFile file
    ) throws Exception {
        Map<String, String> map = gameService.uploadGarticDrawing(gameId, currentUser.getId(), file.getBytes());
        return GarticDrawingUploadResponse.builder()
                .drawingAssetId(map.get("drawingAssetId"))
                .imageUrl(map.get("imageUrl"))
                .build();
    }

    @Operation(
            summary = "Скачать PNG шага Gartic",
            description = "Доступно участникам комнаты. Используйте imageUrl из STEP_GUESS / REVEAL_CHAIN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PNG"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Игра или файл не найдены")
    })
    @GetMapping("/api/games/{gameId}/gartic/drawings/{assetId}")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<?> downloadGarticDrawing(
            @PathVariable UUID gameId,
            @PathVariable UUID assetId,
            @AuthenticationPrincipal User currentUser
    ) {
        GarticDrawingServeResult r = gameService.serveGarticDrawing(gameId, currentUser.getId(), assetId);
        if (r.redirectUrl().isPresent()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(r.redirectUrl().get()))
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(r.body());
    }

    @Operation(
            summary = "Добавить бота в лобби",
            description = "Добавляет одного или несколько активных ботов выбранного типа в лобби игры " +
                    "(GARTIC_PHONE — типы GARTIC_*; QUIPLASH — типы QUIPLASH_*)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Боты добавлены",
                    content = @Content(schema = @Schema(implementation = GameResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос или игра не в LOBBY"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Игра или бот не найдены")
    })
    @PostMapping("/api/games/{gameId}/bots/add")
    @SecurityRequirement(name = "Bearer Authentication")
    public GameResponse addBot(
            @PathVariable UUID gameId,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody AddBotRequest request
    ) {
        return gameService.addBots(gameId, currentUser.getId(), request.getBotType(), request.getCount());
    }

    @Operation(
            summary = "Убрать бота из лобби",
            description = "Удаляет конкретного бота из лобби игры."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Бот удалён",
                    content = @Content(schema = @Schema(implementation = GameResponse.class))),
            @ApiResponse(responseCode = "400", description = "Игра не в LOBBY"),
            @ApiResponse(responseCode = "401", description = "Не авторизован"),
            @ApiResponse(responseCode = "404", description = "Игра или бот не найдены")
    })
    @DeleteMapping("/api/games/{gameId}/bots/{botId}")
    @SecurityRequirement(name = "Bearer Authentication")
    public GameResponse removeBot(
            @PathVariable UUID gameId,
            @PathVariable UUID botId,
            @AuthenticationPrincipal User currentUser
    ) {
        return gameService.removeBot(gameId, currentUser.getId(), botId);
    }

    @Operation(
            summary = "Список доступных ботов",
            description = "Возвращает активных ботов, доступных для добавления в лобби."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список получен"),
            @ApiResponse(responseCode = "401", description = "Не авторизован")
    })
    @GetMapping("/api/bots/available")
    @SecurityRequirement(name = "Bearer Authentication")
    public List<BotInfoResponse> availableBots() {
        return gameService.getAvailableBots();
    }
}

package ru.syncroom.games.controller;

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

@RestController
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @PostMapping("/api/rooms/{roomId}/games")
    @ResponseStatus(HttpStatus.CREATED)
    public GameResponse createGame(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateGameRequest request
    ) {
        return gameService.createGame(roomId, currentUser.getId(), request.getGameType());
    }

    @GetMapping("/api/rooms/{roomId}/games/current")
    public GameResponse getCurrent(@PathVariable UUID roomId) {
        return gameService.getCurrent(roomId);
    }

    @PostMapping("/api/games/{gameId}/ready")
    @ResponseStatus(HttpStatus.OK)
    public void ready(
            @PathVariable UUID gameId,
            @AuthenticationPrincipal User currentUser
    ) {
        gameService.markReady(gameId, currentUser.getId());
    }

    @PostMapping("/api/games/{gameId}/unready")
    @ResponseStatus(HttpStatus.OK)
    public void unready(
            @PathVariable UUID gameId,
            @AuthenticationPrincipal User currentUser
    ) {
        gameService.markUnready(gameId, currentUser.getId());
    }

    @PostMapping("/api/games/{gameId}/leave")
    @ResponseStatus(HttpStatus.OK)
    public void leaveLobby(
            @PathVariable UUID gameId,
            @AuthenticationPrincipal User currentUser
    ) {
        gameService.leaveLobby(gameId, currentUser.getId());
    }

    @PostMapping("/api/games/{gameId}/start")
    @ResponseStatus(HttpStatus.OK)
    public void start(@PathVariable UUID gameId) {
        gameService.startGame(gameId);
    }
}


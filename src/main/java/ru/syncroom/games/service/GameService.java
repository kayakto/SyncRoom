package ru.syncroom.games.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.LazyInitializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.games.domain.*;
import ru.syncroom.games.dto.GameActionMessage;
import ru.syncroom.games.dto.BotInfoResponse;
import ru.syncroom.games.dto.GamePlayerDto;
import ru.syncroom.games.dto.GameResponse;
import ru.syncroom.games.repository.*;
import ru.syncroom.games.service.bot.GarticInferenceGateway;
import ru.syncroom.games.websocket.GameEventSender;
import ru.syncroom.games.websocket.GameTimerService;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final GameSessionRepository gameSessionRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final BotUserRepository botUserRepository;
    private final QuiplashPromptRepository promptRepository;
    private final QuiplashAnswerRepository answerRepository;
    private final QuiplashVoteRepository voteRepository;
    private final PromptBankRepository promptBankRepository;
    private final GarticChainRepository garticChainRepository;
    private final GarticStepRepository garticStepRepository;
    private final RoomRepository roomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final UserRepository userRepository;
    private final GameEventSender eventSender;
    private final GameTimerService gameTimerService;
    private final GarticInferenceGateway garticInferenceGateway;
    private final GameTraceLogger gameTraceLogger;
    private final GarticDrawingAssetStorage garticDrawingAssetStorage;
    private static final int MIN_READY_PLAYERS_TO_START = 3;
    private static final int LOBBY_DISCONNECT_UNREADY_DELAY_SEC = 10;
    private static final int TOTAL_ROUNDS = 3;
    private static final int GARTIC_IMAGE_MAX_BYTES = 2 * 1024 * 1024;
    private static final int BOT_STEP_MAX_RETRIES = 3;
    private static final String DEFAULT_TEXT = "...";
    private static final String WHITE_PNG_BASE64 = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMBAAZ9l1cAAAAASUVORK5CYII=";
    @Value("${games.bot.schedule-after-commit:true}")
    private boolean scheduleBotAfterCommit;

    @Value("${games.gartic.text-timeout-sec:60}")
    private int garticTextTimeoutSec;

    @Value("${games.gartic.draw-timeout-sec:180}")
    private int garticDrawTimeoutSec;

    @Value("${games.bot.draw-model-timeout-sec:150}")
    private int botDrawModelTimeoutSec;

    private final Map<UUID, Object> garticLocks = new ConcurrentHashMap<>();

    @Transactional
    public GameResponse createGame(UUID roomId, UUID creatorId, String gameType) {
        var room = roomRepository.findById(roomId).orElseThrow(() -> new NotFoundException("Room not found"));
        if (!roomParticipantRepository.existsByRoomIdAndUserId(roomId, creatorId)) {
            throw new BadRequestException("User is not a participant of this room");
        }
        if (gameSessionRepository.findFirstByRoomIdAndStatusNotOrderByCreatedAtDesc(roomId, "FINISHED").isPresent()) {
            throw new BadRequestException("Active game already exists in this room");
        }

        User creator = userRepository.findById(creatorId).orElseThrow(() -> new NotFoundException("User not found"));
        GameSession session = gameSessionRepository.save(GameSession.builder()
                .room(room)
                .gameType(gameType)
                .status("LOBBY")
                .build());

        gamePlayerRepository.save(GamePlayer.builder()
                .game(session)
                .user(creator)
                .isReady(false)
                .score(0)
                .build());

        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public GameResponse getCurrent(UUID roomId) {
        GameSession session = gameSessionRepository.findFirstByRoomIdAndStatusNotOrderByCreatedAtDesc(roomId, "FINISHED")
                .orElseThrow(() -> new NotFoundException("No active game"));
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public List<BotInfoResponse> getAvailableBots() {
        return botUserRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(bot -> BotInfoResponse.builder()
                        .id(bot.getId().toString())
                        .name(bot.getName())
                        .avatarUrl(bot.getAvatarUrl())
                        .botType(bot.getBotType())
                        .build())
                .toList();
    }

    @Transactional
    public GameResponse addBots(UUID gameId, UUID requesterId, String botType, int count) {
        GameSession game = gameSessionRepository.findById(gameId).orElseThrow(() -> new NotFoundException("Game not found"));
        if (!"LOBBY".equals(game.getStatus())) {
            throw new BadRequestException("Bots can be added only in LOBBY");
        }
        if ("GARTIC_PHONE".equals(game.getGameType()) && !botType.startsWith("GARTIC_")) {
            throw new BadRequestException("GARTIC game accepts only GARTIC_* bot types");
        }
        if ("QUIPLASH".equals(game.getGameType()) && !botType.startsWith("QUIPLASH_")) {
            throw new BadRequestException("QUIPLASH game accepts only QUIPLASH_* bot types");
        }
        if (!"GARTIC_PHONE".equals(game.getGameType()) && !"QUIPLASH".equals(game.getGameType())) {
            throw new BadRequestException("Bots are not supported for this game type");
        }
        if (!roomParticipantRepository.existsByRoomIdAndUserId(game.getRoom().getId(), requesterId)) {
            throw new BadRequestException("User is not a participant of this room");
        }
        List<BotUser> candidates = botUserRepository.findByBotTypeAndIsActiveTrueOrderByNameAsc(botType);
        if (candidates.isEmpty()) {
            BotUser generated = botUserRepository.save(BotUser.builder()
                    .name(defaultBotName(botType))
                    .avatarUrl("/static/bots/default.png")
                    .botType(botType)
                    .isActive(true)
                    .build());
            candidates = List.of(generated);
        }
        for (int i = 0; i < count; i++) {
            BotUser template = candidates.get(i % candidates.size());
            BotUser selected = template;
            if (gamePlayerRepository.existsByGameIdAndBotUserId(gameId, selected.getId())) {
                // Ensure requested count is respected even when template bot is already in lobby.
                selected = botUserRepository.save(BotUser.builder()
                        .name(template.getName())
                        .avatarUrl(template.getAvatarUrl())
                        .botType(template.getBotType())
                        .isActive(true)
                        .config(template.getConfig())
                        .build());
            }
            GamePlayer botPlayer = gamePlayerRepository.save(GamePlayer.builder()
                    .game(game)
                    .botUser(selected)
                    .isReady(true)
                    .score(0)
                    .build());
            eventSender.sendToGame(gameId, "BOT_ADDED", Map.of(
                    "botId", selected.getId().toString(),
                    "name", selected.getName(),
                    "avatarUrl", selected.getAvatarUrl()
            ));
            eventSender.sendToGame(gameId, "PLAYER_READY", Map.of("userId", participantId(botPlayer).toString()));
        }
        return toResponse(game);
    }

    @Transactional
    public GameResponse removeBot(UUID gameId, UUID requesterId, UUID botId) {
        GameSession game = gameSessionRepository.findById(gameId).orElseThrow(() -> new NotFoundException("Game not found"));
        if (!"LOBBY".equals(game.getStatus())) {
            throw new BadRequestException("Bots can be removed only in LOBBY");
        }
        if (!roomParticipantRepository.existsByRoomIdAndUserId(game.getRoom().getId(), requesterId)) {
            throw new BadRequestException("User is not a participant of this room");
        }
        GamePlayer botPlayer = gamePlayerRepository.findByGameIdAndBotUserId(gameId, botId)
                .orElseThrow(() -> new NotFoundException("Bot is not in lobby"));
        gamePlayerRepository.delete(botPlayer);
        eventSender.sendToGame(gameId, "BOT_REMOVED", Map.of("botId", botId.toString()));
        return toResponse(game);
    }

    @Transactional
    public void markReady(UUID gameId, UUID userId) {
        cancelDisconnectUnreadyTimer(gameId, userId);
        GameSession game = gameSessionRepository.findById(gameId).orElseThrow(() -> new NotFoundException("Game not found"));
        GamePlayer player = gamePlayerRepository.findByGameIdAndUserId(gameId, userId).orElseGet(() -> {
            User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
            return gamePlayerRepository.save(GamePlayer.builder()
                    .game(game)
                    .user(user)
                    .isReady(false)
                    .score(0)
                    .build());
        });
        player.setIsReady(true);
        gamePlayerRepository.save(player);
        eventSender.sendToGame(gameId, "PLAYER_READY", Map.of("userId", userId.toString()));
    }

    @Transactional
    public void markUnready(UUID gameId, UUID userId) {
        cancelDisconnectUnreadyTimer(gameId, userId);
        GameSession game = gameSessionRepository.findById(gameId).orElseThrow(() -> new NotFoundException("Game not found"));
        if (!"LOBBY".equals(game.getStatus())) {
            throw new BadRequestException("Cannot change ready state: game is not in LOBBY");
        }
        GamePlayer player = gamePlayerRepository.findByGameIdAndUserId(gameId, userId)
                .orElseThrow(() -> new NotFoundException("Player not found"));
        player.setIsReady(false);
        gamePlayerRepository.save(player);
        eventSender.sendToGame(gameId, "PLAYER_UNREADY", Map.of("userId", userId.toString()));
    }

    /**
     * Выход из лобби игры (только LOBBY). Удаляет игрока; если игроков не осталось — сессия удаляется.
     */
    @Transactional
    public void leaveLobby(UUID gameId, UUID userId) {
        GameSession game = gameSessionRepository.findById(gameId).orElseThrow(() -> new NotFoundException("Game not found"));
        if (!"LOBBY".equals(game.getStatus())) {
            throw new BadRequestException("Cannot leave lobby: game already started or finished");
        }
        removePlayerFromLobby(game, userId);
    }

    /**
     * Вызывается при выходе пользователя из комнаты: убрать из лобби или прервать игру в процессе.
     */
    @Transactional
    public void onParticipantLeftRoom(UUID roomId, UUID userId) {
        gameSessionRepository.findFirstByRoomIdAndStatusNotOrderByCreatedAtDesc(roomId, "FINISHED")
                .ifPresent(game -> {
                    if ("LOBBY".equals(game.getStatus())) {
                        removePlayerFromLobbyIfPresent(game.getId(), userId);
                    } else if ("IN_PROGRESS".equals(game.getStatus())
                            && gamePlayerRepository.existsByGameIdAndUserId(game.getId(), userId)) {
                        abortGameInProgress(game.getId());
                    }
                });
    }

    private void removePlayerFromLobby(GameSession game, UUID userId) {
        cancelDisconnectUnreadyTimer(game.getId(), userId);
        GamePlayer player = gamePlayerRepository.findByGameIdAndUserId(game.getId(), userId)
                .orElseThrow(() -> new NotFoundException("Player not found"));
        gamePlayerRepository.delete(player);
        eventSender.sendToGame(game.getId(), "PLAYER_LEFT", Map.of("userId", userId.toString()));
        finalizeLobbyIfEmpty(game.getId());
    }

    private void removePlayerFromLobbyIfPresent(UUID gameId, UUID userId) {
        cancelDisconnectUnreadyTimer(gameId, userId);
        gamePlayerRepository.findByGameIdAndUserId(gameId, userId).ifPresent(player -> {
            gamePlayerRepository.delete(player);
            eventSender.sendToGame(gameId, "PLAYER_LEFT", Map.of("userId", userId.toString()));
            finalizeLobbyIfEmpty(gameId);
        });
    }

    private void finalizeLobbyIfEmpty(UUID gameId) {
        if (gamePlayerRepository.countByGameId(gameId) == 0) {
            gameSessionRepository.findById(gameId).ifPresent(gameSessionRepository::delete);
        }
    }

    private void abortGameInProgress(UUID gameId) {
        gameTimerService.cancelAllForGame(gameId);
        GameSession game = gameSessionRepository.findById(gameId).orElse(null);
        if (game == null) {
            return;
        }
        eventSender.sendToGame(gameId, "GAME_CANCELLED", Map.of("reason", "PLAYER_LEFT_ROOM"));
        gameSessionRepository.delete(game);
    }

    @Transactional
    public void startGame(UUID gameId) {
        GameSession game = gameSessionRepository.findById(gameId).orElseThrow(() -> new NotFoundException("Game not found"));
        List<GamePlayer> players = gamePlayerRepository.findByGameId(gameId);
        List<GamePlayer> readyPlayers = players.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsReady()))
                .toList();
        if (readyPlayers.size() < MIN_READY_PLAYERS_TO_START) {
            throw new BadRequestException("Need at least " + MIN_READY_PLAYERS_TO_START + " ready players");
        }

        List<GamePlayer> unreadyPlayers = players.stream()
                .filter(p -> !Boolean.TRUE.equals(p.getIsReady()))
                .toList();
        for (GamePlayer unready : unreadyPlayers) {
            if (isHuman(unready)) {
                cancelDisconnectUnreadyTimer(gameId, unready.getUser().getId());
            }
            gamePlayerRepository.delete(unready);
            if (isHuman(unready)) {
                eventSender.sendToPlayer(gameId, unready.getUser().getId(), "PLAYER_KICKED", Map.of(
                        "userId", unready.getUser().getId().toString(),
                        "reason", "Игра началась без вас — вы не были готовы"
                ));
            }
        }

        game.setStatus("IN_PROGRESS");
        gameSessionRepository.save(game);

        List<Map<String, Object>> playersPayload = readyPlayers.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", participantId(p).toString());
            m.put("name", participantName(p));
            m.put("avatarUrl", participantAvatar(p));
            m.put("isBot", isBot(p));
            return m;
        }).toList();
        eventSender.sendToGame(gameId, "GAME_STARTED", Map.of("players", playersPayload));

        if ("GARTIC_PHONE".equals(game.getGameType())) {
            startGarticGame(game, readyPlayers);
            return;
        }
        startRound(game, 1);
    }

    @Transactional
    public void stopGame(UUID gameId, UUID requesterId) {
        GameSession game = gameSessionRepository.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Game not found"));
        if (!roomParticipantRepository.existsByRoomIdAndUserId(game.getRoom().getId(), requesterId)) {
            throw new BadRequestException("User is not a participant of this room");
        }
        if ("FINISHED".equals(game.getStatus())) {
            return;
        }
        gameTimerService.cancelAllForGame(gameId);
        game.setStatus("FINISHED");
        game.setFinishedAt(java.time.OffsetDateTime.now());
        gameSessionRepository.save(game);
        eventSender.sendToGame(gameId, "GAME_STOPPED", Map.of("byUserId", requesterId.toString()));
        eventSender.sendToGame(gameId, "GAME_FINISHED", Map.of("scores", List.of()));
        gameTraceLogger.trace(gameId, "GAME_STOPPED byUserId=" + requesterId);
    }

    @Transactional
    public void handleAction(UUID gameId, UUID userId, GameActionMessage msg) {
        cancelDisconnectUnreadyTimer(gameId, userId);
        String type = msg.getType();
        gameTraceLogger.trace(gameId, "ACTION_RECEIVED userId=" + userId + " type=" + type + " payload=" + payloadPreview(msg.getPayload()));
        if ("PLAYER_READY".equals(type)) {
            markReady(gameId, userId);
            return;
        }
        if ("PLAYER_UNREADY".equals(type)) {
            markUnready(gameId, userId);
            return;
        }
        if ("PLAYER_LEAVE_LOBBY".equals(type)) {
            leaveLobby(gameId, userId);
            return;
        }
        if ("SUBMIT_ANSWER".equals(type)) {
            submitAnswer(gameId, userId, String.valueOf(msg.getPayload().getOrDefault("text", "...")));
            sendActionAccepted(gameId, userId, "SUBMIT_ANSWER", msg.getPayload());
            return;
        }
        if ("SUBMIT_VOTE".equals(type)) {
            submitVote(gameId, userId, UUID.fromString(String.valueOf(msg.getPayload().get("answerId"))));
            sendActionAccepted(gameId, userId, "SUBMIT_VOTE", msg.getPayload());
            return;
        }
        if ("SUBMIT_PHRASE".equals(type)) {
            submitGarticStep(gameId, resolveHumanPlayer(gameId, userId), "TEXT",
                    String.valueOf(msg.getPayload().getOrDefault("text", DEFAULT_TEXT)));
            sendActionAccepted(gameId, userId, "SUBMIT_PHRASE", msg.getPayload());
            return;
        }
        if ("SUBMIT_GUESS".equals(type)) {
            submitGarticStep(gameId, resolveHumanPlayer(gameId, userId), "TEXT",
                    String.valueOf(msg.getPayload().getOrDefault("text", DEFAULT_TEXT)));
            sendActionAccepted(gameId, userId, "SUBMIT_GUESS", msg.getPayload());
            return;
        }
        if ("SUBMIT_DRAWING".equals(type)) {
            GamePlayer drawer = resolveHumanPlayer(gameId, userId);
            Map<String, Object> pl = msg.getPayload();
            Object assetRaw = pl != null ? pl.get("drawingAssetId") : null;
            String drawingPayload;
            if (assetRaw != null && !String.valueOf(assetRaw).isBlank()) {
                UUID aid = UUID.fromString(String.valueOf(assetRaw).trim());
                if (!garticDrawingAssetStorage.exists(gameId, aid)) {
                    throw new BadRequestException("Drawing asset not found");
                }
                drawingPayload = GarticDrawingAssetStorage.ASSET_PREFIX + aid;
            } else {
                String b64 = pl == null ? "" : String.valueOf(pl.getOrDefault("imageBase64", ""));
                drawingPayload = b64.isBlank() ? WHITE_PNG_BASE64 : b64;
            }
            submitGarticStep(gameId, drawer, "DRAWING", drawingPayload);
            sendActionAccepted(gameId, userId, "SUBMIT_DRAWING", msg.getPayload());
        }
    }

    private void sendActionAccepted(UUID gameId, UUID userId, String actionType, Map<String, Object> payload) {
        Map<String, Object> ack = new HashMap<>();
        ack.put("actionType", actionType);
        if (payload != null) {
            Object text = payload.get("text");
            if (text instanceof String s && !s.isBlank()) {
                ack.put("textPreview", s.substring(0, Math.min(40, s.length())));
            }
            Object image = payload.get("imageBase64");
            if (image instanceof String s && !s.isBlank()) {
                ack.put("imageSize", s.length());
            }
            Object drawingAsset = payload.get("drawingAssetId");
            if (drawingAsset != null && !String.valueOf(drawingAsset).isBlank()) {
                ack.put("drawingAssetId", String.valueOf(drawingAsset));
            }
            Object answerId = payload.get("answerId");
            if (answerId != null) {
                ack.put("answerId", String.valueOf(answerId));
            }
        }
        eventSender.sendToPlayer(gameId, userId, "ACTION_ACCEPTED", ack);
        gameTraceLogger.trace(gameId, "ACTION_ACCEPTED userId=" + userId + " actionType=" + actionType + " ack=" + ack);
    }

    private String payloadPreview(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        Map<String, Object> compact = new LinkedHashMap<>();
        payload.forEach((k, v) -> {
            if (v instanceof String s) {
                compact.put(k, s.length() > 80 ? s.substring(0, 80) + "...(" + s.length() + ")" : s);
            } else {
                compact.put(k, v);
            }
        });
        return compact.toString();
    }

    private GamePlayer resolveHumanPlayer(UUID gameId, UUID userId) {
        return gamePlayerRepository.findByGameIdAndUserId(gameId, userId)
                .orElseThrow(() -> new NotFoundException("Player not found"));
    }

    private void submitAnswer(UUID gameId, UUID userId, String text) {
        QuiplashPrompt prompt = promptRepository.findFirstByGameIdOrderByRoundDesc(gameId)
                .orElseThrow(() -> new NotFoundException("Prompt not found"));
        GamePlayer player = resolveHumanPlayer(gameId, userId);
        submitAnswerForPlayer(prompt, player, text, true);
    }

    private void submitAnswerForPlayer(QuiplashPrompt prompt, GamePlayer player, String text, boolean notifyIfWaiting) {
        UUID gameId = prompt.getGame().getId();
        boolean created = false;
        if (answerRepository.findByPromptIdAndPlayerId(prompt.getId(), player.getId()).isEmpty()) {
            answerRepository.save(QuiplashAnswer.builder()
                    .prompt(prompt)
                    .player(player)
                    .text(text == null || text.isBlank() ? "..." : text)
                    .votes(0)
                    .build());
            created = true;
        }
        long answersCount = answerRepository.countByPromptId(prompt.getId());
        long playersCount = gamePlayerRepository.countByGameId(gameId);
        // Open voting only once when the last missing answer is actually created.
        if (created && answersCount >= playersCount) {
            openVoting(prompt);
        } else if (notifyIfWaiting && isHuman(player)) {
            eventSender.sendToPlayer(gameId, player.getUser().getId(), "WAITING_FOR_OTHERS", Map.of());
        }
    }

    private void submitVote(UUID gameId, UUID userId, UUID answerId) {
        GamePlayer voter = gamePlayerRepository.findByGameIdAndUserId(gameId, userId)
                .orElseThrow(() -> new NotFoundException("Player not found"));
        submitVoteForPlayer(voter, answerId);
    }

    private void submitVoteForPlayer(GamePlayer voter, UUID answerId) {
        QuiplashAnswer answer = answerRepository.findById(answerId).orElseThrow(() -> new NotFoundException("Answer not found"));
        if (answer.getPlayer().getId().equals(voter.getId())) {
            throw new BadRequestException("You cannot vote for your own answer");
        }
        UUID gameId = voter.getGame().getId();
        UUID promptId = answer.getPrompt().getId();
        if (voteRepository.findByPromptIdAndVoterId(promptId, voter.getId()).isPresent()) {
            return;
        }

        voteRepository.save(QuiplashVote.builder()
                .prompt(answer.getPrompt())
                .voter(voter)
                .answer(answer)
                .build());
        answer.setVotes(answer.getVotes() + 1);
        answerRepository.save(answer);

        long voteCount = voteRepository.countByPromptId(promptId);
        long playersCount = gamePlayerRepository.countByGameId(gameId);
        if (voteCount >= playersCount) {
            finalizeRound(answer.getPrompt());
        }
    }

    /**
     * Случайный промпт из банка, без повторения {@code prompt_bank_id} в рамках одной игры.
     * Если свободных записей не хватает (банк меньше числа раундов), допускается повтор.
     */
    private PromptBank pickQuiplashPromptBankEntry(GameSession game) {
        List<PromptBank> bank = promptBankRepository.findAllByOrderByIdAsc();
        if (bank.isEmpty()) {
            return null;
        }
        Set<UUID> usedBankIds = promptRepository.findByGameIdOrderByRoundAsc(game.getId()).stream()
                .map(QuiplashPrompt::getPromptBankId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        List<PromptBank> available = bank.stream()
                .filter(p -> !usedBankIds.contains(p.getId()))
                .collect(Collectors.toList());
        if (available.isEmpty()) {
            available = bank;
        }
        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }

    private void startRound(GameSession game, int round) {
        PromptBank bankEntry = pickQuiplashPromptBankEntry(game);
        String promptText = bankEntry != null ? bankEntry.getText() : "Промпт раунда " + round;
        UUID promptBankId = bankEntry != null ? bankEntry.getId() : null;
        QuiplashPrompt prompt = promptRepository.save(QuiplashPrompt.builder()
                .game(game)
                .round(round)
                .text(promptText)
                .timeLimit(60)
                .promptBankId(promptBankId)
                .build());
        eventSender.sendToGame(game.getId(), "PROMPT_RECEIVED", Map.of(
                "promptId", prompt.getId().toString(),
                "text", prompt.getText(),
                "timeLimit", 60
        ));
        scheduleQuiplashBotAnswers(prompt);
        gameTimerService.schedule(GameTimerService.answerKey(game.getId(), round), 60, () -> onAnswerTimeout(game.getId(), round));
    }

    private void onAnswerTimeout(UUID gameId, int round) {
        QuiplashPrompt prompt = promptRepository.findByGameIdAndRound(gameId, round).orElse(null);
        if (prompt == null) return;
        List<GamePlayer> players = gamePlayerRepository.findByGameId(gameId);
        for (GamePlayer p : players) {
            if (answerRepository.findByPromptIdAndPlayerId(prompt.getId(), p.getId()).isEmpty()) {
                answerRepository.save(QuiplashAnswer.builder()
                        .prompt(prompt)
                        .player(p)
                        .text("...")
                        .votes(0)
                        .build());
            }
        }
        openVoting(prompt);
    }

    private void openVoting(QuiplashPrompt prompt) {
        UUID gameId = prompt.getGame().getId();
        int round = prompt.getRound();
        gameTimerService.cancel(GameTimerService.answerKey(gameId, round));
        List<Map<String, Object>> answers = answerRepository.findByPromptId(prompt.getId()).stream().map(a -> Map.<String, Object>of(
                "answerId", a.getId().toString(),
                "text", a.getText()
        )).toList();
        eventSender.sendToGame(gameId, "WAITING_FOR_VOTES", Map.of(
                "promptId", prompt.getId().toString(),
                "answers", answers,
                "timeLimit", 30
        ));
        scheduleQuiplashBotVotes(prompt);
        gameTimerService.schedule(GameTimerService.voteKey(gameId, round), 30, () -> onVoteTimeout(gameId, round));
    }

    private void scheduleQuiplashBotAnswers(QuiplashPrompt prompt) {
        UUID gameId = prompt.getGame().getId();
        int round = prompt.getRound();
        for (GamePlayer player : gamePlayerRepository.findByGameId(gameId)) {
            if (!isBot(player)) {
                continue;
            }
            String key = "game:" + gameId + ":bot:quiplash:answer:round:" + round + ":player:" + player.getId();
            int delaySec = ThreadLocalRandom.current().nextInt(1, 4);
            gameTimerService.schedule(key, delaySec, () -> {
                try {
                    submitAnswerForPlayer(prompt, player, botQuiplashAnswer(prompt.getText()), false);
                } catch (Exception e) {
                    log.warn("Quiplash bot answer failed: gameId={}, round={}, botPlayerId={}, reason={}",
                            gameId, round, player.getId(), e.getMessage());
                }
            });
        }
    }

    private void scheduleQuiplashBotVotes(QuiplashPrompt prompt) {
        UUID gameId = prompt.getGame().getId();
        int round = prompt.getRound();
        List<QuiplashAnswer> answers = answerRepository.findByPromptId(prompt.getId());
        for (GamePlayer player : gamePlayerRepository.findByGameId(gameId)) {
            if (!isBot(player)) {
                continue;
            }
            String key = "game:" + gameId + ":bot:quiplash:vote:round:" + round + ":player:" + player.getId();
            int delaySec = ThreadLocalRandom.current().nextInt(1, 4);
            gameTimerService.schedule(key, delaySec, () -> {
                try {
                    List<QuiplashAnswer> choices = answers.stream()
                            .filter(a -> !a.getPlayer().getId().equals(player.getId()))
                            .toList();
                    if (choices.isEmpty()) {
                        return;
                    }
                    QuiplashAnswer selected = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
                    submitVoteForPlayer(player, selected.getId());
                } catch (Exception e) {
                    log.warn("Quiplash bot vote failed: gameId={}, round={}, botPlayerId={}, reason={}",
                            gameId, round, player.getId(), e.getMessage());
                }
            });
        }
    }

    private void onVoteTimeout(UUID gameId, int round) {
        QuiplashPrompt prompt = promptRepository.findByGameIdAndRound(gameId, round).orElse(null);
        if (prompt != null) {
            finalizeRound(prompt);
        }
    }

    private void finalizeRound(QuiplashPrompt prompt) {
        UUID gameId = prompt.getGame().getId();
        int round = prompt.getRound();
        gameTimerService.cancelRoundTimers(gameId, round);

        List<QuiplashAnswer> answers = answerRepository.findByPromptId(prompt.getId());
        int maxVotes = answers.stream().mapToInt(QuiplashAnswer::getVotes).max().orElse(0);
        for (QuiplashAnswer a : answers) {
            int points = a.getVotes() * 100 + (a.getVotes() == maxVotes ? 50 : 0);
            GamePlayer p = a.getPlayer();
            p.setScore(p.getScore() + points);
            gamePlayerRepository.save(p);
        }
        List<Map<String, Object>> results = answers.stream().map(a -> Map.<String, Object>of(
                "answerId", a.getId().toString(),
                "text", a.getText(),
                "authorId", participantId(a.getPlayer()).toString(),
                "authorName", participantName(a.getPlayer()),
                "votes", a.getVotes()
        )).toList();
        List<Map<String, Object>> scores = gamePlayerRepository.findByGameId(gameId).stream().map(p -> Map.<String, Object>of(
                "playerId", participantId(p).toString(),
                "playerName", participantName(p),
                "score", p.getScore()
        )).toList();
        eventSender.sendToGame(gameId, "ROUND_RESULT", Map.of("round", round, "results", results, "scores", scores));

        if (round >= TOTAL_ROUNDS) {
            GameSession game = prompt.getGame();
            game.setStatus("FINISHED");
            game.setFinishedAt(java.time.OffsetDateTime.now());
            gameSessionRepository.save(game);
            eventSender.sendToGame(gameId, "GAME_FINISHED", Map.of("scores", scores));
            return;
        }
        gameTimerService.schedule("game:" + gameId + ":next:" + round, 5, () -> {
            GameSession session = gameSessionRepository.findById(gameId).orElse(null);
            if (session != null && "IN_PROGRESS".equals(session.getStatus())) {
                startRound(session, round + 1);
            }
        });
    }

    private void startGarticGame(GameSession game, List<GamePlayer> players) {
        List<GamePlayer> orderedPlayers = orderedPlayers(players);
        for (GamePlayer player : orderedPlayers) {
            garticChainRepository.save(GarticChain.builder()
                    .game(game)
                    .owner(player)
                    .build());
        }
        sendGarticStepInstructions(game.getId(), orderedPlayers, 1);
        scheduleGarticTimeout(game.getId(), 1);
    }

    private void submitGarticStep(UUID gameId, GamePlayer actor, String submittedType, String submittedContent) {
        GameSession game = gameSessionRepository.findById(gameId).orElseThrow(() -> new NotFoundException("Game not found"));
        if (!"GARTIC_PHONE".equals(game.getGameType())) {
            throw new BadRequestException("This action is only available for GARTIC_PHONE");
        }
        List<GamePlayer> orderedPlayers = orderedPlayers(gamePlayerRepository.findByGameId(gameId));
        int playersCount = orderedPlayers.size();
        int currentStep = detectCurrentGarticStep(gameId, playersCount);
        if (currentStep > playersCount) {
            return;
        }
        String expectedType = expectedGarticType(currentStep);
        if (!expectedType.equals(submittedType)) {
            throw new BadRequestException("Unexpected action for current step");
        }
        int playerIndex = indexOfPlayer(orderedPlayers, actor.getId());
        GarticChain assignedChain = resolveChainForPlayerStep(gameId, orderedPlayers, playerIndex, currentStep);
        if (garticStepRepository.findByChainIdAndStepNumber(assignedChain.getId(), currentStep).isPresent()) {
            if (isHuman(actor)) {
                eventSender.sendToPlayer(gameId, actor.getUser().getId(), "WAITING_FOR_OTHERS", Map.of("stepNumber", currentStep));
            }
            gameTraceLogger.trace(gameId, "GARTIC_STEP_DUPLICATE step=" + currentStep + " actor=" + participantNameSafe(actor));
            return;
        }
        String normalizedContent = normalizeGarticContent(gameId, expectedType, submittedContent);
        garticStepRepository.save(GarticStep.builder()
                .chain(assignedChain)
                .player(actor)
                .stepNumber(currentStep)
                .stepType(expectedType)
                .content(normalizedContent)
                .build());
        long submittedNow = garticStepRepository.countByChainGameIdAndStepNumber(gameId, currentStep);
        gameTraceLogger.trace(gameId, "GARTIC_STEP_SUBMITTED step=" + currentStep
                + " expectedType=" + expectedType
                + " actor=" + participantNameSafe(actor)
                + " submitted=" + submittedNow + "/" + orderedPlayers.size());
        if (isHuman(actor) && submittedNow < orderedPlayers.size()) {
            eventSender.sendToPlayer(gameId, actor.getUser().getId(), "WAITING_FOR_OTHERS", Map.of(
                    "stepNumber", currentStep,
                    "submitted", submittedNow,
                    "total", orderedPlayers.size()
            ));
        }
        finishOrAdvanceGartic(gameId, orderedPlayers, currentStep);
    }

    private void finishOrAdvanceGartic(UUID gameId, List<GamePlayer> orderedPlayers, int currentStep) {
        Object lock = garticLocks.computeIfAbsent(gameId, id -> new Object());
        synchronized (lock) {
            long submitted = garticStepRepository.countByChainGameIdAndStepNumber(gameId, currentStep);
            if (submitted < orderedPlayers.size()) {
                gameTraceLogger.trace(gameId, "GARTIC_STEP_WAIT step=" + currentStep + " submitted=" + submitted + "/" + orderedPlayers.size());
                return;
            }
            gameTimerService.cancel(garticStepKey(gameId, currentStep));
            gameTraceLogger.trace(gameId, "GARTIC_STEP_COMPLETE step=" + currentStep + " submitted=" + submitted);
            if (currentStep >= orderedPlayers.size()) {
                gameTraceLogger.trace(gameId, "GARTIC_REVEAL_START");
                revealAndFinishGartic(gameId);
                return;
            }
            int nextStep = currentStep + 1;
            gameTraceLogger.trace(gameId, "GARTIC_STEP_ADVANCE nextStep=" + nextStep);
            sendGarticStepInstructions(gameId, orderedPlayers, nextStep);
            scheduleGarticTimeout(gameId, nextStep);
        }
    }

    private void onGarticStepTimeout(UUID gameId, int stepNumber) {
        gameTraceLogger.trace(gameId, "GARTIC_TIMEOUT step=" + stepNumber);
        GameSession game = gameSessionRepository.findById(gameId).orElse(null);
        if (game == null || !"IN_PROGRESS".equals(game.getStatus()) || !"GARTIC_PHONE".equals(game.getGameType())) {
            return;
        }
        List<GamePlayer> orderedPlayers = orderedPlayers(gamePlayerRepository.findByGameId(gameId));
        String expectedType = expectedGarticType(stepNumber);
        for (int i = 0; i < orderedPlayers.size(); i++) {
            GamePlayer player = orderedPlayers.get(i);
            GarticChain chain = resolveChainForPlayerStep(gameId, orderedPlayers, i, stepNumber);
            if (garticStepRepository.findByChainIdAndStepNumber(chain.getId(), stepNumber).isEmpty()) {
                garticStepRepository.save(GarticStep.builder()
                        .chain(chain)
                        .player(player)
                        .stepNumber(stepNumber)
                        .stepType(expectedType)
                        .content("DRAWING".equals(expectedType)
                                ? garticDrawingAssetStorage.saveDataUrlAsAssetRef(gameId, WHITE_PNG_BASE64, GARTIC_IMAGE_MAX_BYTES)
                                : DEFAULT_TEXT)
                        .build());
            }
        }
        finishOrAdvanceGartic(gameId, orderedPlayers, stepNumber);
    }

    private void sendGarticStepInstructions(UUID gameId, List<GamePlayer> orderedPlayers, int stepNumber) {
        String stepType = expectedGarticType(stepNumber);
        for (int i = 0; i < orderedPlayers.size(); i++) {
            GamePlayer player = orderedPlayers.get(i);
            GarticChain chain = resolveChainForPlayerStep(gameId, orderedPlayers, i, stepNumber);
            Map<String, Object> payload = new HashMap<>();
            if ("DRAWING".equals(stepType)) {
                String phrase = garticStepRepository.findByChainIdAndStepNumber(chain.getId(), stepNumber - 1)
                        .map(GarticStep::getContent).orElse(DEFAULT_TEXT);
                payload.put("phrase", phrase);
                payload.put("timeLimit", garticDrawTimeoutSec);
                if (isHuman(player)) {
                    eventSender.sendToPlayer(gameId, player.getUser().getId(), "STEP_DRAW", payload);
                } else {
                    scheduleBotDrawingStep(gameId, player, stepNumber, phrase);
                }
            } else if (stepNumber == 1) {
                payload.put("stepNumber", 1);
                payload.put("timeLimit", garticTextTimeoutSec);
                if (isHuman(player)) {
                    eventSender.sendToPlayer(gameId, player.getUser().getId(), "STEP_WRITE", payload);
                } else {
                    scheduleBotStep(gameId, player, stepNumber, "TEXT", botPhrase(gameId));
                }
            } else {
                String storedDrawing = garticStepRepository.findByChainIdAndStepNumber(chain.getId(), stepNumber - 1)
                        .map(GarticStep::getContent)
                        .orElseGet(() -> garticDrawingAssetStorage.saveDataUrlAsAssetRef(gameId, WHITE_PNG_BASE64, GARTIC_IMAGE_MAX_BYTES));
                putDrawingWsFields(gameId, payload, storedDrawing);
                payload.put("timeLimit", garticTextTimeoutSec);
                if (isHuman(player)) {
                    eventSender.sendToPlayer(gameId, player.getUser().getId(), "STEP_GUESS", payload);
                } else {
                    scheduleBotStep(gameId, player, stepNumber, "TEXT", botGuess(gameId, storedDrawing));
                }
            }
        }
    }

    private void revealAndFinishGartic(UUID gameId) {
        List<Map<String, Object>> chainsPayload;
        try {
            List<GarticChain> chains = garticChainRepository.findByGameIdWithOwnersForReveal(gameId);
            chainsPayload = chains.stream().map(chain -> {
                Map<String, Object> chainMap = new HashMap<>();
                chainMap.put("ownerId", participantId(chain.getOwner()).toString());
                chainMap.put("ownerName", participantName(chain.getOwner()));
                List<Map<String, Object>> steps = garticStepRepository
                        .findByChainIdOrderByStepNumberAscWithPlayers(chain.getId()).stream()
                        .map(step -> {
                            Map<String, Object> stepMap = new HashMap<>();
                            stepMap.put("playerId", participantId(step.getPlayer()).toString());
                            stepMap.put("playerName", participantName(step.getPlayer()));
                            stepMap.put("type", step.getStepType());
                            if ("DRAWING".equals(step.getStepType())) {
                                enrichDrawingStepForReveal(gameId, stepMap, step.getContent());
                            } else {
                                stepMap.put("content", step.getContent());
                            }
                            return stepMap;
                        }).toList();
                chainMap.put("steps", steps);
                return chainMap;
            }).toList();
        } catch (Exception e) {
            log.warn("Failed to build REVEAL_CHAIN payload for game {}: {}", gameId, e.getMessage(), e);
            gameTraceLogger.trace(gameId, "GARTIC_REVEAL_BUILD_FAILED reason=" + e.getMessage());
            chainsPayload = List.of();
        }
        try {
            eventSender.sendToGame(gameId, "REVEAL_CHAIN", Map.of("chains", chainsPayload));
            gameTraceLogger.trace(gameId, "GARTIC_REVEAL_SENT chains=" + chainsPayload.size());
        } catch (Exception e) {
            log.warn("Failed to send REVEAL_CHAIN for game {}: {}", gameId, e.getMessage());
            gameTraceLogger.trace(gameId, "GARTIC_REVEAL_FAILED reason=" + e.getMessage());
            // Keep the flow alive even when reveal payload is too heavy for WS client/broker.
            eventSender.sendToGame(gameId, "REVEAL_CHAIN_SKIPPED", Map.of("reason", "payload_too_large_or_ws_error"));
        } finally {
            try {
                eventSender.sendToGame(gameId, "GAME_FINISHED", Map.of("scores", List.of()));
                gameSessionRepository.findById(gameId).ifPresent(game -> {
                    game.setStatus("FINISHED");
                    game.setFinishedAt(java.time.OffsetDateTime.now());
                    gameSessionRepository.save(game);
                });
                gameTraceLogger.trace(gameId, "GARTIC_GAME_FINISHED");
            } catch (Exception e) {
                log.error("Failed to finalize Gartic game {}", gameId, e);
            }
        }
    }

    private int detectCurrentGarticStep(UUID gameId, int playersCount) {
        Integer maxStep = garticStepRepository.findMaxStepByGameId(gameId);
        if (maxStep == null) return 1;
        long currentCount = garticStepRepository.countByChainGameIdAndStepNumber(gameId, maxStep);
        return currentCount < playersCount ? maxStep : (maxStep + 1);
    }

    private List<GamePlayer> orderedPlayers(List<GamePlayer> players) {
        return players.stream()
                .sorted(Comparator.comparing(GamePlayer::getId))
                .toList();
    }

    private int indexOfPlayer(List<GamePlayer> orderedPlayers, UUID gamePlayerId) {
        for (int i = 0; i < orderedPlayers.size(); i++) {
            if (orderedPlayers.get(i).getId().equals(gamePlayerId)) {
                return i;
            }
        }
        throw new NotFoundException("Player not found in game");
    }

    private GarticChain resolveChainForPlayerStep(UUID gameId, List<GamePlayer> orderedPlayers, int playerIndex, int stepNumber) {
        int n = orderedPlayers.size();
        int ownerIndex = (playerIndex - (stepNumber - 1) % n + n) % n;
        GamePlayer owner = orderedPlayers.get(ownerIndex);
        return garticChainRepository.findByGameIdAndOwnerId(gameId, owner.getId())
                .orElseThrow(() -> new NotFoundException("Chain not found"));
    }

    private String expectedGarticType(int stepNumber) {
        return stepNumber % 2 == 1 ? "TEXT" : "DRAWING";
    }

    private void scheduleGarticTimeout(UUID gameId, int stepNumber) {
        int timeout = "DRAWING".equals(expectedGarticType(stepNumber)) ? garticDrawTimeoutSec : garticTextTimeoutSec;
        gameTimerService.schedule(garticStepKey(gameId, stepNumber), timeout, () -> onGarticStepTimeout(gameId, stepNumber));
    }

    private String garticStepKey(UUID gameId, int stepNumber) {
        return "game:" + gameId + ":gartic:step:" + stepNumber;
    }

    private String disconnectUnreadyKey(UUID gameId, UUID userId) {
        return "game:" + gameId + ":disconnect:" + userId;
    }

    private void cancelDisconnectUnreadyTimer(UUID gameId, UUID userId) {
        gameTimerService.cancel(disconnectUnreadyKey(gameId, userId));
    }

    /**
     * Планирует перевод игрока в unready через 10 секунд после потери последней STOMP-сессии.
     */
    public void scheduleLobbyUnreadyOnDisconnect(UUID userId) {
        List<GamePlayer> lobbyPlayers = gamePlayerRepository.findLobbyPlayersByUserId(userId);
        if (lobbyPlayers.isEmpty()) {
            return;
        }
        GamePlayer latest = lobbyPlayers.getFirst();
        UUID gameId = latest.getGame().getId();
        gameTimerService.schedule(disconnectUnreadyKey(gameId, userId), LOBBY_DISCONNECT_UNREADY_DELAY_SEC, () -> {
            try {
                if (!gamePlayerRepository.existsByGameIdAndUserId(gameId, userId)) {
                    return;
                }
                GameSession game = gameSessionRepository.findById(gameId).orElse(null);
                if (game == null || !"LOBBY".equals(game.getStatus())) {
                    return;
                }
                GamePlayer gp = gamePlayerRepository.findByGameIdAndUserId(gameId, userId).orElse(null);
                if (gp == null || !Boolean.TRUE.equals(gp.getIsReady())) {
                    return;
                }
                markUnready(gameId, userId);
            } catch (Exception ignored) {
                // Disconnect timeout is best-effort and should not break scheduler thread.
            }
        });
    }

    private boolean isBot(GamePlayer player) {
        return player.getBotUser() != null;
    }

    private boolean isHuman(GamePlayer player) {
        return player.getUser() != null;
    }

    private UUID participantId(GamePlayer player) {
        return isHuman(player) ? player.getUser().getId() : player.getBotUser().getId();
    }

    private String participantName(GamePlayer player) {
        return isHuman(player) ? player.getUser().getName() : player.getBotUser().getName();
    }

    private String participantNameSafe(GamePlayer player) {
        try {
            return participantName(player);
        } catch (LazyInitializationException ignored) {
            try {
                return "participant-" + participantId(player);
            } catch (Exception ignoredToo) {
                return "participant";
            }
        } catch (Exception ignored) {
            return "participant";
        }
    }

    private String participantAvatar(GamePlayer player) {
        return isHuman(player) ? player.getUser().getAvatarUrl() : player.getBotUser().getAvatarUrl();
    }

    private record BotActor(UUID gamePlayerId, UUID participantId, String name) {}

    private BotActor toBotActor(GamePlayer botPlayer) {
        UUID pid = botPlayer.getId();
        try {
            pid = participantId(botPlayer);
        } catch (Exception ignored) {
        }
        String name = participantNameSafe(botPlayer);
        return new BotActor(botPlayer.getId(), pid, name);
    }

    private void scheduleBotStep(UUID gameId, GamePlayer botPlayer, int stepNumber, String type, String content) {
        BotActor actor = toBotActor(botPlayer);
        int delaySec = ThreadLocalRandom.current().nextInt(1, 4);
        String key = "game:" + gameId + ":bot:" + actor.gamePlayerId() + ":step:" + stepNumber + ":attempt:1";
        Runnable scheduleWork = () -> {
            gameTraceLogger.trace(gameId, "BOT_STEP_SCHEDULED bot=" + actor.name() + "(" + actor.participantId() + ")"
                    + " step=" + stepNumber + " type=" + type + " delaySec=" + delaySec);
            gameTimerService.schedule(key, delaySec, () ->
                    executeBotStepWithRetry(gameId, actor, stepNumber, type, content, 1));
        };
        // On startGame we are inside an open transaction; scheduling before commit can race and cause "Chain not found".
        if (scheduleBotAfterCommit && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scheduleWork.run();
                }
            });
        } else {
            scheduleWork.run();
        }
    }

    private void executeBotStepWithRetry(UUID gameId, BotActor actor, int stepNumber, String type, String content, int attempt) {
        GamePlayer botPlayer = gamePlayerRepository.findById(actor.gamePlayerId()).orElse(null);
        if (botPlayer == null) {
            gameTraceLogger.trace(gameId, "BOT_STEP_FAILED bot=" + actor.name() + "(" + actor.participantId() + ")"
                    + " step=" + stepNumber + " type=" + type + " attempt=" + attempt + " reason=Player missing");
            return;
        }
        try {
            submitGarticStep(gameId, botPlayer, type, content);
            gameTraceLogger.trace(gameId, "BOT_STEP_DONE bot=" + actor.name() + "(" + actor.participantId() + ")"
                    + " step=" + stepNumber + " type=" + type + " attempt=" + attempt);
        } catch (Exception e) {
            String reason = e.getMessage();
            boolean retryable = reason != null && (reason.contains("Chain not found") || reason.contains("Player not found"));
            if (retryable && attempt < BOT_STEP_MAX_RETRIES) {
                int nextAttempt = attempt + 1;
                String retryKey = "game:" + gameId + ":bot:" + actor.gamePlayerId() + ":step:" + stepNumber + ":attempt:" + nextAttempt;
                gameTraceLogger.trace(gameId, "BOT_STEP_RETRY bot=" + actor.name() + "(" + actor.participantId() + ")"
                        + " step=" + stepNumber + " type=" + type + " attempt=" + nextAttempt + " reason=" + reason);
                gameTimerService.schedule(retryKey, 2, () ->
                        executeBotStepWithRetry(gameId, actor, stepNumber, type, content, nextAttempt));
                return;
            }
            log.warn("Bot step failed: gameId={}, botPlayerId={}, step={}, type={}, attempt={}, reason={}",
                    gameId, actor.gamePlayerId(), stepNumber, type, attempt, reason);
            gameTraceLogger.trace(gameId, "BOT_STEP_FAILED bot=" + actor.name() + "(" + actor.participantId() + ")"
                    + " step=" + stepNumber + " type=" + type + " attempt=" + attempt + " reason=" + reason);
        }
    }

    private String botPhrase(UUID gameId) {
        // Try several times to avoid duplicate phrase collisions between bots.
        Set<String> existing = garticChainRepository.findByGameId(gameId).stream()
                .map(chain -> garticStepRepository.findByChainIdAndStepNumber(chain.getId(), 1)
                        .map(GarticStep::getContent)
                        .orElse(""))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isBlank() && !DEFAULT_TEXT.equals(s))
                .collect(java.util.stream.Collectors.toSet());
        for (int i = 0; i < 4; i++) {
            Optional<String> generated = garticInferenceGateway.generatePhrase();
            if (generated.isEmpty() || generated.get().isBlank()) {
                continue;
            }
            String phrase = generated.get().trim();
            String key = phrase.toLowerCase();
            if (!existing.contains(key)) {
                return phrase;
            }
        }
        List<String> fallback = List.of(
                "кот в тапках ест морковку",
                "динозавр на самокате",
                "пицца в космосе",
                "чайник играет в футбол"
        );
        String phrase = fallback.get(ThreadLocalRandom.current().nextInt(fallback.size()));
        gameTraceLogger.trace(gameId, "BOT_TEXT_RESULT kind=FALLBACK_LIST text=" + phrase);
        return phrase;
    }

    private String botGuess(UUID gameId, String storedDrawing) {
        String dataUrl = garticDrawingAssetStorage.toDataUrlForInference(gameId, storedDrawing, GARTIC_IMAGE_MAX_BYTES);
        if (dataUrl == null || dataUrl.isBlank() || WHITE_PNG_BASE64.equals(dataUrl)) {
            return "ничего не видно";
        }
        return garticInferenceGateway.guess(dataUrl).orElse("похоже на рисунок кота");
    }

    private String botDraw(UUID gameId, String phrase) {
        gameTraceLogger.trace(gameId, "BOT_DRAW_MODEL_REQUEST timeoutSec=" + botDrawModelTimeoutSec);
        Optional<String> generated = CompletableFuture
                .supplyAsync(() -> garticInferenceGateway.draw(phrase))
                .completeOnTimeout(Optional.empty(), botDrawModelTimeoutSec, TimeUnit.SECONDS)
                .exceptionally(ex -> Optional.empty())
                .join();
        if (generated.isPresent() && !generated.get().isBlank()) {
            String ref = garticDrawingAssetStorage.saveDataUrlAsAssetRef(gameId, generated.get(), GARTIC_IMAGE_MAX_BYTES);
            gameTraceLogger.trace(gameId, "BOT_DRAW_RESULT kind=AI_IMAGE ref=" + ref);
            return ref;
        }
        gameTraceLogger.trace(gameId, "BOT_DRAW_MODEL_EMPTY_OR_TIMEOUT");
        String localDataUrl = botDrawLocalPngDataUrl(phrase);
        if (localDataUrl == null) {
            return null;
        }
        String ref = garticDrawingAssetStorage.saveDataUrlAsAssetRef(gameId, localDataUrl, GARTIC_IMAGE_MAX_BYTES);
        gameTraceLogger.trace(gameId, "BOT_DRAW_RESULT kind=LOCAL_IMAGE ref=" + ref);
        return ref;
    }

    private void scheduleBotDrawingStep(UUID gameId, GamePlayer botPlayer, int stepNumber, String phrase) {
        BotActor actor = toBotActor(botPlayer);
        int delaySec = ThreadLocalRandom.current().nextInt(1, 4);
        String key = "game:" + gameId + ":bot:" + actor.gamePlayerId() + ":step:" + stepNumber + ":draw";
        Runnable scheduleWork = () -> {
            gameTraceLogger.trace(gameId, "BOT_STEP_SCHEDULED bot=" + actor.name() + "(" + actor.participantId() + ")"
                    + " step=" + stepNumber + " type=DRAWING delaySec=" + delaySec);
            gameTimerService.schedule(key, delaySec, () -> executeBotDrawingStep(gameId, actor, stepNumber, phrase));
        };
        if (scheduleBotAfterCommit && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scheduleWork.run();
                }
            });
        } else {
            scheduleWork.run();
        }
    }

    private void executeBotDrawingStep(UUID gameId, BotActor actor, int stepNumber, String phrase) {
        long startedAtMs = System.currentTimeMillis();
        gameTraceLogger.trace(gameId, "BOT_DRAW_GENERATION_STARTED bot=" + actor.name() + "(" + actor.participantId() + ")"
                + " step=" + stepNumber);
        String content = null;
        try {
            content = botDraw(gameId, phrase);
        } catch (Exception e) {
            log.warn("Bot draw generation failed: gameId={}, botPlayerId={}, step={}, reason={}",
                    gameId, actor.gamePlayerId(), stepNumber, e.getMessage());
        }
        long generationMs = System.currentTimeMillis() - startedAtMs;
        if (!isGarticStepOpen(gameId, stepNumber)) {
            gameTraceLogger.trace(gameId, "BOT_DRAW_STILL_GENERATING_ROUND_FINISHED bot=" + actor.name()
                    + "(" + actor.participantId() + ") step=" + stepNumber + " generationMs=" + generationMs);
            return;
        }
        if (content == null || content.isBlank()) {
            gameTraceLogger.trace(gameId, "BOT_DRAW_RESULT kind=NO_IMAGE step=" + stepNumber + " generationMs=" + generationMs);
            return;
        }
        executeBotStepWithRetry(gameId, actor, stepNumber, "DRAWING", content, 1);
    }

    private boolean isGarticStepOpen(UUID gameId, int stepNumber) {
        GameSession game = gameSessionRepository.findById(gameId).orElse(null);
        if (game == null || !"IN_PROGRESS".equals(game.getStatus()) || !"GARTIC_PHONE".equals(game.getGameType())) {
            return false;
        }
        List<GamePlayer> players = orderedPlayers(gamePlayerRepository.findByGameId(gameId));
        if (players.isEmpty()) {
            return false;
        }
        int currentStep = detectCurrentGarticStep(gameId, players.size());
        return currentStep == stepNumber;
    }

    /** Creates a simple recognizable doodle when external draw model is unavailable. */
    private String botDrawLocalPngDataUrl(String phrase) {
        try {
            int width = 768;
            int height = 512;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            String p = phrase == null ? "" : phrase.toLowerCase(Locale.ROOT);
            g.setStroke(new BasicStroke(6f));
            g.setColor(new Color(30, 30, 30));

            if (containsAny(p, "кот", "cat")) {
                drawCat(g);
            } else if (containsAny(p, "пицц", "pizza")) {
                drawPizza(g);
            } else if (containsAny(p, "ножниц", "scissor")) {
                drawScissors(g);
            } else if (containsAny(p, "огур", "cucumber")) {
                drawCucumber(g);
            } else if (containsAny(p, "космос", "space", "астро")) {
                drawSpace(g);
            } else if (containsAny(p, "диноз", "dino")) {
                drawDino(g);
            } else {
                drawGenericScene(g, p);
            }
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return "data:image/png;base64," + b64;
        } catch (Exception e) {
            log.warn("Bot local draw fallback failed: {}", e.getMessage());
            return null;
        }
    }

    private static boolean containsAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) return true;
        }
        return false;
    }

    private static void drawCat(Graphics2D g) {
        g.drawOval(250, 150, 260, 220);
        g.drawLine(300, 150, 340, 95);
        g.drawLine(340, 95, 365, 155);
        g.drawLine(420, 155, 455, 95);
        g.drawLine(455, 95, 485, 155);
        g.fillOval(330, 230, 16, 16);
        g.fillOval(420, 230, 16, 16);
        g.drawArc(360, 260, 40, 30, 200, 140);
        g.drawLine(290, 260, 350, 250);
        g.drawLine(290, 285, 350, 275);
        g.drawLine(410, 250, 470, 260);
        g.drawLine(410, 275, 470, 285);
    }

    private static void drawPizza(Graphics2D g) {
        g.drawLine(220, 120, 560, 280);
        g.drawLine(560, 280, 280, 360);
        g.drawLine(280, 360, 220, 120);
        g.drawArc(205, 95, 370, 210, 20, 120);
        g.drawOval(360, 220, 30, 30);
        g.drawOval(440, 250, 30, 30);
        g.drawOval(320, 290, 26, 26);
        g.drawOval(400, 300, 24, 24);
    }

    private static void drawScissors(Graphics2D g) {
        g.drawOval(220, 260, 100, 100);
        g.drawOval(310, 260, 100, 100);
        g.drawLine(350, 300, 560, 170);
        g.drawLine(350, 320, 560, 390);
        g.drawLine(500, 210, 610, 130);
        g.drawLine(500, 350, 610, 430);
    }

    private static void drawCucumber(Graphics2D g) {
        g.drawRoundRect(220, 200, 340, 110, 70, 70);
        g.drawOval(240, 225, 26, 26);
        g.drawOval(300, 235, 22, 22);
        g.drawOval(360, 222, 24, 24);
        g.drawOval(430, 236, 22, 22);
        g.drawOval(500, 224, 24, 24);
    }

    private static void drawSpace(Graphics2D g) {
        g.drawOval(320, 170, 140, 180);
        g.drawRect(370, 220, 40, 70);
        g.drawLine(280, 340, 320, 300);
        g.drawLine(460, 300, 500, 340);
        g.drawOval(140, 90, 120, 70);
        g.drawOval(550, 110, 140, 80);
        g.fillOval(170, 120, 8, 8);
        g.fillOval(600, 130, 8, 8);
    }

    private static void drawDino(Graphics2D g) {
        g.drawOval(250, 220, 280, 140);
        g.drawOval(470, 170, 120, 110);
        g.drawLine(250, 270, 180, 240);
        g.drawLine(300, 350, 300, 420);
        g.drawLine(430, 350, 430, 420);
        g.fillOval(545, 215, 10, 10);
        g.drawLine(360, 210, 380, 180);
        g.drawLine(390, 210, 410, 182);
        g.drawLine(420, 212, 440, 184);
    }

    private static void drawGenericScene(Graphics2D g, String phrase) {
        int seed = Math.abs(Objects.hash(phrase, System.nanoTime(), UUID.randomUUID()));
        int shift = seed % 80;
        g.drawLine(80, 390, 690, 390);
        g.drawOval(130 + shift, 240, 100 + (seed % 40), 100 + (seed % 40));
        g.drawRect(300 + (shift / 2), 250, 130 + (seed % 40), 100 + ((seed / 7) % 30));
        g.drawRoundRect(500 - (shift / 3), 230, 110 + ((seed / 11) % 35), 120 + ((seed / 13) % 30), 30, 30);
        g.drawLine(110 + shift, 210, 180 + shift, 150 + (seed % 40));
        g.drawLine(620 - shift, 220, 660 - shift, 160 + ((seed / 5) % 40));
    }

    private String botQuiplashAnswer(String promptText) {
        return garticInferenceGateway.generateQuiplashAnswer(promptText)
                .orElse("Звучит как план бота #" + UUID.randomUUID().toString().substring(0, 4));
    }

    private String defaultBotName(String botType) {
        return switch (botType) {
            case "GARTIC_DRAWER" -> "DrawBot";
            case "GARTIC_WRITER" -> "WordSmith";
            case "GARTIC_GUESSER" -> "SketchGuess";
            case "QUIPLASH_JOKER" -> "QuipMaster";
            case "QUIPLASH_VOTER" -> "VoteBot";
            case "GARTIC_BOT" -> "GarticMate";
            case "QUIPLASH_BOT" -> "QuipMate";
            default -> "Bot-" + botType;
        };
    }

    private String normalizeGarticContent(UUID gameId, String type, String content) {
        if (!"DRAWING".equals(type)) {
            return content == null || content.isBlank() ? DEFAULT_TEXT : content;
        }
        if (content == null || content.isBlank()) {
            return garticDrawingAssetStorage.saveDataUrlAsAssetRef(gameId, WHITE_PNG_BASE64, GARTIC_IMAGE_MAX_BYTES);
        }
        if (GarticDrawingAssetStorage.isAssetRef(content)) {
            UUID id = UUID.fromString(content.substring(GarticDrawingAssetStorage.ASSET_PREFIX.length()));
            if (!garticDrawingAssetStorage.exists(gameId, id)) {
                throw new BadRequestException("Drawing asset not found");
            }
            return content;
        }
        if (content.startsWith("data:image/")) {
            return garticDrawingAssetStorage.saveDataUrlAsAssetRef(gameId, content, GARTIC_IMAGE_MAX_BYTES);
        }
        throw new BadRequestException("Drawing must be PNG via POST .../gartic/drawings (drawingAssetId) or legacy data URL");
    }

    private void putDrawingWsFields(UUID gameId, Map<String, Object> target, String storedContent) {
        if (GarticDrawingAssetStorage.isAssetRef(storedContent)) {
            UUID aid = UUID.fromString(storedContent.substring(GarticDrawingAssetStorage.ASSET_PREFIX.length()));
            target.put("drawingAssetId", aid.toString());
            target.put("imageUrl", "/api/games/" + gameId + "/gartic/drawings/" + aid);
        } else if (storedContent != null && storedContent.startsWith("data:image/")) {
            target.put("imageBase64", storedContent);
        }
    }

    private void enrichDrawingStepForReveal(UUID gameId, Map<String, Object> stepMap, String storedContent) {
        if (GarticDrawingAssetStorage.isAssetRef(storedContent)) {
            UUID aid = UUID.fromString(storedContent.substring(GarticDrawingAssetStorage.ASSET_PREFIX.length()));
            stepMap.put("content", null);
            stepMap.put("drawingAssetId", aid.toString());
            stepMap.put("imageUrl", "/api/games/" + gameId + "/gartic/drawings/" + aid);
        } else {
            stepMap.put("content", storedContent);
        }
    }

    @Transactional
    public Map<String, String> uploadGarticDrawing(UUID gameId, UUID userId, byte[] pngBytes) {
        GameSession game = gameSessionRepository.findById(gameId).orElseThrow(() -> new NotFoundException("Game not found"));
        if (!"GARTIC_PHONE".equals(game.getGameType())) {
            throw new BadRequestException("Drawing uploads are only for GARTIC_PHONE");
        }
        if (!"IN_PROGRESS".equals(game.getStatus())) {
            throw new BadRequestException("Drawing uploads are only while the game is in progress");
        }
        if (!roomParticipantRepository.existsByRoomIdAndUserId(game.getRoom().getId(), userId)) {
            throw new BadRequestException("User is not a participant of this room");
        }
        if (!gamePlayerRepository.existsByGameIdAndUserId(gameId, userId)) {
            throw new BadRequestException("User is not in this game");
        }
        UUID assetId = garticDrawingAssetStorage.savePngBytes(gameId, pngBytes, GARTIC_IMAGE_MAX_BYTES);
        return Map.of(
                "drawingAssetId", assetId.toString(),
                "imageUrl", "/api/games/" + gameId + "/gartic/drawings/" + assetId
        );
    }

    @Transactional(readOnly = true)
    public byte[] getGarticDrawingAsset(UUID gameId, UUID requesterId, UUID assetId) {
        GameSession game = gameSessionRepository.findById(gameId).orElseThrow(() -> new NotFoundException("Game not found"));
        if (!roomParticipantRepository.existsByRoomIdAndUserId(game.getRoom().getId(), requesterId)) {
            throw new BadRequestException("User is not a participant of this room");
        }
        if (!garticDrawingAssetStorage.exists(gameId, assetId)) {
            throw new NotFoundException("Drawing asset not found");
        }
        return garticDrawingAssetStorage.loadPng(gameId, assetId);
    }

    private GameResponse toResponse(GameSession session) {
        List<GamePlayerDto> players = gamePlayerRepository.findByGameId(session.getId()).stream().map(p -> GamePlayerDto.builder()
                .id(participantId(p).toString())
                .name(participantName(p))
                .avatarUrl(participantAvatar(p))
                .isBot(isBot(p))
                .isReady(p.getIsReady())
                .score(p.getScore())
                .build()).toList();
        return GameResponse.builder()
                .gameId(session.getId().toString())
                .gameType(session.getGameType())
                .status(session.getStatus())
                .players(players)
                .build();
    }
}


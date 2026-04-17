package ru.syncroom.games.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
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
    private static final int MIN_READY_PLAYERS_TO_START = 3;
    private static final int LOBBY_DISCONNECT_UNREADY_DELAY_SEC = 10;
    private static final int TOTAL_ROUNDS = 3;
    private static final int GARTIC_TEXT_TIMEOUT_SEC = 60;
    private static final int GARTIC_DRAW_TIMEOUT_SEC = 90;
    private static final int GARTIC_IMAGE_MAX_BYTES = 2 * 1024 * 1024;
    private static final String DEFAULT_TEXT = "...";
    private static final String WHITE_PNG_BASE64 = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMBAAZ9l1cAAAAASUVORK5CYII=";
    private static final List<String> BOT_PHRASES = List.of(
            "кот в космосе",
            "пицца на велосипеде",
            "медведь играет в шахматы",
            "робот пьет чай у самовара",
            "динозавр в метро"
    );

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
        if (!"GARTIC_PHONE".equals(game.getGameType())) {
            throw new BadRequestException("Bots are currently supported only for GARTIC_PHONE");
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
    public void handleAction(UUID gameId, UUID userId, GameActionMessage msg) {
        cancelDisconnectUnreadyTimer(gameId, userId);
        String type = msg.getType();
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
            return;
        }
        if ("SUBMIT_VOTE".equals(type)) {
            submitVote(gameId, userId, UUID.fromString(String.valueOf(msg.getPayload().get("answerId"))));
            return;
        }
        if ("SUBMIT_PHRASE".equals(type)) {
            submitGarticStep(gameId, resolveHumanPlayer(gameId, userId), "TEXT",
                    String.valueOf(msg.getPayload().getOrDefault("text", DEFAULT_TEXT)));
            return;
        }
        if ("SUBMIT_GUESS".equals(type)) {
            submitGarticStep(gameId, resolveHumanPlayer(gameId, userId), "TEXT",
                    String.valueOf(msg.getPayload().getOrDefault("text", DEFAULT_TEXT)));
            return;
        }
        if ("SUBMIT_DRAWING".equals(type)) {
            submitGarticStep(gameId, resolveHumanPlayer(gameId, userId), "DRAWING",
                    String.valueOf(msg.getPayload().getOrDefault("imageBase64", WHITE_PNG_BASE64)));
        }
    }

    private GamePlayer resolveHumanPlayer(UUID gameId, UUID userId) {
        return gamePlayerRepository.findByGameIdAndUserId(gameId, userId)
                .orElseThrow(() -> new NotFoundException("Player not found"));
    }

    private void submitAnswer(UUID gameId, UUID userId, String text) {
        QuiplashPrompt prompt = promptRepository.findFirstByGameIdOrderByRoundDesc(gameId)
                .orElseThrow(() -> new NotFoundException("Prompt not found"));
        GamePlayer player = gamePlayerRepository.findByGameIdAndUserId(gameId, userId)
                .orElseThrow(() -> new NotFoundException("Player not found"));
        if (answerRepository.findByPromptIdAndPlayerId(prompt.getId(), player.getId()).isEmpty()) {
            answerRepository.save(QuiplashAnswer.builder()
                    .prompt(prompt)
                    .player(player)
                    .text(text == null || text.isBlank() ? "..." : text)
                    .votes(0)
                    .build());
        }
        long answersCount = answerRepository.countByPromptId(prompt.getId());
        long playersCount = gamePlayerRepository.countByGameId(gameId);
        if (answersCount >= playersCount) {
            openVoting(prompt);
        } else {
            eventSender.sendToPlayer(gameId, userId, "WAITING_FOR_OTHERS", Map.of());
        }
    }

    private void submitVote(UUID gameId, UUID userId, UUID answerId) {
        QuiplashAnswer answer = answerRepository.findById(answerId).orElseThrow(() -> new NotFoundException("Answer not found"));
        GamePlayer voter = gamePlayerRepository.findByGameIdAndUserId(gameId, userId)
                .orElseThrow(() -> new NotFoundException("Player not found"));
        if (answer.getPlayer().getId().equals(voter.getId())) {
            throw new BadRequestException("You cannot vote for your own answer");
        }
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

    private void startRound(GameSession game, int round) {
        String promptText = promptBankRepository.findAll().stream().skip(Math.max(0, round - 1)).findFirst()
                .map(PromptBank::getText).orElse("Промпт раунда " + round);
        QuiplashPrompt prompt = promptRepository.save(QuiplashPrompt.builder()
                .game(game)
                .round(round)
                .text(promptText)
                .timeLimit(60)
                .build());
        eventSender.sendToGame(game.getId(), "PROMPT_RECEIVED", Map.of(
                "promptId", prompt.getId().toString(),
                "text", prompt.getText(),
                "timeLimit", 60
        ));
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
        gameTimerService.schedule(GameTimerService.voteKey(gameId, round), 30, () -> onVoteTimeout(gameId, round));
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
            return;
        }
        String normalizedContent = normalizeGarticContent(expectedType, submittedContent);
        garticStepRepository.save(GarticStep.builder()
                .chain(assignedChain)
                .player(actor)
                .stepNumber(currentStep)
                .stepType(expectedType)
                .content(normalizedContent)
                .build());
        finishOrAdvanceGartic(gameId, orderedPlayers, currentStep);
    }

    private void finishOrAdvanceGartic(UUID gameId, List<GamePlayer> orderedPlayers, int currentStep) {
        long submitted = garticStepRepository.countByChainGameIdAndStepNumber(gameId, currentStep);
        if (submitted < orderedPlayers.size()) {
            return;
        }
        gameTimerService.cancel(garticStepKey(gameId, currentStep));
        if (currentStep >= orderedPlayers.size()) {
            revealAndFinishGartic(gameId);
            return;
        }
        int nextStep = currentStep + 1;
        sendGarticStepInstructions(gameId, orderedPlayers, nextStep);
        scheduleGarticTimeout(gameId, nextStep);
    }

    private void onGarticStepTimeout(UUID gameId, int stepNumber) {
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
                        .content("DRAWING".equals(expectedType) ? WHITE_PNG_BASE64 : DEFAULT_TEXT)
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
                payload.put("timeLimit", GARTIC_DRAW_TIMEOUT_SEC);
                if (isHuman(player)) {
                    eventSender.sendToPlayer(gameId, player.getUser().getId(), "STEP_DRAW", payload);
                } else {
                    scheduleBotStep(gameId, player, stepNumber, "DRAWING", botDraw(phrase));
                }
            } else if (stepNumber == 1) {
                payload.put("stepNumber", 1);
                payload.put("timeLimit", GARTIC_TEXT_TIMEOUT_SEC);
                if (isHuman(player)) {
                    eventSender.sendToPlayer(gameId, player.getUser().getId(), "STEP_WRITE", payload);
                } else {
                    scheduleBotStep(gameId, player, stepNumber, "TEXT", botPhrase());
                }
            } else {
                String imageBase64 = garticStepRepository.findByChainIdAndStepNumber(chain.getId(), stepNumber - 1)
                        .map(GarticStep::getContent).orElse(WHITE_PNG_BASE64);
                payload.put("imageBase64", imageBase64);
                payload.put("timeLimit", GARTIC_TEXT_TIMEOUT_SEC);
                if (isHuman(player)) {
                    eventSender.sendToPlayer(gameId, player.getUser().getId(), "STEP_GUESS", payload);
                } else {
                    scheduleBotStep(gameId, player, stepNumber, "TEXT", botGuess(imageBase64));
                }
            }
        }
    }

    private void revealAndFinishGartic(UUID gameId) {
        List<GarticChain> chains = garticChainRepository.findByGameId(gameId);
        List<Map<String, Object>> chainsPayload = chains.stream().map(chain -> {
            Map<String, Object> chainMap = new HashMap<>();
            chainMap.put("ownerId", participantId(chain.getOwner()).toString());
            chainMap.put("ownerName", participantName(chain.getOwner()));
            List<Map<String, Object>> steps = garticStepRepository.findByChainIdOrderByStepNumberAsc(chain.getId()).stream()
                    .map(step -> {
                        Map<String, Object> stepMap = new HashMap<>();
                        stepMap.put("playerId", participantId(step.getPlayer()).toString());
                        stepMap.put("playerName", participantName(step.getPlayer()));
                        stepMap.put("type", step.getStepType());
                        stepMap.put("content", step.getContent());
                        return stepMap;
                    }).toList();
            chainMap.put("steps", steps);
            return chainMap;
        }).toList();
        eventSender.sendToGame(gameId, "REVEAL_CHAIN", Map.of("chains", chainsPayload));
        eventSender.sendToGame(gameId, "GAME_FINISHED", Map.of("scores", List.of()));
        GameSession game = gameSessionRepository.findById(gameId).orElseThrow(() -> new NotFoundException("Game not found"));
        game.setStatus("FINISHED");
        game.setFinishedAt(java.time.OffsetDateTime.now());
        gameSessionRepository.save(game);
    }

    private int detectCurrentGarticStep(UUID gameId, int playersCount) {
        Integer maxStep = garticStepRepository.findMaxStepByGameId(gameId);
        if (maxStep == null) return 1;
        long currentCount = garticStepRepository.countByChainGameIdAndStepNumber(gameId, maxStep);
        return currentCount < playersCount ? maxStep : (maxStep + 1);
    }

    private List<GamePlayer> orderedPlayers(List<GamePlayer> players) {
        return players.stream()
                .sorted(Comparator.comparing(p -> participantId(p).toString()))
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
        int timeout = "DRAWING".equals(expectedGarticType(stepNumber)) ? GARTIC_DRAW_TIMEOUT_SEC : GARTIC_TEXT_TIMEOUT_SEC;
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

    private String participantAvatar(GamePlayer player) {
        return isHuman(player) ? player.getUser().getAvatarUrl() : player.getBotUser().getAvatarUrl();
    }

    private void scheduleBotStep(UUID gameId, GamePlayer botPlayer, int stepNumber, String type, String content) {
        int delaySec = ThreadLocalRandom.current().nextInt(1, 4);
        String key = "game:" + gameId + ":bot:" + botPlayer.getId() + ":step:" + stepNumber;
        gameTimerService.schedule(key, delaySec, () -> {
            try {
                submitGarticStep(gameId, botPlayer, type, content);
            } catch (Exception ignored) {
                // Bot actions are best-effort and should not break scheduler thread.
            }
        });
    }

    private String botPhrase() {
        return BOT_PHRASES.get(ThreadLocalRandom.current().nextInt(BOT_PHRASES.size()));
    }

    private String botGuess(String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank() || WHITE_PNG_BASE64.equals(imageBase64)) {
            return "ничего не видно";
        }
        return garticInferenceGateway.guess(imageBase64).orElse("похоже на рисунок кота");
    }

    private String botDraw(String phrase) {
        return garticInferenceGateway.draw(phrase).orElse(WHITE_PNG_BASE64);
    }

    private String defaultBotName(String botType) {
        return switch (botType) {
            case "GARTIC_DRAWER" -> "DrawBot";
            case "GARTIC_WRITER" -> "WordSmith";
            case "GARTIC_GUESSER" -> "SketchGuess";
            default -> "Bot-" + botType;
        };
    }

    private String normalizeGarticContent(String type, String content) {
        String normalized = content == null || content.isBlank() ? ("DRAWING".equals(type) ? WHITE_PNG_BASE64 : DEFAULT_TEXT) : content;
        if (!"DRAWING".equals(type)) {
            return normalized;
        }
        int base64Part = normalized.contains(",") ? normalized.substring(normalized.indexOf(",") + 1).length() : normalized.length();
        int approxBytes = (base64Part * 3) / 4;
        if (approxBytes > GARTIC_IMAGE_MAX_BYTES) {
            throw new BadRequestException("Drawing is too large (max 2MB)");
        }
        return normalized;
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


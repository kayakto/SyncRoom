package ru.syncroom.games.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.games.domain.*;
import ru.syncroom.games.dto.GameActionMessage;
import ru.syncroom.games.dto.GamePlayerDto;
import ru.syncroom.games.dto.GameResponse;
import ru.syncroom.games.repository.*;
import ru.syncroom.games.websocket.GameEventSender;
import ru.syncroom.games.websocket.GameTimerService;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameSessionRepository gameSessionRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final QuiplashPromptRepository promptRepository;
    private final QuiplashAnswerRepository answerRepository;
    private final QuiplashVoteRepository voteRepository;
    private final PromptBankRepository promptBankRepository;
    private final RoomRepository roomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final UserRepository userRepository;
    private final GameEventSender eventSender;
    private final GameTimerService gameTimerService;
    private static final int TOTAL_ROUNDS = 3;

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

    @Transactional
    public void markReady(UUID gameId, UUID userId) {
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
    public void startGame(UUID gameId) {
        GameSession game = gameSessionRepository.findById(gameId).orElseThrow(() -> new NotFoundException("Game not found"));
        List<GamePlayer> players = gamePlayerRepository.findByGameId(gameId);
        if (players.size() < 3) throw new BadRequestException("At least 3 players required");
        if (players.stream().anyMatch(p -> !Boolean.TRUE.equals(p.getIsReady()))) {
            throw new BadRequestException("Not all players are ready");
        }
        game.setStatus("IN_PROGRESS");
        gameSessionRepository.save(game);

        List<Map<String, Object>> playersPayload = players.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getUser().getId().toString());
            m.put("name", p.getUser().getName());
            m.put("avatarUrl", p.getUser().getAvatarUrl());
            return m;
        }).toList();
        eventSender.sendToGame(gameId, "GAME_STARTED", Map.of("players", playersPayload));

        startRound(game, 1);
    }

    @Transactional
    public void handleAction(UUID gameId, UUID userId, GameActionMessage msg) {
        String type = msg.getType();
        if ("PLAYER_READY".equals(type)) {
            markReady(gameId, userId);
            return;
        }
        if ("SUBMIT_ANSWER".equals(type)) {
            submitAnswer(gameId, userId, String.valueOf(msg.getPayload().getOrDefault("text", "...")));
            return;
        }
        if ("SUBMIT_VOTE".equals(type)) {
            submitVote(gameId, userId, UUID.fromString(String.valueOf(msg.getPayload().get("answerId"))));
        }
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
                "authorId", a.getPlayer().getUser().getId().toString(),
                "authorName", a.getPlayer().getUser().getName(),
                "votes", a.getVotes()
        )).toList();
        List<Map<String, Object>> scores = gamePlayerRepository.findByGameId(gameId).stream().map(p -> Map.<String, Object>of(
                "playerId", p.getUser().getId().toString(),
                "playerName", p.getUser().getName(),
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

    private GameResponse toResponse(GameSession session) {
        List<GamePlayerDto> players = gamePlayerRepository.findByGameId(session.getId()).stream().map(p -> GamePlayerDto.builder()
                .id(p.getUser().getId().toString())
                .name(p.getUser().getName())
                .avatarUrl(p.getUser().getAvatarUrl())
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


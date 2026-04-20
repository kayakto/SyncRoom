package ru.syncroom.games.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.games.domain.QuiplashAnswer;
import ru.syncroom.games.domain.QuiplashPrompt;
import ru.syncroom.games.dto.GameActionMessage;
import ru.syncroom.games.dto.GameResponse;
import ru.syncroom.games.repository.GamePlayerRepository;
import ru.syncroom.games.repository.QuiplashAnswerRepository;
import ru.syncroom.games.repository.QuiplashPromptRepository;
import ru.syncroom.games.websocket.GameEventSender;
import ru.syncroom.games.websocket.GameTimerService;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("GameService WebSocket Flow Tests")
class GameServiceWebSocketTest {

    /** Valid 1×1 PNG (must pass server-side PNG magic check). */
    private static final String MINI_PNG_DATA_URL =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMBAAZ9l1cAAAAASUVORK5CYII=";

    @Autowired
    private GameService gameService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomParticipantRepository participantRepository;
    @Autowired
    private QuiplashPromptRepository promptRepository;
    @Autowired
    private QuiplashAnswerRepository answerRepository;
    @Autowired
    private GamePlayerRepository gamePlayerRepository;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;
    @MockitoBean
    private GameEventSender gameEventSender;
    @MockitoBean
    private GameTimerService gameTimerService;

    private User u1, u2, u3;
    private Room room;
    private final Map<String, Runnable> scheduledTasks = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        u1 = createUser("ws1@example.com");
        u2 = createUser("ws2@example.com");
        u3 = createUser("ws3@example.com");

        room = roomRepository.save(Room.builder().context("leisure").title("g").maxParticipants(10).isActive(true).build());
        addParticipant(room, u1);
        addParticipant(room, u2);
        addParticipant(room, u3);

        // Для теста полного flow: таймер перехода к следующему раунду запускаем сразу
        doAnswer(inv -> {
            String key = inv.getArgument(0, String.class);
            scheduledTasks.put(key, inv.getArgument(2, Runnable.class));
            Runnable task = inv.getArgument(2, Runnable.class);
            if (key.contains(":next:")) task.run();
            return null;
        }).when(gameTimerService).schedule(anyString(), anyInt(), any(Runnable.class));
    }

    @Test
    @DisplayName("3 раунда завершаются событием GAME_FINISHED")
    void fullThreeRoundFlowFinishesGame() {
        GameResponse game = gameService.createGame(room.getId(), u1.getId(), "QUIPLASH");
        UUID gameId = UUID.fromString(game.getGameId());

        gameService.markReady(gameId, u1.getId());
        gameService.markReady(gameId, u2.getId());
        gameService.markReady(gameId, u3.getId());
        gameService.startGame(gameId);

        // round 1
        playRound(gameId, u1.getId(), "a1", u2.getId(), "a2", u3.getId(), "a3");
        // round 2
        playRound(gameId, u1.getId(), "b1", u2.getId(), "b2", u3.getId(), "b3");
        // round 3
        playRound(gameId, u1.getId(), "c1", u2.getId(), "c2", u3.getId(), "c3");

        verify(gameEventSender, atLeastOnce()).sendToGame(eq(gameId), eq("GAME_FINISHED"), ArgumentMatchers.any());
    }

    @Test
    @DisplayName("startGame запускает с ready>=3 и отправляет PLAYER_KICKED неготовому")
    void startGame_KicksUnreadyPlayers() {
        User u4 = createUser("ws4@example.com");
        addParticipant(room, u4);

        GameResponse game = gameService.createGame(room.getId(), u1.getId(), "QUIPLASH");
        UUID gameId = UUID.fromString(game.getGameId());

        gameService.markReady(gameId, u1.getId());
        gameService.markReady(gameId, u2.getId());
        gameService.markReady(gameId, u3.getId());
        gameService.markReady(gameId, u4.getId());
        gameService.markUnready(gameId, u4.getId());

        gameService.startGame(gameId);

        assertEquals(3, gamePlayerRepository.countByGameId(gameId));
        verify(gameEventSender).sendToPlayer(eq(gameId), eq(u4.getId()), eq("PLAYER_KICKED"), anyMap());
        verify(gameEventSender, atLeastOnce()).sendToGame(eq(gameId), eq("GAME_STARTED"), anyMap());
    }

    @Test
    @DisplayName("disconnect в лобби планирует auto-unready через 10 секунд")
    void disconnectSchedulesLobbyUnready() {
        GameResponse game = gameService.createGame(room.getId(), u1.getId(), "QUIPLASH");
        UUID gameId = UUID.fromString(game.getGameId());
        gameService.markReady(gameId, u1.getId());

        gameService.scheduleLobbyUnreadyOnDisconnect(u1.getId());

        verify(gameTimerService).schedule(eq("game:" + gameId + ":disconnect:" + u1.getId()), eq(10), any(Runnable.class));
    }

    @Test
    @DisplayName("Gartic Phone завершает цепочки и отправляет REVEAL_CHAIN")
    void garticFlowRevealsChainsAndFinishes() {
        GameResponse game = gameService.createGame(room.getId(), u1.getId(), "GARTIC_PHONE");
        UUID gameId = UUID.fromString(game.getGameId());

        gameService.markReady(gameId, u1.getId());
        gameService.markReady(gameId, u2.getId());
        gameService.markReady(gameId, u3.getId());
        gameService.startGame(gameId);

        // step 1 (TEXT)
        submitPhrase(gameId, u1.getId(), "p1");
        submitPhrase(gameId, u2.getId(), "p2");
        submitPhrase(gameId, u3.getId(), "p3");
        // step 2 (DRAWING)
        submitDrawing(gameId, u1.getId(), MINI_PNG_DATA_URL);
        submitDrawing(gameId, u2.getId(), MINI_PNG_DATA_URL);
        submitDrawing(gameId, u3.getId(), MINI_PNG_DATA_URL);
        // step 3 (TEXT guess)
        submitGuess(gameId, u1.getId(), "g1");
        submitGuess(gameId, u2.getId(), "g2");
        submitGuess(gameId, u3.getId(), "g3");

        verify(gameEventSender, atLeastOnce()).sendToGame(eq(gameId), eq("REVEAL_CHAIN"), ArgumentMatchers.any());
        verify(gameEventSender, atLeastOnce()).sendToGame(eq(gameId), eq("GAME_FINISHED"), ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Gartic отклоняет слишком большой base64 рисунок")
    void garticRejectsTooLargeDrawing() {
        GameResponse game = gameService.createGame(room.getId(), u1.getId(), "GARTIC_PHONE");
        UUID gameId = UUID.fromString(game.getGameId());
        gameService.markReady(gameId, u1.getId());
        gameService.markReady(gameId, u2.getId());
        gameService.markReady(gameId, u3.getId());
        gameService.startGame(gameId);

        submitPhrase(gameId, u1.getId(), "p1");
        submitPhrase(gameId, u2.getId(), "p2");
        submitPhrase(gameId, u3.getId(), "p3");

        int maxBytes = 2 * 1024 * 1024;
        byte[] huge = new byte[maxBytes + 1];
        byte[] pngMagic = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(pngMagic, 0, huge, 0, pngMagic.length);
        String hugeDataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(huge);
        assertThrows(BadRequestException.class, () -> submitDrawing(gameId, u1.getId(), hugeDataUrl));
    }

    @Test
    @DisplayName("Gartic timeout подставляет default контент и завершает игру")
    void garticTimeoutFillsDefaultsAndFinishes() {
        GameResponse game = gameService.createGame(room.getId(), u1.getId(), "GARTIC_PHONE");
        UUID gameId = UUID.fromString(game.getGameId());
        gameService.markReady(gameId, u1.getId());
        gameService.markReady(gameId, u2.getId());
        gameService.markReady(gameId, u3.getId());
        gameService.startGame(gameId);

        runScheduled(gameId, 1);
        runScheduled(gameId, 2);
        runScheduled(gameId, 3);

        verify(gameEventSender, atLeastOnce()).sendToGame(eq(gameId), eq("REVEAL_CHAIN"), ArgumentMatchers.any());
        verify(gameEventSender, atLeastOnce()).sendToGame(eq(gameId), eq("GAME_FINISHED"), ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Gartic отправляет персональные STEP_WRITE -> STEP_DRAW -> STEP_GUESS через sendToPlayer")
    void garticSendsPersonalStepSequenceViaSendToPlayer() {
        GameResponse game = gameService.createGame(room.getId(), u1.getId(), "GARTIC_PHONE");
        UUID gameId = UUID.fromString(game.getGameId());

        gameService.markReady(gameId, u1.getId());
        gameService.markReady(gameId, u2.getId());
        gameService.markReady(gameId, u3.getId());
        gameService.startGame(gameId);

        submitPhrase(gameId, u1.getId(), "p1");
        submitPhrase(gameId, u2.getId(), "p2");
        submitPhrase(gameId, u3.getId(), "p3");

        submitDrawing(gameId, u1.getId(), MINI_PNG_DATA_URL);
        submitDrawing(gameId, u2.getId(), MINI_PNG_DATA_URL);
        submitDrawing(gameId, u3.getId(), MINI_PNG_DATA_URL);

        ArgumentCaptor<UUID> gameIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass((Class) Map.class);

        verify(gameEventSender, atLeast(9))
                .sendToPlayer(gameIdCaptor.capture(), userIdCaptor.capture(), typeCaptor.capture(), payloadCaptor.capture());

        boolean hasWriteForU1 = false;
        boolean hasDrawForU1 = false;
        boolean hasGuessForU1 = false;

        var gameIds = gameIdCaptor.getAllValues();
        var userIds = userIdCaptor.getAllValues();
        var types = typeCaptor.getAllValues();

        for (int i = 0; i < types.size(); i++) {
            if (!gameId.equals(gameIds.get(i))) continue;
            if (!u1.getId().equals(userIds.get(i))) continue;
            String t = types.get(i);
            if ("STEP_WRITE".equals(t)) hasWriteForU1 = true;
            if ("STEP_DRAW".equals(t)) hasDrawForU1 = true;
            if ("STEP_GUESS".equals(t)) {
                hasGuessForU1 = true;
                Map<String, Object> p = payloadCaptor.getAllValues().get(i);
                assertTrue(p.containsKey("imageUrl") || p.containsKey("imageBase64"),
                        "STEP_GUESS should expose imageUrl or legacy imageBase64");
            }
        }

        assertTrue(hasWriteForU1, "u1 should receive STEP_WRITE");
        assertTrue(hasDrawForU1, "u1 should receive STEP_DRAW");
        assertTrue(hasGuessForU1, "u1 should receive STEP_GUESS");
    }

    @Test
    @DisplayName("Gartic с ботами: боты делают шаги и игра завершается")
    void garticBotsTakeStepsAndGameFinishes() {
        GameResponse game = gameService.createGame(room.getId(), u1.getId(), "GARTIC_PHONE");
        UUID gameId = UUID.fromString(game.getGameId());

        gameService.addBots(gameId, u1.getId(), "GARTIC_DRAWER", 2);
        gameService.markReady(gameId, u1.getId());
        gameService.startGame(gameId);

        assertEquals(3, gamePlayerRepository.countByGameId(gameId));
        assertEquals(2, gamePlayerRepository.findByGameId(gameId).stream().filter(gp -> gp.getBotUser() != null).count());

        runBotTasks(gameId, 1);
        submitPhrase(gameId, u1.getId(), "human phrase");

        runBotTasks(gameId, 2);
        submitDrawing(gameId, u1.getId(), MINI_PNG_DATA_URL);

        runBotTasks(gameId, 3);
        submitGuess(gameId, u1.getId(), "human guess");

        verify(gameEventSender, atLeastOnce()).sendToGame(eq(gameId), eq("BOT_ADDED"), anyMap());
        verify(gameEventSender, atLeastOnce()).sendToGame(eq(gameId), eq("REVEAL_CHAIN"), anyMap());
        verify(gameEventSender, atLeastOnce()).sendToGame(eq(gameId), eq("GAME_FINISHED"), anyMap());
    }

    @Test
    @DisplayName("На SUBMIT_* сервер отправляет персональный ACTION_ACCEPTED")
    void submitActionsSendActionAcceptedAck() {
        GameResponse game = gameService.createGame(room.getId(), u1.getId(), "GARTIC_PHONE");
        UUID gameId = UUID.fromString(game.getGameId());
        gameService.markReady(gameId, u1.getId());
        gameService.markReady(gameId, u2.getId());
        gameService.markReady(gameId, u3.getId());
        gameService.startGame(gameId);

        submitPhrase(gameId, u1.getId(), "cat phone");

        verify(gameEventSender, atLeastOnce()).sendToPlayer(
                eq(gameId),
                eq(u1.getId()),
                eq("ACTION_ACCEPTED"),
                argThat(m -> m instanceof Map<?, ?> map
                        && "SUBMIT_PHRASE".equals(String.valueOf(map.get("actionType"))))
        );
    }

    @Test
    @DisplayName("Quiplash с ботами: боты отвечают и голосуют, игра завершается")
    void quiplashBotsAnswerAndVoteAndFinishGame() {
        GameResponse game = gameService.createGame(room.getId(), u1.getId(), "QUIPLASH");
        UUID gameId = UUID.fromString(game.getGameId());

        gameService.addBots(gameId, u1.getId(), "QUIPLASH_JOKER", 2);
        gameService.markReady(gameId, u1.getId());
        gameService.startGame(gameId);

        runQuiplashBotTasks(gameId, "answer", 1);
        QuiplashPrompt round1 = promptRepository.findFirstByGameIdOrderByRoundDesc(gameId).orElseThrow();
        QuiplashAnswer humanR1 = answerRepository.findByPromptId(round1.getId()).stream()
                .filter(a -> a.getPlayer().getUser() != null && a.getPlayer().getUser().getId().equals(u1.getId()))
                .findFirst()
                .orElseGet(() -> {
                    submitAnswer(gameId, u1.getId(), "human-r1");
                    return answerRepository.findByPromptId(round1.getId()).stream()
                            .filter(a -> a.getPlayer().getUser() != null && a.getPlayer().getUser().getId().equals(u1.getId()))
                            .findFirst().orElseThrow();
                });

        runQuiplashBotTasks(gameId, "vote", 1);
        submitVote(gameId, u1.getId(), pickNotOwnAnswer(round1, u1.getId(), humanR1.getId()));

        runQuiplashBotTasks(gameId, "answer", 2);
        QuiplashPrompt round2 = promptRepository.findFirstByGameIdOrderByRoundDesc(gameId).orElseThrow();
        QuiplashAnswer humanR2 = answerRepository.findByPromptId(round2.getId()).stream()
                .filter(a -> a.getPlayer().getUser() != null && a.getPlayer().getUser().getId().equals(u1.getId()))
                .findFirst()
                .orElseGet(() -> {
                    submitAnswer(gameId, u1.getId(), "human-r2");
                    return answerRepository.findByPromptId(round2.getId()).stream()
                            .filter(a -> a.getPlayer().getUser() != null && a.getPlayer().getUser().getId().equals(u1.getId()))
                            .findFirst().orElseThrow();
                });

        runQuiplashBotTasks(gameId, "vote", 2);
        submitVote(gameId, u1.getId(), pickNotOwnAnswer(round2, u1.getId(), humanR2.getId()));

        runQuiplashBotTasks(gameId, "answer", 3);
        QuiplashPrompt round3 = promptRepository.findFirstByGameIdOrderByRoundDesc(gameId).orElseThrow();
        QuiplashAnswer humanR3 = answerRepository.findByPromptId(round3.getId()).stream()
                .filter(a -> a.getPlayer().getUser() != null && a.getPlayer().getUser().getId().equals(u1.getId()))
                .findFirst()
                .orElseGet(() -> {
                    submitAnswer(gameId, u1.getId(), "human-r3");
                    return answerRepository.findByPromptId(round3.getId()).stream()
                            .filter(a -> a.getPlayer().getUser() != null && a.getPlayer().getUser().getId().equals(u1.getId()))
                            .findFirst().orElseThrow();
                });

        runQuiplashBotTasks(gameId, "vote", 3);
        submitVote(gameId, u1.getId(), pickNotOwnAnswer(round3, u1.getId(), humanR3.getId()));

        verify(gameEventSender, atLeastOnce()).sendToGame(eq(gameId), eq("WAITING_FOR_VOTES"), anyMap());
        verify(gameEventSender, atLeastOnce()).sendToGame(eq(gameId), eq("ROUND_RESULT"), anyMap());
        verify(gameEventSender, atLeastOnce()).sendToGame(eq(gameId), eq("GAME_FINISHED"), anyMap());
    }

    private void playRound(UUID gameId, UUID p1, String a1, UUID p2, String a2, UUID p3, String a3) {
        QuiplashPrompt prompt = promptRepository.findFirstByGameIdOrderByRoundDesc(gameId).orElseThrow();

        submitAnswer(gameId, p1, a1);
        submitAnswer(gameId, p2, a2);
        submitAnswer(gameId, p3, a3);

        QuiplashAnswer ans1 = answerRepository.findByPromptId(prompt.getId()).stream()
                .filter(a -> a.getPlayer().getUser().getId().equals(p1)).findFirst().orElseThrow();
        QuiplashAnswer ans2 = answerRepository.findByPromptId(prompt.getId()).stream()
                .filter(a -> a.getPlayer().getUser().getId().equals(p2)).findFirst().orElseThrow();
        QuiplashAnswer ans3 = answerRepository.findByPromptId(prompt.getId()).stream()
                .filter(a -> a.getPlayer().getUser().getId().equals(p3)).findFirst().orElseThrow();

        // каждый голосует не за себя
        submitVote(gameId, p1, ans2.getId());
        submitVote(gameId, p2, ans3.getId());
        submitVote(gameId, p3, ans1.getId());
    }

    private void submitAnswer(UUID gameId, UUID userId, String text) {
        GameActionMessage msg = new GameActionMessage();
        msg.setType("SUBMIT_ANSWER");
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", text);
        msg.setPayload(payload);
        gameService.handleAction(gameId, userId, msg);
    }

    private void submitVote(UUID gameId, UUID userId, UUID answerId) {
        GameActionMessage msg = new GameActionMessage();
        msg.setType("SUBMIT_VOTE");
        Map<String, Object> payload = new HashMap<>();
        payload.put("answerId", answerId.toString());
        msg.setPayload(payload);
        gameService.handleAction(gameId, userId, msg);
    }

    private void submitPhrase(UUID gameId, UUID userId, String text) {
        GameActionMessage msg = new GameActionMessage();
        msg.setType("SUBMIT_PHRASE");
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", text);
        msg.setPayload(payload);
        gameService.handleAction(gameId, userId, msg);
    }

    private void submitGuess(UUID gameId, UUID userId, String text) {
        GameActionMessage msg = new GameActionMessage();
        msg.setType("SUBMIT_GUESS");
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", text);
        msg.setPayload(payload);
        gameService.handleAction(gameId, userId, msg);
    }

    private void submitDrawing(UUID gameId, UUID userId, String imageBase64) {
        GameActionMessage msg = new GameActionMessage();
        msg.setType("SUBMIT_DRAWING");
        Map<String, Object> payload = new HashMap<>();
        payload.put("imageBase64", imageBase64);
        msg.setPayload(payload);
        gameService.handleAction(gameId, userId, msg);
    }

    private void runScheduled(UUID gameId, int step) {
        String key = "game:" + gameId + ":gartic:step:" + step;
        Runnable task = scheduledTasks.get(key);
        if (task != null) {
            task.run();
        }
    }

    private void runBotTasks(UUID gameId, int step) {
        String marker = ":step:" + step + ":";
        List<String> keys = new ArrayList<>(scheduledTasks.keySet());
        for (String key : keys) {
            if (key.startsWith("game:" + gameId + ":bot:") && key.contains(marker)) {
                Runnable task = scheduledTasks.remove(key);
                if (task != null) {
                    task.run();
                }
            }
        }
    }

    private void runQuiplashBotTasks(UUID gameId, String phase, int round) {
        String prefix = "game:" + gameId + ":bot:quiplash:" + phase + ":round:" + round + ":player:";
        List<String> keys = new ArrayList<>(scheduledTasks.keySet());
        for (String key : keys) {
            if (key.startsWith(prefix)) {
                Runnable task = scheduledTasks.remove(key);
                if (task != null) {
                    task.run();
                }
            }
        }
    }

    private UUID pickNotOwnAnswer(QuiplashPrompt prompt, UUID userId, UUID fallback) {
        return answerRepository.findByPromptId(prompt.getId()).stream()
                .filter(a -> a.getPlayer().getUser() == null || !a.getPlayer().getUser().getId().equals(userId))
                .map(QuiplashAnswer::getId)
                .findFirst()
                .orElse(fallback);
    }

    private User createUser(String email) {
        return userRepository.save(User.builder()
                .name(email)
                .email(email)
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());
    }

    private void addParticipant(Room r, User u) {
        participantRepository.save(RoomParticipant.builder().room(r).user(u).build());
    }
}


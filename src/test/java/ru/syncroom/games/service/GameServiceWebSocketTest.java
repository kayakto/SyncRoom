package ru.syncroom.games.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("GameService WebSocket Flow Tests")
class GameServiceWebSocketTest {

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


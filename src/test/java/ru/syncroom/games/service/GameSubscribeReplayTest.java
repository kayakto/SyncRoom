package ru.syncroom.games.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.games.dto.GameResponse;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Game subscribe replay")
class GameSubscribeReplayTest {

    @Autowired
    private GameService gameService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomParticipantRepository participantRepository;

    @MockitoBean
    private GameEventSender eventSender;
    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;
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

        u1 = saveUser("r1@example.com");
        u2 = saveUser("r2@example.com");
        u3 = saveUser("r3@example.com");
        room = roomRepository.save(Room.builder()
                .context("leisure")
                .title("G")
                .maxParticipants(10)
                .isActive(true)
                .build());
        for (User u : new User[]{u1, u2, u3}) {
            participantRepository.save(RoomParticipant.builder().room(room).user(u).build());
        }

        doAnswer(inv -> {
            String key = inv.getArgument(0, String.class);
            scheduledTasks.put(key, inv.getArgument(2, Runnable.class));
            return null;
        }).when(gameTimerService).schedule(anyString(), anyInt(), any(Runnable.class));
        when(gameTimerService.getRemainingSeconds(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0, String.class);
            return scheduledTasks.containsKey(key) ? 42 : -1;
        });
    }

    @Test
    @DisplayName("replayMissedEventsOnSubscribe шлёт GAME_STARTED и PROMPT_RECEIVED с остатком таймера")
    void replaySendsGameStartedAndCurrentPhase() {
        GameResponse game = gameService.createGame(room.getId(), u1.getId(), "QUIPLASH");
        UUID gameId = UUID.fromString(game.getGameId());
        gameService.markReady(gameId, u1.getId());
        gameService.markReady(gameId, u2.getId());
        gameService.markReady(gameId, u3.getId());
        gameService.startGame(gameId);

        reset(eventSender);
        when(gameTimerService.getRemainingSeconds(GameTimerService.answerKey(gameId, 1))).thenReturn(42);

        gameService.replayMissedEventsOnSubscribe(gameId, u1.getId());

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventSender, atLeast(2)).sendToPlayer(eq(gameId), eq(u1.getId()), typeCaptor.capture(), any());

        assertTrue(typeCaptor.getAllValues().contains("GAME_STARTED"));
        assertTrue(typeCaptor.getAllValues().contains("PROMPT_RECEIVED"));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventSender, atLeastOnce()).sendToPlayer(
                eq(gameId), eq(u1.getId()), eq("PROMPT_RECEIVED"), payloadCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertEquals(42, payload.get("timeLimit"));
    }

    private User saveUser(String email) {
        return userRepository.save(User.builder()
                .name(email)
                .email(email)
                .provider(AuthProvider.EMAIL)
                .passwordHash("x")
                .createdAt(OffsetDateTime.now())
                .build());
    }
}

package ru.syncroom.games.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.games.domain.GameQueue;
import ru.syncroom.games.domain.GameQueuePlayer;
import ru.syncroom.games.domain.GameSession;
import ru.syncroom.games.repository.GameQueuePlayerRepository;
import ru.syncroom.games.repository.GameQueueRepository;
import ru.syncroom.games.websocket.GameQueueEventSender;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("GameQueueService start order")
class GameQueueStartOrderTest {

    @Autowired
    private GameQueueService gameQueueService;
    @Autowired
    private GameQueueRepository gameQueueRepository;
    @Autowired
    private GameQueuePlayerRepository gameQueuePlayerRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomParticipantRepository participantRepository;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private GameService gameService;
    @MockitoBean
    private GameQueueEventSender queueEventSender;
    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private Room room;
    private User u1;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        gameQueuePlayerRepository.deleteAll();
        gameQueueRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        u1 = userRepository.save(User.builder()
                .name("Host")
                .email("host@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("x")
                .createdAt(OffsetDateTime.now())
                .build());
        User u2 = userRepository.save(User.builder()
                .name("P2")
                .email("p2@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("x")
                .createdAt(OffsetDateTime.now())
                .build());
        User u3 = userRepository.save(User.builder()
                .name("P3")
                .email("p3@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("x")
                .createdAt(OffsetDateTime.now())
                .build());

        room = roomRepository.save(Room.builder()
                .context("leisure")
                .title("G")
                .maxParticipants(10)
                .isActive(true)
                .build());
        for (User u : List.of(u1, u2, u3)) {
            participantRepository.save(RoomParticipant.builder().room(room).user(u).build());
        }

        GameQueue queue = gameQueueRepository.save(GameQueue.builder()
                .room(room)
                .gameType("QUIPLASH")
                .status("WAITING")
                .build());
        for (User u : List.of(u1, u2, u3)) {
            gameQueuePlayerRepository.save(GameQueuePlayer.builder()
                    .queue(queue)
                    .user(u)
                    .isReady(true)
                    .build());
        }

        sessionId = UUID.randomUUID();
        when(gameService.createLobbySessionWithRoster(eq(room.getId()), eq("QUIPLASH"), anyList(), anyList()))
                .thenReturn(GameSession.builder()
                        .id(sessionId)
                        .room(room)
                        .gameType("QUIPLASH")
                        .status("LOBBY")
                        .build());
    }

    @Test
    @DisplayName("startGame уходит до QUEUE_STARTED (replay при подписке требует IN_PROGRESS)")
    void startGameBeforeQueueStarted() {
        gameQueueService.startFromQueue(room.getId(), "QUIPLASH", u1.getId());

        InOrder order = inOrder(gameService, queueEventSender);
        order.verify(gameService).startGame(sessionId);
        order.verify(queueEventSender).send(eq(room.getId()), eq("QUEUE_STARTED"), any(Map.class));
    }
}

package ru.syncroom.games.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import ru.syncroom.games.dto.GameActionMessage;
import ru.syncroom.games.dto.GameResponse;
import ru.syncroom.games.repository.BotUserRepository;
import ru.syncroom.games.support.GameBotCatalogFixture;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Quiplash vote timeout (LazyInitialization fix)")
class GameVoteTimeoutLazyInitTest {

    @Autowired
    private GameService gameService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomParticipantRepository participantRepository;
    @Autowired
    private BotUserRepository botUserRepository;

    @MockitoBean
    private GameEventSender gameEventSender;
    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;
    @MockitoBean
    private GameTimerService gameTimerService;

    private final Map<String, Runnable> scheduledTasks = new ConcurrentHashMap<>();

    private User human;
    private Room room;

    @BeforeEach
    void setUp() {
        scheduledTasks.clear();
        participantRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
        GameBotCatalogFixture.seedIfEmpty(botUserRepository);

        doAnswer(inv -> {
            scheduledTasks.put(inv.getArgument(0, String.class), inv.getArgument(2, Runnable.class));
            return null;
        }).when(gameTimerService).schedule(anyString(), anyInt(), any(Runnable.class));

        human = userRepository.save(User.builder()
                .name("Human")
                .email("human@example.com")
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
        participantRepository.save(RoomParticipant.builder().room(room).user(human).build());
    }

    @Test
    @DisplayName("таймаут голосования шлёт ROUND_RESULT, если живой игрок не голосовал (1 человек + 2 бота)")
    void voteTimeoutSendsRoundResultWhenHumanDoesNotVote() throws Exception {
        UUID gameId = transactionTemplate.execute(status -> {
            GameResponse game = gameService.createGame(room.getId(), human.getId(), "QUIPLASH");
            UUID id = UUID.fromString(game.getGameId());
            gameService.addBots(id, human.getId(), "QUIPLASH_JOKER", 2);
            gameService.markReady(id, human.getId());
            gameService.startGame(id);
            return id;
        });

        runBotTasks(gameId, "answer", 1);
        transactionTemplate.executeWithoutResult(status -> submitAnswer(gameId, human.getId(), "human answer"));

        runBotTasks(gameId, "vote", 1);

        String voteKey = GameTimerService.voteKey(gameId, 1);
        Runnable voteTimeout = scheduledTasks.get(voteKey);
        assertNotNull(voteTimeout, "vote timer must be scheduled after openVoting");

        reset(gameEventSender);

        Thread timerThread = new Thread(voteTimeout);
        timerThread.start();
        timerThread.join(10_000);

        verify(gameEventSender, times(1)).sendToGame(eq(gameId), eq("ROUND_RESULT"), anyMap());
    }

    private void runBotTasks(UUID gameId, String phase, int round) {
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

    private void submitAnswer(UUID gameId, UUID userId, String text) {
        GameActionMessage msg = new GameActionMessage();
        msg.setType("SUBMIT_ANSWER");
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", text);
        msg.setPayload(payload);
        gameService.handleAction(gameId, userId, msg);
    }
}

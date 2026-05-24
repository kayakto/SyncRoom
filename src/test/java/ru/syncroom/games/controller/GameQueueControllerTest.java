package ru.syncroom.games.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.syncroom.games.repository.BotUserRepository;
import ru.syncroom.games.repository.GameQueuePlayerRepository;
import ru.syncroom.games.service.GameQueueService;
import ru.syncroom.games.support.GameBotCatalogFixture;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.security.JwtTokenService;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Game queues API")
class GameQueueControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomParticipantRepository participantRepository;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private GameQueueService gameQueueService;
    @Autowired
    private GameQueuePlayerRepository gameQueuePlayerRepository;
    @Autowired
    private BotUserRepository botUserRepository;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private User user;
    private Room room;
    private String token;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
        botUserRepository.deleteAll();
        GameBotCatalogFixture.seedIfEmpty(botUserRepository);

        user = userRepository.save(User.builder()
                .name("Queue User")
                .email("gq@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("x")
                .createdAt(OffsetDateTime.now())
                .build());
        token = jwtTokenService.generateAccessToken(
                user.getId(), user.getName(), user.getEmail(), user.getProvider().getValue());
        room = roomRepository.save(Room.builder()
                .context("leisure")
                .title("L")
                .maxParticipants(10)
                .isActive(true)
                .build());
        participantRepository.save(RoomParticipant.builder().room(room).user(user).build());
    }

    @Test
    @DisplayName("GET /api/games/config — лимиты по типам")
    void gamesConfig() throws Exception {
        mockMvc.perform(get("/api/games/config").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.QUIPLASH.minPlayers").value(3))
                .andExpect(jsonPath("$.GARTIC_PHONE.minPlayers").value(4));
    }

    @Test
    @DisplayName("GET /queues — две записи по типам; join отражается в snapshot")
    void queuesSnapshotAndJoin() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/games/queues", room.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['QUIPLASH'].players.length()").value(0))
                .andExpect(jsonPath("$['GARTIC_PHONE'].players.length()").value(0));

        mockMvc.perform(post("/api/rooms/{roomId}/games/queues/{gameType}/join", room.getId(), "QUIPLASH")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0].userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.players[0].username").value("Queue User"));

        mockMvc.perform(get("/api/rooms/{roomId}/games/queues", room.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['QUIPLASH'].players.length()").value(1))
                .andExpect(jsonPath("$['GARTIC_PHONE'].players.length()").value(0));
    }

    @Test
    @DisplayName("(service) join второй очереди убирает из первой")
    void serviceJoinSecondQueueLeavesFirst() {
        gameQueueService.joinQueue(room.getId(), "QUIPLASH", user.getId());
        gameQueueService.joinQueue(room.getId(), "GARTIC_PHONE", user.getId());
        var snap = gameQueueService.getQueuesSnapshot(room.getId(), user.getId());
        assertEquals(0, snap.get("QUIPLASH").getPlayers().size());
        assertEquals(1, snap.get("GARTIC_PHONE").getPlayers().size());
    }

    @Test
    @DisplayName("join во вторую очередь выкидывает из первой")
    void joinSecondQueueLeavesFirst() throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/games/queues/{gameType}/join", room.getId(), "QUIPLASH")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rooms/{roomId}/games/queues/{gameType}/join", room.getId(), "GARTIC_PHONE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/{roomId}/games/queues", room.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['QUIPLASH'].players.length()").value(0))
                .andExpect(jsonPath("$['GARTIC_PHONE'].players.length()").value(1));
    }

    @Test
    @DisplayName("POST .../leave убирает из очереди")
    void leaveQueueRemovesPlayer() throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/games/queues/{gameType}/join", room.getId(), "QUIPLASH")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rooms/{roomId}/games/queues/{gameType}/leave", room.getId(), "QUIPLASH")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/rooms/{roomId}/games/queues", room.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['QUIPLASH'].players.length()").value(0));
        assertTrue(gameQueuePlayerRepository.findByUserIdAndRoomId(user.getId(), room.getId()).isEmpty());
    }

    @Test
    @DisplayName("повторный join в ту же очередь — одна строка в БД")
    void joinSameQueueTwiceSingleMembershipRow() {
        gameQueueService.joinQueue(room.getId(), "QUIPLASH", user.getId());
        gameQueueService.joinQueue(room.getId(), "QUIPLASH", user.getId());
        assertEquals(1, gameQueuePlayerRepository.findByUserIdAndRoomId(user.getId(), room.getId()).size());
        var snap = gameQueueService.getQueuesSnapshot(room.getId(), user.getId());
        assertEquals(1, snap.get("QUIPLASH").getPlayers().size());
    }

    @Test
    @DisplayName("переключение GARTIC → QUIPLASH снова оставляет только одну очередь")
    void switchBackToFirstQueueLeavesSecond() {
        gameQueueService.joinQueue(room.getId(), "GARTIC_PHONE", user.getId());
        gameQueueService.joinQueue(room.getId(), "QUIPLASH", user.getId());
        assertEquals(1, gameQueuePlayerRepository.findByUserIdAndRoomId(user.getId(), room.getId()).size());
        var snap = gameQueueService.getQueuesSnapshot(room.getId(), user.getId());
        assertEquals(1, snap.get("QUIPLASH").getPlayers().size());
        assertEquals(0, snap.get("GARTIC_PHONE").getPlayers().size());
    }

    @Test
    @DisplayName("POST /api/rooms/.../leave комнаты снимает пользователя с игровых очередей (WAITING)")
    void leaveRoomClearsWaitingQueueMembership() throws Exception {
        gameQueueService.joinQueue(room.getId(), "QUIPLASH", user.getId());
        mockMvc.perform(post("/api/rooms/{roomId}/leave", room.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertTrue(gameQueuePlayerRepository.findByUserIdAndRoomId(user.getId(), room.getId()).isEmpty());
    }

    @Test
    @DisplayName("join: avatarUrl в очереди — абсолютный URL (public-base-url + путь)")
    void queuePlayerAvatarUrlIsAbsolute() throws Exception {
        user.setAvatarUrl("/icons/icon-192.png");
        userRepository.save(user);

        mockMvc.perform(post("/api/rooms/{roomId}/games/queues/{gameType}/join", room.getId(), "QUIPLASH")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0].avatarUrl").value("http://localhost:8080/icons/icon-192.png"));
    }

    @Test
    @DisplayName("одиночный игрок: 1 человек + 2 Quiplash-бота → старт очереди OK")
    void soloPlayerCanStartQuiplashWithTwoBots() throws Exception {
        joinQueue("QUIPLASH");
        for (String botId : botIdsWithPrefix("QUIPLASH_", 2)) {
            addBotToQueue("QUIPLASH", botId);
        }
        setHumanReady("QUIPLASH", true);

        mockMvc.perform(post("/api/rooms/{roomId}/games/queues/{gameType}/start", room.getId(), "QUIPLASH")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameSessionId").isNotEmpty());

        mockMvc.perform(get("/api/rooms/{roomId}/games/queues", room.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['QUIPLASH'].status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("одиночный игрок: 1 человек + 3 Gartic-бота → старт очереди OK")
    void soloPlayerCanStartGarticWithThreeBots() throws Exception {
        joinQueue("GARTIC_PHONE");
        for (String botId : botIdsWithPrefix("GARTIC_", 3)) {
            addBotToQueue("GARTIC_PHONE", botId);
        }
        setHumanReady("GARTIC_PHONE", true);

        mockMvc.perform(post("/api/rooms/{roomId}/games/queues/{gameType}/start", room.getId(), "GARTIC_PHONE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameSessionId").isNotEmpty());
    }

    @Test
    @DisplayName("одиночный Quiplash: одного бота недостаточно для старта")
    void soloPlayerCannotStartQuiplashWithOnlyOneBot() throws Exception {
        joinQueue("QUIPLASH");
        addBotToQueue("QUIPLASH", botIdsWithPrefix("QUIPLASH_", 1).getFirst());
        setHumanReady("QUIPLASH", true);

        mockMvc.perform(post("/api/rooms/{roomId}/games/queues/{gameType}/start", room.getId(), "QUIPLASH")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST ready обновляет флаг")
    void readyToggle() throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/games/queues/QUIPLASH/join", room.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rooms/{roomId}/games/queues/QUIPLASH/ready", room.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ready\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0].isReady").value(true));
    }

    private void joinQueue(String gameType) throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/games/queues/{gameType}/join", room.getId(), gameType)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void setHumanReady(String gameType, boolean ready) throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/games/queues/{gameType}/ready", room.getId(), gameType)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ready\":" + ready + "}"))
                .andExpect(status().isOk());
    }

    private void addBotToQueue(String gameType, String botId) throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/games/queues/{gameType}/bots", room.getId(), gameType)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"botId\":\"" + botId + "\",\"difficulty\":\"MEDIUM\"}"))
                .andExpect(status().isOk());
    }

    private List<String> botIdsWithPrefix(String prefix, int limit) {
        return botUserRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .filter(b -> b.getBotType() != null && b.getBotType().startsWith(prefix))
                .limit(limit)
                .map(b -> b.getId().toString())
                .toList();
    }
}

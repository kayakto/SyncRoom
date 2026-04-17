package ru.syncroom.games.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("GameController Integration Tests")
class GameControllerTest {

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

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private User u1;
    private User u2;
    private User u3;
    private String t1;
    private String t2;
    private String t3;
    private Room room;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        u1 = createUser("g1@example.com");
        u2 = createUser("g2@example.com");
        u3 = createUser("g3@example.com");
        t1 = token(u1); t2 = token(u2); t3 = token(u3);

        room = roomRepository.save(Room.builder().context("leisure").title("Game room").maxParticipants(10).isActive(true).build());
        addParticipant(room, u1);
        addParticipant(room, u2);
        addParticipant(room, u3);
    }

    @Test
    @DisplayName("Create + current game")
    void createAndGetCurrent() throws Exception {
        String body = "{\"gameType\":\"QUIPLASH\"}";
        String createResp = mockMvc.perform(post("/api/rooms/{roomId}/games", room.getId())
                        .header("Authorization", "Bearer " + t1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameType").value("QUIPLASH"))
                .andExpect(jsonPath("$.status").value("LOBBY"))
                .andReturn().getResponse().getContentAsString();

        String gameId = createResp.replaceAll("(?s).*\"gameId\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/rooms/{roomId}/games/current", room.getId())
                        .header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(gameId));
    }

    @Test
    @DisplayName("Ready + start game")
    void readyAndStart() throws Exception {
        String createResp = mockMvc.perform(post("/api/rooms/{roomId}/games", room.getId())
                        .header("Authorization", "Bearer " + t1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"QUIPLASH\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String gameId = createResp.replaceAll("(?s).*\"gameId\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/games/{gameId}/ready", UUID.fromString(gameId)).header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/games/{gameId}/ready", UUID.fromString(gameId)).header("Authorization", "Bearer " + t2))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/games/{gameId}/ready", UUID.fromString(gameId)).header("Authorization", "Bearer " + t3))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/games/{gameId}/start", UUID.fromString(gameId))
                        .header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Create + start Gartic Phone game")
    void createAndStartGartic() throws Exception {
        String createResp = mockMvc.perform(post("/api/rooms/{roomId}/games", room.getId())
                        .header("Authorization", "Bearer " + t1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"GARTIC_PHONE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameType").value("GARTIC_PHONE"))
                .andReturn().getResponse().getContentAsString();
        String gameId = createResp.replaceAll("(?s).*\"gameId\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/games/{gameId}/ready", UUID.fromString(gameId)).header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/games/{gameId}/ready", UUID.fromString(gameId)).header("Authorization", "Bearer " + t2))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/games/{gameId}/ready", UUID.fromString(gameId)).header("Authorization", "Bearer " + t3))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/games/{gameId}/start", UUID.fromString(gameId))
                        .header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Боты для GARTIC: available + add + remove")
    void botsAvailableAddAndRemove() throws Exception {
        String createResp = mockMvc.perform(post("/api/rooms/{roomId}/games", room.getId())
                        .header("Authorization", "Bearer " + t1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"GARTIC_PHONE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String gameId = extractGameId(createResp);
        UUID gid = UUID.fromString(gameId);

        mockMvc.perform(get("/api/bots/available")
                        .header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/games/{gameId}/bots/add", gid)
                        .header("Authorization", "Bearer " + t1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"botType\":\"GARTIC_DRAWER\",\"count\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[*].isBot", hasItem(true)));

        String botsPayload = mockMvc.perform(get("/api/bots/available")
                        .header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String botId = botsPayload.replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(delete("/api/games/{gameId}/bots/{botId}", gid, UUID.fromString(botId))
                        .header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Боты для GARTIC: add count=2 добавляет двух ботов")
    void botsAddCountTwoHonored() throws Exception {
        String createResp = mockMvc.perform(post("/api/rooms/{roomId}/games", room.getId())
                        .header("Authorization", "Bearer " + t1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"GARTIC_PHONE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID gid = UUID.fromString(extractGameId(createResp));

        mockMvc.perform(post("/api/games/{gameId}/bots/add", gid)
                        .header("Authorization", "Bearer " + t1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"botType\":\"GARTIC_DRAWER\",\"count\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players", hasSize(3)))
                .andExpect(jsonPath("$.players[?(@.isBot == true)]", hasSize(2)));
    }

    @Test
    @DisplayName("POST unready снимает готовность — старт снова запрещён")
    void unreadyBlocksStartUntilAllReadyAgain() throws Exception {
        String gameId = extractGameId(mockMvc.perform(post("/api/rooms/{roomId}/games", room.getId())
                        .header("Authorization", "Bearer " + t1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"QUIPLASH\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        UUID gid = UUID.fromString(gameId);

        mockMvc.perform(post("/api/games/{gameId}/ready", gid).header("Authorization", "Bearer " + t1)).andExpect(status().isOk());
        mockMvc.perform(post("/api/games/{gameId}/ready", gid).header("Authorization", "Bearer " + t2)).andExpect(status().isOk());
        mockMvc.perform(post("/api/games/{gameId}/ready", gid).header("Authorization", "Bearer " + t3)).andExpect(status().isOk());

        mockMvc.perform(post("/api/games/{gameId}/unready", gid).header("Authorization", "Bearer " + t1)).andExpect(status().isOk());

        mockMvc.perform(post("/api/games/{gameId}/start", gid).header("Authorization", "Bearer " + t1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /start — запускает игру с 3+ ready и исключает неготовых")
    void startGame_WithFourPlayers_KicksUnreadyAndStarts() throws Exception {
        User u4 = createUser("g4@example.com");
        String t4 = token(u4);
        addParticipant(room, u4);

        String gameId = extractGameId(mockMvc.perform(post("/api/rooms/{roomId}/games", room.getId())
                        .header("Authorization", "Bearer " + t1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"QUIPLASH\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        UUID gid = UUID.fromString(gameId);

        mockMvc.perform(post("/api/games/{gameId}/ready", gid).header("Authorization", "Bearer " + t1)).andExpect(status().isOk());
        mockMvc.perform(post("/api/games/{gameId}/ready", gid).header("Authorization", "Bearer " + t2)).andExpect(status().isOk());
        mockMvc.perform(post("/api/games/{gameId}/ready", gid).header("Authorization", "Bearer " + t3)).andExpect(status().isOk());
        // u4 remains unready
        mockMvc.perform(post("/api/games/{gameId}/ready", gid).header("Authorization", "Bearer " + t4)).andExpect(status().isOk());
        mockMvc.perform(post("/api/games/{gameId}/unready", gid).header("Authorization", "Bearer " + t4)).andExpect(status().isOk());

        mockMvc.perform(post("/api/games/{gameId}/start", gid)
                        .header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/{roomId}/games/current", room.getId()).header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.players", hasSize(3)))
                .andExpect(jsonPath("$.players[*].id", not(hasItem(u4.getId().toString()))));
    }

    @Test
    @DisplayName("POST leave — выход из лобби, игрок исчезает из списка")
    void leaveLobbyRemovesPlayer() throws Exception {
        String gameId = extractGameId(mockMvc.perform(post("/api/rooms/{roomId}/games", room.getId())
                        .header("Authorization", "Bearer " + t1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"QUIPLASH\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        UUID gid = UUID.fromString(gameId);

        mockMvc.perform(post("/api/games/{gameId}/ready", gid).header("Authorization", "Bearer " + t2)).andExpect(status().isOk());

        mockMvc.perform(post("/api/games/{gameId}/leave", gid).header("Authorization", "Bearer " + t2)).andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/{roomId}/games/current", room.getId()).header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players", hasSize(1)));
    }

    @Test
    @DisplayName("POST /rooms/.../leave очищает лобби-игру, если вышел последний игрок")
    void leaveRoomClearsGameWhenCreatorLeavesAndWasSolePlayer() throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/games", room.getId())
                        .header("Authorization", "Bearer " + t1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"QUIPLASH\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/rooms/{roomId}/leave", room.getId())
                        .header("Authorization", "Bearer " + t1))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/rooms/{roomId}/games/current", room.getId())
                        .header("Authorization", "Bearer " + t2))
                .andExpect(status().isNotFound());
    }

    private static String extractGameId(String createResp) {
        return createResp.replaceAll("(?s).*\"gameId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
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

    private String token(User u) {
        return jwtTokenService.generateAccessToken(u.getId(), u.getName(), u.getEmail(), u.getProvider().getValue());
    }

    private void addParticipant(Room r, User u) {
        participantRepository.save(RoomParticipant.builder().room(r).user(u).build());
    }
}


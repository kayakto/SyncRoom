package ru.syncroom.rooms.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.security.JwtTokenService;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomMessage;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.repository.RoomMessageRepository;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Room chat REST")
class RoomChatControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomParticipantRepository participantRepository;
    @Autowired
    private RoomMessageRepository roomMessageRepository;
    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private User u1;
    private User u2;
    private Room room;
    private String t1;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        roomMessageRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        u1 = userRepository.save(User.builder()
                .name("Chat U1")
                .email("chat1@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("h")
                .createdAt(OffsetDateTime.now())
                .build());
        u2 = userRepository.save(User.builder()
                .name("Chat U2")
                .email("chat2@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("h")
                .createdAt(OffsetDateTime.now())
                .build());
        room = roomRepository.save(Room.builder()
                .context("leisure")
                .title("chat-room")
                .maxParticipants(10)
                .isActive(true)
                .build());
        participantRepository.save(RoomParticipant.builder().room(room).user(u1).build());
        participantRepository.save(RoomParticipant.builder().room(room).user(u2).build());
        t1 = jwtTokenService.generateAccessToken(u1.getId(), u1.getName(), u1.getEmail(), u1.getProvider().getValue());
    }

    @Test
    @DisplayName("GET messages: участник видит историю в хронологическом порядке на странице")
    void participantGetsPagedHistoryChronological() throws Exception {
        OffsetDateTime t0 = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        roomMessageRepository.save(RoomMessage.builder().room(room).user(u1).text("first").createdAt(t0).build());
        roomMessageRepository.save(RoomMessage.builder().room(room).user(u1).text("second").createdAt(t0.plusSeconds(1)).build());

        mockMvc.perform(get("/api/rooms/{roomId}/messages", room.getId())
                        .param("page", "0")
                        .param("size", "50")
                        .header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].text").value("first"))
                .andExpect(jsonPath("$.content[1].text").value("second"))
                .andExpect(jsonPath("$.content[0].userName").value("Chat U1"));
    }

    @Test
    @DisplayName("GET messages: пустая история — content [] и totalPages 0")
    void emptyHistory() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/messages", room.getId())
                        .header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    @DisplayName("GET messages: без query-параметров используются page=0 и size=50")
    void defaultPaginationParams() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/messages", room.getId())
                        .header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    @DisplayName("GET messages: страницы и хронологический порядок внутри страницы (size=2)")
    void paginationAcrossPages() throws Exception {
        OffsetDateTime t0 = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        for (int i = 0; i < 5; i++) {
            User author = (i % 2 == 0) ? u1 : u2;
            roomMessageRepository.save(RoomMessage.builder()
                    .room(room)
                    .user(author)
                    .text("m" + i)
                    .createdAt(t0.plusSeconds(i))
                    .build());
        }

        mockMvc.perform(get("/api/rooms/{roomId}/messages", room.getId())
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].text").value("m3"))
                .andExpect(jsonPath("$.content[1].text").value("m4"));

        mockMvc.perform(get("/api/rooms/{roomId}/messages", room.getId())
                        .param("page", "2")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].text").value("m0"));
    }

    @Test
    @DisplayName("GET messages: отрицательный page и нулевой size нормализуются")
    void negativePageAndZeroSizeNormalized() throws Exception {
        OffsetDateTime t0 = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        roomMessageRepository.save(RoomMessage.builder().room(room).user(u1).text("only").createdAt(t0).build());

        mockMvc.perform(get("/api/rooms/{roomId}/messages", room.getId())
                        .param("page", "-3")
                        .param("size", "0")
                        .header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].text").value("only"));
    }

    @Test
    @DisplayName("GET messages: сообщения разных авторов с корректными userName")
    void twoAuthorsInHistory() throws Exception {
        OffsetDateTime t0 = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        roomMessageRepository.save(RoomMessage.builder().room(room).user(u1).text("a1").createdAt(t0).build());
        roomMessageRepository.save(RoomMessage.builder().room(room).user(u2).text("a2").createdAt(t0.plusSeconds(1)).build());

        mockMvc.perform(get("/api/rooms/{roomId}/messages", room.getId())
                        .header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userName").value("Chat U1"))
                .andExpect(jsonPath("$.content[1].userName").value("Chat U2"))
                .andExpect(jsonPath("$.content[0].userId").value(u1.getId().toString()))
                .andExpect(jsonPath("$.content[1].userId").value(u2.getId().toString()));
    }

    @Test
    @DisplayName("GET messages: не участник получает 400")
    void nonParticipantForbidden() throws Exception {
        User outsider = userRepository.save(User.builder()
                .name("outsider")
                .email("out@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("h")
                .createdAt(OffsetDateTime.now())
                .build());
        String tout = jwtTokenService.generateAccessToken(outsider.getId(), outsider.getName(), outsider.getEmail(), outsider.getProvider().getValue());
        mockMvc.perform(get("/api/rooms/{roomId}/messages", room.getId())
                        .header("Authorization", "Bearer " + tout))
                .andExpect(status().isBadRequest());
    }
}

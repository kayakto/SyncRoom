package ru.syncroom.rooms.controller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.security.JwtTokenService;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.domain.Seat;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.rooms.repository.SeatRepository;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for SeatController.
 *
 * POST /api/rooms/{roomId}/seats/{seatId}/sit
 * POST /api/rooms/{roomId}/seats/{seatId}/leave
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("SeatController Integration Tests")
class SeatControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomParticipantRepository participantRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private JwtTokenService jwtTokenService;

    /** Mock WS broker — avoids needing a real STOMP/Redis broker */
    @MockitoBean private SimpMessagingTemplate messagingTemplate;

    /** Mock Redis — no live Redis needed in tests */
    @MockitoBean private StringRedisTemplate stringRedisTemplate;

    private User testUser;
    private User otherUser;
    private Room testRoom;
    private Seat freeSeat;
    private Seat occupiedByOtherSeat;

    private String authHeader(User user) {
        String token = jwtTokenService.generateAccessToken(
                user.getId(), user.getName(), user.getEmail(), user.getProvider().name());
        return "Bearer " + token;
    }

    private User createUser(String email) {
        return userRepository.save(User.builder()
                .name("User " + email)
                .email(email)
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());
    }

    private Seat createSeat(Room room, double x, double y, User occupant) {
        return seatRepository.save(Seat.builder()
                .room(room)
                .x(x)
                .y(y)
                .occupiedBy(occupant)
                .build());
    }

    @BeforeEach
    void setUp() {
        seatRepository.deleteAll();
        participantRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        testUser  = createUser("seat-test@example.com");
        otherUser = createUser("other@example.com");

        testRoom = roomRepository.save(Room.builder()
                .context("work")
                .title("Работа")
                .maxParticipants(10)
                .isActive(true)
                .build());

        // testUser is in the room
        participantRepository.save(RoomParticipant.builder().room(testRoom).user(testUser).build());

        freeSeat             = createSeat(testRoom, 0.25, 0.40, null);
        occupiedByOtherSeat  = createSeat(testRoom, 0.60, 0.55, otherUser);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /sit — success
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /sit — занять свободное место → 200 + occupiedBy заполнен")
    void testSit_Success() throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/sit",
                    testRoom.getId(), freeSeat.getId())
                .header("Authorization", authHeader(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(freeSeat.getId().toString()))
                .andExpect(jsonPath("$.occupiedBy.id").value(testUser.getId().toString()))
                .andExpect(jsonPath("$.occupiedBy.name").value(testUser.getName()))
                .andExpect(jsonPath("$.x").value(0.25))
                .andExpect(jsonPath("$.y").value(0.40));

        Seat updated = seatRepository.findById(freeSeat.getId()).orElseThrow();
        assertNotNull(updated.getOccupiedBy());
        assertEquals(testUser.getId(), updated.getOccupiedBy().getId());
    }

    @Test
    @DisplayName("POST /sit — идемпотентность: сесть на уже своё место → 200")
    void testSit_Idempotent() throws Exception {
        // testUser sits on freeSeat first
        freeSeat.setOccupiedBy(testUser);
        seatRepository.save(freeSeat);

        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/sit",
                    testRoom.getId(), freeSeat.getId())
                .header("Authorization", authHeader(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupiedBy.id").value(testUser.getId().toString()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /sit — auto-move
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /sit — пересадка: сесть на новое место → старое освобождается")
    void testSit_AutoMove() throws Exception {
        // testUser is already on freeSeat
        freeSeat.setOccupiedBy(testUser);
        seatRepository.save(freeSeat);

        Seat anotherSeat = createSeat(testRoom, 0.80, 0.20, null);

        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/sit",
                    testRoom.getId(), anotherSeat.getId())
                .header("Authorization", authHeader(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(anotherSeat.getId().toString()))
                .andExpect(jsonPath("$.occupiedBy.id").value(testUser.getId().toString()));

        // Old seat should be free now
        Seat oldSeat = seatRepository.findById(freeSeat.getId()).orElseThrow();
        assertNull(oldSeat.getOccupiedBy());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /sit — 409 conflict
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /sit — место занято другим пользователем → 409")
    void testSit_Conflict() throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/sit",
                    testRoom.getId(), occupiedByOtherSeat.getId())
                .header("Authorization", authHeader(testUser)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already occupied")));

        // Seat should still belong to otherUser
        Seat unchanged = seatRepository.findById(occupiedByOtherSeat.getId()).orElseThrow();
        assertEquals(otherUser.getId(), unchanged.getOccupiedBy().getId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /leave — success
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /leave — встать со своего места → 200 + occupiedBy = null")
    void testLeave_Success() throws Exception {
        // testUser sits on freeSeat
        freeSeat.setOccupiedBy(testUser);
        seatRepository.save(freeSeat);

        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/leave",
                    testRoom.getId(), freeSeat.getId())
                .header("Authorization", authHeader(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(freeSeat.getId().toString()))
                .andExpect(jsonPath("$.occupiedBy").doesNotExist());

        Seat updated = seatRepository.findById(freeSeat.getId()).orElseThrow();
        assertNull(updated.getOccupiedBy());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /leave — 403 forbidden
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /leave — встать с чужого места → 403")
    void testLeave_Forbidden() throws Exception {
        // occupiedByOtherSeat belongs to otherUser; testUser tries to stand up
        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/leave",
                    testRoom.getId(), occupiedByOtherSeat.getId())
                .header("Authorization", authHeader(testUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("not sitting")));
    }

    @Test
    @DisplayName("POST /leave — встать со свободного места → 403")
    void testLeave_FreeSeat_Forbidden() throws Exception {
        // freeSeat is null-occupied, nobody can stand up from it
        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/leave",
                    testRoom.getId(), freeSeat.getId())
                .header("Authorization", authHeader(testUser)))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unauthorised
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /sit — без токена → 403 (Spring Security 6 returns 403 for anonymous access)")
    void testSit_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/sit",
                    testRoom.getId(), freeSeat.getId()))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 404 not found
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /sit — несуществующее место → 404")
    void testSit_SeatNotFound() throws Exception {
        mockMvc.perform(post("/api/rooms/{roomId}/seats/{seatId}/sit",
                    testRoom.getId(), java.util.UUID.randomUUID())
                .header("Authorization", authHeader(testUser)))
                .andExpect(status().isNotFound());
    }
}

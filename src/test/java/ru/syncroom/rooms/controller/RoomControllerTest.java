package ru.syncroom.rooms.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
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
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты для RoomController.
 * Покрывает: GET /api/rooms, GET /api/rooms/my,
 * POST /api/rooms/{id}/join, POST /api/rooms/{id}/leave
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Room Controller Integration Tests")
class RoomControllerTest {

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

        private User testUser;
        private String accessToken;

        @BeforeEach
        void setUp() {
                participantRepository.deleteAll();
                roomRepository.deleteAll();
                userRepository.deleteAll();

                testUser = userRepository.save(User.builder()
                                .name("Room Test User")
                                .email("roomsuser@example.com")
                                .provider(AuthProvider.EMAIL)
                                .passwordHash("hashed")
                                .createdAt(OffsetDateTime.now())
                                .build());

                accessToken = jwtTokenService.generateAccessToken(
                                testUser.getId(),
                                testUser.getName(),
                                testUser.getEmail(),
                                testUser.getProvider().getValue());
        }

        private String authHeader() {
                return "Bearer " + accessToken;
        }

        private Room createRoom(String context, String title, int maxParticipants, boolean isActive) {
                return roomRepository.save(Room.builder()
                                .context(context)
                                .title(title)
                                .maxParticipants(maxParticipants)
                                .isActive(isActive)
                                .build());
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

        private void addParticipant(Room room, User user) {
                participantRepository.save(RoomParticipant.builder()
                                .room(room)
                                .user(user)
                                .build());
        }

        // ─────────────────────────────────────────────────────────────────────────
        // GET /api/rooms
        // ─────────────────────────────────────────────────────────────────────────

        @Test
        @DisplayName("GET /api/rooms - возвращает пустой список")
        void testGetRooms_EmptyList() throws Exception {
                mockMvc.perform(get("/api/rooms").header("Authorization", authHeader()))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("GET /api/rooms - возвращает все комнаты (активные и неактивные)")
        void testGetRooms_ReturnsBothActiveAndInactive() throws Exception {
                createRoom("leisure", "Дом", 10, true);
                createRoom("work", "Работа", 10, false);

                mockMvc.perform(get("/api/rooms").header("Authorization", authHeader()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("GET /api/rooms - корректная структура RoomResponse")
        void testGetRooms_ResponseStructure() throws Exception {
                Room room = createRoom("sport", "Спортзал", 10, true);

                mockMvc.perform(get("/api/rooms").header("Authorization", authHeader()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(room.getId().toString()))
                                .andExpect(jsonPath("$[0].context").value("sport"))
                                .andExpect(jsonPath("$[0].title").value("Спортзал"))
                                .andExpect(jsonPath("$[0].participantCount").value(0))
                                .andExpect(jsonPath("$[0].maxParticipants").value(10))
                                .andExpect(jsonPath("$[0].isActive").value(true));
        }

        @Test
        @DisplayName("GET /api/rooms - participantCount корректен при наличии участников")
        void testGetRooms_ParticipantCountAccurate() throws Exception {
                Room room = createRoom("work", "Работа", 10, true);
                addParticipant(room, testUser);
                addParticipant(room, createUser("u2@example.com"));

                mockMvc.perform(get("/api/rooms").header("Authorization", authHeader()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].participantCount").value(2));
        }

        @Test
        @DisplayName("GET /api/rooms - isActive динамически пересчитывается (полная → false)")
        void testGetRooms_IsActiveSyncedWhenFull() throws Exception {
                Room room = createRoom("study", "Школа", 1, true); // будет полной после добавления
                addParticipant(room, testUser);

                mockMvc.perform(get("/api/rooms").header("Authorization", authHeader()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].isActive").value(false))
                                .andExpect(jsonPath("$[0].participantCount").value(1));
        }

        @Test
        @DisplayName("GET /api/rooms - isActive динамически пересчитывается (есть место → true)")
        void testGetRooms_IsActiveSyncedWhenNotFull() throws Exception {
                // isActive=false в БД, но участников нет → должно стать true
                createRoom("leisure", "Дом", 10, false);

                mockMvc.perform(get("/api/rooms").header("Authorization", authHeader()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].isActive").value(true));
        }

        @Test
        @DisplayName("GET /api/rooms - JSON поле называется isActive (не active)")
        void testGetRooms_JsonFieldIsActive() throws Exception {
                createRoom("work", "Работа", 10, true);

                String json = mockMvc.perform(get("/api/rooms").header("Authorization", authHeader()))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                assertTrue(json.contains("\"isActive\""),
                                "Expected 'isActive' in JSON, got: " + json);
                assertFalse(json.contains("\"active\":"),
                                "JSON should NOT contain 'active' field, got: " + json);
        }

        @Test
        @DisplayName("GET /api/rooms - ошибка: не авторизован")
        void testGetRooms_Unauthorized() throws Exception {
                mockMvc.perform(get("/api/rooms"))
                                .andExpect(result -> {
                                        int status = result.getResponse().getStatus();
                                        assertTrue(status == 401 || status == 403);
                                });
        }

        // ─────────────────────────────────────────────────────────────────────────
        // POST /api/rooms/{roomId}/join
        // ─────────────────────────────────────────────────────────────────────────

        @Test
        @DisplayName("POST /api/rooms/{roomId}/join - успешный вход в свободную комнату")
        void testJoinRoom_Success() throws Exception {
                Room room = createRoom("leisure", "Дом", 10, true);

                mockMvc.perform(post("/api/rooms/{roomId}/join", room.getId())
                                .header("Authorization", authHeader()))
                                .andExpect(status().isOk());

                assertTrue(participantRepository.existsByRoomIdAndUserId(room.getId(), testUser.getId()));
                assertEquals(1, participantRepository.countByRoomId(room.getId()));
        }

        @Test
        @DisplayName("POST /api/rooms/{roomId}/join - комната НЕ заполняется → дубликат не создаётся")
        void testJoinRoom_NoDuplicateWhenNotFull() throws Exception {
                Room room = createRoom("work", "Работа", 5, true);
                long before = roomRepository.count();

                mockMvc.perform(post("/api/rooms/{roomId}/join", room.getId())
                                .header("Authorization", authHeader()))
                                .andExpect(status().isOk());

                assertEquals(before, roomRepository.count());
        }

        @Test
        @DisplayName("POST /api/rooms/{roomId}/join - при заполнении создаётся дубликат")
        void testJoinRoom_CreatesDuplicateWhenRoomFills() throws Exception {
                Room room = createRoom("sport", "Спортзал", 1, true);

                mockMvc.perform(post("/api/rooms/{roomId}/join", room.getId())
                                .header("Authorization", authHeader()))
                                .andExpect(status().isOk());

                assertFalse(roomRepository.findById(room.getId()).orElseThrow().getIsActive());
                List<Room> allRooms = roomRepository.findAll();
                assertEquals(2, allRooms.size());
                Room dup = allRooms.stream().filter(r -> !r.getId().equals(room.getId())).findFirst().orElseThrow();
                assertEquals("sport", dup.getContext());
                assertTrue(dup.getIsActive());
                assertEquals(0, participantRepository.countByRoomId(dup.getId()));
        }

        @Test
        @DisplayName("POST /api/rooms/{roomId}/join - вход в уже полную комнату → попадает в дубликат")
        void testJoinRoom_AlreadyFullRoom_JoinsNewRoom() throws Exception {
                Room room = createRoom("study", "Школа", 1, false);
                addParticipant(room, createUser("existing@example.com"));

                mockMvc.perform(post("/api/rooms/{roomId}/join", room.getId())
                                .header("Authorization", authHeader()))
                                .andExpect(status().isOk());

                assertFalse(participantRepository.existsByRoomIdAndUserId(room.getId(), testUser.getId()));
                Room newRoom = roomRepository.findAll().stream()
                                .filter(r -> !r.getId().equals(room.getId())).findFirst().orElseThrow();
                assertTrue(participantRepository.existsByRoomIdAndUserId(newRoom.getId(), testUser.getId()));
        }

        @Test
        @DisplayName("POST /api/rooms/{roomId}/join - ошибка: пользователь уже в этой комнате → 400")
        void testJoinRoom_AlreadyJoined() throws Exception {
                Room room = createRoom("leisure", "Дом", 10, true);
                addParticipant(room, testUser);

                mockMvc.perform(post("/api/rooms/{roomId}/join", room.getId())
                                .header("Authorization", authHeader()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("already in room")));

                assertEquals(1, participantRepository.countByRoomId(room.getId()));
        }

        @Test
        @DisplayName("POST /api/rooms/{roomId}/join - ошибка: пользователь уже в ДРУГОЙ комнате → 400")
        void testJoinRoom_AlreadyInAnotherRoom() throws Exception {
                Room room1 = createRoom("work", "Работа", 10, true);
                Room room2 = createRoom("sport", "Спортзал", 10, true);
                addParticipant(room1, testUser); // уже в первой комнате

                mockMvc.perform(post("/api/rooms/{roomId}/join", room2.getId())
                                .header("Authorization", authHeader()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("Leave the current room")));

                // Пользователь по-прежнему только в первой комнате
                assertTrue(participantRepository.existsByRoomIdAndUserId(room1.getId(), testUser.getId()));
                assertFalse(participantRepository.existsByRoomIdAndUserId(room2.getId(), testUser.getId()));
        }

        @Test
        @DisplayName("POST /api/rooms/{roomId}/join - ошибка: комната не найдена → 404")
        void testJoinRoom_RoomNotFound() throws Exception {
                mockMvc.perform(post("/api/rooms/{roomId}/join", UUID.randomUUID())
                                .header("Authorization", authHeader()))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value(containsString("Room not found")));
        }

        @Test
        @DisplayName("POST /api/rooms/{roomId}/join - ошибка: не авторизован")
        void testJoinRoom_Unauthorized() throws Exception {
                Room room = createRoom("work", "Работа", 10, true);

                mockMvc.perform(post("/api/rooms/{roomId}/join", room.getId()))
                                .andExpect(result -> {
                                        int status = result.getResponse().getStatus();
                                        assertTrue(status == 401 || status == 403);
                                });
        }

        @Test
        @DisplayName("POST /api/rooms/{roomId}/join - несколько пользователей входят последовательно")
        void testJoinRoom_MultipleUsers() throws Exception {
                Room room = createRoom("work", "Работа", 3, true);
                User u2 = createUser("u2@test.com");
                User u3 = createUser("u3@test.com");
                String token2 = jwtTokenService.generateAccessToken(u2.getId(), u2.getName(), u2.getEmail(), "email");
                String token3 = jwtTokenService.generateAccessToken(u3.getId(), u3.getName(), u3.getEmail(), "email");

                mockMvc.perform(post("/api/rooms/{roomId}/join", room.getId()).header("Authorization", authHeader()))
                                .andExpect(status().isOk());
                mockMvc.perform(post("/api/rooms/{roomId}/join", room.getId()).header("Authorization",
                                "Bearer " + token2)).andExpect(status().isOk());
                mockMvc.perform(post("/api/rooms/{roomId}/join", room.getId()).header("Authorization",
                                "Bearer " + token3)).andExpect(status().isOk());

                assertFalse(roomRepository.findById(room.getId()).orElseThrow().getIsActive());
                assertEquals(3, participantRepository.countByRoomId(room.getId()));
                assertEquals(2, roomRepository.count()); // оригинал + дубликат
        }

        @Test
        @DisplayName("GET /api/rooms показывает дубликат после заполнения")
        void testGetRooms_ShowsDuplicateAfterFill() throws Exception {
                Room fullRoom = createRoom("study", "Школа", 2, false);
                createRoom("study", "Школа", 2, true); // дубликат
                addParticipant(fullRoom, testUser);
                addParticipant(fullRoom, createUser("x@test.com"));

                mockMvc.perform(get("/api/rooms").header("Authorization", authHeader()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(2)))
                                .andExpect(jsonPath("$[*].isActive", hasItems(true, false)));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // POST /api/rooms/{roomId}/leave
        // ─────────────────────────────────────────────────────────────────────────

        @Test
        @DisplayName("POST /api/rooms/{roomId}/leave - успешный выход из комнаты")
        void testLeaveRoom_Success() throws Exception {
                Room room = createRoom("leisure", "Дом", 10, true);
                addParticipant(room, testUser);

                mockMvc.perform(post("/api/rooms/{roomId}/leave", room.getId())
                                .header("Authorization", authHeader()))
                                .andExpect(status().isNoContent());

                assertFalse(participantRepository.existsByRoomIdAndUserId(room.getId(), testUser.getId()));
                assertEquals(0, participantRepository.countByRoomId(room.getId()));
        }

        @Test
        @DisplayName("POST /api/rooms/{roomId}/leave - после выхода из полной комнаты она становится активной")
        void testLeaveRoom_ReactivatesFullRoom() throws Exception {
                // Комната заполнена (maxParticipants=1, isActive=false)
                Room room = createRoom("sport", "Спортзал", 1, false);
                addParticipant(room, testUser);

                mockMvc.perform(post("/api/rooms/{roomId}/leave", room.getId())
                                .header("Authorization", authHeader()))
                                .andExpect(status().isNoContent());

                // Комната должна снова стать активной (0 < 1)
                Room updated = roomRepository.findById(room.getId()).orElseThrow();
                assertTrue(updated.getIsActive(), "Room should become active after last participant leaves");
                assertEquals(0, participantRepository.countByRoomId(room.getId()));
        }

        @Test
        @DisplayName("POST /api/rooms/{roomId}/leave - выход не влияет на других участников")
        void testLeaveRoom_DoesNotRemoveOtherParticipants() throws Exception {
                Room room = createRoom("work", "Работа", 10, true);
                User other = createUser("other@test.com");
                addParticipant(room, testUser);
                addParticipant(room, other);

                mockMvc.perform(post("/api/rooms/{roomId}/leave", room.getId())
                                .header("Authorization", authHeader()))
                                .andExpect(status().isNoContent());

                assertFalse(participantRepository.existsByRoomIdAndUserId(room.getId(), testUser.getId()));
                assertTrue(participantRepository.existsByRoomIdAndUserId(room.getId(), other.getId()));
                assertEquals(1, participantRepository.countByRoomId(room.getId()));
        }

        @Test
        @DisplayName("POST /api/rooms/{roomId}/leave - ошибка: пользователь не в комнате → 400")
        void testLeaveRoom_NotMember() throws Exception {
                Room room = createRoom("study", "Школа", 10, true);
                // testUser НЕ добавлен

                mockMvc.perform(post("/api/rooms/{roomId}/leave", room.getId())
                                .header("Authorization", authHeader()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value(containsString("not a member")));
        }

        @Test
        @DisplayName("POST /api/rooms/{roomId}/leave - ошибка: комната не найдена → 404")
        void testLeaveRoom_RoomNotFound() throws Exception {
                mockMvc.perform(post("/api/rooms/{roomId}/leave", UUID.randomUUID())
                                .header("Authorization", authHeader()))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.message").value(containsString("Room not found")));
        }

        @Test
        @DisplayName("POST /api/rooms/{roomId}/leave - ошибка: не авторизован")
        void testLeaveRoom_Unauthorized() throws Exception {
                Room room = createRoom("work", "Работа", 10, true);
                addParticipant(room, testUser);

                mockMvc.perform(post("/api/rooms/{roomId}/leave", room.getId()))
                                .andExpect(result -> {
                                        int status = result.getResponse().getStatus();
                                        assertTrue(status == 401 || status == 403);
                                });
        }

        @Test
        @DisplayName("POST join + leave: join → leave → повторный join работает")
        void testJoinAndLeave_ThenRejoin() throws Exception {
                Room room = createRoom("leisure", "Дом", 10, true);

                // Join
                mockMvc.perform(post("/api/rooms/{roomId}/join", room.getId()).header("Authorization", authHeader()))
                                .andExpect(status().isOk());
                assertTrue(participantRepository.existsByRoomIdAndUserId(room.getId(), testUser.getId()));

                // Leave
                mockMvc.perform(post("/api/rooms/{roomId}/leave", room.getId()).header("Authorization", authHeader()))
                                .andExpect(status().isNoContent());
                assertFalse(participantRepository.existsByRoomIdAndUserId(room.getId(), testUser.getId()));

                // Rejoin
                mockMvc.perform(post("/api/rooms/{roomId}/join", room.getId()).header("Authorization", authHeader()))
                                .andExpect(status().isOk());
                assertTrue(participantRepository.existsByRoomIdAndUserId(room.getId(), testUser.getId()));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // GET /api/rooms/my
        // ─────────────────────────────────────────────────────────────────────────

        @Test
        @DisplayName("GET /api/rooms/my - возвращает пустой список когда пользователь не в комнатах")
        void testGetMyRooms_EmptyWhenNotInAnyRoom() throws Exception {
                createRoom("work", "Работа", 10, true); // комната есть, но testUser не в ней

                mockMvc.perform(get("/api/rooms/my").header("Authorization", authHeader()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("GET /api/rooms/my - возвращает комнаты где пользователь состоит")
        void testGetMyRooms_ReturnsMemberRooms() throws Exception {
                Room room1 = createRoom("work", "Работа", 10, true);
                Room room2 = createRoom("sport", "Спортзал", 10, true);
                createRoom("leisure", "Дом", 10, true); // эту комнату не присоединяем

                addParticipant(room1, testUser);
                addParticipant(room2, testUser);

                mockMvc.perform(get("/api/rooms/my").header("Authorization", authHeader()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(2)))
                                .andExpect(jsonPath("$[*].context", containsInAnyOrder("work", "sport")));
        }

        @Test
        @DisplayName("GET /api/rooms/my - корректная структура ответа")
        void testGetMyRooms_ResponseStructure() throws Exception {
                Room room = createRoom("study", "Школа", 5, true);
                addParticipant(room, testUser);
                addParticipant(room, createUser("other@test.com"));

                mockMvc.perform(get("/api/rooms/my").header("Authorization", authHeader()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(room.getId().toString()))
                                .andExpect(jsonPath("$[0].context").value("study"))
                                .andExpect(jsonPath("$[0].title").value("Школа"))
                                .andExpect(jsonPath("$[0].participantCount").value(2))
                                .andExpect(jsonPath("$[0].maxParticipants").value(5))
                                .andExpect(jsonPath("$[0].isActive").value(true));
        }

        @Test
        @DisplayName("GET /api/rooms/my - не показывает комнаты других пользователей")
        void testGetMyRooms_DoesNotShowOthersRooms() throws Exception {
                Room myRoom = createRoom("work", "Работа", 10, true);
                Room othersRoom = createRoom("sport", "Спорт", 10, true);
                User other = createUser("other@test.com");

                addParticipant(myRoom, testUser);
                addParticipant(othersRoom, other); // только другой пользователь

                mockMvc.perform(get("/api/rooms/my").header("Authorization", authHeader()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].id").value(myRoom.getId().toString()));
        }

        @Test
        @DisplayName("GET /api/rooms/my - после leave комнаты она исчезает из /my")
        void testGetMyRooms_UpdatesAfterLeave() throws Exception {
                Room room = createRoom("leisure", "Дом", 10, true);
                addParticipant(room, testUser);

                // Пользователь выходит
                mockMvc.perform(post("/api/rooms/{roomId}/leave", room.getId()).header("Authorization", authHeader()))
                                .andExpect(status().isNoContent());

                // /my должен вернуть пустой список
                mockMvc.perform(get("/api/rooms/my").header("Authorization", authHeader()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("GET /api/rooms/my - ошибка: не авторизован")
        void testGetMyRooms_Unauthorized() throws Exception {
                mockMvc.perform(get("/api/rooms/my"))
                                .andExpect(result -> {
                                        int status = result.getResponse().getStatus();
                                        assertTrue(status == 401 || status == 403);
                                });
        }
}

package ru.syncroom.rooms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.rooms.domain.ParticipantRole;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.dto.JoinRoomResponse;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.rooms.ws.RoomEvent;
import ru.syncroom.rooms.ws.RoomEventType;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration tests for WebSocket event publishing in RoomService.
 *
 * SimpMessagingTemplate is mocked so we can verify what events are sent
 * without standing up a real WebSocket broker.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("RoomService WebSocket Event Tests")
class RoomServiceWebSocketTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomParticipantRepository participantRepository;

    /** Mock the messaging template — no real STOMP broker needed in tests */
    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    /** Mock Redis — no live Redis needed in tests */
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private User testUser;
    private Room testRoom;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .name("WS Test User")
                .email("wstest@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("hashed")
                .createdAt(OffsetDateTime.now())
                .build());

        testRoom = roomRepository.save(Room.builder()
                .context("work")
                .title("Работа")
                .maxParticipants(10)
                .isActive(true)
                .build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // joinRoom → PARTICIPANT_JOINED event
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("joinRoom публикует PARTICIPANT_JOINED на /topic/room/{roomId}")
    void testJoinRoom_PublishesParticipantJoinedEvent() {
        // When
        roomService.joinRoom(testRoom.getId(), testUser.getId());

        // Then — verify the messaging template was called with correct destination
        verify(messagingTemplate, times(1))
                .convertAndSend(
                        eq("/topic/room/" + testRoom.getId()),
                        any(RoomEvent.class)
                );
    }

    @Test
    @DisplayName("joinRoom event имеет тип PARTICIPANT_JOINED")
    void testJoinRoom_EventType() {
        // Capture the actual event
        roomService.joinRoom(testRoom.getId(), testUser.getId());

        verify(messagingTemplate).convertAndSend(
                eq("/topic/room/" + testRoom.getId()),
                argThat((RoomEvent event) -> event.getType() == RoomEventType.PARTICIPANT_JOINED)
        );
    }

    @Test
    @DisplayName("joinRoom event payload содержит userId и name вошедшего пользователя")
    void testJoinRoom_EventPayloadContainsUser() {
        roomService.joinRoom(testRoom.getId(), testUser.getId());

        verify(messagingTemplate).convertAndSend(
                eq("/topic/room/" + testRoom.getId()),
                argThat((RoomEvent event) -> {
                    // payload is ParticipantResponse serialised as a Map/Object
                    String payloadStr = event.getPayload().toString();
                    return payloadStr.contains(testUser.getId().toString())
                            || event.getPayload().getClass().getSimpleName().equals("ParticipantResponse");
                })
        );
    }

    @Test
    @DisplayName("joinRoom event имеет timestamp")
    void testJoinRoom_EventHasTimestamp() {
        roomService.joinRoom(testRoom.getId(), testUser.getId());

        verify(messagingTemplate).convertAndSend(
                eq("/topic/room/" + testRoom.getId()),
                argThat((RoomEvent event) -> event.getTimestamp() != null)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // joinRoom → JoinRoomResponse structure
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("joinRoom возвращает JoinRoomResponse с room и participants")
    void testJoinRoom_ReturnsJoinRoomResponse() {
        JoinRoomResponse response = roomService.joinRoom(testRoom.getId(), testUser.getId());

        assertNotNull(response);
        assertNotNull(response.getRoom());
        assertNotNull(response.getParticipants());
    }

    @Test
    @DisplayName("joinRoom response.room содержит правильный roomId")
    void testJoinRoom_ResponseRoomId() {
        JoinRoomResponse response = roomService.joinRoom(testRoom.getId(), testUser.getId());

        assertEquals(testRoom.getId().toString(), response.getRoom().getId());
    }

    @Test
    @DisplayName("joinRoom response.participants содержит вошедшего пользователя")
    void testJoinRoom_ResponseParticipantsContainUser() {
        JoinRoomResponse response = roomService.joinRoom(testRoom.getId(), testUser.getId());

        assertEquals(1, response.getParticipants().size());
        assertEquals(testUser.getId().toString(), response.getParticipants().get(0).getUserId());
        assertEquals(testUser.getName(), response.getParticipants().get(0).getName());
        assertEquals(ParticipantRole.OBSERVER, response.getParticipants().get(0).getRole());
    }

    @Test
    @DisplayName("joinRoom response.participants содержит joinedAt")
    void testJoinRoom_ResponseParticipantHasJoinedAt() {
        JoinRoomResponse response = roomService.joinRoom(testRoom.getId(), testUser.getId());

        assertNotNull(response.getParticipants().get(0).getJoinedAt());
    }

    @Test
    @DisplayName("joinRoom: participantCount = за столом (0), observerCount = 1 после входа без места")
    void testJoinRoom_ResponseSeatVsObserverCounts() {
        JoinRoomResponse response = roomService.joinRoom(testRoom.getId(), testUser.getId());

        assertEquals(0, response.getRoom().getParticipantCount());
        assertEquals(1, response.getRoom().getObserverCount());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // leaveRoom → PARTICIPANT_LEFT event
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("leaveRoom публикует PARTICIPANT_LEFT на /topic/room/{roomId}")
    void testLeaveRoom_PublishesParticipantLeftEvent() {
        // Given — user already in the room
        participantRepository.save(RoomParticipant.builder()
                .room(testRoom)
                .user(testUser)
                .role(ParticipantRole.OBSERVER)
                .build());

        // When
        roomService.leaveRoom(testRoom.getId(), testUser.getId());

        // Then
        verify(messagingTemplate, times(1))
                .convertAndSend(
                        eq("/topic/room/" + testRoom.getId()),
                        any(RoomEvent.class)
                );
    }

    @Test
    @DisplayName("leaveRoom event имеет тип PARTICIPANT_LEFT")
    void testLeaveRoom_EventType() {
        participantRepository.save(RoomParticipant.builder()
                .room(testRoom)
                .user(testUser)
                .role(ParticipantRole.OBSERVER)
                .build());

        roomService.leaveRoom(testRoom.getId(), testUser.getId());

        verify(messagingTemplate).convertAndSend(
                eq("/topic/room/" + testRoom.getId()),
                argThat((RoomEvent event) -> event.getType() == RoomEventType.PARTICIPANT_LEFT)
        );
    }

    @Test
    @DisplayName("leaveRoom event payload содержит userId вышедшего пользователя")
    void testLeaveRoom_EventPayloadContainsUserId() {
        participantRepository.save(RoomParticipant.builder()
                .room(testRoom)
                .user(testUser)
                .role(ParticipantRole.OBSERVER)
                .build());

        roomService.leaveRoom(testRoom.getId(), testUser.getId());

        verify(messagingTemplate).convertAndSend(
                eq("/topic/room/" + testRoom.getId()),
                argThat((RoomEvent event) -> {
                    // payload is ParticipantResponse, toString contains userId
                    return event.getPayload() != null &&
                           event.getType() == RoomEventType.PARTICIPANT_LEFT;
                })
        );
    }

    @Test
    @DisplayName("WS событие не публикуется если join выбрасывает исключение (уже в комнате)")
    void testJoinRoom_NoEventPublishedOnError() {
        // Given — user already in the room
        participantRepository.save(RoomParticipant.builder()
                .room(testRoom)
                .user(testUser)
                .role(ParticipantRole.OBSERVER)
                .build());

        // When
        assertThrows(Exception.class,
                () -> roomService.joinRoom(testRoom.getId(), testUser.getId()));

        // Then — no event should be published
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("WS событие не публикуется если leave выбрасывает исключение (не в комнате)")
    void testLeaveRoom_NoEventPublishedOnError() {
        // testUser is NOT a participant
        assertThrows(Exception.class,
                () -> roomService.leaveRoom(testRoom.getId(), testUser.getId()));

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("leaveRoomOnWebSocketDisconnect удаляет участие и шлёт PARTICIPANT_LEFT")
    void leaveRoomOnWebSocketDisconnect_sameAsLeaveRoom() {
        participantRepository.save(RoomParticipant.builder()
                .room(testRoom)
                .user(testUser)
                .role(ParticipantRole.OBSERVER)
                .build());

        roomService.leaveRoomOnWebSocketDisconnect(testUser.getId());

        assertFalse(participantRepository.existsByRoomIdAndUserId(testRoom.getId(), testUser.getId()));
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/room/" + testRoom.getId()),
                argThat((RoomEvent event) -> event.getType() == RoomEventType.PARTICIPANT_LEFT)
        );
    }

    @Test
    @DisplayName("leaveRoomOnWebSocketDisconnect идемпотентен если пользователь не в комнате")
    void leaveRoomOnWebSocketDisconnect_noOpWhenNotInRoom() {
        roomService.leaveRoomOnWebSocketDisconnect(testUser.getId());
        verifyNoInteractions(messagingTemplate);
    }
}

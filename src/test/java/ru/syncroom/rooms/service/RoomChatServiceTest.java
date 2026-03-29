package ru.syncroom.rooms.service;

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
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomMessage;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.dto.ChatMessageResponse;
import ru.syncroom.rooms.repository.RoomMessageRepository;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("RoomChatService")
class RoomChatServiceTest {

    @Autowired
    private RoomChatService roomChatService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomParticipantRepository participantRepository;
    @Autowired
    private RoomMessageRepository roomMessageRepository;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private User u1;
    private User u2;
    private Room room;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        roomMessageRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        u1 = userRepository.save(User.builder()
                .name("Sender")
                .email("sender@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("x")
                .createdAt(OffsetDateTime.now())
                .build());
        u2 = userRepository.save(User.builder()
                .name("Other")
                .email("other@example.com")
                .provider(AuthProvider.EMAIL)
                .passwordHash("x")
                .createdAt(OffsetDateTime.now())
                .build());
        room = roomRepository.save(Room.builder()
                .context("work")
                .title("r")
                .maxParticipants(5)
                .isActive(true)
                .build());
        participantRepository.save(RoomParticipant.builder().room(room).user(u1).build());
    }

    @Test
    @DisplayName("sendMessage сохраняет и шлёт в /topic/room/{id}/chat")
    void sendBroadcastsToChatTopic() {
        UUID roomId = room.getId();
        roomChatService.sendMessage(roomId, u1.getId(), "Привет!");

        assertEquals(1, roomMessageRepository.count());
        ArgumentCaptor<ChatMessageResponse> captor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        verify(messagingTemplate).convertAndSend(eq(RoomChatService.topic(roomId)), captor.capture());
        ChatMessageResponse sent = captor.getValue();
        assertNotNull(sent.getId());
        assertEquals("Привет!", sent.getText());
        assertEquals(u1.getId().toString(), sent.getUserId());
        assertEquals("Sender", sent.getUserName());
    }

    @Test
    @DisplayName("sendMessage: пустой и null текст — 400")
    void sendMessage_rejectsEmptyAndNull() {
        UUID roomId = room.getId();
        assertThrows(BadRequestException.class, () -> roomChatService.sendMessage(roomId, u1.getId(), ""));
        assertThrows(BadRequestException.class, () -> roomChatService.sendMessage(roomId, u1.getId(), "   "));
        assertThrows(BadRequestException.class, () -> roomChatService.sendMessage(roomId, u1.getId(), null));
        assertEquals(0, roomMessageRepository.count());
    }

    @Test
    @DisplayName("sendMessage: длина больше 4000 символов — 400")
    void sendMessage_rejectsTooLong() {
        String tooLong = "x".repeat(4001);
        assertThrows(BadRequestException.class, () -> roomChatService.sendMessage(room.getId(), u1.getId(), tooLong));
        assertEquals(0, roomMessageRepository.count());
    }

    @Test
    @DisplayName("sendMessage: обрезка пробелов по краям")
    void sendMessage_trimsWhitespace() {
        roomChatService.sendMessage(room.getId(), u1.getId(), "  hello  ");
        RoomMessage persisted = roomMessageRepository.findAll().getFirst();
        assertEquals("hello", persisted.getText());
        ArgumentCaptor<ChatMessageResponse> captor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        verify(messagingTemplate).convertAndSend(eq(RoomChatService.topic(room.getId())), captor.capture());
        assertEquals("hello", captor.getValue().getText());
    }

    @Test
    @DisplayName("sendMessage и getMessages: не участник — 400")
    void nonParticipantCannotSendOrRead() {
        UUID roomId = room.getId();
        assertThrows(BadRequestException.class, () -> roomChatService.sendMessage(roomId, u2.getId(), "no"));
        assertThrows(BadRequestException.class, () -> roomChatService.getMessages(roomId, u2.getId(), 0, 50));
    }

    @Test
    @DisplayName("getMessages: пустая история")
    void getMessages_empty() {
        var page = roomChatService.getMessages(room.getId(), u1.getId(), 0, 50);
        assertNotNull(page.getContent());
        assertEquals(0, page.getContent().size());
        assertEquals(0, page.getTotalPages());
    }

    @Test
    @DisplayName("getMessages: totalPages и порядок на второй странице")
    void getMessages_pagination() {
        OffsetDateTime t0 = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        for (int i = 0; i < 4; i++) {
            roomMessageRepository.save(RoomMessage.builder()
                    .room(room)
                    .user(u1)
                    .text("x" + i)
                    .createdAt(t0.plusSeconds(i))
                    .build());
        }
        var page1 = roomChatService.getMessages(room.getId(), u1.getId(), 1, 2);
        assertEquals(2, page1.getTotalPages());
        assertEquals(2, page1.getContent().size());
        assertEquals("x0", page1.getContent().get(0).getText());
        assertEquals("x1", page1.getContent().get(1).getText());
    }

    @Test
    @DisplayName("sendMessage: несколько сообщений — столько же broadcast")
    void sendMessage_multipleBroadcasts() {
        UUID roomId = room.getId();
        roomChatService.sendMessage(roomId, u1.getId(), "a");
        roomChatService.sendMessage(roomId, u1.getId(), "b");
        roomChatService.sendMessage(roomId, u1.getId(), "c");
        assertEquals(3, roomMessageRepository.count());
        verify(messagingTemplate, times(3)).convertAndSend(eq(RoomChatService.topic(roomId)), any(ChatMessageResponse.class));
    }

    @Test
    @DisplayName("граница длины: ровно 4000 символов принимается")
    void sendMessage_exactlyMaxLengthAccepted() {
        String ok = "x".repeat(4000);
        roomChatService.sendMessage(room.getId(), u1.getId(), ok);
        assertEquals(1, roomMessageRepository.count());
        assertEquals(4000, roomMessageRepository.findAll().getFirst().getText().length());
    }
}

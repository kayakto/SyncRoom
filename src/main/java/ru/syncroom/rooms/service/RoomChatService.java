package ru.syncroom.rooms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.rooms.domain.RoomMessage;
import ru.syncroom.rooms.dto.ChatMessageResponse;
import ru.syncroom.rooms.dto.PagedChatMessagesResponse;
import ru.syncroom.rooms.repository.RoomMessageRepository;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomChatService {

    private static final int MAX_MESSAGE_LENGTH = 4000;

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public PagedChatMessagesResponse getMessages(UUID roomId, UUID userId, int page, int size) {
        requireParticipant(roomId, userId);
        if (!roomRepository.existsById(roomId)) {
            throw new NotFoundException("Room not found");
        }
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        PageRequest pr = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RoomMessage> result = roomMessageRepository.findByRoom_IdOrderByCreatedAtDesc(roomId, pr);
        List<ChatMessageResponse> content = new ArrayList<>(result.getContent().stream().map(this::toResponse).toList());
        Collections.reverse(content);
        return PagedChatMessagesResponse.builder()
                .content(content)
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional
    public ChatMessageResponse sendMessage(UUID roomId, UUID userId, String rawText) {
        requireParticipant(roomId, userId);
        if (!roomRepository.existsById(roomId)) {
            throw new NotFoundException("Room not found");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        String text = validateText(rawText);
        RoomMessage saved = roomMessageRepository.save(RoomMessage.builder()
                .room(roomRepository.getReferenceById(roomId))
                .user(user)
                .text(text)
                .build());
        ChatMessageResponse dto = toResponse(saved);
        messagingTemplate.convertAndSend(topic(roomId), dto);
        return dto;
    }

    @Transactional
    public ChatMessageResponse sendSystemBotMessage(UUID roomId, UUID botUserId, String rawText) {
        if (!roomRepository.existsById(roomId)) {
            throw new NotFoundException("Room not found");
        }
        User user = userRepository.findById(botUserId).orElseThrow(() -> new NotFoundException("User not found"));
        String text = validateText(rawText);
        RoomMessage saved = roomMessageRepository.save(RoomMessage.builder()
                .room(roomRepository.getReferenceById(roomId))
                .user(user)
                .text(text)
                .build());
        ChatMessageResponse dto = toResponse(saved);
        messagingTemplate.convertAndSend(topic(roomId), dto);
        return dto;
    }

    private void requireParticipant(UUID roomId, UUID userId) {
        if (!roomParticipantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new BadRequestException("User is not a participant of this room");
        }
    }

    private static String validateText(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Message text must not be empty");
        }
        String t = raw.trim();
        if (t.length() > MAX_MESSAGE_LENGTH) {
            throw new BadRequestException("Message is too long (max " + MAX_MESSAGE_LENGTH + " characters)");
        }
        return t;
    }

    private ChatMessageResponse toResponse(RoomMessage m) {
        boolean isBot = m.getUser().getEmail() != null && m.getUser().getEmail().endsWith("@syncroom.local");
        return ChatMessageResponse.builder()
                .id(m.getId().toString())
                .userId(m.getUser().getId().toString())
                .userName(m.getUser().getName())
                .isBot(isBot)
                .text(m.getText())
                .createdAt(m.getCreatedAt())
                .build();
    }

    public static String topic(UUID roomId) {
        return "/topic/room/" + roomId + "/chat";
    }
}

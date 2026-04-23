package ru.syncroom.rooms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.rooms.domain.ParticipantRole;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.dto.JoinRoomResponse;
import ru.syncroom.rooms.dto.ParticipantResponse;
import ru.syncroom.rooms.dto.RoomResponse;
import ru.syncroom.rooms.dto.SeatDto;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.rooms.repository.SeatRepository;
import ru.syncroom.study.repository.StudyTaskRepository;
import ru.syncroom.games.service.GameService;
import ru.syncroom.rooms.ws.RoomEvent;
import ru.syncroom.rooms.ws.RoomEventType;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final SeatRepository seatRepository;
    private final SeatService seatService;
    private final GameService gameService;
    private final StudyTaskRepository studyTaskRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static final String ROOM_TOPIC = "/topic/room/";

    private void publish(UUID roomId, RoomEventType type, Object payload) {
        RoomEvent event = RoomEvent.of(type, payload);
        messagingTemplate.convertAndSend(ROOM_TOPIC + roomId, event);
        log.debug("WS event {} published to room {}", type, roomId);
    }

    // ─── Public API ─────────────────────────────────────────────────────────

    /**
     * Returns all rooms with current participant count and seat state.
     */
    @Transactional
    public List<RoomResponse> getAllRooms() {
        List<Room> rooms = roomRepository.findAll();

        return rooms.stream().map(room -> {
            int count = participantRepository.countByRoomId(room.getId());
            boolean active = count < room.getMaxParticipants();

            if (room.getIsActive() != active) {
                room.setIsActive(active);
                roomRepository.save(room);
            }

            int seated = seatRepository.countOccupiedByRoomId(room.getId());
            List<SeatDto> seats = seatRepository.findByRoomId(room.getId())
                    .stream().map(SeatDto::from).toList();
            return RoomResponse.from(room, seated, count - seated, seats);
        }).toList();
    }

    /**
     * Join a room. Returns full room state (with seats) + current participant list.
     * Broadcasts PARTICIPANT_JOINED.
     */
    @Transactional
    public JoinRoomResponse joinRoom(UUID roomId, UUID userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // One room at a time
        List<RoomParticipant> existing = participantRepository.findByUserId(userId);
        if (!existing.isEmpty()) {
            throw new BadRequestException(
                    "User is already in room " + existing.getFirst().getRoom().getId() +
                    ". Leave the current room before joining another.");
        }

        int currentCount = participantRepository.countByRoomId(roomId);
        UUID actualRoomId;

        if (currentCount >= room.getMaxParticipants()) {
            room.setIsActive(false);
            roomRepository.save(room);

            Room newRoom = roomRepository.save(Room.builder()
                    .context(room.getContext())
                    .title(room.getTitle())
                    .maxParticipants(room.getMaxParticipants())
                    .backgroundPicture(room.getBackgroundPicture())
                    .isActive(true)
                    .build());

            participantRepository.save(RoomParticipant.builder()
                    .room(newRoom).user(user).role(ParticipantRole.OBSERVER).build());
            actualRoomId = newRoom.getId();
        } else {
            participantRepository.save(RoomParticipant.builder()
                    .room(room).user(user).role(ParticipantRole.OBSERVER).build());

            if (currentCount + 1 >= room.getMaxParticipants()) {
                room.setIsActive(false);
                roomRepository.save(room);

                roomRepository.save(Room.builder()
                        .context(room.getContext())
                        .title(room.getTitle())
                        .maxParticipants(room.getMaxParticipants())
                        .backgroundPicture(room.getBackgroundPicture())
                        .isActive(true)
                        .build());
            }
            actualRoomId = room.getId();
        }

        Room actualRoom = roomRepository.findById(actualRoomId).orElseThrow();
        List<RoomParticipant> allParticipants = participantRepository.findByRoomId(actualRoomId);
        int totalMembers = allParticipants.size();
        int seatedCount = seatRepository.countOccupiedByRoomId(actualRoomId);

        RoomParticipant myParticipation = participantRepository
                .findByRoomIdAndUserId(actualRoomId, userId).orElseThrow();
        ParticipantResponse myDto = ParticipantResponse.from(myParticipation);

        publish(actualRoomId, RoomEventType.PARTICIPANT_JOINED, myDto);

        List<ParticipantResponse> participantDtos = allParticipants.stream()
                .map(ParticipantResponse::from).toList();

        List<SeatDto> seats = seatRepository.findByRoomId(actualRoomId)
                .stream().map(SeatDto::from).toList();

        return JoinRoomResponse.builder()
                .room(RoomResponse.from(actualRoom, seatedCount, totalMembers - seatedCount, seats))
                .participants(participantDtos)
                .build();
    }

    /**
     * Leave a room.
     * Auto-releases any seat the user occupies (sends SEAT_LEFT WS event).
     * Broadcasts PARTICIPANT_LEFT.
     */
    @Transactional
    public void leaveRoom(UUID roomId, UUID userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (!participantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new BadRequestException("User is not a member of this room");
        }

        // Auto-release seat before removing from room (sends SEAT_LEFT WS event)
        seatService.releaseUserSeat(roomId, userId);

        gameService.onParticipantLeftRoom(roomId, userId);

        // После выхода из комнаты пользователь не должен оставлять свои цели видимыми в комнате.
        studyTaskRepository.deleteByRoom_IdAndUser_Id(roomId, userId);

        participantRepository.deleteByRoomIdAndUserId(roomId, userId);

        int countAfter = participantRepository.countByRoomId(roomId);
        boolean shouldBeActive = countAfter < room.getMaxParticipants();
        if (room.getIsActive() != shouldBeActive) {
            room.setIsActive(shouldBeActive);
            roomRepository.save(room);
        }

        ParticipantResponse leavingUser = ParticipantResponse.builder()
                .userId(user.getId().toString())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .build();
        publish(roomId, RoomEventType.PARTICIPANT_LEFT, leavingUser);
    }

    /**
     * Выход из комнаты при обрыве WebSocket (последняя STOMP-сессия пользователя).
     * Идемпотентно: если пользователь ни в одной комнате — ничего не делает.
     */
    @Transactional
    public void leaveRoomOnWebSocketDisconnect(UUID userId) {
        List<RoomParticipant> existing = participantRepository.findByUserId(userId);
        if (existing.isEmpty()) {
            return;
        }
        UUID roomId = existing.getFirst().getRoom().getId();
        leaveRoom(roomId, userId);
    }

    /**
     * Returns all rooms that the user is currently a member of (including seat state).
     */
    @Transactional(readOnly = true)
    public List<RoomResponse> getUserRooms(UUID userId) {
        List<RoomParticipant> participations = participantRepository.findByUserId(userId);

        return participations.stream().map(p -> {
            Room room = p.getRoom();
            int count = participantRepository.countByRoomId(room.getId());
            int seated = seatRepository.countOccupiedByRoomId(room.getId());
            List<SeatDto> seats = seatRepository.findByRoomId(room.getId())
                    .stream().map(SeatDto::from).toList();
            return RoomResponse.from(room, seated, count - seated, seats);
        }).toList();
    }
}

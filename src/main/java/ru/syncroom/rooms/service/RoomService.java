package ru.syncroom.rooms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.dto.JoinRoomResponse;
import ru.syncroom.rooms.dto.ParticipantResponse;
import ru.syncroom.rooms.dto.RoomResponse;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
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
     * Returns all rooms with current participant count.
     * isActive is recalculated dynamically and synced to DB if out of date.
     */
    @Transactional
    public List<RoomResponse> getAllRooms() {
        List<Room> rooms = roomRepository.findAll();

        return rooms.stream().map(room -> {
            int count = participantRepository.countByRoomId(room.getId());
            boolean active = count < room.getMaxParticipants();

            // Sync the flag in DB if it has drifted
            if (room.getIsActive() != active) {
                room.setIsActive(active);
                roomRepository.save(room);
            }

            return RoomResponse.from(room, count);
        }).toList();
    }

    /**
     * Join a room.
     *
     * Business rules:
     * - User may only be in one room at a time.
     * - If the room is full after joining (or was already full), it is marked
     *   isActive=false and a new duplicate room is created.
     *
     * Returns: JoinRoomResponse { room, participants[] }
     * Broadcasts: PARTICIPANT_JOINED to /topic/room/{roomId}
     */
    @Transactional
    public JoinRoomResponse joinRoom(UUID roomId, UUID userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // One room at a time: check if the user is already anywhere
        List<RoomParticipant> existing = participantRepository.findByUserId(userId);
        if (!existing.isEmpty()) {
            throw new BadRequestException(
                    "User is already in room " + existing.getFirst().getRoom().getId() +
                    ". Leave the current room before joining another.");
        }

        int currentCount = participantRepository.countByRoomId(roomId);
        UUID actualRoomId;   // the room the user actually ends up in

        if (currentCount >= room.getMaxParticipants()) {
            // Room is already full — close it and create a duplicate
            room.setIsActive(false);
            roomRepository.save(room);

            Room newRoom = roomRepository.save(Room.builder()
                    .context(room.getContext())
                    .title(room.getTitle())
                    .maxParticipants(room.getMaxParticipants())
                    .isActive(true)
                    .build());

            participantRepository.save(RoomParticipant.builder()
                    .room(newRoom)
                    .user(user)
                    .build());

            actualRoomId = newRoom.getId();

        } else {
            // There is space — add the user to the original room
            participantRepository.save(RoomParticipant.builder()
                    .room(room)
                    .user(user)
                    .build());

            // If room just filled up, close it and open a duplicate
            if (currentCount + 1 >= room.getMaxParticipants()) {
                room.setIsActive(false);
                roomRepository.save(room);

                roomRepository.save(Room.builder()
                        .context(room.getContext())
                        .title(room.getTitle())
                        .maxParticipants(room.getMaxParticipants())
                        .isActive(true)
                        .build());
            }

            actualRoomId = room.getId();
        }

        // Reload the actual room the user was placed in
        Room actualRoom = roomRepository.findById(actualRoomId).orElseThrow();
        List<RoomParticipant> allParticipants = participantRepository.findByRoomId(actualRoomId);
        int participantCount = allParticipants.size();

        // Build the participant DTO for this user only (for the WS event payload)
        RoomParticipant myParticipation = participantRepository
                .findByRoomIdAndUserId(actualRoomId, userId).orElseThrow();
        ParticipantResponse myDto = ParticipantResponse.from(myParticipation);

        // Broadcast PARTICIPANT_JOINED to everyone already in the room
        publish(actualRoomId, RoomEventType.PARTICIPANT_JOINED, myDto);

        // Build and return the REST response
        List<ParticipantResponse> participantDtos = allParticipants.stream()
                .map(ParticipantResponse::from)
                .toList();

        return JoinRoomResponse.builder()
                .room(RoomResponse.from(actualRoom, participantCount))
                .participants(participantDtos)
                .build();
    }

    /**
     * Leave a room.
     *
     * - Removes the user from room_participants.
     * - If the room was full and now has space, it becomes active again.
     * - Broadcasts PARTICIPANT_LEFT to /topic/room/{roomId}.
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

        participantRepository.deleteByRoomIdAndUserId(roomId, userId);

        // Re-check whether the room should be re-opened
        int countAfter = participantRepository.countByRoomId(roomId);
        boolean shouldBeActive = countAfter < room.getMaxParticipants();
        if (room.getIsActive() != shouldBeActive) {
            room.setIsActive(shouldBeActive);
            roomRepository.save(room);
        }

        // Broadcast PARTICIPANT_LEFT — payload contains basic user info
        ParticipantResponse leavingUser = ParticipantResponse.builder()
                .userId(user.getId().toString())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .build();
        publish(roomId, RoomEventType.PARTICIPANT_LEFT, leavingUser);
    }

    /**
     * Returns all rooms that the user is currently a member of.
     */
    @Transactional(readOnly = true)
    public List<RoomResponse> getUserRooms(UUID userId) {
        List<RoomParticipant> participations = participantRepository.findByUserId(userId);

        return participations.stream().map(p -> {
            Room room = p.getRoom();
            int count = participantRepository.countByRoomId(room.getId());
            return RoomResponse.from(room, count);
        }).toList();
    }
}

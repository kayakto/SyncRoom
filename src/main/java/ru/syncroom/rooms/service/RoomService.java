package ru.syncroom.rooms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.dto.RoomResponse;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final UserRepository userRepository;

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
     * If the room is full after joining (or already full):
     * 1. Mark it isActive = false
     * 2. Create a duplicate room (same context, title, maxParticipants, isActive =
     * true)
     * 3. Add user to the new room (if room was already full) or to original (if
     * not)
     */
    @Transactional
    public void joinRoom(UUID roomId, UUID userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Check that the user is not already in any room (one room at a time)
        List<RoomParticipant> existing = participantRepository.findByUserId(userId);
        if (!existing.isEmpty()) {
            throw new BadRequestException(
                    "User is already in room " + existing.getFirst().getRoom().getId() +
                            ". Leave the current room before joining another.");
        }

        int currentCount = participantRepository.countByRoomId(roomId);

        if (currentCount >= room.getMaxParticipants()) {
            // Room is already full — close it and create a duplicate
            room.setIsActive(false);
            roomRepository.save(room);

            Room newRoom = Room.builder()
                    .context(room.getContext())
                    .title(room.getTitle())
                    .maxParticipants(room.getMaxParticipants())
                    .isActive(true)
                    .build();
            newRoom = roomRepository.save(newRoom);

            // Add user to the new room
            participantRepository.save(RoomParticipant.builder()
                    .room(newRoom)
                    .user(user)
                    .build());

        } else {
            // There is space — add the user
            participantRepository.save(RoomParticipant.builder()
                    .room(room)
                    .user(user)
                    .build());

            // Check if room just filled up after this addition
            if (currentCount + 1 >= room.getMaxParticipants()) {
                room.setIsActive(false);
                roomRepository.save(room);

                // Create a new empty duplicate room
                roomRepository.save(Room.builder()
                        .context(room.getContext())
                        .title(room.getTitle())
                        .maxParticipants(room.getMaxParticipants())
                        .isActive(true)
                        .build());
            }
        }
    }

    /**
     * Leave a room.
     *
     * Removes the user from room_participants.
     * After leaving, if the room was full (isActive=false) and now has space,
     * it becomes active again.
     *
     * Errors:
     * - 404: Room not found
     * - 400: User is not a member of the room
     */
    @Transactional
    public void leaveRoom(UUID roomId, UUID userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));

        if (!participantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new BadRequestException("User is not a member of this room");
        }

        participantRepository.deleteByRoomIdAndUserId(roomId, userId);

        // After leaving, re-check whether the room should be re-opened
        int countAfter = participantRepository.countByRoomId(roomId);
        boolean shouldBeActive = countAfter < room.getMaxParticipants();
        if (room.getIsActive() != shouldBeActive) {
            room.setIsActive(shouldBeActive);
            roomRepository.save(room);
        }
    }

    /**
     * Returns all rooms that the user is currently a member of,
     * with participantCount filled in for each room.
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

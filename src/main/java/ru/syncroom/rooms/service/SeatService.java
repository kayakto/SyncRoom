package ru.syncroom.rooms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.rooms.domain.ParticipantRole;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.Seat;
import ru.syncroom.rooms.dto.SeatDto;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.rooms.repository.SeatRepository;
import ru.syncroom.rooms.ws.RoomEvent;
import ru.syncroom.rooms.ws.RoomEventType;
import ru.syncroom.rooms.ws.SeatLeftPayload;
import ru.syncroom.rooms.ws.SeatTakenPayload;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.util.UUID;

/**
 * Business logic for the seat mechanic.
 *
 * Rules:
 *   1. One user = one seat per room.
 *   2. Sitting on a new seat while already seated → auto-move (SEAT_LEFT old + SEAT_TAKEN new).
 *   3. On room leave (HTTP или при закрытии последнего WebSocket) → auto-release seat (SEAT_LEFT).
 *
 * WS topic: /topic/room/{roomId}/seats
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final RoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /** Optional Redis — not required. Falls back to no-op if Redis is not configured. */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private static final String SEATS_TOPIC = "/topic/room/%s/seats";

    // ─── helpers ────────────────────────────────────────────────────────────

    private String seatsTopic(UUID roomId) {
        return String.format(SEATS_TOPIC, roomId);
    }

    private void publishSeatTaken(UUID roomId, Seat seat, User user, int participantCount, int observerCount) {
        SeatTakenPayload payload = SeatTakenPayload.builder()
                .seatId(seat.getId().toString())
                .user(SeatTakenPayload.OccupantInfo.builder()
                        .id(user.getId().toString())
                        .name(user.getName())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .participantCount(participantCount)
                .observerCount(observerCount)
                .build();
        messagingTemplate.convertAndSend(seatsTopic(roomId), RoomEvent.of(RoomEventType.SEAT_TAKEN, payload));
        invalidateCache(roomId);
    }

    private void publishSeatLeft(UUID roomId, UUID seatId, UUID userId, int participantCount, int observerCount) {
        SeatLeftPayload payload = SeatLeftPayload.builder()
                .seatId(seatId.toString())
                .userId(userId.toString())
                .participantCount(participantCount)
                .observerCount(observerCount)
                .build();
        messagingTemplate.convertAndSend(seatsTopic(roomId), RoomEvent.of(RoomEventType.SEAT_LEFT, payload));
        invalidateCache(roomId);
    }

    private int seatedCount(UUID roomId) {
        return seatRepository.countOccupiedByRoomId(roomId);
    }

    private int observerCount(UUID roomId) {
        int total = participantRepository.countByRoomId(roomId);
        return total - seatedCount(roomId);
    }

    /** Invalidate the Redis cache for this room's seat state */
    private void invalidateCache(UUID roomId) {
        try {
            redisTemplate.delete("seats:room:" + roomId);
        } catch (Exception e) {
            log.warn("Redis unavailable, skipping cache invalidation for room {}", roomId);
        }
    }

    // ─── Public API ─────────────────────────────────────────────────────────

    /**
     * Sit on a seat.
     *
     * - If seat is free: occupy it.
     * - If seat is taken by another user: throw 409.
     * - If user is already on another seat in the same room: auto-move.
     */
    @Transactional
    public SeatDto sit(UUID roomId, UUID seatId, UUID userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));

        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new NotFoundException("Seat not found"));

        if (!seat.getRoom().getId().equals(roomId)) {
            throw new BadRequestException("Seat does not belong to this room");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // If the seat is taken by a DIFFERENT user → 409
        if (seat.getOccupiedBy() != null && !seat.getOccupiedBy().getId().equals(userId)) {
            throw new SeatConflictException("Seat is already occupied by another user");
        }

        // If seat is already theirs → idempotent, just return
        if (seat.getOccupiedBy() != null && seat.getOccupiedBy().getId().equals(userId)) {
            return SeatDto.from(seat);
        }

        // Auto-move: if the user was sitting somewhere else in this room, free that seat first (без WS до конца операции)
        final UUID[] previousSeatId = { null };
        seatRepository.findByRoomIdAndOccupiedById(roomId, userId).ifPresent(oldSeat -> {
            previousSeatId[0] = oldSeat.getId();
            oldSeat.setOccupiedBy(null);
            seatRepository.save(oldSeat);
            seatRepository.flush();
            log.debug("Auto-moved user {} from seat {} in room {}", userId, previousSeatId[0], roomId);
        });

        // Occupy new seat
        seat.setOccupiedBy(user);
        Seat saved = seatRepository.save(seat);
        seatRepository.flush();
        participantRepository.updateRoleByRoomIdAndUserId(roomId, userId, ParticipantRole.PARTICIPANT);

        int seated = seatedCount(roomId);
        int observers = observerCount(roomId);
        if (previousSeatId[0] != null) {
            publishSeatLeft(roomId, previousSeatId[0], userId, seated, observers);
        }
        publishSeatTaken(roomId, saved, user, seated, observers);

        log.debug("User {} sat on seat {} in room {}", userId, seatId, roomId);
        return SeatDto.from(saved);
    }

    /**
     * Stand up from a seat.
     *
     * - If it's not the user's seat: throw 403.
     */
    @Transactional
    public SeatDto standUp(UUID roomId, UUID seatId, UUID userId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new NotFoundException("Seat not found"));

        if (!seat.getRoom().getId().equals(roomId)) {
            throw new BadRequestException("Seat does not belong to this room");
        }

        if (seat.getOccupiedBy() == null || !seat.getOccupiedBy().getId().equals(userId)) {
            throw new SeatForbiddenException("You are not sitting on this seat");
        }

        seat.setOccupiedBy(null);
        Seat saved = seatRepository.save(seat);
        seatRepository.flush();
        participantRepository.updateRoleByRoomIdAndUserId(roomId, userId, ParticipantRole.OBSERVER);
        int seated = seatedCount(roomId);
        int observers = observerCount(roomId);
        publishSeatLeft(roomId, seatId, userId, seated, observers);

        log.debug("User {} stood up from seat {} in room {}", userId, seatId, roomId);
        return SeatDto.from(saved);
    }

    /**
     * Release all seats a user occupies in a given room.
     * Called automatically on leaveRoom (including leave triggered by WebSocket disconnect).
     * Does nothing (silently) if user has no seat.
     */
    @Transactional
    public void releaseUserSeat(UUID roomId, UUID userId) {
        seatRepository.findByRoomIdAndOccupiedById(roomId, userId).ifPresent(seat -> {
            UUID seatId = seat.getId();
            int updated = seatRepository.releaseByRoomIdAndUserId(roomId, userId);
            if (updated > 0) {
                seatRepository.flush();
                participantRepository.updateRoleByRoomIdAndUserId(roomId, userId, ParticipantRole.OBSERVER);
                int seated = seatedCount(roomId);
                int observers = observerCount(roomId);
                publishSeatLeft(roomId, seatId, userId, seated, observers);
                log.debug("Released seat for user {} in room {} (roomLeave/disconnect)", userId, roomId);
            }
        });
    }

    // ─── Custom exceptions (mapped by GlobalExceptionHandler) ───────────────

    /** 409 Conflict — seat is taken by another person */
    public static class SeatConflictException extends RuntimeException {
        public SeatConflictException(String msg) { super(msg); }
    }

    /** 403 Forbidden — not the user's seat */
    public static class SeatForbiddenException extends RuntimeException {
        public SeatForbiddenException(String msg) { super(msg); }
    }
}

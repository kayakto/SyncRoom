package ru.syncroom.rooms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.ForbiddenException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.common.exception.UnauthorizedException;
import ru.syncroom.rooms.config.SeatBotProperties;
import ru.syncroom.rooms.domain.ParticipantRole;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.RoomParticipant;
import ru.syncroom.rooms.domain.RoomSeatBot;
import ru.syncroom.rooms.domain.Seat;
import ru.syncroom.rooms.dto.ParticipantResponse;
import ru.syncroom.rooms.dto.SeatBotCatalogEntryDto;
import ru.syncroom.rooms.dto.SeatBotInRoomDto;
import ru.syncroom.rooms.dto.SeatDto;
import ru.syncroom.rooms.dto.SeatDto.OccupantDto;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.rooms.repository.RoomSeatBotRepository;
import ru.syncroom.rooms.repository.SeatRepository;
import ru.syncroom.rooms.seatbot.SeatBotKind;
import ru.syncroom.rooms.ws.RoomEvent;
import ru.syncroom.rooms.ws.RoomEventType;
import ru.syncroom.rooms.ws.SeatLeftPayload;
import ru.syncroom.rooms.ws.SeatTakenPayload;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatBotService {

    private final RoomRepository roomRepository;
    private final SeatRepository seatRepository;
    private final RoomParticipantRepository participantRepository;
    private final RoomSeatBotRepository roomSeatBotRepository;
    private final SeatBotProperties seatBotProperties;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private static final String SEATS_TOPIC = "/topic/room/%s/seats";
    private static final String ROOM_TOPIC = "/topic/room/";

    private void invalidateCache(UUID roomId) {
        try {
            if (redisTemplate != null) {
                redisTemplate.delete("seats:room:" + roomId);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable, skipping cache invalidation for room {}", roomId);
        }
    }

    private int seatedCount(UUID roomId) {
        return seatRepository.countOccupiedByRoomId(roomId);
    }

    private int observerCount(UUID roomId) {
        int total = participantRepository.countByRoomId(roomId);
        return total - seatedCount(roomId);
    }

    @Transactional(readOnly = true)
    public List<SeatBotCatalogEntryDto> catalog(String contextQuery, UUID requesterId) {
        if (requesterId == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        String ctx = contextQuery == null || contextQuery.isBlank()
                ? "WORK"
                : contextQuery.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(SeatBotKind.values())
                .filter(k -> k.supportsRoomContext(ctx))
                .map(this::toCatalogEntry)
                .collect(Collectors.toList());
    }

    private SeatBotCatalogEntryDto toCatalogEntry(SeatBotKind k) {
        return SeatBotCatalogEntryDto.builder()
                .botType(k.getTypeKey())
                .name(k.getDisplayName())
                .avatarUrl(resolveBotAvatarUrl(k))
                .description(k.getDescription())
                .supportedContexts(k.getSupportedContextsUpper())
                .behaviour(SeatBotCatalogEntryDto.BehaviourDto.builder()
                        .reactsToPomodoro(k.isReactsToPomodoro())
                        .createsGoals(k.isCreatesGoals())
                        .likesGoals(k.isLikesGoals())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public List<SeatBotInRoomDto> listInRoom(UUID roomId, UUID requesterId) {
        requireHumanParticipant(roomId, requesterId);
        return roomSeatBotRepository.findByRoom_IdOrderByCreatedAtAsc(roomId).stream()
                .map(b -> SeatBotInRoomDto.builder()
                        .id(b.getId().toString())
                        .botType(b.getBotType())
                        .name(b.getName())
                        .avatarUrl(b.getAvatarUrl())
                        .seatId(b.getSeat().getId().toString())
                        .build())
                .toList();
    }

    @Transactional
    public SeatDto placeBot(UUID roomId, UUID seatId, String botTypeKey, UUID requesterId) {
        requireHumanParticipant(roomId, requesterId);
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));
        if ("leisure".equalsIgnoreCase(room.getContext())) {
            throw new BadRequestException("Seat bots are not available in leisure rooms");
        }
        SeatBotKind kind = SeatBotKind.fromTypeKey(botTypeKey);
        if (kind == null) {
            throw new BadRequestException("Unknown botType");
        }
        if (!kind.supportsRoomContext(room.getContext())) {
            throw new BadRequestException("This bot type is not available in the room context");
        }
        if (roomSeatBotRepository.countByRoom_Id(roomId) >= seatBotProperties.getMaxPerRoom()) {
            throw new BadRequestException("Maximum number of seat bots in this room reached");
        }
        if (roomSeatBotRepository.existsByRoom_IdAndBotType(roomId, kind.getTypeKey())) {
            throw new BadRequestException("This bot type is already in the room");
        }

        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new NotFoundException("Seat not found"));
        if (!seat.getRoom().getId().equals(roomId)) {
            throw new BadRequestException("Seat does not belong to this room");
        }
        if (seat.getOccupiedBy() != null) {
            throw new SeatService.SeatConflictException("Seat is already occupied");
        }
        if (roomSeatBotRepository.findBySeat_Id(seatId).isPresent()) {
            throw new SeatService.SeatConflictException("Seat is already occupied by a bot");
        }

        RoomSeatBot bot = RoomSeatBot.builder()
                .room(room)
                .botType(kind.getTypeKey())
                .seat(seat)
                .name(kind.getDisplayName())
                .avatarUrl(resolveBotAvatarUrl(kind))
                .build();
        bot = roomSeatBotRepository.save(bot);
        roomSeatBotRepository.flush();

        participantRepository.save(RoomParticipant.builder()
                .room(room)
                .seatBot(bot)
                .role(ParticipantRole.PARTICIPANT)
                .build());
        participantRepository.flush();

        int seated = seatedCount(roomId);
        int observers = observerCount(roomId);
        publishSeatTaken(roomId, seat, bot, seated, observers);

        RoomParticipant rp = participantRepository.findBySeatBot_Id(bot.getId()).orElseThrow();
        messagingTemplate.convertAndSend(ROOM_TOPIC + roomId, RoomEvent.of(RoomEventType.PARTICIPANT_JOINED, ParticipantResponse.from(rp)));
        invalidateCache(roomId);

        Seat refreshed = seatRepository.findById(seatId).orElseThrow();
        return SeatDto.builder()
                .id(refreshed.getId().toString())
                .x(refreshed.getX())
                .y(refreshed.getY())
                .occupiedBy(OccupantDto.fromSeatBot(bot))
                .build();
    }

    @Transactional
    public SeatDto removeBotFromSeat(UUID roomId, UUID seatId, UUID requesterId) {
        requireHumanParticipant(roomId, requesterId);
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new NotFoundException("Seat not found"));
        if (!seat.getRoom().getId().equals(roomId)) {
            throw new BadRequestException("Seat does not belong to this room");
        }
        if (seat.getOccupiedBy() != null) {
            throw new BadRequestException("A human is on this seat; use /leave instead");
        }
        RoomSeatBot bot = roomSeatBotRepository.findBySeat_Id(seatId).orElse(null);
        if (bot == null) {
            Seat empty = seatRepository.findById(seatId).orElseThrow();
            return SeatDto.builder()
                    .id(empty.getId().toString())
                    .x(empty.getX())
                    .y(empty.getY())
                    .occupiedBy(null)
                    .build();
        }

        UUID botId = bot.getId();
        String botName = bot.getName();
        String botAvatar = bot.getAvatarUrl();
        deleteSeatBotEntity(bot);

        int seated = seatedCount(roomId);
        int observers = observerCount(roomId);
        publishSeatLeft(roomId, seat.getId(), botId, seated, observers);
        messagingTemplate.convertAndSend(ROOM_TOPIC + roomId, RoomEvent.of(RoomEventType.PARTICIPANT_LEFT,
                ParticipantResponse.builder()
                        .userId(botId.toString())
                        .name(botName)
                        .avatarUrl(botAvatar)
                        .role(ParticipantRole.PARTICIPANT)
                        .isBot(true)
                        .build()));
        invalidateCache(roomId);

        Seat cleared = seatRepository.findById(seatId).orElseThrow();
        return SeatDto.builder()
                .id(cleared.getId().toString())
                .x(cleared.getX())
                .y(cleared.getY())
                .occupiedBy(null)
                .build();
    }

    @Transactional
    public void removeAllBotsInRoom(UUID roomId) {
        List<UUID> botIds = roomSeatBotRepository.findByRoom_IdOrderByCreatedAtAsc(roomId).stream()
                .map(RoomSeatBot::getId)
                .toList();
        for (UUID botId : botIds) {
            RoomSeatBot bot = roomSeatBotRepository.findById(botId).orElse(null);
            if (bot == null) {
                continue;
            }
            UUID seatId = bot.getSeat().getId();
            String name = bot.getName();
            String avatar = bot.getAvatarUrl();
            deleteSeatBotEntity(bot);
            int seated = seatedCount(roomId);
            int observers = observerCount(roomId);
            publishSeatLeft(roomId, seatId, botId, seated, observers);
            messagingTemplate.convertAndSend(ROOM_TOPIC + roomId, RoomEvent.of(RoomEventType.PARTICIPANT_LEFT,
                    ParticipantResponse.builder()
                            .userId(botId.toString())
                            .name(name)
                            .avatarUrl(avatar)
                            .role(ParticipantRole.PARTICIPANT)
                            .isBot(true)
                            .build()));
            invalidateCache(roomId);
        }
    }

    private void requireHumanParticipant(UUID roomId, UUID userId) {
        if (!participantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ForbiddenException("User is not a participant of this room");
        }
    }

    /**
     * Удаляем сначала участника-бота: иначе Hibernate может обнулить {@code seat_bot_id} и нарушить
     * chk_room_participant_user_or_seat_bot до того, как сработает ON DELETE CASCADE в БД.
     */
    private void deleteSeatBotEntity(RoomSeatBot bot) {
        participantRepository.findBySeatBot_Id(bot.getId()).ifPresent(participantRepository::delete);
        participantRepository.flush();
        roomSeatBotRepository.delete(bot);
        roomSeatBotRepository.flush();
    }

    private void publishSeatTaken(UUID roomId, Seat seat, RoomSeatBot bot, int participantCount, int observerCount) {
        var payload = SeatTakenPayload.builder()
                .seatId(seat.getId().toString())
                .user(SeatTakenPayload.OccupantInfo.builder()
                        .id(bot.getId().toString())
                        .name(bot.getName())
                        .avatarUrl(bot.getAvatarUrl())
                        .isBot(true)
                        .build())
                .participantCount(participantCount)
                .observerCount(observerCount)
                .build();
        messagingTemplate.convertAndSend(String.format(SEATS_TOPIC, roomId), RoomEvent.of(RoomEventType.SEAT_TAKEN, payload));
    }

    private void publishSeatLeft(UUID roomId, UUID seatId, UUID occupantId, int participantCount, int observerCount) {
        var payload = SeatLeftPayload.builder()
                .seatId(seatId.toString())
                .userId(occupantId.toString())
                .participantCount(participantCount)
                .observerCount(observerCount)
                .build();
        messagingTemplate.convertAndSend(String.format(SEATS_TOPIC, roomId), RoomEvent.of(RoomEventType.SEAT_LEFT, payload));
    }

    private String resolveBotAvatarUrl(SeatBotKind kind) {
        String candidate = switch (kind) {
            case WORK_FOCUS_BUDDY -> seatBotProperties.getWorkFocusBuddyAvatarUrl();
            case STUDY_HELPER -> seatBotProperties.getStudyHelperAvatarUrl();
            case SPORT_CHEERLEADER -> seatBotProperties.getSportCheerleaderAvatarUrl();
        };
        if (candidate != null && !candidate.isBlank()) {
            return candidate;
        }
        return Objects.requireNonNullElse(seatBotProperties.getDefaultAvatarUrl(), "/icons/icon-192.png");
    }
}

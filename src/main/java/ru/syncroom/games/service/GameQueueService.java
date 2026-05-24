package ru.syncroom.games.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.common.exception.BadRequestException;
import ru.syncroom.common.exception.NotFoundException;
import ru.syncroom.common.web.PublicAbsoluteUrlResolver;
import ru.syncroom.games.domain.*;
import ru.syncroom.games.dto.*;
import ru.syncroom.games.repository.*;
import ru.syncroom.games.support.SupportedGameTypes;
import ru.syncroom.games.websocket.GameQueueEventSender;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.repository.RoomParticipantRepository;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.users.domain.User;
import ru.syncroom.users.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameQueueService {

    private static final String GAMES_ALLOWED_CONTEXT = "leisure";

    private final GameQueueRepository gameQueueRepository;
    private final GameQueuePlayerRepository gameQueuePlayerRepository;
    private final GameQueueBotRepository gameQueueBotRepository;
    private final RoomRepository roomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final UserRepository userRepository;
    private final BotUserRepository botUserRepository;
    private final GameService gameService;
    private final PublicAbsoluteUrlResolver publicAbsoluteUrlResolver;
    private final GameQueueEventSender queueEventSender;

    @Transactional(readOnly = true)
    public Map<String, GameQueueDto> getQueuesSnapshot(UUID roomId, UUID userId) {
        requireParticipant(roomId, userId);
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new NotFoundException("Room not found"));
        assertGamesAllowed(room.getContext());
        Map<String, GameQueueDto> out = new LinkedHashMap<>();
        for (String gt : SupportedGameTypes.allOrdered()) {
            out.put(gt, toDtoOrEmpty(roomId, gt));
        }
        return out;
    }

    @Transactional
    public GameQueueDto joinQueue(UUID roomId, String gameTypeRaw, UUID userId) {
        String gameType = parseGameType(gameTypeRaw);
        requireParticipant(roomId, userId);
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new NotFoundException("Room not found"));
        assertGamesAllowed(room.getContext());

        removeUserFromOtherQueuesInRoom(roomId, userId, gameType);

        GameQueue queue = getOrCreateQueue(room, gameType);
        if (!"WAITING".equals(queue.getStatus())) {
            throw new BadRequestException("Cannot join queue: game already starting or in progress for this type");
        }

        int max = maxPlayers(gameType);
        if (countMembers(queue.getId()) >= max) {
            throw new BadRequestException("Queue is full");
        }

        if (gameQueuePlayerRepository.findByQueue_IdAndUser_Id(queue.getId(), userId).isEmpty()) {
            User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
            GameQueuePlayer p = GameQueuePlayer.builder()
                    .queue(queue)
                    .user(user)
                    .isReady(false)
                    .build();
            gameQueuePlayerRepository.save(p);
            queue.getPlayers().add(p);
        }

        touchQueueNotEmpty(queue);
        GameQueueDto dto = toDto(reloadQueue(queue.getId()));
        GameQueuePlayerDto playerRow = dto.getPlayers().stream()
                .filter(pl -> userId.toString().equals(pl.getUserId()))
                .findFirst()
                .orElseThrow();
        queueEventSender.send(roomId, "QUEUE_PLAYER_JOINED", Map.of(
                "gameType", gameType,
                "player", playerRow
        ));
        return dto;
    }

    @Transactional
    public void leaveQueue(UUID roomId, String gameTypeRaw, UUID userId) {
        String gameType = parseGameType(gameTypeRaw);
        requireParticipant(roomId, userId);
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new NotFoundException("Room not found"));
        assertGamesAllowed(room.getContext());

        GameQueue queue = gameQueueRepository.findByRoom_IdAndGameType(roomId, gameType)
                .orElseThrow(() -> new NotFoundException("Queue not found"));
        if (!"WAITING".equals(queue.getStatus())) {
            throw new BadRequestException("Cannot leave queue while game is active for this type");
        }
        int removed = gameQueuePlayerRepository.deleteByQueueIdAndUserId(queue.getId(), userId);
        if (removed > 0) {
            queueEventSender.send(roomId, "QUEUE_PLAYER_LEFT", Map.of(
                    "gameType", gameType,
                    "userId", userId.toString()
            ));
        }
        refreshMarkedEmpty(reloadQueue(queue.getId()));
    }

    @Transactional
    public GameQueueDto setReady(UUID roomId, String gameTypeRaw, UUID userId, boolean ready) {
        String gameType = parseGameType(gameTypeRaw);
        requireParticipant(roomId, userId);
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new NotFoundException("Room not found"));
        assertGamesAllowed(room.getContext());

        GameQueue queue = gameQueueRepository.findByRoom_IdAndGameType(roomId, gameType)
                .orElseThrow(() -> new NotFoundException("Queue not found"));
        if (!"WAITING".equals(queue.getStatus())) {
            throw new BadRequestException("Cannot change ready state while game is active for this type");
        }
        GameQueuePlayer p = gameQueuePlayerRepository.findByQueue_IdAndUser_Id(queue.getId(), userId)
                .orElseThrow(() -> new BadRequestException("Not in this queue"));
        p.setIsReady(ready);
        gameQueuePlayerRepository.save(p);
        queueEventSender.send(roomId, "QUEUE_PLAYER_READY_CHANGED", Map.of(
                "gameType", gameType,
                "userId", userId.toString(),
                "isReady", ready
        ));
        return toDto(reloadQueue(queue.getId()));
    }

    @Transactional
    public GameQueueStartResponse startFromQueue(UUID roomId, String gameTypeRaw, UUID userId) {
        String gameType = parseGameType(gameTypeRaw);
        requireParticipant(roomId, userId);
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new NotFoundException("Room not found"));
        assertGamesAllowed(room.getContext());

        GameQueue queue = gameQueueRepository.findByRoom_IdAndGameType(roomId, gameType)
                .orElseThrow(() -> new NotFoundException("Queue not found"));
        if (!"WAITING".equals(queue.getStatus())) {
            throw new BadRequestException("Queue cannot be started in current state");
        }

        queue = reloadQueue(queue.getId());
        List<GameQueuePlayer> humans = gameQueuePlayerRepository.findByQueue_Id(queue.getId());
        List<GameQueueBot> bots = gameQueueBotRepository.findByQueue_Id(queue.getId());
        int min = minPlayers(gameType);
        if (humans.isEmpty()) {
            throw new BadRequestException("Queue is empty");
        }
        if (!humans.stream().allMatch(h -> Boolean.TRUE.equals(h.getIsReady()))) {
            throw new BadRequestException("All human players must be ready to start");
        }
        int total = humans.size() + bots.size();
        if (total < min) {
            throw new BadRequestException("Need at least " + min + " players (humans + bots) to start");
        }
        if (total > maxPlayers(gameType)) {
            throw new BadRequestException("Too many players in queue");
        }

        List<UUID> humanIds = humans.stream().map(h -> h.getUser().getId()).toList();
        List<UUID> botIds = bots.stream().map(b -> b.getBotUser().getId()).toList();

        queue.setStatus("STARTING");
        gameQueueRepository.save(queue);

        GameSession session = gameService.createLobbySessionWithRoster(roomId, gameType, humanIds, botIds);

        gameQueuePlayerRepository.deleteAll(humans);
        gameQueueBotRepository.deleteAll(bots);

        queue = reloadQueue(queue.getId());
        // deleteAll не очищает in-memory @OneToMany — иначе save() падает с ObjectDeletedException
        queue.getPlayers().clear();
        queue.getBots().clear();
        queue.setLinkedGameSessionId(session.getId());
        queue.setStatus("IN_PROGRESS");
        touchQueueNotEmpty(queue);
        gameQueueRepository.save(queue);

        // Clients learn gameId from QUEUE_STARTED and subscribe to /topic/game/{id} before phases are published.
        queueEventSender.send(roomId, "QUEUE_STARTED", Map.of(
                "gameType", gameType,
                "gameSessionId", session.getId().toString()
        ));

        gameService.startGame(session.getId());

        return GameQueueStartResponse.builder()
                .gameSessionId(session.getId().toString())
                .build();
    }

    @Transactional
    public GameQueueDto addBot(UUID roomId, String gameTypeRaw, UUID requesterId, GameQueueAddBotRequest req) {
        String gameType = parseGameType(gameTypeRaw);
        requireParticipant(roomId, requesterId);
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new NotFoundException("Room not found"));
        assertGamesAllowed(room.getContext());

        UUID botId;
        try {
            botId = UUID.fromString(req.getBotId());
        } catch (Exception e) {
            throw new BadRequestException("Invalid botId");
        }
        String difficulty = normalizeDifficulty(req.getDifficulty());

        GameQueue queue = getOrCreateQueue(room, gameType);
        if (!"WAITING".equals(queue.getStatus())) {
            throw new BadRequestException("Cannot add bots while game is active for this type");
        }
        if (countMembers(queue.getId()) >= maxPlayers(gameType)) {
            throw new BadRequestException("Queue is full");
        }

        BotUser bot = botUserRepository.findById(botId).orElseThrow(() -> new NotFoundException("Bot not found"));
        validateBotTypeForGame(gameType, bot.getBotType());

        if (gameQueueBotRepository.findByQueue_IdAndBotUser_Id(queue.getId(), botId).isPresent()) {
            throw new BadRequestException("Bot is already in this queue");
        }

        GameQueueBot row = GameQueueBot.builder()
                .queue(queue)
                .botUser(bot)
                .difficulty(difficulty)
                .build();
        gameQueueBotRepository.save(row);
        queue.getBots().add(row);

        touchQueueNotEmpty(queue);
        GameQueueDto dto = toDto(reloadQueue(queue.getId()));
        GameQueueBotDto botDto = dto.getBots().stream()
                .filter(b -> botId.toString().equals(b.getBotId()))
                .findFirst()
                .orElseThrow();
        queueEventSender.send(roomId, "QUEUE_BOT_ADDED", Map.of(
                "gameType", gameType,
                "bot", botDto
        ));
        return dto;
    }

    @Transactional
    public void removeBot(UUID roomId, String gameTypeRaw, UUID requesterId, UUID botId) {
        String gameType = parseGameType(gameTypeRaw);
        requireParticipant(roomId, requesterId);
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new NotFoundException("Room not found"));
        assertGamesAllowed(room.getContext());

        GameQueue queue = gameQueueRepository.findByRoom_IdAndGameType(roomId, gameType)
                .orElseThrow(() -> new NotFoundException("Queue not found"));
        if (!"WAITING".equals(queue.getStatus())) {
            throw new BadRequestException("Cannot remove bots while game is active for this type");
        }
        GameQueueBot row = gameQueueBotRepository.findByQueue_IdAndBotUser_Id(queue.getId(), botId)
                .orElseThrow(() -> new NotFoundException("Bot is not in this queue"));
        gameQueueBotRepository.delete(row);
        queueEventSender.send(roomId, "QUEUE_BOT_REMOVED", Map.of(
                "gameType", gameType,
                "botId", botId.toString()
        ));
        refreshMarkedEmpty(reloadQueue(queue.getId()));
    }

    /**
     * Called when a linked game session ends or is aborted — dismantles lobby state and allows a new cycle.
     */
    @Transactional
    public void onLinkedGameEnded(UUID gameSessionId) {
        gameQueueRepository.findByLinkedGameSessionId(gameSessionId).ifPresent(queue -> {
            UUID roomId = queue.getRoom().getId();
            String gameType = queue.getGameType();
            queue.setLinkedGameSessionId(null);
            queue.setStatus("WAITING");
            gameQueueRepository.save(queue);
            refreshMarkedEmpty(reloadQueue(queue.getId()));
            queueEventSender.send(roomId, "QUEUE_FINISHED", Map.of("gameType", gameType));
        });
    }

    @Transactional
    public void removeUserFromAllQueuesInRoom(UUID roomId, UUID userId) {
        List<String> waitingTypes = gameQueuePlayerRepository.findWaitingGameTypesForUserInRoom(userId, roomId);
        if (waitingTypes.isEmpty()) {
            return;
        }
        gameQueuePlayerRepository.deleteAllWaitingForUserInRoom(userId, roomId);
        for (String gt : waitingTypes) {
            queueEventSender.send(roomId, "QUEUE_PLAYER_LEFT", Map.of(
                    "gameType", gt,
                    "userId", userId.toString()
            ));
            gameQueueRepository.findByRoom_IdAndGameType(roomId, gt)
                    .ifPresent(q -> refreshMarkedEmpty(reloadQueue(q.getId())));
        }
    }

    @Scheduled(fixedDelayString = "${games.queue.empty-ttl-scan-ms:60000}")
    @Transactional
    public void purgeStaleEmptyQueues() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(5);
        for (GameQueue q : gameQueueRepository.findStaleWaitingMarkedEmpty(threshold)) {
            long pc = gameQueuePlayerRepository.countByQueue_Id(q.getId());
            long bc = gameQueueBotRepository.countByQueue_Id(q.getId());
            if (pc == 0 && bc == 0 && q.getLinkedGameSessionId() == null) {
                gameQueueRepository.delete(q);
            }
        }
    }

    private GameQueueDto toDtoOrEmpty(UUID roomId, String gameType) {
        return gameQueueRepository.findByRoom_IdAndGameType(roomId, gameType)
                .map(this::toDto)
                .orElseGet(() -> GameQueueDto.builder()
                        .gameType(gameType)
                        .status("WAITING")
                        .minPlayers(minPlayers(gameType))
                        .maxPlayers(maxPlayers(gameType))
                        .players(List.of())
                        .bots(List.of())
                        .build());
    }

    private GameQueueDto toDto(GameQueue queue) {
        String gt = queue.getGameType();
        List<GameQueuePlayerDto> players = gameQueuePlayerRepository.findByQueue_Id(queue.getId()).stream()
                .sorted(Comparator.comparing(GameQueuePlayer::getJoinedAt))
                .map(p -> GameQueuePlayerDto.builder()
                        .userId(p.getUser().getId().toString())
                        .username(p.getUser().getName())
                        .avatarUrl(publicAbsoluteUrlResolver.resolve(p.getUser().getAvatarUrl()))
                        .isBot(false)
                        .isReady(Boolean.TRUE.equals(p.getIsReady()))
                        .build())
                .collect(Collectors.toList());

        List<GameQueueBotDto> bots = gameQueueBotRepository.findByQueue_Id(queue.getId()).stream()
                .map(b -> GameQueueBotDto.builder()
                        .botId(b.getBotUser().getId().toString())
                        .name(b.getBotUser().getName())
                        .avatarUrl(publicAbsoluteUrlResolver.resolve(b.getBotUser().getAvatarUrl()))
                        .difficulty(b.getDifficulty())
                        .isBot(true)
                        .isReady(true)
                        .build())
                .toList();

        return GameQueueDto.builder()
                .gameType(gt)
                .status(queue.getStatus())
                .minPlayers(minPlayers(gt))
                .maxPlayers(maxPlayers(gt))
                .players(players)
                .bots(bots)
                .build();
    }

    private GameQueue reloadQueue(UUID queueId) {
        return gameQueueRepository.findById(queueId).orElseThrow(() -> new NotFoundException("Queue not found"));
    }

    private GameQueue getOrCreateQueue(Room room, String gameType) {
        return gameQueueRepository.findByRoom_IdAndGameType(room.getId(), gameType)
                .orElseGet(() -> gameQueueRepository.save(GameQueue.builder()
                        .room(room)
                        .gameType(gameType)
                        .status("WAITING")
                        .build()));
    }

    private void removeUserFromOtherQueuesInRoom(UUID roomId, UUID userId, String targetGameType) {
        List<String> leftTypes = gameQueuePlayerRepository.findWaitingGameTypesExcept(userId, roomId, targetGameType);
        if (leftTypes.isEmpty()) {
            return;
        }
        gameQueuePlayerRepository.deleteFromOtherWaitingQueues(userId, roomId, targetGameType);
        for (String gt : leftTypes) {
            queueEventSender.send(roomId, "QUEUE_PLAYER_LEFT", Map.of(
                    "gameType", gt,
                    "userId", userId.toString()
            ));
            gameQueueRepository.findByRoom_IdAndGameType(roomId, gt)
                    .ifPresent(q -> refreshMarkedEmpty(reloadQueue(q.getId())));
        }
    }

    private void touchQueueNotEmpty(GameQueue queue) {
        queue.setMarkedEmptyAt(null);
        gameQueueRepository.save(queue);
    }

    private void refreshMarkedEmpty(GameQueue queue) {
        long n = countMembers(queue.getId());
        if (n == 0 && "WAITING".equals(queue.getStatus()) && queue.getLinkedGameSessionId() == null) {
            queue.setMarkedEmptyAt(OffsetDateTime.now());
        } else {
            queue.setMarkedEmptyAt(null);
        }
        gameQueueRepository.save(queue);
    }

    private long countMembers(UUID queueId) {
        return gameQueuePlayerRepository.countByQueue_Id(queueId) + gameQueueBotRepository.countByQueue_Id(queueId);
    }

    private void requireParticipant(UUID roomId, UUID userId) {
        if (!roomParticipantRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new BadRequestException("User is not a participant of this room");
        }
    }

    private void assertGamesAllowed(String roomContext) {
        if (!GAMES_ALLOWED_CONTEXT.equals(roomContext)) {
            throw new BadRequestException("Games are available only in leisure rooms");
        }
    }

    private String parseGameType(String raw) {
        String t = SupportedGameTypes.normalizePathSegment(raw);
        if (!SupportedGameTypes.isSupported(t)) {
            throw new BadRequestException("Unsupported game type");
        }
        return t;
    }

    private int minPlayers(String gameType) {
        return "GARTIC_PHONE".equals(gameType) ? 4 : 3;
    }

    private int maxPlayers(String gameType) {
        return 8;
    }

    private void validateBotTypeForGame(String gameType, String botType) {
        if ("GARTIC_PHONE".equals(gameType) && !botType.startsWith("GARTIC_")) {
            throw new BadRequestException("GARTIC game accepts only GARTIC_* bot types");
        }
        if ("QUIPLASH".equals(gameType) && !botType.startsWith("QUIPLASH_")) {
            throw new BadRequestException("QUIPLASH game accepts only QUIPLASH_* bot types");
        }
    }

    private static String normalizeDifficulty(String d) {
        if (d == null || d.isBlank()) {
            return "MEDIUM";
        }
        String u = d.trim().toUpperCase();
        if (!u.equals("EASY") && !u.equals("MEDIUM") && !u.equals("HARD")) {
            throw new BadRequestException("difficulty must be EASY, MEDIUM, or HARD");
        }
        return u;
    }
}

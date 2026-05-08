package ru.syncroom.games.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.syncroom.games.domain.GameQueuePlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameQueuePlayerRepository extends JpaRepository<GameQueuePlayer, UUID> {

    Optional<GameQueuePlayer> findByQueue_IdAndUser_Id(UUID queueId, UUID userId);

    List<GameQueuePlayer> findByQueue_Id(UUID queueId);

    List<GameQueuePlayer> findByUser_Id(UUID userId);

    @Query("select p from GameQueuePlayer p join p.queue q where p.user.id = :userId and q.room.id = :roomId")
    List<GameQueuePlayer> findByUserIdAndRoomId(@Param("userId") UUID userId, @Param("roomId") UUID roomId);

    @Query("select q.gameType from GameQueuePlayer p join p.queue q where p.user.id = :userId and q.room.id = :roomId and q.gameType <> :targetType and q.status = 'WAITING'")
    List<String> findWaitingGameTypesExcept(@Param("userId") UUID userId, @Param("roomId") UUID roomId, @Param("targetType") String targetType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from GameQueuePlayer p where p.user.id = :userId and p.queue.room.id = :roomId and p.queue.gameType <> :targetType and p.queue.status = 'WAITING'")
    int deleteFromOtherWaitingQueues(@Param("userId") UUID userId, @Param("roomId") UUID roomId, @Param("targetType") String targetType);

    @Query("select q.gameType from GameQueuePlayer p join p.queue q where p.user.id = :userId and q.room.id = :roomId and q.status = 'WAITING'")
    List<String> findWaitingGameTypesForUserInRoom(@Param("userId") UUID userId, @Param("roomId") UUID roomId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from GameQueuePlayer p where p.user.id = :userId and p.queue.room.id = :roomId and p.queue.status = 'WAITING'")
    int deleteAllWaitingForUserInRoom(@Param("userId") UUID userId, @Param("roomId") UUID roomId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from GameQueuePlayer p where p.queue.id = :queueId and p.user.id = :userId")
    int deleteByQueueIdAndUserId(@Param("queueId") UUID queueId, @Param("userId") UUID userId);

    long countByQueue_Id(UUID queueId);
}

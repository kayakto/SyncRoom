package ru.syncroom.study.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.syncroom.study.domain.TaskLike;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TaskLikeRepository extends JpaRepository<TaskLike, UUID> {

    long countByTask_Id(UUID taskId);

    boolean existsByTask_IdAndUser_Id(UUID taskId, UUID userId);

    void deleteByTask_IdAndUser_Id(UUID taskId, UUID userId);

    @Query("select l.task.id, count(l) from TaskLike l where l.task.room.id = :roomId group by l.task.id")
    List<Object[]> countByTaskGroupedInRoom(@Param("roomId") UUID roomId);

    @Query("select l.task.id from TaskLike l where l.user.id = :userId and l.task.room.id = :roomId")
    Set<UUID> findTaskIdsLikedByUserInRoom(@Param("userId") UUID userId, @Param("roomId") UUID roomId);

    @Query("select l.task.id from TaskLike l where l.user.id = :userId and l.task.id in :taskIds")
    Set<UUID> findTaskIdsLikedByUserAmong(@Param("userId") UUID userId, @Param("taskIds") Collection<UUID> taskIds);

    @Query("select count(l) from TaskLike l where l.task.user.id = :ownerId and l.task.room.id = :roomId")
    long countLikesOnTasksOwnedByUserInRoom(@Param("ownerId") UUID ownerId, @Param("roomId") UUID roomId);

    @Query("select count(l) from TaskLike l where l.task.ownerSeatBot.id = :ownerBotId and l.task.room.id = :roomId")
    long countLikesOnTasksOwnedBySeatBotInRoom(@Param("ownerBotId") UUID ownerBotId, @Param("roomId") UUID roomId);

    @Query("select count(l) from TaskLike l where l.task.user.id = :ownerId")
    long countLikesOnTasksOwnedByUser(@Param("ownerId") UUID ownerId);

    boolean existsByTask_IdAndLikerSeatBot_Id(UUID taskId, UUID likerSeatBotId);
}

package ru.syncroom.study.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.syncroom.study.domain.StudyTask;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudyTaskRepository extends JpaRepository<StudyTask, UUID> {

    List<StudyTask> findByUserIdAndRoomIdOrderBySortOrderAsc(UUID userId, UUID roomId);

    Optional<StudyTask> findByIdAndUserIdAndRoomId(UUID id, UUID userId, UUID roomId);

    Optional<StudyTask> findByIdAndRoom_Id(UUID id, UUID roomId);

    List<StudyTask> findByRoom_IdOrderByUser_IdAscSortOrderAsc(UUID roomId);

    long countByUser_IdAndRoom_Id(UUID userId, UUID roomId);

    long countByUser_IdAndRoom_IdAndIsDoneTrue(UUID userId, UUID roomId);

    long countByUser_IdAndIsDoneTrue(UUID userId);

    long countByUser_IdAndIsDoneTrueAndUpdatedAtGreaterThanEqual(UUID userId, OffsetDateTime threshold);

    List<StudyTask> findTop20ByUser_IdAndIsDoneTrueOrderByUpdatedAtDesc(UUID userId);

    void deleteByRoom_IdAndUser_Id(UUID roomId, UUID userId);

    long countByOwnerSeatBot_IdAndRoom_Id(UUID ownerSeatBotId, UUID roomId);

    long countByOwnerSeatBot_IdAndRoom_IdAndIsDoneTrue(UUID ownerSeatBotId, UUID roomId);

    @Query("SELECT COALESCE(MAX(t.sortOrder), -1) FROM StudyTask t WHERE t.ownerSeatBot.id = :botId AND t.room.id = :roomId")
    int maxSortOrderByOwnerSeatBot(@Param("botId") UUID botId, @Param("roomId") UUID roomId);

    @Query("SELECT t FROM StudyTask t WHERE t.room.id = :roomId AND t.user IS NOT NULL AND t.isDone = false")
    List<StudyTask> findOpenHumanTasksInRoom(@Param("roomId") UUID roomId);
}


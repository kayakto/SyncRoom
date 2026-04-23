package ru.syncroom.study.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.syncroom.study.domain.StudyTask;

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

    void deleteByRoom_IdAndUser_Id(UUID roomId, UUID userId);
}


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
}


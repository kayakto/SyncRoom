package ru.syncroom.study.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.syncroom.study.domain.PomodoroSession;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PomodoroSessionRepository extends JpaRepository<PomodoroSession, UUID> {

    Optional<PomodoroSession> findByRoomId(UUID roomId);

    void deleteByRoomId(UUID roomId);
}


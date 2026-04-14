package ru.syncroom.study.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.syncroom.study.domain.PomodoroSession;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PomodoroSessionRepository extends JpaRepository<PomodoroSession, UUID> {

    Optional<PomodoroSession> findByRoomId(UUID roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PomodoroSession p WHERE p.room.id = :roomId")
    Optional<PomodoroSession> findByRoomIdForUpdate(@Param("roomId") UUID roomId);

    @Query("SELECT p.room.id FROM PomodoroSession p WHERE p.phase IN ('WORK', 'BREAK', 'LONG_BREAK') "
            + "AND p.phaseEndAt IS NOT NULL AND p.phaseEndAt <= :now")
    List<UUID> findRoomIdsWithExpiredPhase(@Param("now") OffsetDateTime now);

    void deleteByRoomId(UUID roomId);
}


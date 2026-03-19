package ru.syncroom.projector.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.syncroom.projector.domain.ProjectorSession;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ProjectorSession entities.
 */
@Repository
public interface ProjectorSessionRepository extends JpaRepository<ProjectorSession, UUID> {

    /** Find the active projector session for a given room. */
    Optional<ProjectorSession> findByRoomId(UUID roomId);

    /** Find a projector session by its SRS stream key (used in SRS HTTP callback). */
    Optional<ProjectorSession> findByStreamKey(String streamKey);
}

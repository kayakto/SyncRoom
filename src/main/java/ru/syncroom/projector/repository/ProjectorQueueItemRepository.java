package ru.syncroom.projector.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.syncroom.projector.domain.ProjectorQueueItem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectorQueueItemRepository extends JpaRepository<ProjectorQueueItem, UUID> {
    boolean existsByRoom_IdAndUser_IdAndStatusIn(UUID roomId, UUID userId, Collection<String> statuses);

    Optional<ProjectorQueueItem> findFirstByRoom_IdAndStatusOrderByCreatedAtAsc(UUID roomId, String status);

    Optional<ProjectorQueueItem> findFirstByRoom_IdAndStatus(UUID roomId, String status);

    List<ProjectorQueueItem> findByRoom_IdAndStatusInOrderByCreatedAtAsc(UUID roomId, Collection<String> statuses);
}

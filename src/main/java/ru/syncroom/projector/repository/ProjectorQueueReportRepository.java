package ru.syncroom.projector.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.syncroom.projector.domain.ProjectorQueueReport;

import java.util.UUID;

@Repository
public interface ProjectorQueueReportRepository extends JpaRepository<ProjectorQueueReport, UUID> {
    boolean existsByQueueItem_IdAndReporter_Id(UUID queueItemId, UUID reporterId);
    long countByQueueItem_Id(UUID queueItemId);
}

package ru.syncroom.points.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.syncroom.points.domain.Point;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PointRepository extends JpaRepository<Point, UUID> {

    List<Point> findByUserId(UUID userId);

    Optional<Point> findByIdAndUserId(UUID id, UUID userId);

    List<Point> findByUserIdAndContext(UUID userId, String context);

    void deleteByIdAndUserId(UUID id, UUID userId);
}

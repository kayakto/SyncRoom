package ru.syncroom.rooms.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.syncroom.rooms.domain.RoomMessage;

import java.util.UUID;

public interface RoomMessageRepository extends JpaRepository<RoomMessage, UUID> {

    @EntityGraph(attributePaths = "user")
    Page<RoomMessage> findByRoom_IdOrderByCreatedAtDesc(UUID roomId, Pageable pageable);
}

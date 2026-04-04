package ru.syncroom.rooms.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.syncroom.rooms.domain.RoomMessage;

import java.util.UUID;

public interface RoomMessageRepository extends JpaRepository<RoomMessage, UUID> {

    /**
     * Без {@code @EntityGraph}+{@link Page}: в Hibernate это часто даёт ошибки count/pagination
     * и 500 на проде (PostgreSQL). Связь {@code user} подгружается в той же транзакции сервиса (lazy).
     */
    Page<RoomMessage> findByRoom_IdOrderByCreatedAtDesc(UUID roomId, Pageable pageable);
}

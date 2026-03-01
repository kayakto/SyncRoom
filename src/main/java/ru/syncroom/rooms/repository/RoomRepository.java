package ru.syncroom.rooms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.syncroom.rooms.domain.Room;

import java.util.List;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    List<Room> findByIsActiveTrue();

    List<Room> findByContextAndIsActiveTrue(String context);
}

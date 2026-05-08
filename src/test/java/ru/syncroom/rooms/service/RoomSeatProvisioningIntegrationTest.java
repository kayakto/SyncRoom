package ru.syncroom.rooms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import ru.syncroom.rooms.domain.Room;
import ru.syncroom.rooms.domain.Seat;
import ru.syncroom.rooms.repository.RoomRepository;
import ru.syncroom.rooms.repository.RoomSeatBotRepository;
import ru.syncroom.rooms.repository.SeatRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Провижининг: сетка мест и дефолтные seat-боты")
class RoomSeatProvisioningIntegrationTest {

    @Autowired
    private SeatService seatService;
    @Autowired
    private SeatBotService seatBotService;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private RoomSeatBotRepository roomSeatBotRepository;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void purge() {
        roomSeatBotRepository.deleteAll();
        seatRepository.deleteAll();
        roomRepository.deleteAll();
    }

    private Room saveRoom(String context) {
        return roomRepository.save(Room.builder()
                .context(context)
                .title("T")
                .maxParticipants(10)
                .isActive(true)
                .build());
    }

    private List<Seat> seatsOrdered(UUID roomId) {
        return seatRepository.findByRoom_IdOrderByXAscYAsc(roomId);
    }

    @Test
    @DisplayName("ensureSeatGridForRoom создаёт 10 мест; повторный вызов идемпотентен")
    void ensureSeatGridIdempotent() {
        Room room = saveRoom("work");
        seatService.ensureSeatGridForRoom(room.getId());
        assertEquals(10, seatRepository.countByRoom_Id(room.getId()));
        List<Seat> first = seatsOrdered(room.getId());
        assertEquals(0.10, first.getFirst().getX(), 1e-9);
        assertEquals(0.30, first.getFirst().getY(), 1e-9);

        seatService.ensureSeatGridForRoom(room.getId());
        assertEquals(10, seatRepository.countByRoom_Id(room.getId()));
    }

    @Test
    @DisplayName("STUDY: первые два места по (x,y) — STUDY_HELPER и WORK_FOCUS_BUDDY")
    void studyBotsOnFirstTwoSeats() {
        Room room = saveRoom("study");
        seatService.ensureSeatGridForRoom(room.getId());
        seatBotService.seedDefaultSeatBotsIfNeeded(room.getId(), false);

        List<Seat> ord = seatsOrdered(room.getId());
        assertEquals(2, roomSeatBotRepository.countByRoom_Id(room.getId()));
        assertEquals("STUDY_HELPER",
                roomSeatBotRepository.findBySeat_Id(ord.get(0).getId()).orElseThrow().getBotType());
        assertEquals("WORK_FOCUS_BUDDY",
                roomSeatBotRepository.findBySeat_Id(ord.get(1).getId()).orElseThrow().getBotType());
    }

    @Test
    @DisplayName("повторный seedDefaultSeatBotsIfNeeded не дублирует ботов")
    void seedIdempotent() {
        Room room = saveRoom("work");
        seatService.ensureSeatGridForRoom(room.getId());
        seatBotService.seedDefaultSeatBotsIfNeeded(room.getId(), false);
        seatBotService.seedDefaultSeatBotsIfNeeded(room.getId(), false);
        assertEquals(1, roomSeatBotRepository.countByRoom_Id(room.getId()));
    }

    @Test
    @DisplayName("WORK — один WORK_FOCUS_BUDDY на первом месте")
    void workOneBot() {
        Room room = saveRoom("work");
        seatService.ensureSeatGridForRoom(room.getId());
        seatBotService.seedDefaultSeatBotsIfNeeded(room.getId(), false);
        List<Seat> ord = seatsOrdered(room.getId());
        assertEquals(1, roomSeatBotRepository.countByRoom_Id(room.getId()));
        assertEquals("WORK_FOCUS_BUDDY",
                roomSeatBotRepository.findBySeat_Id(ord.getFirst().getId()).orElseThrow().getBotType());
    }

    @Test
    @DisplayName("SPORT — SPORT_CHEERLEADER на первом месте")
    void sportOneBot() {
        Room room = saveRoom("sport");
        seatService.ensureSeatGridForRoom(room.getId());
        seatBotService.seedDefaultSeatBotsIfNeeded(room.getId(), false);
        List<Seat> ord = seatsOrdered(room.getId());
        assertEquals(1, roomSeatBotRepository.countByRoom_Id(room.getId()));
        assertEquals("SPORT_CHEERLEADER",
                roomSeatBotRepository.findBySeat_Id(ord.getFirst().getId()).orElseThrow().getBotType());
    }

    @Test
    @DisplayName("leisure — сетка возможна, сидинг ботов не добавляет записей")
    void leisureNoBots() {
        Room room = saveRoom("leisure");
        seatService.ensureSeatGridForRoom(room.getId());
        seatBotService.seedDefaultSeatBotsIfNeeded(room.getId(), false);
        assertEquals(0, roomSeatBotRepository.countByRoom_Id(room.getId()));
    }
}

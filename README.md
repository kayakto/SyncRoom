# SyncRoom Backend

Spring Boot backend для приложения SyncRoom с JWT-авторизацией, REST API и WebSocket (STOMP).

## Технологии

| | |
|--|--|
| Java 21 + Spring Boot **3.4.3** | Основа |
| PostgreSQL + Flyway | База данных и миграции |
| Spring Security + JWT | Авторизация |
| Spring WebSocket + STOMP | Real-time события |
| Redis (опционально) | Кэш состояния мест |
| Spring Data JPA | ORM |
| SpringDoc (Swagger UI) | Документация API |
| H2 + JUnit 5 + MockMvc | Тесты |

## Запуск

### Docker (рекомендуется)
```bash
docker-compose up -d        # запустить
docker-compose down         # остановить
docker-compose down -v      # остановить + удалить данные
docker-compose up -d --build  # пересобрать
docker-compose logs -f app  # логи
```
Приложение: http://localhost:8080  
Swagger UI: http://localhost:8080/swagger-ui.html

### Gradle
```bash
gradle bootRun
gradle test
```

### Переменные окружения (`.env`)
```
DB_NAME=syncroom
DB_USERNAME=syncroom
DB_PASSWORD=syncroom
JWT_SECRET=your-secret-min-32-chars
JWT_ACCESS_EXPIRATION=900000       # 15 минут
JWT_REFRESH_EXPIRATION=2592000000  # 30 дней
REDIS_HOST=localhost               # опционально
REDIS_PORT=6379                    # опционально
APP_PORT=8080
```

---

## API — сводная таблица

Все эндпоинты кроме `/api/auth/*` требуют `Authorization: Bearer <accessToken>`.

### Авторизация

| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/api/auth/register` | Регистрация email |
| POST | `/api/auth/email` | Вход email/password |
| POST | `/api/auth/oauth` | Вход через VK / Yandex |
| POST | `/api/auth/refresh` | Обновление токенов |

### Профиль пользователя

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/users/me` | Получить свой профиль |
| PUT | `/api/users/me` | Обновить профиль |

### Points — точки на карте

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/users/{userId}/points` | Список точек |
| POST | `/api/users/{userId}/points` | Создать точку |
| PUT | `/api/users/{userId}/points/{pointId}` | Обновить точку |
| DELETE | `/api/users/{userId}/points/{pointId}` | Удалить точку → 204 |

### Rooms — комнаты

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/rooms` | Все комнаты (включая `seats[]`) |
| GET | `/api/rooms/my` | Комнаты текущего пользователя |
| POST | `/api/rooms/{id}/join` | Войти → `{ room, participants[] }` |
| POST | `/api/rooms/{id}/leave` | Выйти → 204, авто-освобождает место |

> **Правило:** 1 пользователь = 1 комната одновременно.  
> При заполнении комнаты автоматически создаётся новая с теми же параметрами.

**Структура комнаты** включает `backgroundPicture` и `seats[]` (с нормализованными координатами 0.0–1.0).

### Seats — места в комнате ✨

| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/api/rooms/{roomId}/seats/{seatId}/sit` | Занять место → `SeatDto` |
| POST | `/api/rooms/{roomId}/seats/{seatId}/leave` | Встать с места → `SeatDto` |

**Правила:** пользователь может занимать только 1 место в комнате. При выборе нового места — автоматическая пересадка. При выходе из комнаты — место освобождается автоматически.

**Коды ответов:** `200` успех, `403` чужое место, `404` не найдено, `409` место занято другим.

---

## WebSocket (STOMP)

### Подключение

| Эндпоинт | Назначение |
|----------|-----------| 
| `ws://host:8080/ws-stomp` | Нативный WebSocket (Android) |
| `ws://host:8080/ws/websocket` | SockJS transport (web) |

Токен передаётся в STOMP CONNECT frame:
```
CONNECT
Authorization:Bearer <accessToken>
```

### Топики

| Топик | События |
|-------|---------|
| `/topic/room/{roomId}` | `PARTICIPANT_JOINED`, `PARTICIPANT_LEFT` |
| `/topic/room/{roomId}/seats` | `SEAT_TAKEN`, `SEAT_LEFT` ✨ |

### Типы событий

| Тип | Топик | Триггер | Payload |
|-----|-------|---------|---------|
| `PARTICIPANT_JOINED` | `.../room/{id}` | `POST /join` | `{ userId, name, avatarUrl, joinedAt }` |
| `PARTICIPANT_LEFT` | `.../room/{id}` | `POST /leave` | `{ userId, name, avatarUrl }` |
| `SEAT_TAKEN` | `.../room/{id}/seats` | `POST /sit` | `{ seatId, user: { id, name, avatarUrl } }` |
| `SEAT_LEFT` | `.../room/{id}/seats` | `POST /leave seat` или выход | `{ seatId, userId }` |

### Тестирование WebSocket

Открой `stomp-test.html` в браузере (корень проекта):
1. Вставь `accessToken` → **Connect**
2. Нажми **GET /api/rooms** — авто-заполнит roomId и первый свободный seatId
3. **Subscribe /seats** → **POST /sit** → увидишь `SEAT_TAKEN`

> Postman WebSocket tab не подходит для STOMP — используй `stomp-test.html`.

---

## Структура проекта

```
src/main/java/ru/syncroom/
├── auth/           # Авторизация (JWT, OAuth VK/Yandex, email)
├── users/          # Профиль пользователя
├── points/         # Точки на карте (CRUD)
├── rooms/
│   ├── controller/ # REST: /api/rooms, /api/rooms/{id}/seats
│   ├── service/    # RoomService, SeatService (бизнес-логика + WS-события)
│   ├── dto/        # RoomResponse (с seats[]), SeatDto, JoinRoomResponse
│   ├── domain/     # Room, RoomParticipant, Seat (JPA)
│   ├── repository/ # RoomRepository, SeatRepository
│   └── ws/         # RoomEvent, RoomEventType, SeatTakenPayload, SeatLeftPayload
└── common/
    ├── config/     # SecurityConfig (CORS), WebSocketConfig, WebSocketSecurityConfig
    ├── security/   # JwtTokenService, JwtAuthenticationFilter
    └── exception/  # GlobalExceptionHandler (400/403/404/409)
```

**Миграции Flyway:**
```
V1 — создание users
V2 — создание rooms + participants
V3 — создание points
V4 — добавление background_picture к rooms
V5 — создание seats (10 мест на комнату, нормализованные x/y)
```

---

## Тесты

```bash
gradle test
```

| Файл | Что тестирует | Тестов |
|------|--------------|--------|
| `AuthControllerTest` | register, email login, OAuth, refresh | 13 |
| `UserControllerTest` | GET/PUT /me | 9 |
| `PointControllerTest` | CRUD точек | 24 |
| `RoomControllerTest` | GET rooms/my, join, leave | 31 |
| `RoomServiceWebSocketTest` | WS-события PARTICIPANT_JOINED/LEFT | 14 |
| `SeatControllerTest` | sit, stand-up, auto-move, 403, 409, 404 | 9 |
| **Итого** | | **103** |

Тесты используют H2 in-memory БД, `@MockitoBean SimpMessagingTemplate` (Spring Boot 3.4+), Redis не требуется.

---

## Безопасность

- JWT HMAC-SHA256 (HS256)
- Access token: 15 минут | Refresh token: 30 дней
- Пароли: BCrypt
- WebSocket auth: JWT в STOMP CONNECT frame (через `ChannelInterceptor`)
- CORS: разрешены все origins для dev-среды (`CorsConfigurationSource`)

---

## Для Android разработчика

См. [README-ANDROID.md](README-ANDROID.md) — полная документация API, Kotlin data classes, Retrofit спецификация, сценарии WS-событий.

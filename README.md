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
| GET | `/api/rooms/{roomId}/messages` | История чата комнаты (`?page=0&size=50`) |

> **Правило:** 1 пользователь = 1 комната одновременно.  
> При заполнении комнаты автоматически создаётся новая с теми же параметрами.

**Структура комнаты** включает `backgroundPicture` и `seats[]` (с нормализованными координатами 0.0–1.0).

**Чат комнаты** (любой `context`, активная или пассивная комната):

- Историю может запросить только участник комнаты.
- В теле страницы `content[]` сообщения отсортированы по времени **от старых к новым** (в пределах страницы).
- Новые сообщения отправляются через WebSocket (см. ниже), дублируются в БД и рассылаются подписчикам `/topic/room/{roomId}/chat` **объектом сообщения** (без обёртки `type`/`payload`).

Пример ответа `GET /api/rooms/{roomId}/messages?page=0&size=50`:

```json
{
  "content": [
    { "id": "uuid", "userId": "uuid", "userName": "Аня", "text": "Привет!", "createdAt": "2026-03-29T12:00:00+03:00" }
  ],
  "totalPages": 1
}
```

### Seats — места в комнате ✨

| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/api/rooms/{roomId}/seats/{seatId}/sit` | Занять место → `SeatDto` |
| POST | `/api/rooms/{roomId}/seats/{seatId}/leave` | Встать с места → `SeatDto` |

**Правила:** пользователь может занимать только 1 место в комнате. При выборе нового места — автоматическая пересадка. При выходе из комнаты — место освобождается автоматически.

**Коды ответов:** `200` успех, `403` чужое место, `404` не найдено, `409` место занято другим.

### Projector — совместный просмотр (Embed + RTMP)

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/rooms/{roomId}/projector` | Текущее состояние проектора в комнате |
| POST | `/api/rooms/{roomId}/projector` | Включить / перезапустить проектор |
| DELETE | `/api/rooms/{roomId}/projector` | Выключить проектор (только хост) |
| POST | `/api/projector/srs-callback` | Внутренний callback от SRS (RTMP медиасервер) |

**Вариант A — EMBED (готовое видео):**

- Request:  
  `POST /api/rooms/{roomId}/projector`
  ```json
  { "mode": "EMBED", "videoUrl": "https://vk.com/video_ext.php?oid=-123&id=456", "videoTitle": "Лекция" }
  ```
- В БД создаётся / обновляется `projector_sessions` с `mode="EMBED"`, `videoUrl`, `isPlaying=false`, `positionMs=0`.

**Вариант B — STREAM (RTMP → HLS):**

- Request:  
  `POST /api/rooms/{roomId}/projector`
  ```json
  { "mode": "STREAM", "videoTitle": "Стрим Полины" }
  ```
- Сервер генерирует:
  - `streamKey = "room-" + roomId`
  - `videoUrl = "http://{SRS_HOST}:8085/live/{streamKey}.m3u8"` — для воспроизведения у зрителей
  - `rtmpUrl = "rtmp://{SRS_HOST}:1935/live/{streamKey}"` — возвращается **только хосту** (для OBS / камеры).

**Правила:**

- 1 активный проектор на комнату (`UNIQUE(room_id)`).
- Включить проектор может любой участник комнаты; при этом хостом становится он, старая сессия затирается.
- Выключить (`DELETE`) и управлять воспроизведением (play/pause/seek) может только текущий хост.

### Pomodoro — общий таймер для учебных комнат

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/rooms/{roomId}/pomodoro` | Текущее состояние помодоро-таймера |
| POST | `/api/rooms/{roomId}/pomodoro/start` | Запустить помодоро (только `context="study"`) |
| POST | `/api/rooms/{roomId}/pomodoro/pause` | Поставить таймер на паузу |
| POST | `/api/rooms/{roomId}/pomodoro/resume` | Продолжить после паузы |
| POST | `/api/rooms/{roomId}/pomodoro/skip` | Пропустить текущую фазу (WORK/BREAK/LONG_BREAK) |
| DELETE | `/api/rooms/{roomId}/pomodoro` | Остановить и удалить таймер |

**Старт помодоро:**

```json
POST /api/rooms/{roomId}/pomodoro/start
{
  "workDuration": 1500,
  "breakDuration": 300,
  "longBreakDuration": 900,
  "roundsTotal": 4
}
```

- если тело пустое — используются значения по умолчанию (25/5/15/4);
- только участники комнаты с `context="study"` могут запускать таймер;
- в комнате может быть только один активный таймер (UNIQUE room_id).

**Ответ `PomodoroResponse`:**

```json
{
  "id": "uuid",
  "roomId": "uuid",
  "startedBy": { "id": "user-uuid", "name": "Аня", "avatarUrl": "https://..." },
  "phase": "WORK",
  "currentRound": 1,
  "roundsTotal": 4,
  "phaseEndAt": "2026-03-18T12:25:00Z",
  "workDuration": 1500,
  "breakDuration": 300,
  "longBreakDuration": 900
}
```

### Study Tasks — персональные таски в учебной комнате

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/rooms/{roomId}/tasks` | Мои таски в комнате (`sortOrder` по возрастанию) |
| POST | `/api/rooms/{roomId}/tasks` | Создать таск |
| PUT | `/api/rooms/{roomId}/tasks/{taskId}` | Обновить таск (partial update) |
| DELETE | `/api/rooms/{roomId}/tasks/{taskId}` | Удалить таск |

**Создание таска:**

```json
POST /api/rooms/{roomId}/tasks
{
  "text": "Прочитать главу 5"
}
```

- `sortOrder` назначается как `MAX(sortOrder) + 1` для пользователя в этой комнате.

**Ответ `TaskResponse`:**

```json
{
  "id": "uuid",
  "text": "Прочитать главу 5",
  "isDone": false,
  "sortOrder": 2
}
```

### Games — Quiplash + Gartic Phone

| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/api/rooms/{roomId}/games` | Создать игровую сессию (лобби) |
| GET | `/api/rooms/{roomId}/games/current` | Получить активную игру в комнате |
| POST | `/api/games/{gameId}/ready` | Отметиться готовым (upsert игрока) |
| POST | `/api/games/{gameId}/start` | Старт игры (минимум 3 ready игрока) |

**Create game request:**

```json
{
  "gameType": "QUIPLASH"
}
```

`gameType` может быть:
- `QUIPLASH`
- `GARTIC_PHONE`

**Create game response:**

```json
{
  "gameId": "uuid",
  "gameType": "QUIPLASH",
  "status": "LOBBY",
  "players": [
    { "id": "user-uuid", "name": "Полина", "avatarUrl": "...", "isReady": false, "score": 0 }
  ]
}
```

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
| `/topic/room/{roomId}/projector` | `PROJECTOR_STARTED`, `PROJECTOR_STOPPED`, `PROJECTOR_CONTROL`, `STREAM_LIVE`, `STREAM_OFFLINE` ✨ |
| `/topic/room/{roomId}/pomodoro` | `POMODORO_STARTED`, `POMODORO_PHASE_CHANGED`, `POMODORO_PAUSED`, `POMODORO_RESUMED`, `POMODORO_STOPPED` ✨ |
| `/topic/room/{roomId}/chat` | Сообщения чата: JSON `{ id, userId, userName, text, createdAt }` |
| `/topic/game/{gameId}` | Quiplash: `GAME_STARTED`, `PROMPT_RECEIVED`, `WAITING_FOR_OTHERS`, `WAITING_FOR_VOTES`, `ROUND_RESULT`, `GAME_FINISHED`; Gartic: `STEP_WRITE`, `STEP_DRAW`, `STEP_GUESS`, `REVEAL_CHAIN`, `GAME_FINISHED` ✨ |

### Управление проектором (STOMP)

**Подписка:**

```text
SUBSCRIBE
destination:/topic/room/{roomId}/projector
id:sub-projector
```

**События:**

- `PROJECTOR_STARTED`
  ```json
  {
    "type": "PROJECTOR_STARTED",
    "payload": {
      "host": { "id": "uuid", "name": "Полина", "avatarUrl": "https://..." },
      "mode": "EMBED",
      "videoUrl": "https://vk.com/video_ext.php?oid=-123&id=456",
      "videoTitle": "Лекция по матану"
    },
    "timestamp": "2026-03-18T12:00:00Z"
  }
  ```
- `PROJECTOR_STOPPED` — `{ "hostId": "uuid" }`
- `PROJECTOR_CONTROL` (только EMBED) — `{ "action": "PLAY|PAUSE|SEEK", "positionMs": 42000 }`
- `STREAM_LIVE` / `STREAM_OFFLINE` (только STREAM)

**Отправка управления (от хоста, EMBED):**

```text
SEND
destination:/app/room/{roomId}/projector/control

{
  "action": "PLAY",
  "positionMs": 42000
}
```

### Типы событий

| Тип | Топик | Триггер | Payload |
|-----|-------|---------|---------|
| `PARTICIPANT_JOINED` | `.../room/{id}` | `POST /join` | `{ userId, name, avatarUrl, joinedAt }` |
| `PARTICIPANT_LEFT` | `.../room/{id}` | `POST /leave` | `{ userId, name, avatarUrl }` |
| `SEAT_TAKEN` | `.../room/{id}/seats` | `POST /sit` | `{ seatId, user: { id, name, avatarUrl } }` |
| `SEAT_LEFT` | `.../room/{id}/seats` | `POST /leave seat` или выход | `{ seatId, userId }` |
| `PROJECTOR_STARTED` | `.../room/{id}/projector` | `POST /projector` | `{ host, mode, videoUrl, videoTitle, streamKey? }` |
| `PROJECTOR_STOPPED` | `.../room/{id}/projector` | `DELETE /projector` или выход хоста | `{ hostId }` |
| `PROJECTOR_CONTROL` | `.../room/{id}/projector` | WS SEND `/projector/control` | `{ action, positionMs }` |
| `STREAM_LIVE` | `.../room/{id}/projector` | SRS `on_publish` | `{ videoUrl }` |
| `STREAM_OFFLINE` | `.../room/{id}/projector` | SRS `on_unpublish` | `{}` |
| `GAME_STARTED` | `.../game/{id}` | `POST /games/{id}/start` | `{ players[] }` |
| `PROMPT_RECEIVED` | `.../game/{id}` | старт / следующий раунд | `{ promptId, text, timeLimit }` |
| `WAITING_FOR_OTHERS` | `.../game/{id}` | игрок отправил ответ раньше других | `{}` |
| `WAITING_FOR_VOTES` | `.../game/{id}` | собраны ответы | `{ promptId, answers[], timeLimit }` |
| `ROUND_RESULT` | `.../game/{id}` | завершено голосование раунда | `{ round, results[], scores[] }` |
| `GAME_FINISHED` | `.../game/{id}` | завершен 3-й раунд | `{ scores[] }` |
| `STEP_WRITE` | `.../game/{id}` | старт шага Gartic (первый текст) | `{ stepNumber, timeLimit: 60 }` |
| `STEP_DRAW` | `.../game/{id}` | шаг рисования в Gartic | `{ phrase, timeLimit: 90 }` |
| `STEP_GUESS` | `.../game/{id}` | шаг угадывания в Gartic | `{ imageBase64, timeLimit: 60 }` |
| `REVEAL_CHAIN` | `.../game/{id}` | окончание Gartic, показ цепочек | `{ chains[] }` |

### Тестирование WebSocket

Открой `stomp-test.html` в браузере (корень проекта):
1. Вставь `accessToken` → **Connect**
2. Нажми **GET /api/rooms** — авто-заполнит roomId и первый свободный seatId
3. **Subscribe /seats** → **POST /sit** → увидишь `SEAT_TAKEN`
4. В блоке **Проектор**: заполни Room ID, mode / URL, нажми **POST /projector (start)** и **👂 Subscribe /projector** — в логе появятся события проектора.
5. В блоке **Игры**:
   - **Quiplash**: `gameType=QUIPLASH`, отправляй `SUBMIT_ANSWER` / `SUBMIT_VOTE`;
   - **Gartic Phone**: `gameType=GARTIC_PHONE`, отправляй `SUBMIT_PHRASE` / `SUBMIT_DRAWING` / `SUBMIT_GUESS`.
6. В блоке **Чат**: `POST /join` в комнату → **GET /messages** и **Subscribe `/topic/room/{roomId}/chat`** → **WS SEND** `/app/room/{roomId}/chat` с `{ "text": "..." }`.

> Postman WebSocket tab не подходит для STOMP — используй `stomp-test.html`.

---

## Структура проекта

```
src/main/java/ru/syncroom/
├── auth/           # Авторизация (JWT, OAuth VK/Yandex, email)
├── users/          # Профиль пользователя
├── points/         # Точки на карте (CRUD)
├── rooms/
│   ├── controller/ # REST: /api/rooms, /api/rooms/{id}/seats, /api/rooms/{id}/messages (чат)
│   ├── service/    # RoomService, SeatService, RoomChatService
│   ├── dto/        # RoomResponse (с seats[]), SeatDto, JoinRoomResponse
│   ├── domain/     # Room, RoomParticipant, Seat, RoomMessage (JPA)
│   ├── repository/ # RoomRepository, SeatRepository, RoomMessageRepository
│   └── ws/         # RoomEvent, RoomChatWsController, SeatTakenPayload, SeatLeftPayload
├── projector/
│   ├── controller/ # REST: /api/rooms/{roomId}/projector, SRS callback
│   ├── service/    # ProjectorService (EMBED/STREAM, WS-события)
│   ├── dto/        # ProjectorRequest, ProjectorResponse, UserDto
│   ├── domain/     # ProjectorSession (JPA)
│   ├── repository/ # ProjectorSessionRepository
│   └── ws/         # ProjectorEvent, ProjectorEventType, ProjectorWsController
├── games/
│   ├── controller/ # REST: /api/rooms/{roomId}/games, /api/games/{gameId}/...
│   ├── service/    # GameService (Quiplash flow + scoring + timers)
│   ├── dto/        # CreateGameRequest, GameResponse, GameActionMessage
│   ├── domain/     # GameSession, GamePlayer, Quiplash* entities, PromptBank
│   ├── repository/ # Game*/Quiplash*/PromptBank repositories
│   └── websocket/  # GameWebSocketHandler, GameEventSender, GameTimerService
└── common/
    ├── config/     # SecurityConfig (CORS), WebSocketConfig, WebSocketSecurityConfig
    ├── security/   # JwtTokenService, JwtAuthenticationFilter
    └── exception/  # GlobalExceptionHandler (400/403/404/409)
```

**Миграции Flyway (основные):**
```
V1 — создание users
V2 — создание rooms + participants
V3 — создание points
V4 — добавление background_picture к rooms
V5 — создание seats (10 мест на комнату, нормализованные x/y)
V6 — создание projector_sessions (проектор-стрим)
V7 — создание pomodoro_sessions и study_tasks
V8 — добавление полей paused_phase и remaining_seconds к pomodoro_sessions
V9 — создание game_sessions, game_players, quiplash_* и prompt_bank
V10 — создание gartic_chains и gartic_steps
V11 — создание room_messages (чат комнат)
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
| `ProjectorControllerTest` | REST + SRS callback для проектора | 7 |
| `PomodoroControllerTest` | REST для помодоро | 6 |
| `StudyTaskControllerTest` | REST для учебных тасков | 4 |
| `GameControllerTest` | REST сценарии игр (create/current/ready/start, включая Gartic) | 3 |
| `GameServiceWebSocketTest` | Quiplash + Gartic WS flow, валидации и таймауты | 4 |
| `RoomChatControllerTest` | История чата: пагинация, пустой список, два автора, доступ | 7 |
| `RoomChatServiceTest` | Валидация, trim, пагинация, broadcast, граница 4000 символов | 9 |
| **Итого** | | **141** |

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

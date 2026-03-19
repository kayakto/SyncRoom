# SyncRoom — Документация для Android разработчика

> Как работать с текущим backend API с Android. Основной акцент — новая фича **проектора** (EMBED / RTMP-стрим).

**Base URL:** `http://host:8080`  
**WebSocket URL:** `ws://host:8080/ws-stomp`

---

## 1. Краткое резюме существующих фич

Полная детализация старых возможностей описана в `README.md`. Здесь только выжимка:

- **Auth** — JWT авторизация:
  - `POST /api/auth/email`
  - `POST /api/auth/register`
  - `POST /api/auth/oauth`
  - `POST /api/auth/refresh`
  - Ответ:
    ```json
    { "accessToken": "eyJ...", "refreshToken": "eyJ...", "isFirstLogin": true }
    ```

- **Points** — точки на карте:
  - CRUD по `GET/POST/PUT/DELETE /api/users/{userId}/points`.

- **Rooms** — комнаты:
  - `GET /api/rooms`, `GET /api/rooms/my`
  - `POST /api/rooms/{id}/join`, `POST /api/rooms/{id}/leave`
  - Ограничение: один пользователь может быть только в одной комнате одновременно.

- **Seats** — места в комнате:
  - `POST /api/rooms/{roomId}/seats/{seatId}/sit`
  - `POST /api/rooms/{roomId}/seats/{seatId}/leave`
  - Топик `/topic/room/{roomId}/seats` с событиями `SEAT_TAKEN` / `SEAT_LEFT`.

Ниже подробно описаны **проектор**, **помодоро** и **учебные таски**.

---

## 2. Проектор: совместный просмотр видео

### 2.1. Концепция

В каждой комнате может быть один активный «проектор»:

- **EMBED** — проигрывание готового видео по URL:
  - VK Video (`vk.com/video_ext.php?...`)
  - RuTube (`rutube.ru/play/embed/...`)
  - Прямые `.mp4`, `.webm`, `.m3u8` и т.п.
- **STREAM** — живой RTMP-стрим (OBS / камера телефона → SRS медиасервер → HLS для зрителей).

Любой участник комнаты может:

- включить проектор (становится хостом, старая сессия перезаписывается),
- выключить проектор, если он сейчас хост.

---

## 3. REST API проектора

Все эндпоинты (кроме `srs-callback`) требуют заголовок:

```http
Authorization: Bearer <accessToken>
```

### 3.1. Эндпоинты

```text
GET    /api/rooms/{roomId}/projector      // текущее состояние проектора
POST   /api/rooms/{roomId}/projector      // включить / перезапустить
DELETE /api/rooms/{roomId}/projector      // выключить (только хост)

POST   /api/projector/srs-callback        // внутренний HTTP callback от SRS (без JWT)
```

### 3.2. Запуск EMBED-режима

```http
POST /api/rooms/{roomId}/projector
Content-Type: application/json

{
  "mode": "EMBED",
  "videoUrl": "https://vk.com/video_ext.php?oid=-12345&id=67890",
  "videoTitle": "Лекция по матану"
}
```

- пользователь должен быть участником комнаты;
- `videoUrl` обязателен для EMBED;
- при успешном вызове:
  - в БД создаётся/обновляется запись `projector_sessions`,
  - рассылается WebSocket-событие `PROJECTOR_STARTED` в `/topic/room/{roomId}/projector`.

### 3.3. Запуск STREAM-режима

```http
POST /api/rooms/{roomId}/projector
Content-Type: application/json

{
  "mode": "STREAM",
  "videoTitle": "Стрим Полины"
}
```

- `videoUrl` не передаётся;
- сервер:
  - генерирует `streamKey = "room-" + roomId`;
  - формирует `videoUrl = "http://{SRS_HOST}:8085/live/{streamKey}.m3u8"` — HLS для проигрывания;
  - сохраняет в БД `isLive = false`;
  - в ответе **только хосту** добавляет:
    ```json
    "rtmpUrl": "rtmp://{SRS_HOST}:1935/live/room-..."
    ```
    — её нужно вставить в OBS/камеру.

### 3.4. Получение состояния проектора

```http
GET /api/rooms/{roomId}/projector
```

- 200 — если проектор включён,
- 404 — если проектор не активен в этой комнате.

Пример ответа (EMBED):

```json
{
  "id": "uuid",
  "roomId": "uuid",
  "host": { "id": "user-uuid", "name": "Полина", "avatarUrl": "https://..." },
  "mode": "EMBED",
  "videoUrl": "https://vk.com/video_ext.php?oid=-12345&id=67890",
  "videoTitle": "Лекция по матану",
  "isPlaying": true,
  "positionMs": 42000,
  "isLive": false,
  "updatedAt": "2026-03-18T12:00:00Z"
}
```

Пример ответа (STREAM, для хоста):

```json
{
  "id": "uuid",
  "roomId": "uuid",
  "host": { "id": "user-uuid", "name": "Полина", "avatarUrl": "https://..." },
  "mode": "STREAM",
  "videoUrl": "http://host:8085/live/room-abc123.m3u8",
  "videoTitle": "Стрим Полины",
  "streamKey": "room-abc123",
  "rtmpUrl": "rtmp://host:1935/live/room-abc123",
  "isPlaying": false,
  "positionMs": 0,
  "isLive": true,
  "updatedAt": "2026-03-18T12:00:00Z"
}
```

Если запрос делает **зритель**, поле `rtmpUrl` будет `null`/отсутствовать.

### 3.5. Выключение проектора

```http
DELETE /api/rooms/{roomId}/projector   → 204 No Content
```

- может вызвать только текущий хост;
- backend также автоматически выключает проектор, если хост выходит из комнаты.

---

## 4. WebSocket: события проектора

### 4.1. Подключение STOMP

```text
CONNECT
Authorization:Bearer <accessToken>
accept-version:1.2
```

URL: `ws://host:8080/ws-stomp`.

### 4.2. Каналы

| Назначение | Адрес |
|-----------|-------|
| События проектора | `/topic/room/{roomId}/projector` |
| Управление EMBED (от хоста) | `/app/room/{roomId}/projector/control` |

### 4.3. Формат событий

Общий формат:

```json
{
  "type": "PROJECTOR_STARTED",
  "payload": { ... },
  "timestamp": "2026-03-18T12:00:00Z"
}
```

Основные типы:

- `PROJECTOR_STARTED` — проектор включён или перезаписан;
- `PROJECTOR_STOPPED` — проектор выключен;
- `PROJECTOR_CONTROL` — play/pause/seek (EMBED);
- `STREAM_LIVE` — стрим пошёл в эфир (SRS `on_publish`);
- `STREAM_OFFLINE` — стрим завершился (SRS `on_unpublish`).

### 4.4. Управление EMBED с Android

Хост управляет проектором через STOMP:

```text
SEND
destination:/app/room/{roomId}/projector/control

{
  "action": "PLAY",      // PLAY | PAUSE | SEEK
  "positionMs": 42000
}
```

Backend:

1. проверяет, что это хост;
2. обновляет в БД `isPlaying` и `positionMs`;
3. рассылает `PROJECTOR_CONTROL` всем в `/topic/room/{roomId}/projector`.

---

## 5. Kotlin-модели и примеры кода

### 5.1. Data-классы

```kotlin
@Serializable
data class ProjectorUserDto(
    val id: String,
    val name: String,
    val avatarUrl: String?
)

@Serializable
data class ProjectorResponse(
    val id: String,
    val roomId: String,
    val host: ProjectorUserDto,
    val mode: String,              // "EMBED" или "STREAM"
    val videoUrl: String,
    val videoTitle: String? = null,
    val streamKey: String? = null,
    val rtmpUrl: String? = null,   // только для хоста STREAM
    @SerialName("isPlaying") val isPlaying: Boolean,
    val positionMs: Long,
    @SerialName("isLive") val isLive: Boolean,
    val updatedAt: String
)

@Serializable
data class StartProjectorRequest(
    val mode: String,          // "EMBED" или "STREAM"
    val videoUrl: String? = null,
    val videoTitle: String? = null
)

@Serializable
data class ProjectorEventEnvelope(
    val type: String,
    val payload: JsonObject,
    val timestamp: String
)

@Serializable
data class ProjectorControlPayload(
    val action: String,
    val positionMs: Long
)
```

### 5.2. Retrofit-интерфейс (добавка к существующему)

```kotlin
interface SyncRoomApi {
    // ... уже существующие методы (auth, points, rooms, seats) ...

    // Projector
    @GET("/api/rooms/{roomId}/projector")
    suspend fun getProjector(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String
    ): ProjectorResponse

    @POST("/api/rooms/{roomId}/projector")
    suspend fun startProjector(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String,
        @Body body: StartProjectorRequest
    ): ProjectorResponse

    @DELETE("/api/rooms/{roomId}/projector")
    suspend fun stopProjector(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String
    )
}
```

### 5.3. Подписка на события проектора

```kotlin
// Подключение STOMP
stompClient.connect(
    "ws://host:8080/ws-stomp",
    customHeaders = mapOf("Authorization" to "Bearer $token")
)

// Подписка на проектор
stompClient.subscribeTo("/topic/room/$roomId/projector").collect { frame ->
    val event = Json.decodeFromString<ProjectorEventEnvelope>(frame.body)
    when (event.type) {
        "PROJECTOR_STARTED" -> handleProjectorStarted(event.payload)
        "PROJECTOR_STOPPED" -> handleProjectorStopped()
        "PROJECTOR_CONTROL" -> {
            val payload = Json.decodeFromJsonElement(
                ProjectorControlPayload.serializer(),
                event.payload
            )
            handleProjectorControl(payload)
        }
        "STREAM_LIVE"       -> handleStreamLive(event.payload)
        "STREAM_OFFLINE"    -> handleStreamOffline()
    }
}
```

### 5.4. Отправка `PROJECTOR_CONTROL` с Android

```kotlin
fun sendProjectorControl(roomId: String, action: String, positionMs: Long) {
    val body = ProjectorControlPayload(action = action, positionMs = positionMs)
    val json = Json.encodeToString(ProjectorControlPayload.serializer(), body)
    stompClient.send(
        destination = "/app/room/$roomId/projector/control",
        body = json
    )
}
```

---

## 6. Практические сценарии

### 6.1. Совместный просмотр EMBED-видео

1. Пользователь входит в комнату (`POST /api/rooms/{id}/join`).
2. Отправляет:
   ```kotlin
   api.startProjector(
       roomId,
       "Bearer $token",
       StartProjectorRequest(
           mode = "EMBED",
           videoUrl = embedUrl,
           videoTitle = title
       )
   )
   ```
3. Все клиенты, подписанные на `/topic/room/{roomId}/projector`, получают `PROJECTOR_STARTED`.
4. Хост управляет play/pause/seek через `sendProjectorControl`.

### 6.2. RTMP-стрим с OBS / камеры

1. Хост вызывает:
   ```kotlin
   val resp = api.startProjector(
       roomId,
       "Bearer $token",
       StartProjectorRequest(mode = "STREAM", videoTitle = "Стрим")
   )
   val rtmpUrl = resp.rtmpUrl      // показать в UI или вставить в OBS
   val hlsUrl  = resp.videoUrl     // адрес для ExoPlayer у зрителей
   ```
2. В OBS:
   - `Server`: часть до последнего `/` из `rtmpUrl`;
   - `Stream key`: хвост после `/`.
3. Когда OBS начинает стрим, SRS дергает `POST /api/projector/srs-callback`, backend шлёт `STREAM_LIVE`.
4. Все зрители могут начать воспроизведение `videoUrl` через ExoPlayer.
5. Когда стрим заканчивается, приходит `STREAM_OFFLINE`, `isLive` в REST станет `false`.

---

## 7. Тестирование без Android (stomp-test.html)

Для ручной проверки WS и REST проектора используйте `stomp-test.html` в корне проекта:

1. Запустить backend (`docker-compose up` или `gradle bootRun`).
2. Открыть `stomp-test.html` в браузере.
3. Вставить `accessToken` → **⚡ Connect**.
4. Нажать **📋 GET /api/rooms** — выберется комната.
5. В блоке **Проектор**:
   - указать `Room ID`, `Mode`, `Video URL` (для EMBED),
   - нажать **👂 Subscribe /projector**,
   - нажать **▶ POST /projector (start)**,
   - для EMBED поиграться с **🎛 SEND PROJECTOR_CONTROL (WS)**,
   - для STREAM — скопировать `rtmpUrl` в OBS и посмотреть `STREAM_LIVE` / `STREAM_OFFLINE`.

Эти же события и структуры данных вы будете использовать на Android.

---

## 6. Помодоро-таймер для учебных комнат

### 6.1. REST API

```text
GET    /api/rooms/{roomId}/pomodoro
POST   /api/rooms/{roomId}/pomodoro/start
POST   /api/rooms/{roomId}/pomodoro/pause
POST   /api/rooms/{roomId}/pomodoro/resume
POST   /api/rooms/{roomId}/pomodoro/skip
DELETE /api/rooms/{roomId}/pomodoro
```

- Доступно только для комнат с `context = "study"`.
- Любой участник комнаты может запускать/останавливать таймер.

**Старт помодоро:**

```http
POST /api/rooms/{roomId}/pomodoro/start
Content-Type: application/json

{
  "workDuration": 1500,
  "breakDuration": 300,
  "longBreakDuration": 900,
  "roundsTotal": 4
}
```

Если тело пустое — используются значения по умолчанию (25/5/15/4).

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

### 6.2. WebSocket события помодоро

Подписка:

```text
SUBSCRIBE
destination:/topic/room/{roomId}/pomodoro
id:sub-pomodoro
```

События (формат как у остальных WS-событий):

- `POMODORO_STARTED` — отправляется после `start`;
- `POMODORO_PHASE_CHANGED` — автоматический или ручной переход фазы (WORK/BREAK/LONG_BREAK);
- `POMODORO_PAUSED` — `{ "phase": "PAUSED", "remainingSeconds": N }`;
- `POMODORO_RESUMED` — `{ "phase": "...", "phaseEndAt": "..." }`;
- `POMODORO_STOPPED` — таймер остановлен или цикл завершён.

```json
{
  "type": "POMODORO_PAUSED",
  "payload": {
    "phase": "PAUSED",
    "remainingSeconds": 847
  },
  "timestamp": "2026-03-18T12:10:53Z"
}
```

### 6.3. Kotlin-модели для помодоро

```kotlin
@Serializable
data class PomodoroResponse(
    val id: String,
    val roomId: String,
    val startedBy: ProjectorUserDto,
    val phase: String,           // WORK, BREAK, LONG_BREAK, PAUSED, FINISHED
    val currentRound: Int,
    val roundsTotal: Int,
    val phaseEndAt: String?,     // ISO 8601, null если PAUSED/FINISHED
    val workDuration: Int,
    val breakDuration: Int,
    val longBreakDuration: Int
)

@Serializable
data class PomodoroStartRequest(
    val workDuration: Int? = null,
    val breakDuration: Int? = null,
    val longBreakDuration: Int? = null,
    val roundsTotal: Int? = null
)

@Serializable
data class PomodoroEventEnvelope(
    val type: String,
    val payload: JsonObject,
    val timestamp: String
)
```

Расширение Retrofit-интерфейса:

```kotlin
interface SyncRoomApi {
    // ...

    @GET("/api/rooms/{roomId}/pomodoro")
    suspend fun getPomodoro(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String
    ): PomodoroResponse

    @POST("/api/rooms/{roomId}/pomodoro/start")
    suspend fun startPomodoro(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String,
        @Body body: PomodoroStartRequest
    ): PomodoroResponse

    @POST("/api/rooms/{roomId}/pomodoro/pause")
    suspend fun pausePomodoro(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String
    ): PomodoroResponse

    @POST("/api/rooms/{roomId}/pomodoro/resume")
    suspend fun resumePomodoro(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String
    ): PomodoroResponse

    @POST("/api/rooms/{roomId}/pomodoro/skip")
    suspend fun skipPomodoro(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String
    ): PomodoroResponse

    @DELETE("/api/rooms/{roomId}/pomodoro")
    suspend fun stopPomodoro(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String
    )
}
```

---

## 7. Учебные таски (Study Tasks)

### 7.1. REST API

```text
GET    /api/rooms/{roomId}/tasks
POST   /api/rooms/{roomId}/tasks
PUT    /api/rooms/{roomId}/tasks/{taskId}
DELETE /api/rooms/{roomId}/tasks/{taskId}
```

- Возвращаются только таски **текущего пользователя** в комнате.
- `sortOrder` определяет порядок показа.

**Примеры:**

```json
GET /api/rooms/{roomId}/tasks
[
  { "id": "uuid", "text": "Прочитать главу 5", "isDone": false, "sortOrder": 0 },
  { "id": "uuid", "text": "Сделать конспект", "isDone": true, "sortOrder": 1 }
]
```

```json
POST /api/rooms/{roomId}/tasks
{ "text": "Прочитать главу 5" }
```

```json
PUT /api/rooms/{roomId}/tasks/{taskId}
{ "text": "Прочитать главу 5 и 6", "isDone": true, "sortOrder": 0 }
```

### 7.2. Kotlin-модели тасков

```kotlin
@Serializable
data class StudyTaskResponse(
    val id: String,
    val text: String,
    @SerialName("isDone") val isDone: Boolean,
    val sortOrder: Int
)

@Serializable
data class CreateTaskRequest(val text: String)

@Serializable
data class UpdateTaskRequest(
    val text: String? = null,
    val isDone: Boolean? = null,
    val sortOrder: Int? = null
)
```

Расширение Retrofit:

```kotlin
interface SyncRoomApi {
    // ...

    @GET("/api/rooms/{roomId}/tasks")
    suspend fun getTasks(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String
    ): List<StudyTaskResponse>

    @POST("/api/rooms/{roomId}/tasks")
    suspend fun createTask(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String,
        @Body body: CreateTaskRequest
    ): StudyTaskResponse

    @PUT("/api/rooms/{roomId}/tasks/{taskId}")
    suspend fun updateTask(
        @Path("roomId") roomId: String,
        @Path("taskId") taskId: String,
        @Header("Authorization") token: String,
        @Body body: UpdateTaskRequest
    ): StudyTaskResponse

    @DELETE("/api/rooms/{roomId}/tasks/{taskId}")
    suspend fun deleteTask(
        @Path("roomId") roomId: String,
        @Path("taskId") taskId: String,
        @Header("Authorization") token: String
    )
}
```

---

## 8. Практика для Android-разработчика (помодоро + таски)

- **Помодоро:**
  - при заходе в `study`-комнату можно показать кнопку «Запустить таймер» → `startPomodoro`;
  - подписаться на `/topic/room/{roomId}/pomodoro` и обновлять UI по событиям `POMODORO_STARTED`, `POMODORO_PHASE_CHANGED`, `POMODORO_PAUSED`, `POMODORO_RESUMED`, `POMODORO_STOPPED`;
  - локальный отсчёт таймера привязывать к `phaseEndAt`.
- **Учебные таски:**
  - на экране комнаты показывать список из `GET /tasks` для текущего пользователя;
  - создание/изменение/удаление тасков делается чисто через REST, без WebSocket;
  - `sortOrder` можно использовать для drag & drop сортировки (при перестановке пересчитывать и отправлять `UpdateTaskRequest` с новыми значениями).

# SyncRoom — Документация для Android разработчика

> Актуальное состояние backend API. Все эндпоинты требуют `Authorization: Bearer <accessToken>`, кроме `/api/auth/*`.

**Base URL:** `http://host:8080`  
**WebSocket URL:** `ws://host:8080/ws-stomp`

---

## Что изменилось в последних шагах

| Шаг | Что добавлено |
|-----|---------------|
| **1 — Auth** | JWT авторизация: email, VK, Yandex |
| **2 — Points** | CRUD точек на карте |
| **3 — Rooms** | Список комнат, join, leave, my rooms; правило «1 пользователь = 1 комната» |
| **4 — WebSocket** | STOMP-подключение, события `PARTICIPANT_JOINED` / `PARTICIPANT_LEFT`, обновлённый ответ `POST /join` |

---

## Авторизация

```
POST /api/auth/email       { "email": "...", "password": "..." }
POST /api/auth/register    { "email": "...", "password": "...", "name": "..." }
POST /api/auth/oauth       { "provider": "vk|yandex", "accessToken": "..." }
POST /api/auth/refresh     { "refreshToken": "..." }
```

**Ответ (все auth-эндпоинты):**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "isFirstLogin": true
}
```

---

## Points — Точки на карте

```
GET    /api/users/{userId}/points
POST   /api/users/{userId}/points
PUT    /api/users/{userId}/points/{pointId}
DELETE /api/users/{userId}/points/{pointId}  → 204
```

**Структура точки:**
```json
{ "id": "uuid", "userId": "uuid", "context": "work|study|sport|leisure",
  "title": "Офис", "address": "Тверская, 1", "latitude": 55.75, "longitude": 37.62 }
```

---

## Rooms — Комнаты

> **Правило:** 1 пользователь = 1 комната. Чтобы войти в другую — сначала `/leave`.  
> **`isActive`** = есть ли свободные места.

**Структура комнаты:**
```json
{ "id": "uuid", "context": "work", "title": "Работа",
  "participantCount": 3, "maxParticipants": 10, "isActive": true }
```

### Эндпоинты

```
GET  /api/rooms           → все комнаты (фильтруй isActive на клиенте)
GET  /api/rooms/my        → комнаты текущего пользователя
POST /api/rooms/{id}/join → войти в комнату
POST /api/rooms/{id}/leave → выйти из комнаты → 204
```

### POST /join — ответ (обновлено в Шаге 4)

```json
{
  "room": { "id": "uuid", "context": "work", "title": "Работа",
            "participantCount": 1, "maxParticipants": 10, "isActive": true },
  "participants": [
    { "userId": "uuid", "name": "Иван", "avatarUrl": null, "joinedAt": "2026-03-10T13:00:00+05:00" }
  ]
}
```

**Коды ошибок (join/leave):**
| Код | Причина |
|-----|---------|
| `400` | Уже в комнате / не состоит в комнате |
| `404` | Комната не найдена |
| `401` | Нет токена |

---

## WebSocket (STOMP) — Шаг 4 ✨

### Подключение

| Эндпоинт | Назначение |
|----------|-----------|
| `ws://host:8080/ws-stomp` | **Нативный WS** — для Android (рекомендуется) |
| `ws://host:8080/ws/websocket` | SockJS transport — для web-клиентов |

**Токен** передаётся в STOMP CONNECT frame (не в HTTP заголовке):
```
CONNECT
Authorization:Bearer <accessToken>
accept-version:1.2
```

### Подписка на события комнаты

```
SUBSCRIBE
destination:/topic/room/{roomId}
id:sub-0
```

### Формат события (приходит подписчикам)

```json
{
  "type": "PARTICIPANT_JOINED",
  "payload": {
    "userId": "uuid",
    "name": "Иван",
    "avatarUrl": null,
    "joinedAt": "2026-03-10T13:00:00+05:00"
  },
  "timestamp": "2026-03-10T13:00:00+05:00"
}
```

**Типы событий:**
| Тип | Когда |
|-----|-------|
| `PARTICIPANT_JOINED` | Кто-то вызвал `POST /join` |
| `PARTICIPANT_LEFT` | Кто-то вызвал `POST /leave` |

### Когда приходят события

```
Android A             Backend               Android B
   │                     │                     │
   │─ POST /join ────────►│                     │
   │◄─ JoinRoomResponse──│                     │
   │                     │── PARTICIPANT_JOINED ──► (все подписчики /topic/room/{id})
   │                     │                     │
   │─ POST /leave ───────►│                     │
   │◄─ 204 No Content ───│                     │
   │                     │── PARTICIPANT_LEFT ───► (все подписчики /topic/room/{id})
```

### Kotlin (Android) — пример подключения

```kotlin
// build.gradle: implementation("org.hildan.krossbow:krossbow-stomp-kxserialization-kotlinx:7.0.0")
// или: implementation("com.github.NaikSoftware:StompProtocolAndroid:1.6.6")

val client = OkHttpClient()
val stompClient = StompClient.over(WebSocket.factory(client))

val token = "eyJhbG..." // из SharedPreferences

stompClient.connect(
    "ws://192.168.x.x:8080/ws-stomp",
    customHeaders = mapOf("Authorization" to "Bearer $token")
)

// Подписка
stompClient
    .subscribeTo("/topic/room/$roomId")
    .collect { frame ->
        val event = Json.decodeFromString<RoomEvent>(frame.body)
        when (event.type) {
            "PARTICIPANT_JOINED" -> updateParticipantList(event.payload)
            "PARTICIPANT_LEFT"   -> removeParticipant(event.payload)
        }
    }
```

```kotlin
// Data classes
@Serializable
data class RoomEvent(
    val type: String,
    val payload: JsonObject,
    val timestamp: String
)

@Serializable
data class ParticipantResponse(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    val joinedAt: String?
)
```

### Retrofit — полная спецификация

```kotlin
interface SyncRoomApi {
    // Auth
    @POST("/api/auth/email")   suspend fun login(@Body b: LoginRequest): AuthResponse
    @POST("/api/auth/register") suspend fun register(@Body b: RegisterRequest): AuthResponse
    @POST("/api/auth/oauth")   suspend fun oauth(@Body b: OAuthRequest): AuthResponse
    @POST("/api/auth/refresh") suspend fun refresh(@Body b: RefreshRequest): AuthResponse

    // Points
    @GET("/api/users/{uid}/points")
    suspend fun getPoints(@Path("uid") uid: String, @Header("Authorization") token: String): List<PointResponse>

    @POST("/api/users/{uid}/points")
    suspend fun createPoint(@Path("uid") uid: String, @Header("Authorization") token: String, @Body b: CreatePointRequest): PointResponse

    @PUT("/api/users/{uid}/points/{pid}")
    suspend fun updatePoint(@Path("uid") uid: String, @Path("pid") pid: String, @Header("Authorization") token: String, @Body b: CreatePointRequest): PointResponse

    @DELETE("/api/users/{uid}/points/{pid}")
    suspend fun deletePoint(@Path("uid") uid: String, @Path("pid") pid: String, @Header("Authorization") token: String)

    // Rooms
    @GET("/api/rooms")        suspend fun getRooms(@Header("Authorization") token: String): List<RoomResponse>
    @GET("/api/rooms/my")     suspend fun getMyRooms(@Header("Authorization") token: String): List<RoomResponse>
    @POST("/api/rooms/{id}/join")  suspend fun joinRoom(@Path("id") id: String, @Header("Authorization") token: String): JoinRoomResponse
    @POST("/api/rooms/{id}/leave") suspend fun leaveRoom(@Path("id") id: String, @Header("Authorization") token: String)
}

data class JoinRoomResponse(val room: RoomResponse, val participants: List<ParticipantResponse>)
data class RoomResponse(val id: String, val context: String, val title: String,
    val participantCount: Int, val maxParticipants: Int,
    @SerializedName("isActive") val isActive: Boolean)
data class ParticipantResponse(val userId: String, val name: String, val avatarUrl: String?, val joinedAt: String?)
```

---

## Тестирование WebSocket

Для ручного тестирования STOMP без Android — открой файл:

```
stomp-test.html
```

1. Открой в браузере (Chrome/Firefox)
2. Вставь `accessToken` → **Connect**
3. Вставь `roomId` → **Subscribe**
4. Вызови `POST /api/rooms/{id}/join` через Postman — увидишь событие `PARTICIPANT_JOINED`

> Postman WebSocket tab не подходит для STOMP — он не добавляет обязательный `\0` в конце фреймов. Используй `stomp-test.html`.

---

# SyncRoom — Документация для Android разработчика

> Актуальное состояние backend API. Все эндпоинты требуют `Authorization: Bearer <accessToken>`, кроме `/api/auth/*`.

**Base URL:** `http://host:8080`  
**WebSocket URL:** `ws://host:8080/ws-stomp`

---

## Что изменилось в последних шагах

| Шаг | Что добавлено |
|-----|---------------|
| **1 — Auth** | JWT авторизация: email, VK, Yandex |
| **2 — Points** | CRUD точек на карте |
| **3 — Rooms** | Список комнат, join, leave, my rooms; правило «1 пользователь = 1 комната» |
| **4 — WebSocket** | STOMP-подключение, события `PARTICIPANT_JOINED` / `PARTICIPANT_LEFT`, обновлённый ответ `POST /join` |
| **5 — Seats** ✨ | Механика мест в комнате: занять (`/sit`), встать (`/leave`), автопересадка, WS-события `SEAT_TAKEN` / `SEAT_LEFT`, `backgroundPicture` у комнат |

---

## Авторизация

```
POST /api/auth/email       { "email": "...", "password": "..." }
POST /api/auth/register    { "email": "...", "password": "...", "name": "..." }
POST /api/auth/oauth       { "provider": "vk|yandex", "accessToken": "..." }
POST /api/auth/refresh     { "refreshToken": "..." }
```

**Ответ (все auth-эндпоинты):**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "isFirstLogin": true
}
```

---

## Points — Точки на карте

```
GET    /api/users/{userId}/points
POST   /api/users/{userId}/points
PUT    /api/users/{userId}/points/{pointId}
DELETE /api/users/{userId}/points/{pointId}  → 204
```

**Структура точки:**
```json
{ "id": "uuid", "userId": "uuid", "context": "work|study|sport|leisure",
  "title": "Офис", "address": "Тверская, 1", "latitude": 55.75, "longitude": 37.62 }
```

---

## Rooms — Комнаты

> **Правило:** 1 пользователь = 1 комната. Чтобы войти в другую — сначала `/leave`.  
> **`isActive`** = есть ли свободные места.

**Структура комнаты (обновлено в Шаге 5 — добавлены `backgroundPicture` и `seats`):**
```json
{
  "id": "uuid",
  "context": "work",
  "title": "Работа",
  "participantCount": 3,
  "maxParticipants": 10,
  "isActive": true,
  "backgroundPicture": "https://cdn.example.com/bg/work.jpg",
  "seats": [
    {
      "id": "uuid",
      "x": 0.25,
      "y": 0.40,
      "occupiedBy": null
    },
    {
      "id": "uuid",
      "x": 0.50,
      "y": 0.40,
      "occupiedBy": {
        "id": "uuid",
        "name": "Иван",
        "avatarUrl": null
      }
    }
  ]
}
```

> **`x`, `y`** — нормализованные координаты от 0.0 до 1.0. Умножь на ширину/высоту экрана чтобы получить пиксели.  
> **`occupiedBy`** — `null` если место свободно, иначе данные сидящего пользователя.

### Эндпоинты комнат

```
GET  /api/rooms           → все комнаты (со списком seats[])
GET  /api/rooms/my        → комнаты текущего пользователя (со списком seats[])
POST /api/rooms/{id}/join → войти в комнату
POST /api/rooms/{id}/leave → выйти из комнаты → 204 (+ автоматически освобождает место)
```

### POST /join — ответ

```json
{
  "room": {
    "id": "uuid", "context": "work", "title": "Работа",
    "participantCount": 1, "maxParticipants": 10, "isActive": true,
    "backgroundPicture": "https://cdn.example.com/bg/work.jpg",
    "seats": [ ... ]
  },
  "participants": [
    { "userId": "uuid", "name": "Иван", "avatarUrl": null, "joinedAt": "2026-03-10T13:00:00+05:00" }
  ]
}
```

**Коды ошибок (join/leave):**
| Код | Причина |
|-----|---------|
| `400` | Уже в комнате / не состоит в комнате |
| `404` | Комната не найдена |
| `401` | Нет токена |

---

## Seats — Места в комнате ✨ (Шаг 5)

### Эндпоинты

```
POST /api/rooms/{roomId}/seats/{seatId}/sit    → занять место
POST /api/rooms/{roomId}/seats/{seatId}/leave  → встать с места
```

### Правила поведения

| Сценарий | Что происходит |
|----------|----------------|
| Место свободно | Занимает, возвращает `SeatDto` с `occupiedBy` |
| Место уже занято **текущим** пользователем | Идемпотентно, возвращает тот же `SeatDto` |
| Место занято **другим** пользователем | `409 Conflict` |
| Пользователь уже сидит на **другом** месте в этой же комнате | **Автопересадка**: SEAT_LEFT старого + SEAT_TAKEN нового |
| Пользователь вызывает `/leave` со своего места | Освобождает место, SEAT_LEFT |
| Пользователь вызывает `/leave` с чужого места | `403 Forbidden` |
| Пользователь покидает комнату (`POST /leave`) | Место освобождается **автоматически** |

### POST /sit — ответ

```json
{
  "id": "uuid",
  "x": 0.25,
  "y": 0.40,
  "occupiedBy": {
    "id": "uuid",
    "name": "Иван",
    "avatarUrl": null
  }
}
```

### POST /leave (seat) — ответ

```json
{
  "id": "uuid",
  "x": 0.25,
  "y": 0.40,
  "occupiedBy": null
}
```

**Коды ошибок:**
| Код | Причина |
|-----|---------|
| `200` | Успешно |
| `403` | Это не твоё место (`/leave`) |
| `404` | Место или комната не найдены |
| `409` | Место занято другим пользователем (`/sit`) |

---

## WebSocket (STOMP)

### Подключение

| Эндпоинт | Назначение |
|----------|-----------| 
| `ws://host:8080/ws-stomp` | **Нативный WS** — для Android (рекомендуется) |
| `ws://host:8080/ws/websocket` | SockJS transport — для web-клиентов |

**Токен** передаётся в STOMP CONNECT frame:
```
CONNECT
Authorization:Bearer <accessToken>
accept-version:1.2
```

### Топики

| Топик | Назначение |
|-------|-----------|
| `/topic/room/{roomId}` | Участники комнаты (PARTICIPANT_JOINED / LEFT) |
| `/topic/room/{roomId}/seats` | Места в комнате (SEAT_TAKEN / SEAT_LEFT) ✨ |

### Формат события (общий)

```json
{
  "type": "SEAT_TAKEN",
  "payload": { ... },
  "timestamp": "2026-03-10T13:00:00+05:00"
}
```

### Типы событий

| Тип | Топик | Когда | Payload |
|-----|-------|-------|---------|
| `PARTICIPANT_JOINED` | `/topic/room/{id}` | `POST /join` | `{ userId, name, avatarUrl, joinedAt }` |
| `PARTICIPANT_LEFT` | `/topic/room/{id}` | `POST /leave` | `{ userId, name, avatarUrl }` |
| `SEAT_TAKEN` | `/topic/room/{id}/seats` | `POST /sit` | `{ seatId, user: { id, name, avatarUrl } }` |
| `SEAT_LEFT` | `/topic/room/{id}/seats` | `POST /leave seat` или выход из комнаты | `{ seatId, userId }` |

### Сценарий: автопересадка

Если пользователь уже сидит на месте A и вызывает `/sit` на место B — приходят **два события**:
```json
// 1. Сначала:
{ "type": "SEAT_LEFT",  "payload": { "seatId": "<A>", "userId": "<uid>" } }
// 2. Потом:
{ "type": "SEAT_TAKEN", "payload": { "seatId": "<B>", "user": { "id": "<uid>", "name": "..." } } }
```

### Когда приходят события мест

```
Android A             Backend               Android B
   │                     │                     │
   │─ POST /sit ─────────►│                     │
   │◄─ 200 SeatDto ───────│                     │
   │                     │── SEAT_TAKEN ─────────► (все /topic/room/{id}/seats)
   │                     │                     │
   │─ POST /leave (seat)─►│                     │
   │◄─ 200 SeatDto ───────│                     │
   │                     │── SEAT_LEFT ──────────► (все /topic/room/{id}/seats)
   │                     │                     │
   │─ POST /leave (room)─►│                     │
   │◄─ 204 ───────────────│                     │
   │                     │── SEAT_LEFT ──────────► (автоматически, если сидел)
   │                     │── PARTICIPANT_LEFT ───► (в /topic/room/{id})
```

### Kotlin (Android) — пример подключения

```kotlin
// build.gradle: implementation("org.hildan.krossbow:krossbow-stomp-kxserialization-kotlinx:7.0.0")

val token = "eyJhbG..." // из SharedPreferences

stompClient.connect(
    "ws://192.168.x.x:8080/ws-stomp",
    customHeaders = mapOf("Authorization" to "Bearer $token")
)

// Подписка на участников
stompClient.subscribeTo("/topic/room/$roomId").collect { frame ->
    val event = Json.decodeFromString<RoomEvent>(frame.body)
    when (event.type) {
        "PARTICIPANT_JOINED" -> updateParticipantList(event.payload)
        "PARTICIPANT_LEFT"   -> removeParticipant(event.payload)
    }
}

// Подписка на места
stompClient.subscribeTo("/topic/room/$roomId/seats").collect { frame ->
    val event = Json.decodeFromString<RoomEvent>(frame.body)
    when (event.type) {
        "SEAT_TAKEN" -> markSeatOccupied(event.payload)
        "SEAT_LEFT"  -> markSeatFree(event.payload)
    }
}
```

```kotlin
// Data classes
@Serializable
data class RoomEvent(
    val type: String,
    val payload: JsonObject,
    val timestamp: String
)

// Seats
@Serializable
data class SeatDto(
    val id: String,
    val x: Double,
    val y: Double,
    val occupiedBy: OccupantDto?
)

@Serializable
data class OccupantDto(
    val id: String,
    val name: String,
    val avatarUrl: String?
)

// WS seat payloads
@Serializable
data class SeatTakenPayload(val seatId: String, val user: OccupantDto)
@Serializable
data class SeatLeftPayload(val seatId: String, val userId: String)

// Room (обновлено)
@Serializable
data class RoomResponse(
    val id: String,
    val context: String,
    val title: String,
    val participantCount: Int,
    val maxParticipants: Int,
    @SerialName("isActive") val isActive: Boolean,
    val backgroundPicture: String?,
    val seats: List<SeatDto>
)

data class ParticipantResponse(val userId: String, val name: String, val avatarUrl: String?, val joinedAt: String?)
data class JoinRoomResponse(val room: RoomResponse, val participants: List<ParticipantResponse>)
```

### Retrofit — полная спецификация

```kotlin
interface SyncRoomApi {
    // Auth
    @POST("/api/auth/email")    suspend fun login(@Body b: LoginRequest): AuthResponse
    @POST("/api/auth/register") suspend fun register(@Body b: RegisterRequest): AuthResponse
    @POST("/api/auth/oauth")    suspend fun oauth(@Body b: OAuthRequest): AuthResponse
    @POST("/api/auth/refresh")  suspend fun refresh(@Body b: RefreshRequest): AuthResponse

    // User
    @GET("/api/users/me")  suspend fun getProfile(@Header("Authorization") token: String): UserResponse
    @PUT("/api/users/me")  suspend fun updateProfile(@Header("Authorization") token: String, @Body b: UpdateUserRequest): UserResponse

    // Points
    @GET("/api/users/{uid}/points")
    suspend fun getPoints(@Path("uid") uid: String, @Header("Authorization") token: String): List<PointResponse>
    @POST("/api/users/{uid}/points")
    suspend fun createPoint(@Path("uid") uid: String, @Header("Authorization") token: String, @Body b: CreatePointRequest): PointResponse
    @PUT("/api/users/{uid}/points/{pid}")
    suspend fun updatePoint(@Path("uid") uid: String, @Path("pid") pid: String, @Header("Authorization") token: String, @Body b: CreatePointRequest): PointResponse
    @DELETE("/api/users/{uid}/points/{pid}")
    suspend fun deletePoint(@Path("uid") uid: String, @Path("pid") pid: String, @Header("Authorization") token: String)

    // Rooms
    @GET("/api/rooms")         suspend fun getRooms(@Header("Authorization") token: String): List<RoomResponse>
    @GET("/api/rooms/my")      suspend fun getMyRooms(@Header("Authorization") token: String): List<RoomResponse>
    @POST("/api/rooms/{id}/join")  suspend fun joinRoom(@Path("id") id: String, @Header("Authorization") token: String): JoinRoomResponse
    @POST("/api/rooms/{id}/leave") suspend fun leaveRoom(@Path("id") id: String, @Header("Authorization") token: String)

    // Seats ✨
    @POST("/api/rooms/{roomId}/seats/{seatId}/sit")
    suspend fun sit(@Path("roomId") roomId: String, @Path("seatId") seatId: String, @Header("Authorization") token: String): SeatDto

    @POST("/api/rooms/{roomId}/seats/{seatId}/leave")
    suspend fun standUp(@Path("roomId") roomId: String, @Path("seatId") seatId: String, @Header("Authorization") token: String): SeatDto
}
```

---

## Тестирование WebSocket

Для ручного тестирования STOMP без Android — открой файл `stomp-test.html` в браузере:

```
stomp-test.html  (в корне проекта)
```

1. Вставь `accessToken` → **⚡ Connect**
2. Нажми **📋 GET /api/rooms** — автоматически заполнит `roomId` и первый свободный `seatId`
3. Нажми **👂 Subscribe /seats** — подписаться на события мест
4. Нажми **🪑 POST /sit** → в логе появится `SEAT_TAKEN`
5. Нажми **🚶 POST /leave seat** → появится `SEAT_LEFT`

> Postman WebSocket tab не подходит для STOMP — он не добавляет обязательный `\0` в конце фреймов. Используй `stomp-test.html`.

---

## Обработка ошибок

```json
{ "message": "Описание ошибки", "status": 400 }
```

| Код | Когда |
|-----|-------|
| `400` | Плохой запрос (уже в комнате, не твоё место и т.д.) |
| `401` | Нет или истёк JWT токен |
| `403` | Нет прав (чужое место) |
| `404` | Ресурс не найден |
| `409` | Конфликт (место занято) |
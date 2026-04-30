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
  - `POST /api/auth/oauth` (`redirectUri` опционально, обязателен для web OAuth-flow)
  - `POST /api/auth/refresh`
  - `GET /api/auth/csrf` (для web-клиента)
  - `POST /api/auth/logout` (для web-клиента)
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
  - При **закрытии последнего** STOMP-подключения с JWT в CONNECT сервер сам выполняет тот же выход, что и `POST .../leave` (место, участник, лобби/игра — см. раздел WebSocket ниже).

- **Seats** — места в комнате:
  - `POST /api/rooms/{roomId}/seats/{seatId}/sit`
  - `POST /api/rooms/{roomId}/seats/{seatId}/leave`
  - Топик `/topic/room/{roomId}/seats` с событиями `SEAT_TAKEN` / `SEAT_LEFT` (в payload — актуальные `participantCount` / `observerCount`).
  - В `RoomResponse`: **`participantCount`** = за столом (занятые места), **`observerCount`** = в комнате без места; в `ParticipantResponse` поле **`role`**: `OBSERVER` | `PARTICIPANT`.

Ниже подробно описаны **проектор**, **помодоро** и **учебные таски**.

### 1.1. Важно про web auth (чтобы не сломать Android)

- Backend поддерживает **два режима одновременно**:
  - `Bearer` в `Authorization` (основной для Android; без изменений);
  - `HttpOnly` cookies + CSRF (основной для web).
- При `email/register/oauth/refresh` backend теперь дополнительно ставит cookies:
  - `SR_ACCESS_TOKEN`
  - `SR_REFRESH_TOKEN`
- Android-приложение может продолжать работать как раньше, через токены из JSON-ответа.
- Для web OAuth рекомендуется передавать `redirectUri` в `/api/auth/oauth`; backend валидирует его по allowlist.

Пример OAuth запроса с redirect:
```json
{
  "provider": "vk",
  "accessToken": "vk_access_token_123",
  "redirectUri": "http://localhost:5173/auth/callback"
}
```

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

## 6. Помодоро-таймер для комнат study и work

### 6.1. REST API

```text
GET    /api/rooms/{roomId}/pomodoro
POST   /api/rooms/{roomId}/pomodoro/start
POST   /api/rooms/{roomId}/pomodoro/pause
POST   /api/rooms/{roomId}/pomodoro/resume
POST   /api/rooms/{roomId}/pomodoro/skip
DELETE /api/rooms/{roomId}/pomodoro
```

- Доступно для `context = "study"` **и** `context = "work"`. В `sport` / `leisure` бекенд отвечает `400` («Pomodoro is not available…») на все перечисленные методы.
- Любой участник комнаты может запускать/останавливать таймер.
- **Автосмена фазы на сервере:** раз в ~1 с бекенд ищет активные сессии (`WORK` / `BREAK` / `LONG_BREAK`), у которых `phaseEndAt` уже наступил, и переводит на следующую фазу — в WebSocket уходит тот же `POMODORO_PHASE_CHANGED`, что и при ручном `skip`, либо `POMODORO_STOPPED` при завершении цикла. Клиенту **не обязательно** вызывать `skip` по локальному таймеру: достаточно подписаться на топик и обновлять UI по событиям; `skip` остаётся для ручного «пропустить фазу». После рестарта сервера таймер в памяти сбрасывается, но фаза догонится за счёт этого механизма (пока сессия есть в БД).

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
- `POMODORO_PHASE_CHANGED` — переход фазы (WORK/BREAK/LONG_BREAK): после **истечения** `phaseEndAt` на сервере, по **ручному** `skip` или по внутреннему таймеру процесса;
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
GET    /api/rooms/{roomId}/tasks/all
GET    /api/rooms/{roomId}/leaderboard
GET    /api/rooms/{roomId}/bots
POST   /api/rooms/{roomId}/bots/motivational-goals/activate
DELETE /api/rooms/{roomId}/bots/motivational-goals/deactivate
PUT    /api/rooms/{roomId}/bots/{botId}/config
DELETE /api/rooms/{roomId}/bots/{botId}
POST   /api/rooms/{roomId}/tasks
POST   /api/rooms/{roomId}/tasks/{taskId}/like
DELETE /api/rooms/{roomId}/tasks/{taskId}/like
PUT    /api/rooms/{roomId}/tasks/{taskId}
DELETE /api/rooms/{roomId}/tasks/{taskId}
```

- Возвращаются только таски **текущего пользователя** в комнате.
- `sortOrder` определяет порядок показа.

**Лайки и лидерборд** (участники комнаты):

```text
GET    /api/rooms/{roomId}/tasks/all
GET    /api/rooms/{roomId}/leaderboard
POST   /api/rooms/{roomId}/tasks/{taskId}/like
DELETE /api/rooms/{roomId}/tasks/{taskId}/like
```

- `GET .../tasks/all` — все цели **всех** участников с полями: `id`, `text`, `isDone`, `sortOrder`, `ownerId`, `ownerName`, `isBot`, `likeCount`, `likedByMe`.
- `GET .../leaderboard` — массив по **всем** участникам комнаты, сортировка по убыванию `totalLikes`, затем по имени: `userId`, `userName`, `avatarUrl`, `totalLikes`, `completedTasks`, `totalTasks` (`totalLikes` — сколько лайков набрали **цели этого пользователя** в комнате).
- Лайкать можно только **чужие** цели; свой таск — `400`. Повторный `POST` like идемпотентен (второй раз без нового WS `TASK_LIKED`). `DELETE` like без лайка — `200`, `likedByMe: false`, без события `TASK_UNLIKED`.
- Мотивационный бот целей: `POST .../bots/motivational-goals/activate` принимает `{ "goalCount": 1..10, "autoSuggest": true|false, "suggestOnBreak": true|false }`.
- Ограничения активации бота: пользователь должен быть участником комнаты (`400` иначе), `goalCount` должен быть в диапазоне `1..10` (`400` иначе).
- В одной комнате можно держать несколько экземпляров мотивационного бота; управление конкретным экземпляром идёт через `botId` (`PUT .../bots/{botId}/config`, `DELETE .../bots/{botId}`).
- Если `autoSuggest=true`, бот сразу создаёт задачи от `MotivBot`; если `suggestOnBreak=true`, бот создаёт цели на переходах помодоро в `BREAK`/`LONG_BREAK`.
- Подписка на обновления лайков и задач бота: **`/topic/room/{roomId}/tasks`** (см. ниже).

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

**Ответ лайка (POST/DELETE):**

```json
{ "taskId": "uuid", "likeCount": 3, "likedByMe": true }
```

**Элемент `GET .../tasks/all`:**

```json
{
  "id": "uuid",
  "text": "Выучить Kotlin coroutines",
  "isDone": false,
  "sortOrder": 1,
  "ownerId": "uuid",
  "ownerName": "Иван",
  "likeCount": 3,
  "likedByMe": true
}
```

**Элемент `GET .../leaderboard`:**

```json
{
  "userId": "uuid",
  "userName": "Иван",
  "avatarUrl": "https://...",
  "totalLikes": 12,
  "completedTasks": 3,
  "totalTasks": 5
}
```

### 7.2. WebSocket: лайки на таски

Подписка:

```text
SUBSCRIBE
destination:/topic/room/{roomId}/tasks
```

События (конверт как у других модулей: `type`, `payload`, `timestamp`):

- `TASK_CREATED` — после успешного `POST /api/rooms/{roomId}/tasks`:
  ```json
  {
    "type": "TASK_CREATED",
    "payload": {
      "taskId": "uuid",
      "text": "Прочитать главу 5",
      "isDone": false,
      "sortOrder": 0,
      "ownerId": "uuid",
      "ownerName": "Иван",
      "likeCount": 0,
      "likedByMe": false
    },
    "timestamp": "..."
  }
  ```
- `TASK_UPDATED` — после `PUT /api/rooms/{roomId}/tasks/{taskId}`:
  ```json
  {
    "type": "TASK_UPDATED",
    "payload": {
      "taskId": "uuid",
      "text": "Обновлённый текст",
      "isDone": true,
      "sortOrder": 1,
      "ownerId": "uuid",
      "ownerName": "Иван"
    },
    "timestamp": "..."
  }
  ```
- `TASK_DELETED` — после `DELETE /api/rooms/{roomId}/tasks/{taskId}`:
  ```json
  {
    "type": "TASK_DELETED",
    "payload": {
      "taskId": "uuid",
      "ownerId": "uuid"
    },
    "timestamp": "..."
  }
  ```

- `TASK_LIKED` — после первого успешного `POST .../like`:
  ```json
  {
    "type": "TASK_LIKED",
    "payload": {
      "taskId": "uuid",
      "userId": "uuid",
      "userName": "Иван",
      "likeCount": 5,
      "action": "LIKE"
    },
    "timestamp": "..."
  }
  ```
- `TASK_UNLIKED` — после `DELETE .../like`, если лайк был удалён:
  ```json
  {
    "type": "TASK_UNLIKED",
    "payload": {
      "taskId": "uuid",
      "userId": "uuid",
      "likeCount": 4,
      "action": "UNLIKE"
    },
    "timestamp": "..."
  }
  ```
- `BOT_GOAL_SUGGESTED` — когда мотивационный бот предложил новую цель:
  ```json
  {
    "type": "BOT_GOAL_SUGGESTED",
    "payload": {
      "task": {
        "id": "uuid",
        "text": "Прочитать 30 страниц",
        "ownerId": "bot-uuid",
        "ownerName": "MotivBot",
        "isBot": true
      }
    },
    "timestamp": "..."
  }
  ```

### 7.3. Kotlin-модели тасков

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

@Serializable
data class StudyTaskWithLikesResponse(
    val id: String,
    val text: String,
    @SerialName("isDone") val isDone: Boolean,
    val sortOrder: Int,
    val ownerId: String,
    val ownerName: String,
    val likeCount: Long,
    @SerialName("likedByMe") val likedByMe: Boolean
)

@Serializable
data class TaskLikeResult(
    val taskId: String,
    val likeCount: Long,
    @SerialName("likedByMe") val likedByMe: Boolean
)

@Serializable
data class LeaderboardEntry(
    val userId: String,
    val userName: String,
    val avatarUrl: String? = null,
    val totalLikes: Long,
    val completedTasks: Long,
    val totalTasks: Long
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

    @GET("/api/rooms/{roomId}/tasks/all")
    suspend fun getAllTasksWithLikes(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String
    ): List<StudyTaskWithLikesResponse>

    @GET("/api/rooms/{roomId}/leaderboard")
    suspend fun getStudyLeaderboard(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String
    ): List<LeaderboardEntry>

    @POST("/api/rooms/{roomId}/tasks/{taskId}/like")
    suspend fun likeTask(
        @Path("roomId") roomId: String,
        @Path("taskId") taskId: String,
        @Header("Authorization") token: String
    ): TaskLikeResult

    @DELETE("/api/rooms/{roomId}/tasks/{taskId}/like")
    suspend fun unlikeTask(
        @Path("roomId") roomId: String,
        @Path("taskId") taskId: String,
        @Header("Authorization") token: String
    ): TaskLikeResult

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
  - при заходе в комнату с `context` **study** или **work** можно показать кнопку «Запустить таймер» → `startPomodoro`;
  - подписаться на `/topic/room/{roomId}/pomodoro` и обновлять UI по событиям `POMODORO_STARTED`, `POMODORO_PHASE_CHANGED`, `POMODORO_PAUSED`, `POMODORO_RESUMED`, `POMODORO_STOPPED`;
  - локальный отсчёт можно вести от `phaseEndAt`, но **обязательно** подстраиваться под `POMODORO_PHASE_CHANGED` с сервера (автосмена фазы ~раз в секунду на бекенде);
  - кнопка «Пропустить фазу» → `skipPomodoro` (необязательна для корректной смены фаз).
- **Учебные таски:**
  - личный список: `GET /tasks`; общая картина с лайками: `GET /tasks/all`; лидерборд: `GET /leaderboard`;
  - подписка на `/topic/room/{roomId}/tasks` для `TASK_CREATED` / `TASK_UPDATED` / `TASK_DELETED` / `TASK_LIKED` / `TASK_UNLIKED` / `BOT_GOAL_SUGGESTED`;
  - создание/изменение/удаление **своих** тасков — REST; лайки — `POST`/`DELETE .../like;
  - `sortOrder` можно использовать для drag & drop (после перестановки — `UpdateTaskRequest`).

---

## 9. Игры (Quiplash) — инструкция для Android

### 9.1. REST API игр

```text
POST /api/rooms/{roomId}/games
GET  /api/rooms/{roomId}/games/current
POST /api/games/{gameId}/ready
POST /api/games/{gameId}/unready
POST /api/games/{gameId}/leave
POST /api/games/{gameId}/start
```

`POST /start`: игра запускается, когда есть **минимум 3 ready**. Неготовые игроки не блокируют старт — сервер исключает их из лобби и отправляет им `PLAYER_KICKED`.

Выход из комнаты `POST /api/rooms/{roomId}/leave` также убирает пользователя из лобби-игры или **отменяет** игру в статусе `IN_PROGRESS` (событие `GAME_CANCELLED`). То же относится к **закрытию последней** STOMP-сессии с JWT (обрыв сети, см. раздел WebSocket).

**Создание игры:**

```json
POST /api/rooms/{roomId}/games
{
  "gameType": "QUIPLASH"
}
```

**Ответ:**

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

### 9.2. WebSocket игры

Подписка:

```text
SUBSCRIBE
destination:/topic/game/{gameId}
id:sub-game
```

Отправка действий:

```text
SEND
destination:/app/game/{gameId}/action
```

### 9.3. Серверные события, которые нужно обрабатывать на Android

- `GAME_STARTED`
- `PLAYER_KICKED` — `{ userId, reason }` (персонально неготовому игроку при старте)
- `PLAYER_UNREADY` — `{ userId }`
- `PLAYER_LEFT` — `{ userId }` (лобби или после выхода из комнаты)
- `GAME_CANCELLED` — `{ reason: "PLAYER_LEFT_ROOM" }` (игра шла, кто-то вышел из комнаты)
- `PROMPT_RECEIVED`
- `WAITING_FOR_OTHERS`
- `WAITING_FOR_VOTES`
- `ROUND_RESULT`
- `GAME_FINISHED`

Пример события:

```json
{
  "type": "PROMPT_RECEIVED",
  "payload": {
    "promptId": "uuid",
    "text": "Что было бы, если бы коты умели говорить?",
    "timeLimit": 60
  },
  "timestamp": "2026-03-18T12:00:00Z"
}
```

### 9.4. Действия клиента (WS)

**Отметиться готовым:**

```json
{
  "type": "PLAYER_READY",
  "payload": {}
}
```

**Снять готовность / выйти из лобби (эквивалент REST `unready` / `leave`):**

```json
{ "type": "PLAYER_UNREADY", "payload": {} }
```

```json
{ "type": "PLAYER_LEAVE_LOBBY", "payload": {} }
```

**Отправить ответ:**

```json
{
  "type": "SUBMIT_ANSWER",
  "payload": { "text": "Они бы критиковали нашу еду" }
}
```

**Отправить голос:**

```json
{
  "type": "SUBMIT_VOTE",
  "payload": { "answerId": "uuid-1" }
}
```

### 9.5. Kotlin модели для игры

```kotlin
@Serializable
data class GameResponse(
    val gameId: String,
    val gameType: String,
    val status: String,
    val players: List<GamePlayerDto>
)

@Serializable
data class GamePlayerDto(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val isReady: Boolean = false,
    val score: Int = 0
)

@Serializable
data class GameEventEnvelope(
    val type: String,
    val payload: JsonObject,
    val timestamp: String
)

@Serializable
data class GameActionMessage(
    val type: String,
    val payload: JsonObject = buildJsonObject { }
)
```

### 9.6. Retrofit расширение (добавка к текущему SyncRoomApi)

```kotlin
interface SyncRoomApi {
    // ...
    @POST("/api/rooms/{roomId}/games")
    suspend fun createGame(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String,
        @Body body: CreateGameRequest
    ): GameResponse

    @GET("/api/rooms/{roomId}/games/current")
    suspend fun getCurrentGame(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String
    ): GameResponse

    @POST("/api/games/{gameId}/ready")
    suspend fun setReady(
        @Path("gameId") gameId: String,
        @Header("Authorization") token: String
    )

    @POST("/api/games/{gameId}/unready")
    suspend fun setUnready(
        @Path("gameId") gameId: String,
        @Header("Authorization") token: String
    )

    @POST("/api/games/{gameId}/leave")
    suspend fun leaveGameLobby(
        @Path("gameId") gameId: String,
        @Header("Authorization") token: String
    )

    @POST("/api/games/{gameId}/start")
    suspend fun startGame(
        @Path("gameId") gameId: String,
        @Header("Authorization") token: String
    )
}

@Serializable
data class CreateGameRequest(val gameType: String = "QUIPLASH")
```

### 9.7. Что важно в текущей реализации Quiplash

- 3 раунда.
- Таймаут ответа: 60 сек (`"..."` для неответивших).
- Таймаут голосования: 30 сек.
- Автопереход между раундами: 5 сек.
- После 3-го раунда сервер шлёт `GAME_FINISHED`.

---

## 10. Игры (Gartic Phone) — инструкция для Android

### 10.1. REST API

Для создания/старта используются те же эндпоинты:

```text
POST /api/rooms/{roomId}/games      // body: { "gameType": "GARTIC_PHONE" }
GET  /api/rooms/{roomId}/games/current
POST /api/games/{gameId}/ready
POST /api/games/{gameId}/start
POST /api/games/{gameId}/gartic/drawings   // multipart: поле `file` (PNG), только пока игра IN_PROGRESS
GET  /api/games/{gameId}/gartic/drawings/{drawingAssetId}   // скачать PNG (тот же JWT, участник комнаты)
```

Ответ `POST .../gartic/drawings`:

```json
{ "drawingAssetId": "uuid", "imageUrl": "/api/games/{gameId}/gartic/drawings/{drawingAssetId}" }
```

Дальше в WebSocket `SUBMIT_DRAWING` передаёте только `drawingAssetId` — без base64 в STOMP.

### 10.2. WebSocket события

**Общий топик** — подписка `destination:/topic/game/{gameId}`:

- `GAME_STARTED`, `REVEAL_CHAIN`, `GAME_FINISHED`, `BOT_ADDED`, `PLAYER_READY`, …

**Личные шаги Gartic** (`STEP_WRITE`, `STEP_DRAW`, `STEP_GUESS`, `WAITING_FOR_OTHERS`) сервер шлёт **только текущему игроку** через user-destination. Нужна вторая подписка (principal = ваш `userId` из JWT):

```text
destination:/user/topic/game/{gameId}
```

Без неё в общем `/topic/game/...` вы не увидите свой ход — только события для всех.

- `STEP_WRITE` — написать фразу (`timeLimit: 60`)
- `STEP_DRAW` — нарисовать по фразе (`timeLimit: 90`)
- `STEP_GUESS` — угадать по рисунку (`timeLimit: 60`). Картинка: `drawingAssetId` + относительный `imageUrl` (скачать через GET выше с Bearer). Поле `imageBase64` больше не используется в нормальном потоке (оставлено только для совместимости со старыми данными).
- `WAITING_FOR_OTHERS` — вы уже сходили, ждёте остальных
- `ACTION_ACCEPTED` — сервер принял ваш `SUBMIT_*` (клиенту можно показывать "ход сохранён")

В общем топике дополнительно: `REVEAL_CHAIN`, `GAME_FINISHED` (и др.).

Пример:

```json
{
  "type": "STEP_DRAW",
  "payload": {
    "phrase": "Кот в космосе ест пиццу",
    "timeLimit": 90
  },
  "timestamp": "2026-03-20T12:00:00Z"
}
```

### 10.3. Действия клиента (SEND `/app/game/{gameId}/action`)

```json
{ "type": "SUBMIT_PHRASE",  "payload": { "text": "Кот в космосе ест пиццу" } }
```

Предпочтительно (после `POST .../gartic/drawings`):

```json
{ "type": "SUBMIT_DRAWING", "payload": { "drawingAssetId": "uuid" } }
```

Устаревший вариант (тяжёлый для WebSocket):

```json
{ "type": "SUBMIT_DRAWING", "payload": { "imageBase64": "data:image/png;base64,..." } }
```

```json
{ "type": "SUBMIT_GUESS",   "payload": { "text": "Космонавт-кот обедает" } }
```

### 10.4. Важные правила backend

- Минимум 3 **готовых** игрока для старта.
- Неготовые игроки при старте исключаются из лобби (`PLAYER_KICKED`).
- Шагов столько же, сколько игроков.
- Чередование шагов: `TEXT -> DRAWING -> TEXT -> ...`.
- Таймауты:
  - текстовый шаг (`STEP_WRITE`, `STEP_GUESS`) -> `...`
  - шаг рисования (`STEP_DRAW`) -> белый PNG.
- Ограничение на рисунок: до ~2 MB декодированного PNG (multipart или legacy base64). На диске шаг хранится как `asset:uuid`; в WebSocket уходят только `drawingAssetId` и `imageUrl`.

### 10.5. Пример Kotlin модели события

```kotlin
@Serializable
data class GameEventEnvelope(
    val type: String,
    val payload: JsonObject,
    val timestamp: String
)
```

### 10.6. Боты в играх (Gartic + Quiplash)

REST для лобби ботов:

```text
GET    /api/bots/available
POST   /api/games/{gameId}/bots/add
DELETE /api/games/{gameId}/bots/{botId}
```

`POST /api/games/{gameId}/bots/add` body:

```json
{ "botType": "GARTIC_DRAWER", "count": 1 }
```

Поддерживаемые `botType`:
- `GARTIC_BOT` — один универсальный бот на Gartic (в игре выполняет все роли по очереди, как человек)
- `GARTIC_DRAWER`, `GARTIC_WRITER`, `GARTIC_GUESSER` — то же поведение в рантайме, различаются только шаблоном в каталоге
- `QUIPLASH_BOT` — универсальный бот Quiplash (ответы + голоса)
- `QUIPLASH_JOKER`, `QUIPLASH_VOTER` — то же по смыслу, уточнение роли в каталоге

Ответом приходит обычный `GameResponse`, где у ботов:
- `players[].isBot = true`
- `players[].id` — id bot_user (не userId человека)

Kotlin-модели:

```kotlin
@Serializable
data class AddBotRequest(
    val botType: String,
    val count: Int = 1
)

@Serializable
data class BotInfoResponse(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val botType: String
)
```

Retrofit-добавка:

```kotlin
interface SyncRoomApi {
    // ...
    @GET("/api/bots/available")
    suspend fun getAvailableBots(
        @Header("Authorization") token: String
    ): List<BotInfoResponse>

    @POST("/api/games/{gameId}/bots/add")
    suspend fun addBots(
        @Path("gameId") gameId: String,
        @Header("Authorization") token: String,
        @Body body: AddBotRequest
    ): GameResponse

    @DELETE("/api/games/{gameId}/bots/{botId}")
    suspend fun removeBot(
        @Path("gameId") gameId: String,
        @Path("botId") botId: String,
        @Header("Authorization") token: String
    ): GameResponse
}
```

WS-события для UI лобби:
- `BOT_ADDED` `{ botId, name, avatarUrl }`
- `BOT_REMOVED` `{ botId }`

Во время `GARTIC_PHONE` в `STEP_GUESS` / `REVEAL_CHAIN` для шагов `DRAWING`:
- `drawingAssetId`, `imageUrl` (относительный путь к GET выше); `content` для таких шагов — `null`.
- Legacy: в старых сессиях в `content` мог остаться data URL — клиент может отобразить его, если `drawingAssetId` нет.

Во время `QUIPLASH` боты автоматически:
- отправляют ответы на `PROMPT_RECEIVED`,
- голосуют на этапе `WAITING_FOR_VOTES`.

От клиента ничего дополнительного не требуется: UI получает стандартные события
`WAITING_FOR_VOTES`, `ROUND_RESULT`, `GAME_FINISHED`.

### 10.7. Как Android-команде проверить всё локально (через Docker)

Одна команда для backend + local AI:

```bash
docker compose -f docker-compose.yml -f docker-compose.local-ai.yml up -d --build
```

После этого:
- backend: `http://localhost:8080`
- swagger: `http://localhost:8080/swagger-ui.html`
- картинки, которые рисуют боты, дополнительно сохраняются в `img-generated/` (в корне проекта backend).

---

## 11. Чат комнаты

### REST

```text
GET /api/rooms/{roomId}/messages?page=0&size=50
Authorization: Bearer <accessToken>
```

Ответ:

```json
{
  "content": [
    { "id": "uuid", "userId": "uuid", "userName": "Аня", "text": "Привет!", "createdAt": "2026-03-29T12:00:00+03:00" }
  ],
  "totalPages": 1
}
```

Доступ только для участников комнаты (`POST .../join`). Порядок элементов в `content` на странице — **хронологический** (старые выше, новые ниже).

### WebSocket (STOMP)

**Подписка (все сообщения чата):**

```text
SUBSCRIBE
destination:/topic/room/{roomId}/chat
```

Тело кадра — один JSON-объект сообщения (без envelope):

```json
{
  "id": "uuid",
  "userId": "uuid",
  "userName": "Аня",
  "text": "Привет!",
  "createdAt": "2026-03-29T12:00:00+03:00"
}
```

**Отправка сообщения:**

```text
SEND
destination:/app/room/{roomId}/chat

{ "text": "Привет!" }
```

### Kotlin

```kotlin
@Serializable
data class ChatMessageDto(
    val id: String,
    val userId: String,
    val userName: String,
    val text: String,
    val createdAt: String
)

@Serializable
data class PagedChatMessagesDto(
    val content: List<ChatMessageDto>,
    val totalPages: Int
)
```

Расширение Retrofit:

```kotlin
@GET("/api/rooms/{roomId}/messages")
suspend fun getRoomMessages(
    @Path("roomId") roomId: String,
    @Header("Authorization") token: String,
    @Query("page") page: Int = 0,
    @Query("size") size: Int = 50
): PagedChatMessagesDto
```

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
| **4 — WebSocket** | STOMP-подключение, события `PARTICIPANT_JOINED` / `PARTICIPANT_LEFT`, автовыход при обрыве последней сессии (JWT), обновлённый ответ `POST /join` |

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
> **`isActive`** = есть ли свободные **слоты членства** в комнате (по числу записей в `room_participants`, не по столу).

**Счётчики:** `participantCount` — сколько **сидят за столом**; `observerCount` — сколько **в лаунже** (вошли через `/join`, но не заняли место). Сумма совпадает с числом людей в комнате.

**Структура комнаты:**
```json
{ "id": "uuid", "context": "work", "title": "Работа",
  "participantCount": 3, "observerCount": 2, "maxParticipants": 10, "isActive": true }
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
            "participantCount": 0, "observerCount": 1, "maxParticipants": 10, "isActive": true },
  "participants": [
    { "userId": "uuid", "name": "Иван", "avatarUrl": null, "role": "OBSERVER", "joinedAt": "2026-03-10T13:00:00+05:00" }
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

### Обрыв соединения (краш, потеря сети, закрытие сокета)

Рекомендуется держать **одно** STOMP-подключение на пользователя в комнате (или учитывать мультисессии ниже).

Когда закрывается **последняя** активная STOMP-сессия этого пользователя **и** в CONNECT был валидный `Authorization: Bearer <accessToken>`:

- бэкенд делает то же, что `POST /api/rooms/{roomId}/leave`: снимает с места, удаляет участие, шлёт `PARTICIPANT_LEFT` (и при необходимости `SEAT_LEFT`);
- **игра:** как при HTTP leave — выход из лобби / отмена матча для игрока (`GAME_CANCELLED` при `IN_PROGRESS`).

Если JWT в CONNECT не передавали — при отключении комната **не** очищается. Если открыты **две** вкладки с двумя сессиями — закрытие одной **не** выводит из комнаты.

Без WebSocket (только REST) при обрыве сети пользователь остаётся в комнате до явного `POST .../leave` после восстановления связи.

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
    "role": "OBSERVER",
    "joinedAt": "2026-03-10T13:00:00+05:00"
  },
  "timestamp": "2026-03-10T13:00:00+05:00"
}
```

**Типы событий:**
| Тип | Когда |
|-----|-------|
| `PARTICIPANT_JOINED` | Кто-то вызвал `POST /join` |
| `PARTICIPANT_LEFT` | `POST /leave` или закрытие **последней** STOMP-сессии пользователя (с JWT в CONNECT) |

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
   │                     │                     │
   │─ TCP/STOMP close ───►│  (последняя сессия, был JWT в CONNECT)
   │                     │── SEAT_LEFT? ────────► (если сидел на месте)
   │                     │── PARTICIPANT_LEFT ───► (как при POST /leave)
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
    val role: String? = null,
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
    val participantCount: Int, val observerCount: Int, val maxParticipants: Int,
    @SerializedName("isActive") val isActive: Boolean)
data class ParticipantResponse(val userId: String, val name: String, val avatarUrl: String?, val role: String?, val joinedAt: String?)
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
| **4 — WebSocket** | STOMP-подключение, события `PARTICIPANT_JOINED` / `PARTICIPANT_LEFT`, автовыход при обрыве последней сессии (JWT), обновлённый ответ `POST /join` |
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
> **`isActive`** = есть ли свободные **слоты членства** (по `room_participants`).

**Счётчики:** `participantCount` — за столом; `observerCount` — в лаунже (без места).

**Структура комнаты (обновлено в Шаге 5 — добавлены `backgroundPicture` и `seats`):**
```json
{
  "id": "uuid",
  "context": "work",
  "title": "Работа",
  "participantCount": 3,
  "observerCount": 2,
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

Тот же эффект, что `POST .../leave`, даёт **закрытие последней** STOMP-сессии пользователя при наличии JWT в CONNECT (подробнее — в разделе WebSocket ниже).

### POST /join — ответ

```json
{
  "room": {
    "id": "uuid", "context": "work", "title": "Работа",
    "participantCount": 0, "observerCount": 1, "maxParticipants": 10, "isActive": true,
    "backgroundPicture": "https://cdn.example.com/bg/work.jpg",
    "seats": [ ... ]
  },
  "participants": [
    { "userId": "uuid", "name": "Иван", "avatarUrl": null, "role": "OBSERVER", "joinedAt": "2026-03-10T13:00:00+05:00" }
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
| Закрыта **последняя** STOMP-сессия с JWT (как при обрыве сети) | Как строка выше: сервер выполняет тот же выход из комнаты |

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

**Обрыв соединения:** при закрытии **последней** STOMP-сессии с JWT в CONNECT сервер выполняет тот же сценарий, что `POST /api/rooms/{roomId}/leave` (место, `PARTICIPANT_LEFT`, логика игры). Полное описание — в разделе **«WebSocket (STOMP) — Шаг 4»**. Если открыто несколько сессий (вкладки), закрытие одной не выводит из комнаты.

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
| `PARTICIPANT_JOINED` | `/topic/room/{id}` | `POST /join` | `{ userId, name, avatarUrl, joinedAt, role }` |
| `PARTICIPANT_LEFT` | `/topic/room/{id}` | `POST /leave` или закрытие **последнего** STOMP (с JWT) | `{ userId, name, avatarUrl }` |
| `SEAT_TAKEN` | `/topic/room/{id}/seats` | `POST /sit` | `{ seatId, user: { id, name, avatarUrl }, participantCount, observerCount }` |
| `SEAT_LEFT` | `/topic/room/{id}/seats` | `POST /leave seat`, выход из комнаты или закрытие **последнего** STOMP (с JWT) | `{ seatId, userId, participantCount, observerCount }` |

### Сценарий: автопересадка

Если пользователь уже сидит на месте A и вызывает `/sit` на место B — приходят **два события** с **одинаковыми** итоговыми `participantCount` / `observerCount` (число за столом не меняется):
```json
// 1. Сначала:
{ "type": "SEAT_LEFT",  "payload": { "seatId": "<A>", "userId": "<uid>", "participantCount": 2, "observerCount": 0 } }
// 2. Потом:
{ "type": "SEAT_TAKEN", "payload": { "seatId": "<B>", "user": { "id": "<uid>", "name": "..." }, "participantCount": 2, "observerCount": 0 } }
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
   │                     │                     │
   │─ TCP/STOMP close ───►│  (последняя сессия, JWT в CONNECT)
   │                     │── SEAT_LEFT ──────────► (если сидел)
   │                     │── PARTICIPANT_LEFT ───► (как POST /leave room)
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
data class SeatTakenPayload(
    val seatId: String,
    val user: OccupantDto,
    val participantCount: Int,
    val observerCount: Int
)
@Serializable
data class SeatLeftPayload(
    val seatId: String,
    val userId: String,
    val participantCount: Int,
    val observerCount: Int
)

// Room (обновлено)
@Serializable
data class RoomResponse(
    val id: String,
    val context: String,
    val title: String,
    val participantCount: Int,
    val observerCount: Int,
    val maxParticipants: Int,
    @SerialName("isActive") val isActive: Boolean,
    val backgroundPicture: String?,
    val seats: List<SeatDto>
)

data class ParticipantResponse(val userId: String, val name: String, val avatarUrl: String?, val role: String?, val joinedAt: String?)
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
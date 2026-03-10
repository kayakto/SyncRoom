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
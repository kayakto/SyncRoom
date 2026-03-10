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

## Обработка ошибок

```json
{ "message": "Описание ошибки", "status": 400 }
```
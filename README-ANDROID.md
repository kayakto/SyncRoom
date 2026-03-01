# SyncRoom — Что изменилось в последнем обновлении

> **Для Android-разработчиков:** В этом обновлении добавлены три новых раздела API кроме авторизации: **Points** (сохранённые места пользователя), **Rooms** (комнаты) и обновлённые правила участия в комнатах.

---

## Базовый URL и авторизация

Каждый запрос (кроме `/api/auth/*`) должен содержать заголовок:

```
Authorization: Bearer <accessToken>
```

`accessToken` получается при логине через `/api/auth/login`, `/api/auth/register` или `/api/auth/oauth`.

---

## 📍 Points — Сохранённые места пользователя

Пользователь может сохранять до N мест с геолокацией и контекстом (`work`, `study`, `sport`, `leisure`).

### Структура объекта `PointResponse`

```json
{
  "id": "uuid",
  "userId": "uuid",
  "context": "work",
  "title": "Офис",
  "address": "Москва, Тверская ул., 1",
  "latitude": 55.751244,
  "longitude": 37.618423
}
```

### Получить все точки пользователя

```
GET /api/users/{userId}/points
Authorization: Bearer <token>
```

**Ответ `200 OK`:** массив `PointResponse[]`

```kotlin
// Retrofit
@GET("/api/users/{userId}/points")
suspend fun getPoints(
    @Path("userId") userId: String,
    @Header("Authorization") token: String
): List<PointResponse>
```

---

### Создать точку

```
POST /api/users/{userId}/points
Authorization: Bearer <token>
Content-Type: application/json
```

**Тело запроса:**

```json
{
  "context": "work",
  "title": "Офис",
  "address": "Москва, Тверская ул., 1",
  "latitude": 55.751244,
  "longitude": 37.618423
}
```

Допустимые значения `context`: `work`, `study`, `sport`, `leisure` (регистр не важен — нормализуется на сервере).

**Ответ `201 Created`:** объект `PointResponse`

**Ошибки:**
| Код | Причина |
|-----|---------|
| `400` | Неверный `context` или не пройдена валидация полей |
| `404` | Пользователь не найден |
| `401` | Нет токена или токен недействителен |

```kotlin
@POST("/api/users/{userId}/points")
suspend fun createPoint(
    @Path("userId") userId: String,
    @Header("Authorization") token: String,
    @Body request: CreatePointRequest
): PointResponse

data class CreatePointRequest(
    val context: String,   // "work" | "study" | "sport" | "leisure"
    val title: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)
```

---

### Обновить точку

```
PUT /api/users/{userId}/points/{pointId}
Authorization: Bearer <token>
Content-Type: application/json
```

Тело запроса — то же, что и при создании. Обновляются все поля.

**Ответ `200 OK`:** обновлённый `PointResponse`

**Ошибки:**
| Код | Причина |
|-----|---------|
| `404` | Точка не найдена (или принадлежит другому пользователю) |
| `400` | Неверный `context` или валидация |

---

### Удалить точку

```
DELETE /api/users/{userId}/points/{pointId}
Authorization: Bearer <token>
```

**Ответ `204 No Content`**

**Ошибки:** `404` — точка не найдена, `401` — не авторизован.

---

## 🏠 Rooms — Комнаты

> **Важное правило:** пользователь может находиться только в **одной комнате** одновременно. Чтобы войти в другую — нужно сначала выйти из текущей.

### Структура объекта `RoomResponse`

```json
{
  "id": "uuid",
  "context": "sport",
  "title": "Спортзал",
  "participantCount": 3,
  "maxParticipants": 10,
  "isActive": true
}
```

> **Важно:** поле называется именно `isActive` (не `active`). При маппинге в Kotlin убедитесь, что сериализатор не меняет имя.

**`isActive`** — вычисляется динамически:
- `true` → `participantCount < maxParticipants` (есть свободные места)
- `false` → комната заполнена

---

### Получить все комнаты

```
GET /api/rooms
Authorization: Bearer <token>
```

**Ответ `200 OK`:** массив `RoomResponse[]` — возвращает ВСЕ комнаты. Фильтруйте по `isActive = true` на клиенте, чтобы показывать только доступные.

```kotlin
@GET("/api/rooms")
suspend fun getAllRooms(
    @Header("Authorization") token: String
): List<RoomResponse>
```

---

### Получить комнаты, в которых состоит текущий пользователь

```
GET /api/rooms/my
Authorization: Bearer <token>
```

**Ответ `200 OK`:** массив `RoomResponse[]` — только те комнаты, где пользователь является участником. Поскольку действует правило «1 пользователь = 1 комната», обычно возвращает 0 или 1 элемент.

```kotlin
@GET("/api/rooms/my")
suspend fun getMyRooms(
    @Header("Authorization") token: String
): List<RoomResponse>
```

---

### Войти в комнату

```
POST /api/rooms/{roomId}/join
Authorization: Bearer <token>
```

Тело запроса не нужно.

**Ответ `200 OK`** (тело пустое)

**Бизнес-логика при заполнении комнаты:**
Когда последнее место занято, сервер автоматически:
1. Помечает текущую комнату как `isActive = false`
2. Создаёт новую пустую комнату с теми же `context`, `title`, `maxParticipants`

Если вы отправляете `join` к уже заполненной комнате — вас автоматически добавляют в новую.

**Ошибки:**
| Код | Причина |
|-----|---------|
| `400` | Вы уже состоите в какой-либо комнате (сначала выйдите через `/leave`) |
| `404` | Комната не найдена |
| `401` | Не авторизован |

```kotlin
@POST("/api/rooms/{roomId}/join")
suspend fun joinRoom(
    @Path("roomId") roomId: String,
    @Header("Authorization") token: String
): Unit
```

---

### Покинуть комнату

```
POST /api/rooms/{roomId}/leave
Authorization: Bearer <token>
```

Тело запроса не нужно.

**Ответ `204 No Content`**

**Бизнес-логика при выходе:**
Если комната была закрыта (`isActive = false`, т.е. заполнена) и после выхода пользователя освободилось место — комната автоматически снова становится `isActive = true`.

**Ошибки:**
| Код | Причина |
|-----|---------|
| `400` | Вы не состоите в этой комнате |
| `404` | Комната не найдена |
| `401` | Не авторизован |

```kotlin
@POST("/api/rooms/{roomId}/leave")
suspend fun leaveRoom(
    @Path("roomId") roomId: String,
    @Header("Authorization") token: String
): Unit
```

---

## Типичный сценарий работы с комнатами

```kotlin
// 1. Получаем список доступных комнат
val rooms = api.getAllRooms(token)
val availableRooms = rooms.filter { it.isActive }

// 2. Пользователь выбирает комнату и входит
api.joinRoom(selectedRoom.id, token)

// 3. Проверяем в каких комнатах сейчас пользователь
val myRooms = api.getMyRooms(token)

// 4. Пользователь выходит из комнаты
api.leaveRoom(selectedRoom.id, token)

// 5. Если пользователь уже в комнате и хочет сменить — сначала выйти
val myRooms = api.getMyRooms(token)
if (myRooms.isNotEmpty()) {
    api.leaveRoom(myRooms.first().id, token)
}
api.joinRoom(newRoom.id, token)
```

---

## Обработка ошибок

Все ошибки возвращаются в едином формате:

```json
{
  "message": "Описание ошибки",
  "status": 400
}
```

Рекомендуемая обработка:

```kotlin
data class ApiError(
    val message: String,
    val status: Int
)

// В Retrofit interceptor или ViewModel:
try {
    api.joinRoom(roomId, token)
} catch (e: HttpException) {
    val error = gson.fromJson(e.response()?.errorBody()?.string(), ApiError::class.java)
    when (e.code()) {
        400 -> showError(error.message) // уже в комнате / другие бизнес-ошибки
        401 -> refreshTokenOrLogout()
        404 -> showError("Комната не найдена")
    }
}
```

---

## Полная Retrofit-спецификация

```kotlin
interface SyncRoomApi {

    // Auth (без изменений)
    @POST("/api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("/api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("/api/auth/oauth")
    suspend fun loginWithOAuth(@Body body: OAuthRequest): AuthResponse

    @POST("/api/auth/refresh")
    suspend fun refreshToken(@Body body: RefreshRequest): AuthResponse

    // Points
    @GET("/api/users/{userId}/points")
    suspend fun getPoints(
        @Path("userId") userId: String,
        @Header("Authorization") token: String
    ): List<PointResponse>

    @POST("/api/users/{userId}/points")
    suspend fun createPoint(
        @Path("userId") userId: String,
        @Header("Authorization") token: String,
        @Body body: CreatePointRequest
    ): PointResponse

    @PUT("/api/users/{userId}/points/{pointId}")
    suspend fun updatePoint(
        @Path("userId") userId: String,
        @Path("pointId") pointId: String,
        @Header("Authorization") token: String,
        @Body body: CreatePointRequest
    ): PointResponse

    @DELETE("/api/users/{userId}/points/{pointId}")
    suspend fun deletePoint(
        @Path("userId") userId: String,
        @Path("pointId") pointId: String,
        @Header("Authorization") token: String
    ): Unit

    // Rooms
    @GET("/api/rooms")
    suspend fun getAllRooms(
        @Header("Authorization") token: String
    ): List<RoomResponse>

    @GET("/api/rooms/my")
    suspend fun getMyRooms(
        @Header("Authorization") token: String
    ): List<RoomResponse>

    @POST("/api/rooms/{roomId}/join")
    suspend fun joinRoom(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String
    ): Unit

    @POST("/api/rooms/{roomId}/leave")
    suspend fun leaveRoom(
        @Path("roomId") roomId: String,
        @Header("Authorization") token: String
    ): Unit
}

// Data classes
data class PointResponse(
    val id: String,
    val userId: String,
    val context: String,
    val title: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

data class CreatePointRequest(
    val context: String,
    val title: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

data class RoomResponse(
    val id: String,
    val context: String,
    val title: String,
    val participantCount: Int,
    val maxParticipants: Int,
    @SerializedName("isActive") val isActive: Boolean  // важно: именно isActive
)
```
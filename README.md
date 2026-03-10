# SyncRoom Backend

Spring Boot backend РґР»СЏ РїСЂРёР»РѕР¶РµРЅРёСЏ SyncRoom СЃ JWT-Р°РІС‚РѕСЂРёР·Р°С†РёРµР№, REST API Рё WebSocket (STOMP).

## РўРµС…РЅРѕР»РѕРіРёРё

| | |
|--|--|
| Java 21 + Spring Boot **3.4.3** | РћСЃРЅРѕРІР° |
| PostgreSQL + Flyway | Р‘Р°Р·Р° РґР°РЅРЅС‹С… Рё РјРёРіСЂР°С†РёРё |
| Spring Security + JWT | РђРІС‚РѕСЂРёР·Р°С†РёСЏ |
| Spring WebSocket + STOMP | Real-time СЃРѕР±С‹С‚РёСЏ |
| Redis (РѕРїС†РёРѕРЅР°Р»СЊРЅРѕ) | РљСЌС€ СЃРѕСЃС‚РѕСЏРЅРёСЏ РјРµСЃС‚ |
| Spring Data JPA | ORM |
| SpringDoc (Swagger UI) | Р”РѕРєСѓРјРµРЅС‚Р°С†РёСЏ API |
| H2 + JUnit 5 + MockMvc | РўРµСЃС‚С‹ |

## Р—Р°РїСѓСЃРє

### Docker (СЂРµРєРѕРјРµРЅРґСѓРµС‚СЃСЏ)
```bash
docker-compose up -d        # Р·Р°РїСѓСЃС‚РёС‚СЊ
docker-compose down         # РѕСЃС‚Р°РЅРѕРІРёС‚СЊ
docker-compose down -v      # РѕСЃС‚Р°РЅРѕРІРёС‚СЊ + СѓРґР°Р»РёС‚СЊ РґР°РЅРЅС‹Рµ
docker-compose up -d --build  # РїРµСЂРµСЃРѕР±СЂР°С‚СЊ
docker-compose logs -f app  # Р»РѕРіРё
```
РџСЂРёР»РѕР¶РµРЅРёРµ: http://localhost:8080  
Swagger UI: http://localhost:8080/swagger-ui.html

### Gradle
```bash
gradle bootRun
gradle test
```

### РџРµСЂРµРјРµРЅРЅС‹Рµ РѕРєСЂСѓР¶РµРЅРёСЏ (`.env`)
```
DB_NAME=syncroom
DB_USERNAME=syncroom
DB_PASSWORD=syncroom
JWT_SECRET=your-secret-min-32-chars
JWT_ACCESS_EXPIRATION=900000       # 15 РјРёРЅСѓС‚
JWT_REFRESH_EXPIRATION=2592000000  # 30 РґРЅРµР№
REDIS_HOST=localhost               # РѕРїС†РёРѕРЅР°Р»СЊРЅРѕ
REDIS_PORT=6379                    # РѕРїС†РёРѕРЅР°Р»СЊРЅРѕ
APP_PORT=8080
```

---

## API вЂ” СЃРІРѕРґРЅР°СЏ С‚Р°Р±Р»РёС†Р°

Р’СЃРµ СЌРЅРґРїРѕРёРЅС‚С‹ РєСЂРѕРјРµ `/api/auth/*` С‚СЂРµР±СѓСЋС‚ `Authorization: Bearer <accessToken>`.

### РђРІС‚РѕСЂРёР·Р°С†РёСЏ

| РњРµС‚РѕРґ | URL | РћРїРёСЃР°РЅРёРµ |
|-------|-----|----------|
| POST | `/api/auth/register` | Р РµРіРёСЃС‚СЂР°С†РёСЏ email |
| POST | `/api/auth/email` | Р’С…РѕРґ email/password |
| POST | `/api/auth/oauth` | Р’С…РѕРґ С‡РµСЂРµР· VK / Yandex |
| POST | `/api/auth/refresh` | РћР±РЅРѕРІР»РµРЅРёРµ С‚РѕРєРµРЅРѕРІ |

### РџСЂРѕС„РёР»СЊ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ

| РњРµС‚РѕРґ | URL | РћРїРёСЃР°РЅРёРµ |
|-------|-----|----------|
| GET | `/api/users/me` | РџРѕР»СѓС‡РёС‚СЊ СЃРІРѕР№ РїСЂРѕС„РёР»СЊ |
| PUT | `/api/users/me` | РћР±РЅРѕРІРёС‚СЊ РїСЂРѕС„РёР»СЊ |

### Points вЂ” С‚РѕС‡РєРё РЅР° РєР°СЂС‚Рµ

| РњРµС‚РѕРґ | URL | РћРїРёСЃР°РЅРёРµ |
|-------|-----|----------|
| GET | `/api/users/{userId}/points` | РЎРїРёСЃРѕРє С‚РѕС‡РµРє |
| POST | `/api/users/{userId}/points` | РЎРѕР·РґР°С‚СЊ С‚РѕС‡РєСѓ |
| PUT | `/api/users/{userId}/points/{pointId}` | РћР±РЅРѕРІРёС‚СЊ С‚РѕС‡РєСѓ |
| DELETE | `/api/users/{userId}/points/{pointId}` | РЈРґР°Р»РёС‚СЊ С‚РѕС‡РєСѓ в†’ 204 |

### Rooms вЂ” РєРѕРјРЅР°С‚С‹

| РњРµС‚РѕРґ | URL | РћРїРёСЃР°РЅРёРµ |
|-------|-----|----------|
| GET | `/api/rooms` | Р’СЃРµ РєРѕРјРЅР°С‚С‹ (РІРєР»СЋС‡Р°СЏ `seats[]`) |
| GET | `/api/rooms/my` | РљРѕРјРЅР°С‚С‹ С‚РµРєСѓС‰РµРіРѕ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ |
| POST | `/api/rooms/{id}/join` | Р’РѕР№С‚Рё в†’ `{ room, participants[] }` |
| POST | `/api/rooms/{id}/leave` | Р’С‹Р№С‚Рё в†’ 204, Р°РІС‚Рѕ-РѕСЃРІРѕР±РѕР¶РґР°РµС‚ РјРµСЃС‚Рѕ |

> **РџСЂР°РІРёР»Рѕ:** 1 РїРѕР»СЊР·РѕРІР°С‚РµР»СЊ = 1 РєРѕРјРЅР°С‚Р° РѕРґРЅРѕРІСЂРµРјРµРЅРЅРѕ.  
> РџСЂРё Р·Р°РїРѕР»РЅРµРЅРёРё РєРѕРјРЅР°С‚С‹ Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё СЃРѕР·РґР°С‘С‚СЃСЏ РЅРѕРІР°СЏ СЃ С‚РµРјРё Р¶Рµ РїР°СЂР°РјРµС‚СЂР°РјРё.

**РЎС‚СЂСѓРєС‚СѓСЂР° РєРѕРјРЅР°С‚С‹** РІРєР»СЋС‡Р°РµС‚ `backgroundPicture` Рё `seats[]` (СЃ РЅРѕСЂРјР°Р»РёР·РѕРІР°РЅРЅС‹РјРё РєРѕРѕСЂРґРёРЅР°С‚Р°РјРё 0.0вЂ“1.0).

### Seats вЂ” РјРµСЃС‚Р° РІ РєРѕРјРЅР°С‚Рµ вњЁ

| РњРµС‚РѕРґ | URL | РћРїРёСЃР°РЅРёРµ |
|-------|-----|----------|
| POST | `/api/rooms/{roomId}/seats/{seatId}/sit` | Р—Р°РЅСЏС‚СЊ РјРµСЃС‚Рѕ в†’ `SeatDto` |
| POST | `/api/rooms/{roomId}/seats/{seatId}/leave` | Р’СЃС‚Р°С‚СЊ СЃ РјРµСЃС‚Р° в†’ `SeatDto` |

**РџСЂР°РІРёР»Р°:** РїРѕР»СЊР·РѕРІР°С‚РµР»СЊ РјРѕР¶РµС‚ Р·Р°РЅРёРјР°С‚СЊ С‚РѕР»СЊРєРѕ 1 РјРµСЃС‚Рѕ РІ РєРѕРјРЅР°С‚Рµ. РџСЂРё РІС‹Р±РѕСЂРµ РЅРѕРІРѕРіРѕ РјРµСЃС‚Р° вЂ” Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєР°СЏ РїРµСЂРµСЃР°РґРєР°. РџСЂРё РІС‹С…РѕРґРµ РёР· РєРѕРјРЅР°С‚С‹ вЂ” РјРµСЃС‚Рѕ РѕСЃРІРѕР±РѕР¶РґР°РµС‚СЃСЏ Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё.

**РљРѕРґС‹ РѕС‚РІРµС‚РѕРІ:** `200` СѓСЃРїРµС…, `403` С‡СѓР¶РѕРµ РјРµСЃС‚Рѕ, `404` РЅРµ РЅР°Р№РґРµРЅРѕ, `409` РјРµСЃС‚Рѕ Р·Р°РЅСЏС‚Рѕ РґСЂСѓРіРёРј.

---

## WebSocket (STOMP)

### РџРѕРґРєР»СЋС‡РµРЅРёРµ

| Р­РЅРґРїРѕРёРЅС‚ | РќР°Р·РЅР°С‡РµРЅРёРµ |
|----------|-----------| 
| `ws://host:8080/ws-stomp` | РќР°С‚РёРІРЅС‹Р№ WebSocket (Android) |
| `ws://host:8080/ws/websocket` | SockJS transport (web) |

РўРѕРєРµРЅ РїРµСЂРµРґР°С‘С‚СЃСЏ РІ STOMP CONNECT frame:
```
CONNECT
Authorization:Bearer <accessToken>
```

### РўРѕРїРёРєРё

| РўРѕРїРёРє | РЎРѕР±С‹С‚РёСЏ |
|-------|---------|
| `/topic/room/{roomId}` | `PARTICIPANT_JOINED`, `PARTICIPANT_LEFT` |
| `/topic/room/{roomId}/seats` | `SEAT_TAKEN`, `SEAT_LEFT` вњЁ |

### РўРёРїС‹ СЃРѕР±С‹С‚РёР№

| РўРёРї | РўРѕРїРёРє | РўСЂРёРіРіРµСЂ | Payload |
|-----|-------|---------|---------|
| `PARTICIPANT_JOINED` | `вЂ¦/room/{id}` | `POST /join` | `{ userId, name, avatarUrl, joinedAt }` |
| `PARTICIPANT_LEFT` | `вЂ¦/room/{id}` | `POST /leave` | `{ userId, name, avatarUrl }` |
| `SEAT_TAKEN` | `вЂ¦/room/{id}/seats` | `POST /sit` | `{ seatId, user: { id, name, avatarUrl } }` |
| `SEAT_LEFT` | `вЂ¦/room/{id}/seats` | `POST /leave seat` РёР»Рё РІС‹С…РѕРґ РёР· РєРѕРјРЅР°С‚С‹ | `{ seatId, userId }` |

### РўРµСЃС‚РёСЂРѕРІР°РЅРёРµ WebSocket

РћС‚РєСЂРѕР№ `stomp-test.html` РІ Р±СЂР°СѓР·РµСЂРµ (РєРѕСЂРµРЅСЊ РїСЂРѕРµРєС‚Р°):
1. Р’СЃС‚Р°РІСЊ `accessToken` в†’ **вљЎ Connect**
2. РќР°Р¶РјРё **рџ“‹ GET /api/rooms** вЂ” Р°РІС‚Рѕ-Р·Р°РїРѕР»РЅРёС‚ roomId Рё РїРµСЂРІС‹Р№ СЃРІРѕР±РѕРґРЅС‹Р№ seatId
3. **рџ‘‚ Subscribe /seats** в†’ **рџЄ‘ POST /sit** в†’ СѓРІРёРґРёС€СЊ `SEAT_TAKEN`

> Postman WebSocket tab РЅРµ РїРѕРґС…РѕРґРёС‚ РґР»СЏ STOMP вЂ” РёСЃРїРѕР»СЊР·СѓР№ `stomp-test.html`.

---

## РЎС‚СЂСѓРєС‚СѓСЂР° РїСЂРѕРµРєС‚Р°

```
src/main/java/ru/syncroom/
в”њв”Ђв”Ђ auth/           # РђРІС‚РѕСЂРёР·Р°С†РёСЏ (JWT, OAuth VK/Yandex, email)
в”њв”Ђв”Ђ users/          # РџСЂРѕС„РёР»СЊ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ
в”њв”Ђв”Ђ points/         # РўРѕС‡РєРё РЅР° РєР°СЂС‚Рµ (CRUD)
в”њв”Ђв”Ђ rooms/
в”‚   в”њв”Ђв”Ђ controller/ # REST: /api/rooms, /api/rooms/{id}/seats
в”‚   в”њв”Ђв”Ђ service/    # RoomService, SeatService (Р±РёР·РЅРµСЃ-Р»РѕРіРёРєР° + WS-СЃРѕР±С‹С‚РёСЏ)
в”‚   в”њв”Ђв”Ђ dto/        # RoomResponse (СЃ seats[]), SeatDto, JoinRoomResponse
в”‚   в”њв”Ђв”Ђ domain/     # Room, RoomParticipant, Seat (JPA)
в”‚   в”њв”Ђв”Ђ repository/ # RoomRepository, SeatRepository
в”‚   в””в”Ђв”Ђ ws/         # RoomEvent, RoomEventType, SeatTakenPayload, SeatLeftPayload
в””в”Ђв”Ђ common/
    в”њв”Ђв”Ђ config/     # SecurityConfig (CORS), WebSocketConfig, WebSocketSecurityConfig
    в”њв”Ђв”Ђ security/   # JwtTokenService, JwtAuthenticationFilter
    в””в”Ђв”Ђ exception/  # GlobalExceptionHandler (400/403/404/409)
```

**РњРёРіСЂР°С†РёРё Flyway:**
```
V1 вЂ” СЃРѕР·РґР°РЅРёРµ users
V2 вЂ” СЃРѕР·РґР°РЅРёРµ rooms + participants
V3 вЂ” СЃРѕР·РґР°РЅРёРµ points
V4 вЂ” РґРѕР±Р°РІР»РµРЅРёРµ background_picture Рє rooms
V5 вЂ” СЃРѕР·РґР°РЅРёРµ seats (10 РјРµСЃС‚ РЅР° РєРѕРјРЅР°С‚Сѓ, РЅРѕСЂРјР°Р»РёР·РѕРІР°РЅРЅС‹Рµ x/y)
```

---

## РўРµСЃС‚С‹

```bash
gradle test
```

| Р¤Р°Р№Р» | Р§С‚Рѕ С‚РµСЃС‚РёСЂСѓРµС‚ | РўРµСЃС‚РѕРІ |
|------|--------------|--------|
| `AuthControllerTest` | register, email login, OAuth, refresh | 13 |
| `UserControllerTest` | GET/PUT /me | 9 |
| `PointControllerTest` | CRUD С‚РѕС‡РµРє | 24 |
| `RoomControllerTest` | GET rooms/my, join, leave | 31 |
| `RoomServiceWebSocketTest` | WS-СЃРѕР±С‹С‚РёСЏ PARTICIPANT_JOINED/LEFT | 14 |
| `SeatControllerTest` | sit, stand-up, auto-move, 403, 409, 404 | 9 |
| **РС‚РѕРіРѕ** | | **103** |

РўРµСЃС‚С‹ РёСЃРїРѕР»СЊР·СѓСЋС‚ H2 in-memory Р‘Р”, `@MockitoBean SimpMessagingTemplate` (Spring Boot 3.4+), Redis РЅРµ С‚СЂРµР±СѓРµС‚СЃСЏ.

---

## Р‘РµР·РѕРїР°СЃРЅРѕСЃС‚СЊ

- JWT HMAC-SHA256 (HS256)
- Access token: 15 РјРёРЅСѓС‚ | Refresh token: 30 РґРЅРµР№
- РџР°СЂРѕР»Рё: BCrypt
- WebSocket auth: JWT РІ STOMP CONNECT frame (С‡РµСЂРµР· `ChannelInterceptor`)
- CORS: СЂР°Р·СЂРµС€РµРЅС‹ РІСЃРµ origins РґР»СЏ dev-СЃСЂРµРґС‹ (`CorsConfigurationSource`)

---

## Р”Р»СЏ Android СЂР°Р·СЂР°Р±РѕС‚С‡РёРєР°

РЎРј. [README-ANDROID.md](README-ANDROID.md) вЂ” РїРѕР»РЅР°СЏ РґРѕРєСѓРјРµРЅС‚Р°С†РёСЏ API, Kotlin data classes, Retrofit СЃРїРµС†РёС„РёРєР°С†РёСЏ, СЃС†РµРЅР°СЂРёРё WS-СЃРѕР±С‹С‚РёР№.


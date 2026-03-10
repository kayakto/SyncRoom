# SyncRoom Backend

Spring Boot backend для приложения SyncRoom с JWT-авторизацией, REST API и WebSocket (STOMP).

## Технологии

| | |
|--|--|
| Java 21 + Spring Boot 3.2 | Основа |
| PostgreSQL + Flyway | База данных и миграции |
| Spring Security + JWT | Авторизация |
| Spring WebSocket + STOMP | Real-time события |
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
| GET | `/api/rooms` | Все комнаты |
| GET | `/api/rooms/my` | Комнаты текущего пользователя |
| POST | `/api/rooms/{id}/join` | Войти в комнату → `{ room, participants[] }` |
| POST | `/api/rooms/{id}/leave` | Выйти из комнаты → 204 |

> **Правило:** 1 пользователь = 1 комната одновременно. Для входа в другую нужно сначала выйти из текущей.  
> При заполнении комнаты автоматически создаётся новая (дубликат) с теми же параметрами.

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

### Подписка на события комнаты
```
SUBSCRIBE /topic/room/{roomId}
```

### Формат события
```json
{
  "type": "PARTICIPANT_JOINED",
  "payload": { "userId": "uuid", "name": "Иван", "avatarUrl": null, "joinedAt": "..." },
  "timestamp": "2026-03-10T13:00:00+05:00"
}
```

| Событие | Триггер |
|---------|---------|
| `PARTICIPANT_JOINED` | `POST /api/rooms/{id}/join` |
| `PARTICIPANT_LEFT` | `POST /api/rooms/{id}/leave` |

### Тестирование WebSocket

Открой `stomp-test.html` в браузере (находится в корне проекта):
1. Вставь `accessToken`
2. Нажми **Connect**
3. Вставь `roomId` → **Subscribe**
4. Вызови `/join` или `/leave` в Postman — в браузере появится событие

> Postman WebSocket tab не подходит для STOMP — используй `stomp-test.html`.

---

## Структура проекта

```
src/main/java/ru/syncroom/
├── auth/           # Авторизация (JWT, OAuth VK/Yandex, email)
├── users/          # Профиль пользователя
├── points/         # Точки на карте (CRUD)
├── rooms/
│   ├── controller/ # REST: /api/rooms
│   ├── service/    # Бизнес-логика + WS-события
│   ├── dto/        # RoomResponse, JoinRoomResponse, ParticipantResponse
│   ├── domain/     # Room, RoomParticipant (JPA)
│   ├── repository/
│   └── ws/         # RoomEvent, RoomEventType (STOMP)
└── common/
    ├── config/     # SecurityConfig, WebSocketConfig, WebSocketSecurityConfig
    ├── security/   # JwtTokenService, JwtAuthenticationFilter
    └── exception/  # GlobalExceptionHandler
```

---

## Тесты

```bash
gradle test
```

| Файл | Что тестирует |
|------|--------------|
| `AuthControllerTest` | register, email login, OAuth, refresh |
| `UserControllerTest` | GET/PUT /me |
| `PointControllerTest` | CRUD точек |
| `RoomControllerTest` | GET rooms/my, join, leave (34 теста) |
| `RoomServiceWebSocketTest` | WS-события PARTICIPANT_JOINED/LEFT (15 тестов) |

Тесты используют H2 in-memory БД и `@MockBean SimpMessagingTemplate`.

---

## Безопасность

- JWT HMAC-SHA256 (HS256)
- Access token: 15 минут | Refresh token: 30 дней
- Пароли: BCrypt
- WebSocket auth: JWT в STOMP CONNECT frame (через `ChannelInterceptor`)
- WS handshake: без токена (auth внутри STOMP-соединения)

---

## Для Android разработчика

См. [README-ANDROID.md](README-ANDROID.md) — полная документация API, Kotlin-примеры кода, Retrofit спецификация.

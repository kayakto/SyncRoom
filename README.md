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
docker-compose up -d        # запустить БЕЗ БОТОВ
docker-compose down         # остановить
docker-compose down -v      # остановить + удалить данные
docker-compose up -d --build  # пересобрать
docker-compose logs -f app  # логи
```
Приложение: http://localhost:8080  
Swagger UI: http://localhost:8080/swagger-ui.html

### Docker + Local AI (one-button)

```bash
docker compose -f docker-compose.yml -f docker-compose.local-ai.yml up -d --build
```

Этот режим поднимает весь backend и локальные AI-сервисы для ботов:
- `app`, `postgres`, `media`
- `inference-mock`
- `ollama` (+ авто-pull `llava:7b`)
- `local-draw`

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
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
APP_AUTH_COOKIES_ACCESS_TOKEN_NAME=SR_ACCESS_TOKEN
APP_AUTH_COOKIES_REFRESH_TOKEN_NAME=SR_REFRESH_TOKEN
APP_AUTH_COOKIES_DOMAIN=
APP_AUTH_COOKIES_PATH=/
APP_AUTH_COOKIES_SECURE=false
APP_AUTH_COOKIES_SAME_SITE=Lax
APP_AUTH_OAUTH_VK_ALLOWED_REDIRECT_URIS=http://localhost:5173/auth/callback
APP_AUTH_OAUTH_YANDEX_ALLOWED_REDIRECT_URIS=http://localhost:5173/auth/callback
REDIS_HOST=localhost               # опционально
REDIS_PORT=6379                    # опционально
APP_PORT=8080
BOTS_INFERENCE_ENABLED=true
BOTS_INFERENCE_DRAW_URL=http://localhost:8091/api/draw
BOTS_INFERENCE_GUESS_URL=http://localhost:8091/api/guess
```

### Локальный inference для игровых ботов (DRAW/GUESS)

Мини-сервис лежит в `inference-mock/` и совместим с `GameService` без изменения API.

Самый простой запуск вообще без ручной установки локальных ИИ:

```bash
docker compose -f docker-compose.yml -f docker-compose.local-ai.yml up -d --build
```

Что поднимется:

- `ollama` с автозагрузкой модели `llava:7b` для подписи/угадывания картинки;
- `local-draw` — совместимый draw-service с локальной tiny Stable Diffusion моделью;
- `inference-mock` — прокси-адаптер для `SyncRoom`;
- сам `SyncRoom`.

Сгенерированные изображения автоматически сохраняются в папку
`img-generated` в корне проекта (в формате `.png`).

Важно:

- первый запуск может идти долго, потому что контейнеры скачивают модели;
- по умолчанию draw-service использует `segmind/tiny-sd`, это компромисс между скоростью и качеством;
- `Ollama` будет доступен на `http://localhost:11434`, draw-service на `http://localhost:7860`.

Быстрый запуск:

```bash
cd inference-mock
python -m venv .venv
. .venv/Scripts/activate
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8091
```

Проверка:

```bash
curl http://localhost:8091/health
```

Режимы:

- `INFERENCE_PROVIDER=local` — локальный режим по умолчанию.
- `INFERENCE_PROVIDER=ollama` — локальный vision через Ollama (`llava:7b`), без внешних API.
- `INFERENCE_PROVIDER=local` — только локальные нейросети:
  - draw: `LOCAL_DRAW_URL` (например A1111 / SD WebUI) -> mock
  - guess: `LOCAL_GUESS_URL` или Ollama -> mock

Переменные для локального backend:

```bash
INFERENCE_PROVIDER=local
LOCAL_DRAW_URL=http://localhost:7860/sdapi/v1/txt2img
LOCAL_GUESS_URL=http://localhost:8100/api/guess  # опционально
OLLAMA_URL=http://127.0.0.1:11434
OLLAMA_VISION_MODEL=llava:7b
```

Для Docker Compose сервис `inference-mock` уже добавлен и включается автоматически.

### Будет ли backend работать без микросервиса ботов?

Да, backend работает и без локального AI-оверлея:
- если запускать только `docker-compose.yml`, API и игры работают;
- боты в Gartic тоже работают, но генерация изображений/подписей идёт через fallback (упрощённый результат);
- если нужен именно AI-качество, запускай с `docker-compose.local-ai.yml`.

---

## API — сводная таблица

Все эндпоинты кроме `/api/auth/*` требуют `Authorization: Bearer <accessToken>`.

### Авторизация

| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/api/auth/register` | Регистрация email |
| POST | `/api/auth/email` | Вход email/password |
| POST | `/api/auth/oauth` | Вход через VK / Yandex (опц. `redirectUri`) |
| POST | `/api/auth/refresh` | Обновление токенов (из body или cookie) |
| GET | `/api/auth/csrf` | Выдать CSRF токен для web-клиента |
| POST | `/api/auth/logout` | Очистить auth cookies |

**Web auth flow (cookie + CSRF):**

- login/register/oauth/refresh ставят `HttpOnly` cookies: `SR_ACCESS_TOKEN`, `SR_REFRESH_TOKEN` (имена настраиваются через env);
- для браузера можно работать без хранения JWT в `localStorage`:
  1. `GET /api/auth/csrf` (получить `XSRF-TOKEN`);
  2. отправлять state-changing запросы с `X-XSRF-TOKEN`;
  3. `withCredentials: true` на клиенте;
- для мобильных и других non-browser клиентов bearer flow сохранён: `Authorization: Bearer <accessToken>`.

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

**Счётчики в `RoomResponse`:**

- `participantCount` — сколько человек **за столом** (занятые места в `seats[]`);
- `observerCount` — сколько **в комнате без места** (лаунж / наблюдатели). После `POST /join` пользователь по умолчанию наблюдатель; после `POST .../sit` становится участником за столом; `POST .../leave` (место) снова делает наблюдателем.

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

### Pomodoro — общий таймер для учебных и рабочих комнат

Доступно при `context="study"` **или** `context="work"`. Для `sport` / `leisure` все эндпоинты помодоро возвращают `400`.

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/rooms/{roomId}/pomodoro` | Текущее состояние помодоро-таймера |
| POST | `/api/rooms/{roomId}/pomodoro/start` | Запустить помодоро (`study` или `work`) |
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
- только участники комнаты с `context="study"` или `context="work"` могут пользоваться помодоро (все методы, включая `GET` / `DELETE`);
- в комнате может быть только один активный таймер (UNIQUE room_id).
- **Автосмена фаз на сервере:** раз в 1 с фоновая задача ищет сессии с `phase` WORK/BREAK/LONG_BREAK и `phaseEndAt <= now()`, переводит на следующую фазу и шлёт `POMODORO_PHASE_CHANGED` (или `POMODORO_STOPPED` при завершении). Дублирует и подстраховывает in-memory таймер (`PomodoroTimerService`), в том числе после рестарта JVM. В профиле `test` планировщик отключён (`@Profile("!test")`).

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
| GET | `/api/rooms/{roomId}/tasks/all` | Все таски участников с `ownerId`, `ownerName`, `isBot`, `likeCount`, `likedByMe` |
| GET | `/api/rooms/{roomId}/leaderboard` | Лидерборд: лайки на цели, `completedTasks` / `totalTasks` |
| POST | `/api/rooms/{roomId}/tasks/{taskId}/like` | Лайк чужой цели → `{ taskId, likeCount, likedByMe }` + WS `TASK_LIKED` |
| DELETE | `/api/rooms/{roomId}/tasks/{taskId}/like` | Снять лайк → то же + WS `TASK_UNLIKED` |
| POST | `/api/rooms/{roomId}/tasks` | Создать таск |
| PUT | `/api/rooms/{roomId}/tasks/{taskId}` | Обновить таск (partial update) |
| DELETE | `/api/rooms/{roomId}/tasks/{taskId}` | Удалить таск |
| POST | `/api/rooms/{roomId}/bots/motivational-goals/activate` | Активировать бота целей (`goalCount`, `autoSuggest`, `suggestOnBreak`) |
| DELETE | `/api/rooms/{roomId}/bots/motivational-goals/deactivate` | Деактивировать бота целей |
| GET | `/api/rooms/{roomId}/bots` | Список ботов комнаты |
| PUT | `/api/rooms/{roomId}/bots/{botId}/config` | Обновить конфиг конкретного бота комнаты |
| DELETE | `/api/rooms/{roomId}/bots/{botId}` | Удалить бота из комнаты |

Правила лайков: только участники той же комнаты; **нельзя** лайкать свою цель; один лайк с пользователя на цель; при удалении цели лайки удаляются каскадом.

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

**Мотивационный бот целей (`MOTIVATIONAL_GOALS`):**

- работает через таблицы `room_bot` и `bot_goal_template`;
- можно добавить несколько экземпляров бота в одной комнате (каждый с отдельным `botId` и конфигом);
- при активации с `autoSuggest=true` создаёт `goalCount` задач от системного пользователя `MotivBot`;
- при `suggestOnBreak=true` создаёт цели при переходе помодоро в `BREAK`/`LONG_BREAK`;
- публикует WS-событие `BOT_GOAL_SUGGESTED` в `/topic/room/{roomId}/tasks`.

### Games — Quiplash + Gartic Phone

| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/api/rooms/{roomId}/games` | Создать игровую сессию (лобби) |
| GET | `/api/rooms/{roomId}/games/current` | Получить активную игру в комнате |
| POST | `/api/games/{gameId}/ready` | Отметиться готовым (upsert игрока) |
| POST | `/api/games/{gameId}/unready` | Снять готовность (только `LOBBY`) |
| POST | `/api/games/{gameId}/leave` | Выйти из лобби игры (только `LOBBY`); если игроков не осталось — сессия удаляется |
| POST | `/api/games/{gameId}/start` | Старт игры (минимум 3 ready игрока) |
| GET | `/api/bots/available` | Список доступных ботов |
| POST | `/api/games/{gameId}/bots/add` | Добавить ботов в лобби (GARTIC_* / QUIPLASH_*) |
| DELETE | `/api/games/{gameId}/bots/{botId}` | Убрать бота из лобби |

При старте учитываются только игроки с `isReady=true`: если ready-игроков 3+, игра стартует сразу, а неготовые удаляются из лобби и получают персональное WS-событие `PLAYER_KICKED`.

При **`POST /api/rooms/{roomId}/leave`** сервер также убирает пользователя из активной игры этой комнаты: в **LOBBY** — как `leave`, при **IN_PROGRESS** — игра **отменяется** (`GAME_CANCELLED`), таймеры сбрасываются, сессия удаляется.

**Create game request:**

```json
{
  "gameType": "QUIPLASH"
}
```

`gameType` может быть:
- `QUIPLASH`
- `GARTIC_PHONE`

`botType` для `/bots/add`:
- для `GARTIC_PHONE`: `GARTIC_DRAWER`, `GARTIC_WRITER`, `GARTIC_GUESSER`
- для `QUIPLASH`: `QUIPLASH_JOKER`, `QUIPLASH_VOTER`

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

### Обрыв WebSocket (закрытие STOMP-сессии)

Если клиент **закрывает последнее** STOMP-подключение, в котором при CONNECT был передан валидный JWT (`Authorization: Bearer …`), сервер выполняет **тот же выход из комнаты**, что и при `POST /api/rooms/{roomId}/leave`:

- освобождается место (при необходимости уходит `SEAT_LEFT` на `/topic/room/{roomId}/seats`);
- участник удаляется из комнаты, подписчикам `/topic/room/{roomId}` уходит `PARTICIPANT_LEFT`;
- **игра:** как при HTTP leave — выход из лобби / отмена `IN_PROGRESS` для игрока (см. `GAME_CANCELLED` в таблице ниже).

**Нюансы:**

- Без JWT в CONNECT (`StompPrincipal` не установлен) при отключении комната **не** меняется.
- Если у пользователя **несколько** одновременных STOMP-сессий (две вкладки и т.п.), закрытие одной сессии **не** удаляет из комнаты — пока жива хотя бы одна сессия с тем же пользователем.
- Клиент **без** WebSocket (только REST) при обрыве сети в БД остаётся участником, пока не вызовет `POST .../leave` или не подключит STOMP и снова не отключится.

### Топики

| Топик | События |
|-------|---------|
| `/topic/room/{roomId}` | `PARTICIPANT_JOINED`, `PARTICIPANT_LEFT` |
| `/topic/room/{roomId}/seats` | `SEAT_TAKEN`, `SEAT_LEFT` ✨ |
| `/topic/room/{roomId}/projector` | `PROJECTOR_STARTED`, `PROJECTOR_STOPPED`, `PROJECTOR_CONTROL`, `STREAM_LIVE`, `STREAM_OFFLINE` ✨ |
| `/topic/room/{roomId}/pomodoro` | `POMODORO_STARTED`, `POMODORO_PHASE_CHANGED`, `POMODORO_PAUSED`, `POMODORO_RESUMED`, `POMODORO_STOPPED` ✨ |
| `/topic/room/{roomId}/tasks` | `TASK_CREATED`, `TASK_UPDATED`, `TASK_DELETED`, `TASK_LIKED`, `TASK_UNLIKED`, `BOT_GOAL_SUGGESTED` |
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
| `PARTICIPANT_JOINED` | `.../room/{id}` | `POST /join` | `{ userId, name, avatarUrl, joinedAt, role }` (`OBSERVER` \| `PARTICIPANT`) |
| `PARTICIPANT_LEFT` | `.../room/{id}` | `POST /leave` или закрытие **последнего** STOMP (с JWT) | `{ userId, name, avatarUrl }` (без `role`) |
| `SEAT_TAKEN` | `.../room/{id}/seats` | `POST /sit` | `{ seatId, user: { id, name, avatarUrl }, participantCount, observerCount }` |
| `SEAT_LEFT` | `.../room/{id}/seats` | `POST /leave seat`, выход из комнаты или закрытие **последнего** STOMP (с JWT) | `{ seatId, userId, participantCount, observerCount }` |
| `PROJECTOR_STARTED` | `.../room/{id}/projector` | `POST /projector` | `{ host, mode, videoUrl, videoTitle, streamKey? }` |
| `PROJECTOR_STOPPED` | `.../room/{id}/projector` | `DELETE /projector` или выход хоста | `{ hostId }` |
| `PROJECTOR_CONTROL` | `.../room/{id}/projector` | WS SEND `/projector/control` | `{ action, positionMs }` |
| `STREAM_LIVE` | `.../room/{id}/projector` | SRS `on_publish` | `{ videoUrl }` |
| `STREAM_OFFLINE` | `.../room/{id}/projector` | SRS `on_unpublish` | `{}` |
| `TASK_CREATED` | `.../room/{id}/tasks` | `POST .../tasks` | `{ taskId, text, isDone, sortOrder, ownerId, ownerName, likeCount, likedByMe }` |
| `TASK_UPDATED` | `.../room/{id}/tasks` | `PUT .../tasks/{id}` | `{ taskId, text, isDone, sortOrder, ownerId, ownerName }` |
| `TASK_DELETED` | `.../room/{id}/tasks` | `DELETE .../tasks/{id}` | `{ taskId, ownerId }` |
| `TASK_LIKED` | `.../room/{id}/tasks` | `POST .../tasks/{id}/like` | `{ taskId, userId, userName, likeCount, action: "LIKE" }` |
| `TASK_UNLIKED` | `.../room/{id}/tasks` | `DELETE .../tasks/{id}/like` | `{ taskId, userId, likeCount, action: "UNLIKE" }` |
| `BOT_GOAL_SUGGESTED` | `.../room/{id}/tasks` | activate бота / переход помодоро в break | `{ task: { id, text, ownerId, ownerName, isBot } }` |
| `GAME_STARTED` | `.../game/{id}` | `POST /games/{id}/start` | `{ players[] }` |
| `PLAYER_KICKED` | `.../game/{id}` (user queue) | `POST /games/{id}/start` когда игрок не готов | `{ userId, reason }` |
| `PLAYER_UNREADY` | `.../game/{id}` | `POST /games/{id}/unready` или WS | `{ userId }` |
| `PLAYER_LEFT` | `.../game/{id}` | `POST /games/{id}/leave` или выход из комнаты (лобби) | `{ userId }` |
| `GAME_CANCELLED` | `.../game/{id}` | выход из комнаты при игре `IN_PROGRESS` | `{ reason: "PLAYER_LEFT_ROOM" }` |
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
├── study/
│   ├── controller/ # PomodoroController, StudyTaskController
│   ├── service/    # PomodoroService, PomodoroTimerService, StudyTaskService
│   ├── schedule/   # PomodoroPhaseScheduler (@Scheduled автосмена фаз, не в test)
│   ├── domain/     # PomodoroSession, StudyTask, TaskLike
│   ├── dto/        # Pomodoro*, Task*, Leaderboard*
│   ├── repository/
│   └── ws/         # PomodoroEvent, StudyTaskWsEvent
└── common/
    ├── config/     # SecurityConfig, WebSocketConfig, WebSocketSecurityConfig, SchedulingConfig
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
V12 — таблица task_like (лайки на study_tasks)
V18 — room_bot, bot_goal_template и seed MotivBot/шаблонов целей
```

---

## Тесты

```bash
gradle test
```

| Файл | Что тестирует | Тестов |
|------|--------------|--------|
| `AuthControllerTest` | register, email login, OAuth, refresh | 16 |
| `UserControllerTest` | GET/PUT /me | 9 |
| `PointControllerTest` | CRUD точек | 24 |
| `RoomControllerTest` | GET rooms/my, join, leave | 31 |
| `RoomServiceWebSocketTest` | WS-события PARTICIPANT_JOINED/LEFT, leave при обрыве WS | 16 |
| `WebSocketRoomDisconnectListenerTest` | STOMP disconnect → leave комнаты, мультисессии | 3 |
| `SeatControllerTest` | sit, stand-up, auto-move, 403, 409, 404 | 9 |
| `ProjectorControllerTest` | REST + SRS callback для проектора | 8 |
| `PomodoroControllerTest` | REST для помодоро (study + work) | 9 |
| `PomodoroAdvancePhaseTest` | `advancePhaseIfExpired`, BREAK/LONG_BREAK, WS, выборка | 6 |
| `StudyTaskControllerTest` | Таски, лайки, leaderboard, идемпотентность, не участник | 12 |
| `RoomBotControllerTest` | Активация бота целей, автогенерация, break-триггер, негативные кейсы | 6 |
| `GameControllerTest` | REST игры: create/current/ready/unready/leave/start, leaveRoom→игра | 7 |
| `GameServiceWebSocketTest` | Quiplash + Gartic WS flow, валидации, `PLAYER_KICKED` и таймауты | 6 |
| `RoomChatControllerTest` | История чата: пагинация, пустой список, два автора, доступ | 7 |
| `RoomChatServiceTest` | Валидация, trim, пагинация, broadcast, граница 4000 символов | 9 |
| **Итого** | | **173** |

Тесты используют H2 in-memory БД, `@MockitoBean SimpMessagingTemplate` (Spring Boot 3.4+), Redis не требуется.

---

## Безопасность

- JWT HMAC-SHA256 (HS256)
- Access token: 15 минут | Refresh token: 30 дней
- Пароли: BCrypt
- WebSocket auth: JWT в STOMP CONNECT frame (через `ChannelInterceptor`)
- CORS: allowlist origin-ов через `APP_CORS_ALLOWED_ORIGINS` (используется и для REST, и для WS handshake)
- Web cookie auth: `HttpOnly` cookies (`SameSite=Lax`, `Secure` настраивается через env)
- CSRF для web-клиентов: `CookieCsrfTokenRepository`, endpoint `GET /api/auth/csrf`, header `X-XSRF-TOKEN`
- OAuth redirect URI allowlist:
  - `APP_AUTH_OAUTH_VK_ALLOWED_REDIRECT_URIS`
  - `APP_AUTH_OAUTH_YANDEX_ALLOWED_REDIRECT_URIS`

---

## Для Android разработчика

См. [README-ANDROID.md](README-ANDROID.md) — полная документация API, Kotlin data classes, Retrofit спецификация, сценарии WS-событий.

# SyncRoom Backend

Backend приложение для SyncRoom на базе Spring Boot с поддержкой аутентификации через OAuth (VK, Yandex) и email/password, используя JWT токены.

## Технологии

- Java 21
- Spring Boot 3.2.0
- Gradle (Kotlin DSL)
- PostgreSQL
- Flyway (миграции БД)
- Spring Security с JWT
- Spring Data JPA
- SpringDoc OpenAPI (Swagger UI) для документации API


## Запуск приложения

### Используя Docker (рекомендуется):

Самый простой способ запустить всё приложение одной командой:

```bash
docker-compose up -d
```

📖 **Подробные инструкции по Docker**: см. [DOCKER.md](DOCKER.md)

Это запустит:
- PostgreSQL базу данных на порту 5432
- Spring Boot приложение на порту 8080

Приложение будет доступно по адресу http://localhost:8080.

**Остановка:**
```bash
docker-compose down
```

**Остановка с удалением данных:**
```bash
docker-compose down -v
```

**Пересборка и запуск:**
```bash
docker-compose up -d --build
```

**Просмотр логов:**
```bash
docker-compose logs -f app
```

**Переменные окружения для Docker:**

Вы можете создать файл `.env` в корне проекта для настройки переменных окружения. Пример конфигурации находится в файле `.env.example`:

```bash
cp .env.example .env
# Отредактируйте .env файл по необходимости
```

Доступные переменные:
- `DB_NAME` - имя базы данных (по умолчанию: `syncroom`)
- `DB_USERNAME` - имя пользователя БД (по умолчанию: `syncroom`)
- `DB_PASSWORD` - пароль БД (по умолчанию: `syncroom`)
- `JWT_SECRET` - секретный ключ для JWT (обязательно измените в production!)
- `JWT_ACCESS_EXPIRATION` - время жизни access токена в миллисекундах (по умолчанию: `900000` = 15 минут)
- `JWT_REFRESH_EXPIRATION` - время жизни refresh токена в миллисекундах (по умолчанию: `2592000000` = 30 дней)
- `APP_PORT` - порт приложения (по умолчанию: `8080`)
- `POSTGRES_PORT` - порт PostgreSQL (по умолчанию: `5432`)

### Запуск используя Gradle Wrapper:

📖 **Подробные инструкции по GRADLE**: см. [RUN GRADLE.md](./RUN%20GRADLE.md)

Swagger UI позволяет:
- Просматривать все доступные endpoints
- Видеть схемы запросов и ответов
- Тестировать API прямо из браузера
- Использовать JWT токены для авторизованных запросов (кнопка "Authorize" в Swagger UI)

## API Endpoints

Все endpoints находятся под префиксом `/api/auth`.

### 1. Регистрация пользователя

**POST** `/api/auth/register`

Регистрирует нового пользователя с email/password.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "name": "John Doe"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "isFirstLogin": true,
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "John Doe",
    "email": "user@example.com",
    "provider": "email",
    "avatarUrl": null
  }
}
```

### 2. Аутентификация по email/password

**POST** `/api/auth/email`

Аутентифицирует пользователя по email и паролю.

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response:** (аналогично регистрации)

### 3. OAuth аутентификация

**POST** `/api/auth/oauth`

Аутентифицирует пользователя через OAuth провайдер (VK или Yandex).

**Request Body:**
```json
{
  "provider": "vk",
  "accessToken": "vk_access_token_from_client"
}
```

**Примечание:** 
- Для VK: требуется реальный access token, полученный от VK OAuth (https://oauth.vk.com/authorize). Backend проверяет токен через VK API (https://api.vk.com/method/users.get).
- Для Yandex: требуется реальный access token, полученный от Yandex OAuth (https://oauth.yandex.ru/authorize). Backend проверяет токен через Yandex API (https://login.yandex.ru/info).

**Response:** (аналогично регистрации)

### 4. Обновление токенов

**POST** `/api/auth/refresh`

Обновляет access и refresh токены.

**Request Body:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

## Примеры curl запросов

### Регистрация:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123",
    "name": "Test User"
  }'
```

### Вход по email:
```bash
curl -X POST http://localhost:8080/api/auth/email \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123"
  }'
```

### OAuth (VK):
```bash
curl -X POST http://localhost:8080/api/auth/oauth \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "vk",
    "accessToken": "YOUR_VK_ACCESS_TOKEN"
  }'
```

### Обновление токенов:
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "YOUR_REFRESH_TOKEN_HERE"
  }'
```

### Защищённый endpoint (требует JWT):
```bash
curl -X GET http://localhost:8080/api/some-protected-endpoint \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN_HERE"
```

## User Profile Endpoints

Все endpoints для работы с профилем находятся под префиксом `/api/users` и требуют JWT аутентификации.

### 1. Получить текущий профиль

**GET** `/api/users/me`

Возвращает профиль текущего аутентифицированного пользователя.

**Headers:**
```
Authorization: Bearer YOUR_ACCESS_TOKEN
```

**Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "John Doe",
  "email": "user@example.com",
  "provider": "email",
  "avatarUrl": "https://example.com/avatar.jpg"
}
```

### 2. Обновить профиль

**PUT** `/api/users/me`

Обновляет профиль текущего аутентифицированного пользователя.

**Headers:**
```
Authorization: Bearer YOUR_ACCESS_TOKEN
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "Updated Name",
  "email": "newemail@example.com",
  "avatarUrl": "https://example.com/new-avatar.jpg"
}
```

**Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Updated Name",
  "email": "newemail@example.com",
  "provider": "email",
  "avatarUrl": "https://example.com/new-avatar.jpg"
}
```

**Примечания:**
- Email должен быть уникальным (если меняется на существующий email другого пользователя - вернется ошибка 400)
- Имя и email обязательны
- AvatarUrl опционален (может быть null)
- Для OAuth пользователей (VK, Yandex) можно обновлять все поля, включая email

## Примеры curl запросов для профиля

### Получить профиль:
```bash
curl -X GET http://localhost:8080/api/users/me \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

### Обновить профиль:
```bash
curl -X PUT http://localhost:8080/api/users/me \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "New Name",
    "email": "newemail@example.com",
    "avatarUrl": "https://example.com/avatar.jpg"
  }'
```

## Структура проекта

```
src/main/java/ru/syncroom/
├── SyncRoomApplication.java          # Главный класс приложения
├── auth/                             # Модуль аутентификации
│   ├── controller/                   # REST контроллеры
│   ├── service/                      # Бизнес-логика
│   ├── dto/                          # DTO классы
│   └── client/                       # Внешние клиенты (OAuth)
├── users/                            # Модуль пользователей
│   ├── controller/                   # REST контроллеры (профиль)
│   ├── service/                      # Бизнес-логика (профиль)
│   ├── dto/                          # DTO классы (профиль)
│   ├── domain/                       # JPA сущности
│   └── repository/                   # Репозитории
├── common/                           # Общие компоненты
│   ├── config/                       # Конфигурации
│   ├── security/                     # JWT и Security
│   └── exception/                    # Обработка ошибок
└── resources/
    ├── application.yml               # Основная конфигурация
    ├── application-dev.yml           # Конфигурация для dev профиля
    └── db/migration/                 # Flyway миграции
        └── V1__create_users.sql
```

## Миграции базы данных

Миграции выполняются автоматически при запуске приложения через Flyway. Все миграции находятся в `src/main/resources/db/migration/`.

## Безопасность

- JWT токены используют алгоритм HMAC-SHA256 (HS256)
- Access токены живут 15 минут (по умолчанию)
- Refresh токены живут 30 дней (по умолчанию)
- Пароли хешируются с помощью BCrypt
- Все endpoints кроме `/api/auth/*` требуют аутентификации

## Замечания

1. **OAuth клиент:** 
   - ✅ VK OAuth реализован и работает с реальным VK API
   - ✅ Yandex OAuth реализован и работает с реальным Yandex API
   - Backend проверяет валидность access token через соответствующие API провайдеров

2. **JWT Secret:** В production обязательно измените `JWT_SECRET` на надёжный случайный ключ длиной не менее 32 символов.

3. **База данных:** Убедитесь, что PostgreSQL запущен и доступен перед запуском приложения.

## Тестирование

Проект содержит интеграционные тесты для всех endpoints аутентификации.

### Запуск тестов

```bash
./gradlew test
```

или

```bash
gradle test
```

### Структура тестов

Тесты находятся в:
- `src/test/java/ru/syncroom/auth/controller/AuthControllerTest.java` - тесты аутентификации
- `src/test/java/ru/syncroom/users/controller/UserControllerTest.java` - тесты профиля

Покрытие тестами:

**Аутентификация:**
- ✅ **POST /api/auth/register** - успешная регистрация, дубликат email, валидация
- ✅ **POST /api/auth/email** - успешная аутентификация, неверный пароль, пользователь не найден
- ✅ **POST /api/auth/oauth** - успешная аутентификация (новый/существующий пользователь), неверный провайдер, неверный токен
- ✅ **POST /api/auth/refresh** - успешное обновление, неверный токен, access токен вместо refresh, пользователь не найден

**Профиль пользователя:**
- ✅ **GET /api/users/me** - успешное получение профиля, не авторизован
- ✅ **PUT /api/users/me** - успешное обновление, дубликат email, валидация, не авторизован

Тесты используют:
- H2 in-memory database для изоляции
- MockBean для ExternalOAuthClient (не делают реальные запросы к VK API)
- Spring Boot Test с MockMvc для тестирования REST endpoints
- Профиль `test` с отдельной конфигурацией

## Разработка

Для разработки используется профиль `dev`, который активируется по умолчанию. Все настройки можно переопределить через переменные окружения.

## Лицензия

Проект создан для SyncRoom.

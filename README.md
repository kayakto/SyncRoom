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

## Требования

- Java 21 или выше
- PostgreSQL 12 или выше
- Gradle 7.5+ (или используйте Gradle Wrapper)

## Настройка базы данных

1. Создайте базу данных PostgreSQL:
```sql
CREATE DATABASE syncroom;
CREATE USER syncroom WITH PASSWORD 'syncroom';
GRANT ALL PRIVILEGES ON DATABASE syncroom TO syncroom;
```

2. Настройте переменные окружения (опционально):
   - `DB_NAME` - имя базы данных (по умолчанию: `syncroom`)
   - `DB_USERNAME` - имя пользователя БД (по умолчанию: `syncroom`)
   - `DB_PASSWORD` - пароль БД (по умолчанию: `syncroom`)
   - `JWT_SECRET` - секретный ключ для JWT (по умолчанию: `your-256-bit-secret-key-change-in-production-minimum-32-characters`)
   - `JWT_ACCESS_EXPIRATION` - время жизни access токена в миллисекундах (по умолчанию: `900000` = 15 минут)
   - `JWT_REFRESH_EXPIRATION` - время жизни refresh токена в миллисекундах (по умолчанию: `2592000000` = 30 дней)

## Запуск приложения

### Используя Gradle Wrapper:

```bash
./gradlew bootRun
```

### Используя установленный Gradle:

```bash
gradle bootRun
```

Приложение запустится на порту 8080 (http://localhost:8080).

## Swagger UI (API Документация)

После запуска приложения доступна интерактивная документация API через Swagger UI:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

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
  "accessToken": "external_access_token_from_provider"
}
```

**Примечание:** В текущей реализации используется заглушка `ExternalOAuthClient`, которая возвращает фейковые данные. В production необходимо заменить на реальные API вызовы к VK/Yandex.

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

### OAuth (заглушка):
```bash
curl -X POST http://localhost:8080/api/auth/oauth \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "vk",
    "accessToken": "fake_token_123"
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

1. **OAuth клиент:** Текущая реализация `ExternalOAuthClient` является заглушкой. Для production необходимо:
   - Реализовать реальные API вызовы к VK OAuth API
   - Реализовать реальные API вызовы к Yandex OAuth API
   - Добавить обработку ошибок от внешних API

2. **JWT Secret:** В production обязательно измените `JWT_SECRET` на надёжный случайный ключ длиной не менее 32 символов.

3. **База данных:** Убедитесь, что PostgreSQL запущен и доступен перед запуском приложения.

## Разработка

Для разработки используется профиль `dev`, который активируется по умолчанию. Все настройки можно переопределить через переменные окружения.

## Лицензия

Проект создан для SyncRoom.

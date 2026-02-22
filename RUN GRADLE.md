**Переменные окружения:**

Необходимо создать файл `.env` в корне проекта для настройки переменных окружения. Пример конфигурации находится в файле `.env.example`:

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

### Требования для локального запуска:
- Java 21 или выше
- PostgreSQL 12 или выше
- Gradle 7.5+ (или используйте Gradle Wrapper)

### Настройка базы данных при локальном запуске:

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

```bash
./gradlew bootRun
```

### Используя установленный Gradle:

```bash
gradle bootRun
```

**Примечание:** При локальном запуске убедитесь, что PostgreSQL запущен и доступен.

Приложение запустится на порту 8080 (http://localhost:8080).

## Swagger UI (API Документация)

После запуска приложения доступна интерактивная документация API через Swagger UI:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

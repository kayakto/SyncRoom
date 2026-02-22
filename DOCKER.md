# Docker инструкции

## Быстрый старт

Запуск всего приложения одной командой:

```bash
docker-compose up -d
```

Приложение будет доступно по адресу:
- **API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html

## Основные команды

### Запуск
```bash
docker-compose up -d
```

### Остановка
```bash
docker-compose down
```

### Остановка с удалением данных
```bash
docker-compose down -v
```

### Пересборка и запуск
```bash
docker-compose up -d --build
```

### Просмотр логов
```bash
# Все сервисы
docker-compose logs -f

# Только приложение
docker-compose logs -f app

# Только база данных
docker-compose logs -f postgres
```

### Выполнение команд в контейнере
```bash
# В контейнере приложения
docker-compose exec app sh

# В контейнере базы данных
docker-compose exec postgres psql -U syncroom -d syncroom
```

## Настройка переменных окружения

Создайте файл `.env` в корне проекта:

```env
DB_NAME=syncroom
DB_USERNAME=syncroom
DB_PASSWORD=syncroom
JWT_SECRET=your-256-bit-secret-key-change-in-production-minimum-32-characters
JWT_ACCESS_EXPIRATION=900000
JWT_REFRESH_EXPIRATION=2592000000
APP_PORT=8080
POSTGRES_PORT=5432
```

## Структура сервисов

### PostgreSQL
- **Контейнер**: `syncroom-postgres`
- **Порт**: `5432` (по умолчанию)
- **Данные**: Сохраняются в Docker volume `postgres_data`
- **Healthcheck**: Автоматическая проверка готовности

### Spring Boot приложение
- **Контейнер**: `syncroom-app`
- **Порт**: `8080` (по умолчанию)
- **Профиль**: `docker`
- **Зависимости**: Ждёт готовности PostgreSQL перед запуском

## Troubleshooting

### Проблема: Приложение не может подключиться к базе данных

**Решение**: Убедитесь, что PostgreSQL контейнер запущен и здоров:
```bash
docker-compose ps
docker-compose logs postgres
```

### Проблема: Порт уже занят

**Решение**: Измените порты в `.env` файле или `docker-compose.yml`:
```env
APP_PORT=8081
POSTGRES_PORT=5433
```

### Проблема: Нужно пересобрать образ

**Решение**: 
```bash
docker-compose build --no-cache
docker-compose up -d
```

### Проблема: Очистка всех данных

**Решение**:
```bash
docker-compose down -v
docker-compose up -d
```

## Production рекомендации

⚠️ **Важно для production:**

1. **Измените JWT_SECRET** на надёжный случайный ключ длиной не менее 32 символов
2. **Измените пароли БД** на сложные
3. **Используйте secrets management** (Docker Secrets, Kubernetes Secrets, etc.)
4. **Настройте резервное копирование** базы данных
5. **Используйте reverse proxy** (nginx, traefik) для HTTPS
6. **Настройте мониторинг** и логирование
7. **Ограничьте ресурсы** контейнеров (CPU, память)

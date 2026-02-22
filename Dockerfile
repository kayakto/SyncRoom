# Multi-stage build для оптимизации размера образа
FROM gradle:8.10-jdk21 AS build

WORKDIR /app

# Копируем файлы конфигурации Gradle (сначала для кэширования слоев)
COPY build.gradle.kts settings.gradle.kts ./

# Копируем исходный код
COPY src ./src

# Собираем приложение (gradle уже установлен в образе)
RUN gradle clean build -x test --no-daemon

# Финальный образ
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Создаем непривилегированного пользователя
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Копируем JAR из стадии сборки
COPY --from=build /app/build/libs/*.jar app.jar

# Открываем порт
EXPOSE 8080

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]

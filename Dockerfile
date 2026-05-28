# Multi-stage build для оптимизации размера образа
FROM gradle:8.10-jdk21 AS build

WORKDIR /app

# Gradle Wrapper + конфиг (кэш слоёв)
COPY build.gradle.kts settings.gradle.kts gradlew gradlew.bat ./
COPY gradle ./gradle

RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src

RUN ./gradlew --no-daemon bootJar -x test --refresh-dependencies

# Финальный образ
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Создаем непривилегированного пользователя
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Копируем fat JAR (имя задано в build.gradle.kts — см. tasks.bootJar)
COPY --from=build /app/build/libs/syncroom-boot.jar app.jar

# Открываем порт
EXPOSE 8080

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]

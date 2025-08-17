# 1. Используем Gradle для сборки fat-jar
FROM gradle:8.7-jdk17 AS builder
WORKDIR /app
COPY . .
# Используем buildFatJar для создания fat JAR
RUN gradle clean buildFatJar --no-daemon
# Отладка: вывести содержимое /app/build/libs/ (уберите после тестирования)
RUN ls -la /app/server/build/libs/

# Этап запуска
FROM openjdk:17-jdk-slim
WORKDIR /app
# Копируем fat JAR (используйте маску, если имя точно неизвестно)
COPY --from=builder /app/server/build/libs/mindweaverserver-1.0.0-all.jar ./
# Или укажите точное имя: COPY --from=builder /app/build/libs/mindweaverserver-1.0.0-all.jar ./
ENV TG_BOT_TOKEN=changeme
ENV TG_CHAT_ID=changeme
# Запускаем fat JAR
CMD ["java", "-jar", "mindweaverserver-1.0.0-all.jar"]
# Если имя JAR отличается, замените на реальное (проверьте по ls)
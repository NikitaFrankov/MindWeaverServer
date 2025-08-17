## 1. Используем Gradle для сборки fat-jar
#FROM gradle:8.7-jdk17 AS builder
#WORKDIR /app
#COPY . .
#
## Используем buildFatJar для создания fat JAR
#RUN gradle clean buildFatJar --no-daemon
#
## Этап запуска
#FROM openjdk:17-jdk-slim
#WORKDIR /app
## Копируем fat JAR
#COPY --from=builder /app/server/build/libs/mindweaverserver-1.0.0-all.jar ./
## Переменные, определенные в .env
#ENV TG_BOT_TOKEN=changeme
#ENV TG_CHAT_ID=changeme
#ENV CHAT_GPT_API_KEY=changeme
#ENV GITHUB_OWNER_NAME=changeme
#ENV GITHUB_REPO_NAME=changeme
#ENV GITHUB_API_TOKEN=changeme
#
## Запускаем fat JAR
#CMD ["java", "-jar", "mindweaverserver-1.0.0-all.jar"]
FROM gradle:8.7-jdk17 AS builder

WORKDIR /app
COPY . .

# Устанавливаем сертификаты (если базовый образ slim, то часто нужны)
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*

# Собираем fat JAR
RUN ./gradlew clean buildFatJar --no-daemon

# Финальный образ
FROM openjdk:17-jdk-slim

WORKDIR /app
COPY --from=builder /app/server/build/libs/mindweaverserver-1.0.0-all.jar ./

ENTRYPOINT ["java", "-jar", "mindweaverserver-1.0.0-all.jar"]
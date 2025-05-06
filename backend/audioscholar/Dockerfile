FROM maven:3.9.6-eclipse-temurin-21 AS builder  # <--- RENAMED STAGE
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y \
    fontconfig \
    libfreetype6 \
    --no-install-recommends && \
    rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/target/audioscholar-*.jar app.jar # <--- UPDATED STAGE NAME

ENTRYPOINT ["java", "-Djava.awt.headless=true", "-Xmx256m", "-jar", "app.jar"]
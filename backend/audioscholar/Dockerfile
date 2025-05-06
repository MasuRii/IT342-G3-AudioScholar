FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y \
    fontconfig \
    libfreetype6 \
    # Example: Add this line for common Microsoft fonts (requires accepting EULA during build if used)
    # ttf-mscorefonts-installer \
    --no-install-recommends && \
    # Clean up apt cache to reduce image size
    rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/audioscholar-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.awt.headless=true", "-Xmx768m", "-jar", "app.jar"]

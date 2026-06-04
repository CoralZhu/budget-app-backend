# Multi-stage build for smaller image
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml ./
COPY mvnw ./
COPY .mvn ./.mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Build
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# JVM memory tuning for Render 512MB free tier
# -Xmx256m: max heap 256MB (leaves room for JVM overhead)
# -Xss256k: smaller thread stack
# -XX:+UseSerialGC: serial GC uses less memory than G1
# -XX:MaxRAMPercentage=70: cap container memory usage
ENV JAVA_OPTS="-Xmx256m -Xss256k -XX:+UseSerialGC -XX:MaxRAMPercentage=70"

# Render injects PORT env var
EXPOSE 8080

# Use SPRING_PROFILES_ACTIVE=prod to load application-prod.properties
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

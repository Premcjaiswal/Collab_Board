# Production image for CollabBoard (used by Render; works on any Docker host).
# Secrets are never baked in: they come from the host's environment variables at runtime.

# ---- build: compile the Spring Boot jar with Maven on JDK 21 ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests run locally and in review (mvn clean package); skipped here to keep the free-tier build fast.
RUN mvn -B -q -DskipTests package

# ---- run: slim Java 21 runtime as a non-root user ----
FROM eclipse-temurin:21-jre-jammy
RUN useradd --system --create-home --uid 10001 collabboard
WORKDIR /app
COPY --from=build /src/target/collabboard-1.0.0.jar app.jar
RUN mkdir -p /app/data && chown -R collabboard /app
USER collabboard
# Sized for a 512 MB instance (Render free): bounded heap/metaspace, small thread stacks, serial GC.
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:MaxMetaspaceSize=128m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
CMD ["java", "-jar", "/app/app.jar"]

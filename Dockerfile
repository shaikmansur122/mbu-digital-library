# ---- build stage: compiles the project with Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- run stage: small Java runtime only ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app && mkdir -p /app/uploads && chown -R app /app
COPY --from=build /build/target/digital-library-0.0.1-SNAPSHOT.jar app.jar
USER app

# Free hosts give very little memory (512 MB) and CPU, so keep the JVM small and quick to start.
ENV JAVA_TOOL_OPTIONS="-Xmx300m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
ENV THYMELEAF_CACHE=true COOKIE_SECURE=true

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# Build Stage
FROM gradle:8-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN ./gradlew :server:installDist --no-daemon

# Run Stage
FROM eclipse-temurin:17-jre
EXPOSE 8080
RUN mkdir /app
COPY --from=build /home/gradle/src/server/build/install/server/ /app/
WORKDIR /app/bin
CMD ["./server"]

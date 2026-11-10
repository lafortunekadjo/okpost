FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache fontconfig ttf-dejavu && addgroup -S okpost && adduser -S okpost -G okpost && mkdir -p /data/fichiers && chown -R okpost:okpost /data
USER okpost
WORKDIR /app
COPY --from=build /src/target/okpost.jar app.jar
ENV JAVA_OPTS="-Xmx512m -XX:+UseSerialGC" FILES_DIR=/data/fichiers
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

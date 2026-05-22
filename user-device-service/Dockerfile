# Build from repository root: docker compose build user-device-service
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY certs/ca.crt user-device-service/src/main/resources/ca.crt
COPY user-device-service/pom.xml user-device-service/
COPY user-device-service/src user-device-service/src
WORKDIR /build/user-device-service
RUN mvn -q -DskipTests package \
 && cp target/user-device-service-*.jar /build/app.jar

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /build/app.jar /app/app.jar
COPY docker/config/spring/application-docker.yml /app/config/application-docker.yml
RUN chown -R spring:spring /app
USER spring
EXPOSE 8088
ENV SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/config/application-docker.yml
ENTRYPOINT ["java","-jar","/app/app.jar"]

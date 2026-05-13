# Build from repository root
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY data-processing-service/pom.xml .
COPY data-processing-service/src ./src
RUN mvn -q -DskipTests package

FROM flink:1.18.1-java17
USER root
RUN apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/target/data-processing-service-1.0-SNAPSHOT.jar /opt/flink/usrlib/job.jar
COPY docker/flink/submit-job.sh /submit-job.sh
RUN sed -i 's/\r$//' /submit-job.sh && chmod +x /submit-job.sh && chown flink:flink /opt/flink/usrlib/job.jar
USER flink
ENTRYPOINT ["/bin/bash", "/submit-job.sh"]

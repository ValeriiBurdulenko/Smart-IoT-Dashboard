#!/bin/bash
set -euo pipefail

JOBMANAGER="${JOBMANAGER:-flink-jobmanager}"
JOBMANAGER_PORT="${JOBMANAGER_PORT:-8081}"
JAR="/opt/flink/usrlib/job.jar"
MAIN="data_processing.com.flink.DataProcessingJob"

echo "Waiting for Flink REST at http://${JOBMANAGER}:${JOBMANAGER_PORT} ..."
for _ in $(seq 1 90); do
  if curl -sf "http://${JOBMANAGER}:${JOBMANAGER_PORT}/overview" >/dev/null; then
    break
  fi
  sleep 2
done

JAAS='org.apache.kafka.common.security.plain.PlainLoginModule required username="device_processing_service_user" password="flink-secret";'

exec /opt/flink/bin/flink run -m "${JOBMANAGER}:${JOBMANAGER_PORT}" -d \
  -c "${MAIN}" "${JAR}" \
  --kafka.brokers kafka:29092 \
  --kafka.topic.raw iot-telemetry-raw \
  --kafka.topic.processed iot-telemetry-processed \
  --kafka.topic.deletions iot-device-deletions \
  --kafka.topic.dlq iot-telemetry-dlq \
  --kafka.topic.alerts iot-telemetry-alerts \
  --kafka.group.id.telemetry flink-group \
  --kafka.group.id.deletions flink-group \
  --security.protocol SASL_PLAINTEXT \
  --sasl.mechanism PLAIN \
  --sasl.jaas.config "${JAAS}" \
  --influxdb.url http://influxdb:8086 \
  --influxdb.token my-super-secret-auth-token \
  --influxdb.org smart-iot-dashboard \
  --influxdb.bucket iot-data \
  --checkpoint.storage.path file:///tmp/flink-checkpoints

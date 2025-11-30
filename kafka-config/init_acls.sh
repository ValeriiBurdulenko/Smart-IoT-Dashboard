#!/bin/bash
set -e

echo "🚀 Starting Kafka Setup..."

# Creating a temporary configuration for connecting the administrator
cat > /tmp/admin.properties <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="admin" password="admin-secret";
EOF

# Variable for command reduction
KAFKA_ACL="kafka-acls --bootstrap-server kafka:29092 --command-config /tmp/admin.properties"
KAFKA_TOPIC="kafka-topics --bootstrap-server kafka:29092 --command-config /tmp/admin.properties"

echo "📦 Creating Topics (Idempotent)..."
$KAFKA_TOPIC --create --if-not-exists --topic iot-telemetry-raw --partitions 3 --replication-factor 1
$KAFKA_TOPIC --create --if-not-exists --topic iot-telemetry-processed --partitions 3 --replication-factor 1
$KAFKA_TOPIC --create --if-not-exists --topic iot-device-deletions --partitions 1 --replication-factor 1 # Reihenfolge wichtog, deswegen 1 fur logs
$KAFKA_TOPIC --create --if-not-exists --topic iot-commands --partitions 1 --replication-factor 1

echo "📝 Applying ACLs..."

# --- 1. BRIDGE USER (Python Bridge) ---
# Can ONLY write to raw telemetry
$KAFKA_ACL --add --allow-principal User:mqtt_kafka_bridge_user --operation Write --topic iot-telemetry-raw

# --- 2. USER DEVICE SERVICE (Java Backend) ---
# Can write in the deletion and commands thread
$KAFKA_ACL --add --allow-principal User:user_device_service_user --operation Write --topic iot-device-deletions
$KAFKA_ACL --add --allow-principal User:user_device_service_user --operation Write --topic iot-commands
# (Optional) Can read processed data for the UI (if needed)
$KAFKA_ACL --add --allow-principal User:user_device_service_user --operation Read --topic iot-telemetry-processed --group backend-group

# --- 3. FLINK USER (Device Data Processing) ---
# Reads raw data and deletions
$KAFKA_ACL --add --allow-principal User:device_processing_service_user --operation Read --topic iot-telemetry-raw --group flink-group
$KAFKA_ACL --add --allow-principal User:device_processing_service_user --operation Read --topic iot-device-deletions --group flink-group

# Writes processed data and commands
$KAFKA_ACL --add --allow-principal User:device_processing_service_user --operation Write --topic iot-telemetry-processed
$KAFKA_ACL --add --allow-principal User:device_processing_service_user --operation Write --topic iot-commands

echo "✅ Setup Complete!"
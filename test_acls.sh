#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "🛠️  Setting up test configurations..."

# 1. Create config for BRIDGE
docker exec kafka bash -c 'cat > /tmp/bridge.properties <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="mqtt_kafka_bridge_user" password="bridge-secret";
EOF'

# 2. Create config for BACKEND
docker exec kafka bash -c 'cat > /tmp/backend.properties <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="user_device_service_user" password="service-secret";
EOF'

# 3. Create config for FLINK
docker exec kafka bash -c 'cat > /tmp/flink.properties <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="device_processing_service_user" password="flink-secret";
EOF'

# 4. Create config for HACKER
docker exec kafka bash -c 'cat > /tmp/hacker.properties <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="user_device_service_user" password="WRONG_PASSWORD";
EOF'

echo "✅ Configs created."
echo "----------------------------------------------------------------"

# Function to check writing (Producer)
test_write() {
    user=$1
    topic=$2
    expect_success=$3
    
    echo -n "📝 User [$user] writing to [$topic] (Expect: $expect_success)... "
    
    # Attempt to write message "test"
    # 2>&1 redirects errors to stdout so we can read them
    OUTPUT=$(echo "test" | docker exec -i kafka timeout 5s kafka-console-producer --bootstrap-server kafka:29092 --producer.config /tmp/$user.properties --topic $topic 2>&1)
    
    # Check for authorization errors
    if [[ $OUTPUT == *"AuthorizationException"* ]] || [[ $OUTPUT == *"SaslAuthenticationException"* ]] || [[ $OUTPUT == *"Authentication failed"* ]]; then
        if [ "$expect_success" == "FAIL" ]; then
            echo -e "${GREEN}PASS (Blocked)${NC}"
        else
            echo -e "${RED}FAIL (Error! Access denied, but should be allowed)${NC}"
            # echo "   Log: $OUTPUT"
        fi
    else
        if [ "$expect_success" == "PASS" ]; then
            echo -e "${GREEN}PASS (Success)${NC}"
        else
             if [[ $OUTPUT == *"ERROR"* ]] || [[ $OUTPUT == *"WARN"* ]]; then
                echo -e "${GREEN}PASS (Silent error)${NC}"
             else
                echo -e "${RED}FAIL (Access allowed or client silent)${NC}"
             fi
        fi
    fi
}

# Function to check reading (Consumer)
test_read() {
    user=$1
    topic=$2
    expect_success=$3
    group=$4
    
    echo -n "👀 User [$user] reading from [$topic] (Expect: $expect_success)... "
    
    CMD="kafka-console-consumer --bootstrap-server kafka:29092 --consumer.config /tmp/$user.properties --topic $topic --max-messages 1"
    if [ ! -z "$group" ]; then
        CMD="$CMD --group $group"
    fi

    OUTPUT=$(docker exec -i kafka timeout 6s $CMD 2>&1)

    if [[ $OUTPUT == *"AuthorizationException"* ]] || [[ $OUTPUT == *"SaslAuthenticationException"* ]] || [[ $OUTPUT == *"Authentication failed"* ]]; then
         if [ "$expect_success" == "FAIL" ]; then
            echo -e "${GREEN}PASS (Blocked)${NC}"
        else
            echo -e "${RED}FAIL (Error! Access denied, but should be allowed)${NC}"
            # echo "   Log: $OUTPUT"
        fi
    else
        if [ "$expect_success" == "PASS" ]; then
            echo -e "${GREEN}PASS (Connection successful)${NC}"
        else
            echo -e "${RED}FAIL (Error! Access allowed, but should be denied)${NC}"
        fi
    fi
}

# --- RUN TESTS ---

echo ""
echo "=== 🌉 TESTS BRIDGE (mqtt-kafka-bridge) ==="
test_write "bridge" "iot-telemetry-raw" "PASS"
test_write "bridge" "iot-commands" "FAIL"
test_write "bridge" "iot-device-deletions" "FAIL"
test_read  "bridge" "iot-telemetry-raw" "FAIL" # Bridge only writes

echo ""
echo "=== ☕ TESTS BACKEND (user-device-service) ==="
test_write "backend" "iot-commands" "PASS"
test_write "backend" "iot-device-deletions" "PASS"
test_write "backend" "iot-telemetry-raw" "FAIL" # Backend does not send raw data
test_write "backend" "iot-telemetry-processed" "FAIL"
test_read  "backend" "iot-commands" "FAIL"
test_read  "backend" "iot-telemetry-processed" "PASS" "backend-group"

echo ""
echo "=== 🌊 TESTS FLINK (data-processing) ==="
test_write "flink" "iot-telemetry-processed" "PASS"
test_read  "flink" "iot-telemetry-raw" "PASS" "flink-group"
test_write "flink" "iot-device-deletions" "FAIL"

echo ""
echo "=== 🏴‍☠️ TESTS HACKER (Incorrect Password) ==="
test_write "hacker" "iot-commands" "FAIL"
test_read  "hacker" "iot-telemetry-raw" "FAIL"

echo "----------------------------------------------------------------"
echo "🏁 Tests completed."
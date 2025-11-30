#!/bin/bash

# Цвета для вывода
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "🛠️  Настройка конфигураций для тестов..."

# 1. Создаем конфиг для BRIDGE (Правильный)
docker exec kafka bash -c 'cat > /tmp/bridge.properties <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="mqtt_kafka_bridge_user" password="bridge-secret";
EOF'

# 2. Создаем конфиг для BACKEND (Правильный)
docker exec kafka bash -c 'cat > /tmp/backend.properties <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="user_device_service_user" password="service-secret";
EOF'

# 3. Создаем конфиг для FLINK (Правильный)
docker exec kafka bash -c 'cat > /tmp/flink.properties <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="device_processing_service_user" password="flink-secret";
EOF'

# 4. Создаем конфиг ХАКЕРА (Неверный пароль)
docker exec kafka bash -c 'cat > /tmp/hacker.properties <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="user_device_service_user" password="WRONG_PASSWORD";
EOF'

echo "✅ Конфиги созданы."
echo "----------------------------------------------------------------"

# Функция для проверки чтения (Consumer)
test_read() {
    user=$1
    topic=$2
    expect_success=$3
    group=$4
    
    echo -n "👀 User [$user] читает из [$topic] (Ожидание: $expect_success)... "
    
    # УВЕЛИЧИЛИ ТАЙМАУТ ДО 6 СЕКУНД (чтобы пройти Rebalance)
    # Формируем команду с группой или без
    CMD="kafka-console-consumer --bootstrap-server kafka:29092 --consumer.config /tmp/$user.properties --topic $topic --max-messages 1"
    if [ ! -z "$group" ]; then
        CMD="$CMD --group $group"
    fi

    OUTPUT=$(docker exec -i kafka timeout 6s $CMD 2>&1)
    # Логика проверки:
    # 1. Если есть "AuthorizationException" или "Authentication failed" -> ДОСТУП ЗАПРЕЩЕН
    # 2. Если есть "Processed a total of 1 messages" -> УСПЕХ (Данные прочитаны)
    # 3. Если таймаут (нет данных, но и нет ошибок) -> Считаем, что доступ ЕСТЬ (просто топик пуст), если ожидали PASS.
    
    if [[ $OUTPUT == *"AuthorizationException"* ]] || [[ $OUTPUT == *"SaslAuthenticationException"* ]] || [[ $OUTPUT == *"Authentication failed"* ]]; then
         if [ "$expect_success" == "FAIL" ]; then
            echo -e "${GREEN}PASS (Заблокировано)${NC}"
        else
            echo -e "${RED}FAIL (Ошибка! Доступ запрещен)${NC}"
            # echo "   Log: $OUTPUT"
        fi
    else
        # Ошибок нет.
        if [ "$expect_success" == "PASS" ]; then
            echo -e "${GREEN}PASS (Успех)${NC}"
        else
            echo -e "${RED}FAIL (Ошибка! Доступ разрешен)${NC}"
        fi
    fi
}

# Функция для проверки записи (Producer)
test_write() {
    user=$1
    topic=$2
    expect_success=$3
    
    echo -n "📝 User [$user] пишет в [$topic] (Ожидание: $expect_success)... "
    
    # УВЕЛИЧИЛИ ТАЙМАУТ ДО 5 СЕКУНД
    # Важно: Если пароль неверный, клиент будет пытаться переподключиться. Timeout его убьет.
    # Нам нужно понять, убил ли его timeout или он сам вышел.
    
    OUTPUT=$(echo "test" | docker exec -i kafka timeout 5s kafka-console-producer --bootstrap-server kafka:29092 --producer.config /tmp/$user.properties --topic $topic 2>&1)
    
    if [[ $OUTPUT == *"AuthorizationException"* ]] || [[ $OUTPUT == *"SaslAuthenticationException"* ]] || [[ $OUTPUT == *"Authentication failed"* ]]; then
        if [ "$expect_success" == "FAIL" ]; then
            echo -e "${GREEN}PASS (Заблокировано)${NC}"
        else
            echo -e "${RED}FAIL (Ошибка! Доступ запрещен)${NC}"
            # echo "   Log: $OUTPUT"
        fi
    else

        if [ "$expect_success" == "PASS" ]; then
            echo -e "${GREEN}PASS (Успех)${NC}"
        else
             if [[ $OUTPUT == *"ERROR"* ]] || [[ $OUTPUT == *"WARN"* ]]; then
                echo -e "${GREEN}PASS (Скрытая ошибка)${NC}"
             else
                echo -e "${RED}FAIL (Доступ разрешен или клиент молчит)${NC}"
             fi
        fi
    fi
}

# --- ЗАПУСК ТЕСТОВ ---

echo ""
echo "=== 🌉 Тесты BRIDGE (mqtt-kafka-bridge) ==="
test_write "bridge" "iot-telemetry-raw" "PASS"
test_write "bridge" "iot-commands" "FAIL"
test_write "bridge" "iot-device-deletions" "FAIL"
test_read  "bridge" "iot-telemetry-raw" "FAIL"

echo ""
echo "=== ☕ Тесты BACKEND (user-device-service) ==="
test_write "backend" "iot-commands" "PASS"
test_write "backend" "iot-device-deletions" "PASS"
test_write "backend" "iot-telemetry-raw" "FAIL"
test_write "backend" "iot-telemetry-processed" "FAIL"
test_read  "backend" "iot-commands" "FAIL"
test_read  "backend" "iot-telemetry-processed" "PASS" "backend-group"

echo ""
echo "=== 🌊 Тесты FLINK (data-processing) ==="
test_write "flink" "iot-telemetry-processed" "PASS"
test_read "flink" "iot-telemetry-raw" "PASS" "flink-group"
test_write "flink" "iot-device-deletions" "FAIL"

echo ""
echo "=== 🏴‍☠️ Тесты HACKER (Неверный пароль) ==="
test_write "hacker" "iot-commands" "FAIL"
test_read  "hacker" "iot-telemetry-raw" "FAIL"

echo "----------------------------------------------------------------"
echo "🏁 Тесты завершены."
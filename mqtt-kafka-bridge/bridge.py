import paho.mqtt.client as mqtt
from kafka import KafkaProducer, errors as KafkaErrors
import json
import os
import ssl
import sys
from dotenv import load_dotenv

# --- 1. Configuration ---
load_dotenv() # Load variables from .env file

MQTT_BROKER_HOST = os.getenv("MQTT_BROKER_HOST", "localhost")
MQTT_USE_TLS = os.getenv("MQTT_USE_TLS", "false").lower() == 'true'
MQTT_PORT_TLS = int(os.getenv("MQTT_PORT_TLS", "8883"))
MQTT_PORT = int(os.getenv("MQTT_PORT", "1883"))
MQTT_TOPIC_IN = os.getenv("MQTT_TOPIC_IN", "iot/telemetry/ingress")
MQTT_CLIENT_ID_BASE = os.getenv("MQTT_CLIENT_ID_BASE", "mqtt-kafka-bridge")
MQTT_CLIENT_ID = f"{MQTT_CLIENT_ID_BASE}-{os.urandom(4).hex()}"
MQTT_USERNAME = os.getenv("MQTT_USERNAME") # Get username from .env
MQTT_PASSWORD = os.getenv("MQTT_PASSWORD") # Get password from .env
MQTT_CA_CERT = os.getenv("MQTT_CA_CERT", "ca.crt")

KAFKA_BROKER = os.getenv("KAFKA_BROKER", "localhost:9092")
KAFKA_TOPIC_OUT = os.getenv("KAFKA_TOPIC_OUT", "iot-telemetry-raw")

print("--- 🚀 MQTT-Kafka Bridge Configuration ---")
print(f"MQTT Broker: {MQTT_BROKER_HOST} (TLS: {MQTT_USE_TLS})")
print(f"MQTT Client ID: {MQTT_CLIENT_ID}")
print(f"MQTT Username: {MQTT_USERNAME}")
print(f"MQTT Topic (In): {MQTT_TOPIC_IN}")
print(f"Kafka Broker: {KAFKA_BROKER}")
print(f"Kafka Topic (Out): {KAFKA_TOPIC_OUT}")
print("-----------------------------------------")


# --- 2. Kafka Producer Setup ---
try:
    print(f"Connecting to Kafka at {KAFKA_BROKER}...")
    producer = KafkaProducer(
        bootstrap_servers=KAFKA_BROKER,
        value_serializer=lambda v: json.dumps(v).encode('utf-8'),
        retries=5, # Add retries for resilience
        acks='all' # Wait for all replicas (stronger guarantee)
    )
    print("✅ Kafka Producer connected.")
except KafkaErrors.NoBrokersAvailable as e:
    print(f"❌ Critical: Kafka broker at {KAFKA_BROKER} is not available. {e}")
    sys.exit(1)
except Exception as e:
    print(f"❌ Failed to connect to Kafka: {e}")
    sys.exit(1)

# --- 3. MQTT Callbacks ---
def on_connect(client, userdata, flags, rc):
    """Callback for MQTT connection."""
    if rc == 0:
        print(f"✅ MQTT Connected Successfully to {MQTT_BROKER_HOST}.")
        # Subscribe only after a successful connection
        client.subscribe(MQTT_TOPIC_IN, qos=1) # Use QoS 1
        print(f"👂 Subscribed to MQTT topic: {MQTT_TOPIC_IN}")
    else:
        error_msg = {
            1: "Connection refused - incorrect protocol version",
            2: "Connection refused - invalid client identifier",
            3: "Connection refused - server unavailable",
            4: "Connection refused - bad username or password",
            5: "Connection refused - not authorised (ACL)",
        }.get(rc, f"Unknown error code: {rc}")
        print(f"❌ MQTT Connection Failed: {error_msg}")
        # TODO In a real app, you might want to exit or implement a retry loop
        # sys.exit(1)

def on_message(client, userdata, msg):
    """Callback for processing incoming MQTT messages."""
    try:
        payload_str = msg.payload.decode('utf-8')
        print(f"📡 MQTT message received from '{msg.topic}'")
        message_json = json.loads(payload_str)

        # Use deviceId from message as Kafka key for partitioning
        key = message_json.get("deviceId", "").encode('utf-8')

        # Send to Kafka
        producer.send(KAFKA_TOPIC_OUT, value=message_json, key=key)

        # producer.flush() # Uncomment for immediate send (impacts performance)
        print(f"➡️ Message forwarded to Kafka topic '{KAFKA_TOPIC_OUT}' (Key: {key.decode()})")
    except json.JSONDecodeError:
        print(f"⚠️ Received non-JSON message, skipping: {msg.payload}")
    except Exception as e:
        print(f"🚨 Error processing/forwarding message: {e}")

def on_disconnect(client, userdata, rc):
    """Callback for MQTT disconnection."""
    if rc != 0:
        print(f"🔌 Unexpected MQTT Disconnection (Code: {rc}). Will attempt to reconnect...")
    else:
        print("🔌 MQTT Disconnected gracefully.")

# --- 4. MQTT Client Setup ---
mqtt_client = mqtt.Client(client_id=MQTT_CLIENT_ID, protocol=mqtt.MQTTv311)
mqtt_client.on_connect = on_connect
mqtt_client.on_message = on_message
mqtt_client.on_disconnect = on_disconnect

# --- Authentication ---
if MQTT_USERNAME and MQTT_PASSWORD:
    mqtt_client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)
    print("🔑 MQTT authentication configured.")
else:
    print("⚠️ MQTT authentication NOT configured. This will likely fail.")

# --- TLS ---
port_to_use = MQTT_PORT
if MQTT_USE_TLS:
    port_to_use = MQTT_PORT_TLS
    if not os.path.exists(MQTT_CA_CERT):
        print(f"❌ TLS Error: CA certificate file not found at '{MQTT_CA_CERT}'")
        sys.exit(1)
    try:
        mqtt_client.tls_set(
            ca_certs=MQTT_CA_CERT,
            cert_reqs=ssl.CERT_REQUIRED,
            tls_version=ssl.PROTOCOL_TLSv1_2
        )
        print("🔐 MQTT TLS configured.")
    except Exception as e:
        print(f"❌ Error configuring MQTT TLS: {e}")
        sys.exit(1)
else:
    print("ℹ️ MQTT TLS is NOT enabled.")


# --- 5. Main Execution ---
try:
    print(f"🔌 Connecting to MQTT Broker at {MQTT_BROKER_HOST}:{port_to_use}...")
    # Enable automatic reconnect logic built into Paho client
    mqtt_client.reconnect_delay_set(min_delay=1, max_delay=120)
    mqtt_client.connect(MQTT_BROKER_HOST, port_to_use, 60)
    mqtt_client.loop_forever() # Blocks and handles reconnects automatically
except KeyboardInterrupt:
    print("\n🛑 Bridge stopped by user.")
except Exception as e:
    print(f"🚨 Critical connection/runtime error: {e}")
finally:
    if producer:
        producer.close()
        print("🔌 Kafka connection closed.")
    if mqtt_client:
        mqtt_client.disconnect()
        print("🔌 MQTT connection closed.")
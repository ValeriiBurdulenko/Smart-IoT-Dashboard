import paho.mqtt.client as mqtt
from confluent_kafka import Producer, KafkaException
import os
import ssl
import sys
import logging
import time
import signal
import threading
from datetime import datetime
from flask import Flask, jsonify
from dotenv import load_dotenv

# --- 1. Configuration ---
load_dotenv()

# Logging setup
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] [%(name)s] %(message)s',
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler('mqtt-kafka-bridge.log')
    ]
)
logger = logging.getLogger('Bridge')

# MQTT Config
MQTT_BROKER_HOST = os.getenv("MQTT_BROKER_HOST", "localhost")
MQTT_USE_TLS = os.getenv("MQTT_USE_TLS", "false").lower() == 'true'
MQTT_PORT_TLS = int(os.getenv("MQTT_PORT_TLS", "8883"))
MQTT_PORT = int(os.getenv("MQTT_PORT", "1883"))
MQTT_TOPIC_IN = os.getenv("MQTT_TOPIC_IN", "iot/telemetry/+")
MQTT_CLIENT_ID = f"mqtt-kafka-bridge-{os.urandom(4).hex()}"
MQTT_USERNAME = os.getenv("MQTT_USERNAME")
MQTT_PASSWORD = os.getenv("MQTT_PASSWORD")
MQTT_CA_CERT = os.getenv("MQTT_CA_CERT", "ca.crt")

# Kafka Config
KAFKA_BROKER = os.getenv("KAFKA_BROKER", "localhost:9092")
KAFKA_TOPIC_OUT = os.getenv("KAFKA_TOPIC_OUT", "iot-telemetry-raw")
KAFKA_SASL_USERNAME = os.getenv("KAFKA_SASL_USERNAME", "bridge_user")
KAFKA_SASL_PASSWORD = os.getenv("KAFKA_SASL_PASSWORD")
KAFKA_SECURITY_PROTOCOL = os.getenv("KAFKA_SECURITY_PROTOCOL", "SASL_PLAINTEXT")
KAFKA_SASL_MECHANISM = os.getenv("KAFKA_SASL_MECHANISM", "PLAIN")

# Performance Config
MAX_QUEUE_SIZE = int(os.getenv("MAX_QUEUE_SIZE", "10000"))
WARNING_THRESHOLD = int(MAX_QUEUE_SIZE * 0.8)
HEALTH_PORT = int(os.getenv("HEALTH_PORT", "8080"))

logger.info("=" * 50)
logger.info("MQTT-Kafka Bridge Starting")
logger.info(f"MQTT: {MQTT_BROKER_HOST}:{MQTT_PORT_TLS if MQTT_USE_TLS else MQTT_PORT} (TLS: {MQTT_USE_TLS})")
logger.info(f"Kafka: {KAFKA_BROKER} (User: {KAFKA_SASL_USERNAME})")
logger.info(f"Health Check: http://0.0.0.0:{HEALTH_PORT}/health")
logger.info("=" * 50)

# --- 2. Global State & Metrics ---
class Metrics:
    def __init__(self):
        self.messages_received = 0
        self.messages_sent = 0
        self.messages_dropped = 0
        self.messages_failed = 0
        self.invalid_topics = 0
        self.kafka_errors = 0
        self.mqtt_connected = False
        self.last_message_time = None
        self.start_time = time.time()
        self.devices_seen = set()

    def uptime(self):
        return time.time() - self.start_time

metrics = Metrics()
shutdown_event = threading.Event()
producer = None
mqtt_client = None


# --- 4. Kafka Producer Setup ---
kafka_conf = {
    'bootstrap.servers': KAFKA_BROKER,
    'client.id': MQTT_CLIENT_ID,
    'security.protocol': KAFKA_SECURITY_PROTOCOL,
    'sasl.mechanism': KAFKA_SASL_MECHANISM,
    'sasl.username': KAFKA_SASL_USERNAME,
    'sasl.password': KAFKA_SASL_PASSWORD,
    'acks': 'all',
    'retries': 3,
    'queue.buffering.max.messages': MAX_QUEUE_SIZE,
    'queue.buffering.max.kbytes': 512000,  # 500MB
    'compression.type': 'snappy',
    'linger.ms': 20,
}

try:
    producer = Producer(kafka_conf)
    logger.info("Kafka Producer initialized")
except Exception as e:
    logger.error(f"Failed to initialize Kafka Producer: {e}")
    sys.exit(1)

def delivery_report(err, msg):
    """Callback from Kafka thread"""
    if err is not None:
        # Log error but don't crash
        logger.error(f"Delivery failed for {msg.key()}: {err}")
        metrics.messages_failed += 1
        metrics.kafka_errors += 1
    else:
        metrics.messages_sent += 1
        if metrics.messages_sent % 100 == 0:
            logger.info(f"Delivered {metrics.messages_sent} messages (queue: {len(producer)})")

# --- 5. Background Kafka Polling ---
def kafka_poller():
    logger.info("Kafka polling thread started")
    while not shutdown_event.is_set():
        try:
            producer.poll(0.1)
        except Exception as e:
            logger.error(f"Polling error: {e}")
    logger.info("Kafka polling thread stopped")

polling_thread = threading.Thread(target=kafka_poller, daemon=True)
polling_thread.start()

# --- 6. MQTT Callbacks (CRITICAL SECTION) ---
def on_connect(client, userdata, flags, rc, properties=None):
    if rc == 0:
        logger.info(f"MQTT Connected to {MQTT_BROKER_HOST}")
        metrics.mqtt_connected = True
        client.subscribe(MQTT_TOPIC_IN, qos=1)
    else:
        logger.error(f"MQTT Connect Failed. Code: {rc}")
        metrics.mqtt_connected = False

def on_message(client, userdata, msg):
    """
    NON-BLOCKING Handler.
    Must execute extremely fast to avoid MQTT disconnection.
    """
    try:
        metrics.messages_received += 1
        metrics.last_message_time = time.time()

        # 1. DecoValidate
        topic_parts = msg.topic.split('/')
        if len(topic_parts) == 3 and topic_parts[0] == 'iot' and topic_parts[1] == 'telemetry':
            device_id = topic_parts[2]
        else:
            metrics.invalid_topics += 1
            return

        # 2. Backpressure Check (Non-blocking)
        queue_len = len(producer)
        if queue_len >= MAX_QUEUE_SIZE:
            logger.error(f"DROPPED: device={device_id}, queue={queue_len}/{MAX_QUEUE_SIZE}")
            metrics.messages_dropped += 1
            return

        if queue_len > WARNING_THRESHOLD and metrics.messages_received % 100 == 0:
            logger.warning(f"Kafka queue high: {queue_len}/{MAX_QUEUE_SIZE}")

        # 3. Produce (Async push to buffer)
        try:
            producer.produce(
                KAFKA_TOPIC_OUT,
                key=device_id.encode('utf-8'),
                value=msg.payload,
                callback=delivery_report
            )
        except BufferError:
            metrics.messages_dropped += 1
            logger.error("Buffer full caught in produce")
        except KafkaException as e:
            metrics.kafka_errors += 1
            logger.error(f"Kafka produce error: {e}")

    except Exception as e:
        logger.error(f"Unexpected error in on_message: {e}", exc_info=True)
        metrics.messages_failed += 1

def on_disconnect(client, userdata, disconnect_flags, rc, properties=None):
    metrics.mqtt_connected = False
    if rc != 0:
        logger.warning(f"Unexpected MQTT Disconnection (rc={rc})")
    else:
        logger.info("MQTT Disconnected gracefully")

# --- 7. MQTT Client Setup ---
mqtt_client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=MQTT_CLIENT_ID)
mqtt_client.on_connect = on_connect
mqtt_client.on_message = on_message
mqtt_client.on_disconnect = on_disconnect

if MQTT_USERNAME and MQTT_PASSWORD:
    mqtt_client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)

port_to_use = MQTT_PORT
if MQTT_USE_TLS:
    port_to_use = MQTT_PORT_TLS
    if not os.path.exists(MQTT_CA_CERT):
        logger.error(f"TLS Error: CA cert not found at '{MQTT_CA_CERT}'")
        sys.exit(1)
    mqtt_client.tls_set(ca_certs=MQTT_CA_CERT, cert_reqs=ssl.CERT_REQUIRED, tls_version=ssl.PROTOCOL_TLSv1_2)
    logger.info("TLS enabled")

# --- 8. Health Server ---
app = Flask(__name__)
log = logging.getLogger('werkzeug')
log.setLevel(logging.ERROR)

@app.route('/health')
def health_check():
    """Kubernetes-style health check"""
    healthy = (
            metrics.mqtt_connected and
            metrics.last_message_time is not None and
            (time.time() - metrics.last_message_time) < 120  # 2 min timeout
    )

    status_code = 200 if healthy else 503
    return jsonify({
        'status': 'healthy' if healthy else 'unhealthy',
        'mqtt_connected': metrics.mqtt_connected,
        'uptime_seconds': round(metrics.uptime(), 2),
        'last_message_ago': round(time.time() - metrics.last_message_time, 2) if metrics.last_message_time else None
    }), status_code

@app.route('/metrics')
def prometheus_metrics():
    """Prometheus-compatible metrics endpoint"""
    return f"""# HELP mqtt_kafka_messages_received_total Total messages received from MQTT
# TYPE mqtt_kafka_messages_received_total counter
mqtt_kafka_messages_received_total {metrics.messages_received}

# HELP mqtt_kafka_messages_sent_total Total messages sent to Kafka
# TYPE mqtt_kafka_messages_sent_total counter
mqtt_kafka_messages_sent_total {metrics.messages_sent}

# HELP mqtt_kafka_messages_failed_total Total failed messages
# TYPE mqtt_kafka_messages_failed_total counter
mqtt_kafka_messages_failed_total {metrics.messages_failed}

# HELP mqtt_kafka_validation_errors_total Schema validation errors
# TYPE mqtt_kafka_validation_errors_total counter
mqtt_kafka_validation_errors_total {metrics.invalid_topics}

# HELP mqtt_kafka_kafka_errors_total Kafka producer errors
# TYPE mqtt_kafka_kafka_errors_total counter
mqtt_kafka_kafka_errors_total {metrics.kafka_errors}

# HELP mqtt_kafka_mqtt_connected MQTT connection status
# TYPE mqtt_kafka_mqtt_connected gauge
mqtt_kafka_mqtt_connected {1 if metrics.mqtt_connected else 0}

# HELP mqtt_kafka_queue_size Current Kafka producer queue size
# TYPE mqtt_kafka_queue_size gauge
mqtt_kafka_queue_size {len(producer)}

# HELP mqtt_kafka_uptime_seconds Bridge uptime in seconds
# TYPE mqtt_kafka_uptime_seconds counter
mqtt_kafka_uptime_seconds {metrics.uptime()}
""", 200, {'Content-Type': 'text/plain; charset=utf-8'}

@app.route('/stats')
def stats():
    """Human-readable statistics"""
    return jsonify({
        'received': metrics.messages_received,
        'sent': metrics.messages_sent,
        'failed': metrics.messages_failed,
        'validation_errors': metrics.invalid_topics,
        'kafka_errors': metrics.kafka_errors,
        'success_rate': round(metrics.messages_sent / max(metrics.messages_received, 1) * 100, 2),
        'queue_size': len(producer),
        'uptime': f"{metrics.uptime():.0f}s",
        'mqtt_connected': metrics.mqtt_connected
    })

@app.route('/devices')
def devices():
    """List all seen device IDs"""
    return jsonify({
        'count': len(metrics.devices_seen),
        'devices': list(metrics.devices_seen)
    })

def run_health_server():
    """Run Flask health server in background"""
    logger.info(f"Health server starting on port {HEALTH_PORT}")
    app.run(host='0.0.0.0', port=HEALTH_PORT, threaded=True)

health_thread = threading.Thread(target=run_health_server, daemon=True)
health_thread.start()

# --- 9. Signal Handlers for Graceful Shutdown ---
def signal_handler(sig, frame):
    """Handle shutdown signals"""
    logger.info(f"Shutdown signal received ({sig})")
    shutdown_event.set()

signal.signal(signal.SIGINT, signal_handler)
signal.signal(signal.SIGTERM, signal_handler)

# --- 10. MQTT Connection with Retry ---
def connect_mqtt_with_retry(max_retries=5):
    """Connect to MQTT with exponential backoff"""
    for attempt in range(max_retries):
        try:
            logger.info(f"Connecting to MQTT (attempt {attempt + 1}/{max_retries})")
            mqtt_client.connect(MQTT_BROKER_HOST, port_to_use, 60)
            return True
        except Exception as e:
            logger.error(f"Connection failed: {e}")
            if attempt < max_retries - 1:
                wait_time = 2 ** attempt
                logger.info(f"Retrying in {wait_time}s...")
                time.sleep(wait_time)
    return False

# --- 11. Main Execution ---
try:
    # Connect with retry
    if not connect_mqtt_with_retry():
        logger.error("Failed to connect to MQTT after retries")
        sys.exit(1)

    # Start non-blocking MQTT loop
    mqtt_client.loop_start()
    logger.info("Bridge is running. Press Ctrl+C to stop.")

    # Main thread just waits for shutdown signal
    while not shutdown_event.is_set():
        time.sleep(1)

        # Periodic stats log
        if int(time.time()) % 60 == 0:
            logger.info(
                f"Stats: RX={metrics.messages_received} "
                f"TX={metrics.messages_sent} "
                f"Failed={metrics.messages_failed} "
                f"Queue={len(producer)}"
            )

except KeyboardInterrupt:
    logger.info("Keyboard interrupt")
except Exception as e:
    logger.error(f"Critical Error: {e}", exc_info=True)
finally:
    logger.info("Shutting down gracefully...")

    # Stop MQTT
    if mqtt_client:
        mqtt_client.loop_stop()
        mqtt_client.disconnect()
        logger.info("MQTT disconnected")

    # Flush Kafka messages
    if producer:
        logger.info("Flushing Kafka messages...")
        remaining = len(producer)
        if remaining > 0:
            logger.info(f"Waiting for {remaining} messages to be sent...")
        producer.flush(timeout=10)
        logger.info("Kafka flushed")

    logger.info(f"Final stats: RX={metrics.messages_received}, TX={metrics.messages_sent}, Failed={metrics.messages_failed}")
    logger.info("Bridge stopped cleanly")
    sys.exit(0)
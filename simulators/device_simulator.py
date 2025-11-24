import paho.mqtt.client as mqtt
import time
import json
import os
import requests
import threading
import ssl
import sys
import random
import signal
import logging
from logging.handlers import RotatingFileHandler
from flask import Flask, request, render_template_string
from datetime import datetime, timezone
from dotenv import load_dotenv
from werkzeug.serving import make_server
from cryptography.fernet import Fernet
import base64
import hashlib

if sys.stdout.encoding != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except AttributeError:
        pass

# --- 1. Configuration ---
load_dotenv()

PROVISIONING_SERVER_PORT = 9090
DEVICE_NAME_FOR_FILES = "default"

if len(sys.argv) == 2:
    try:
        PROVISIONING_SERVER_PORT = int(sys.argv[1])
        DEVICE_NAME_FOR_FILES = str(PROVISIONING_SERVER_PORT)
    except ValueError:
        print(f"Error: Port '{sys.argv[1]}' must be a number.")
        sys.exit(1)

# Unique files per device
CREDENTIALS_FILE = f"credentials_{DEVICE_NAME_FOR_FILES}.enc"
STATE_FILE = f"state_{DEVICE_NAME_FOR_FILES}.json"
LOG_FILE = f"device_{DEVICE_NAME_FOR_FILES}.log"

MQTT_BROKER_HOST = os.getenv('MQTT_BROKER_HOST', "localhost")
MQTT_USE_TLS = os.getenv('MQTT_USE_TLS', 'false').lower() == 'true'
MQTT_PORT_TLS = int(os.getenv('MQTT_BROKER_PORT_TLS', "8883"))
MQTT_PORT = int(os.getenv('MQTT_BROKER_PORT', "1883"))
MQTT_CA_CERT = os.getenv('MQTT_CA_CERT', "ca.crt")
TELEMETRY_TOPIC = os.getenv('TELEMETRY_TOPIC', "iot/telemetry/ingress")
USER_DEVICE_SERVICE_URL = os.getenv('USER_DEVICE_SERVICE_URL', "http://localhost:8088/api/v1/devices")
PUBLISH_INTERVAL = int(os.getenv('PUBLISH_INTERVAL', "5"))

# Buffer settings
MAX_BUFFER_SIZE = int(os.getenv('MAX_BUFFER_SIZE', "100"))
MIN_TEMP = float(os.getenv('MIN_TEMP', "-40.0"))
MAX_TEMP = float(os.getenv('MAX_TEMP', "100.0"))

# --- 2. Global Variables ---
mqtt_client = None
device_id = None
device_token = None
connected_to_mqtt = False
flask_app = Flask(__name__)
factory_reset_event = threading.Event()
shutdown_event = threading.Event()
flask_server = None
start_time = time.time()
logger = None

# Telemetry buffer for offline mode
telemetry_buffer = []

# Simulation state
current_temp = 22.0
target_temp = None
heating_on = False

# --- 3. Logging Setup ---
class ContextFilter(logging.Filter):
    """
    This filter adds a ‘port’ field to ALL logs,
    including Flask and library system logs.
    """
    def filter(self, record):
        record.port = PROVISIONING_SERVER_PORT
        return True


def setup_logging():
    """Configure logging with rotation and context filter"""
    # Creating a filter
    port_filter = ContextFilter()

    # Configure the formatter that expects %(port)s
    formatter = logging.Formatter('%(asctime)s [%(levelname)s] [Port:%(port)s] %(message)s')

    # 1. File Handler
    file_handler = RotatingFileHandler(LOG_FILE, maxBytes=5*1024*1024, backupCount=3, encoding='utf-8')
    file_handler.setFormatter(formatter)
    file_handler.addFilter(port_filter)

    # 2. Console Handler
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setFormatter(formatter)
    console_handler.addFilter(port_filter)

    # 3. Root Logger
    log = logging.getLogger()
    log.setLevel(logging.INFO)

    # We clear old handlers (so that they don't duplicate when restarting)
    if log.hasHandlers():
        log.handlers.clear()

    log.addHandler(file_handler)
    log.addHandler(console_handler)

    return log

# --- 4. Encryption for Credentials ---
def get_encryption_key():
    """Generate device-specific encryption key"""
    device_seed = f"{PROVISIONING_SERVER_PORT}-{DEVICE_NAME_FOR_FILES}"
    key = hashlib.sha256(device_seed.encode()).digest()
    return base64.urlsafe_b64encode(key)

def save_credentials_secure(credentials):
    """Save encrypted credentials"""
    try:
        fernet = Fernet(get_encryption_key())
        encrypted = fernet.encrypt(json.dumps(credentials).encode())
        with open(CREDENTIALS_FILE, 'wb') as f:
            f.write(encrypted)
        logger.info("Credentials saved securely")
    except Exception as e:
        logger.error(f"Failed to save credentials: {e}")
        raise

def load_credentials_secure():
    """Load and decrypt credentials"""
    try:
        fernet = Fernet(get_encryption_key())
        with open(CREDENTIALS_FILE, 'rb') as f:
            decrypted = fernet.decrypt(f.read())
        return json.loads(decrypted)
    except Exception as e:
        logger.error(f"Failed to load credentials: {e}")
        raise

# --- 5. State Management ---
def load_initial_state():
    """Load persistent settings from NVM"""
    global current_temp, target_temp, heating_on

    current_temp = round(random.uniform(18.0, 23.0), 2)

    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, 'r') as f:
                state = json.load(f)
                target_temp = state.get("target_temp", None)
                logger.info(f"Settings loaded: target_temp={target_temp}")
        except (json.JSONDecodeError, IOError) as e:
            logger.warning(f"Error reading state file: {e}")
            target_temp = None
    else:
        logger.info("No state file found. Waiting for commands.")
        target_temp = None

    logger.info(f"Initial state: current={current_temp}°C, target={target_temp}°C, heating={heating_on}")

def save_current_state():
    """Save persistent settings to NVM"""
    try:
        state_data = {"target_temp": target_temp}
        with open(STATE_FILE, 'w') as f:
            json.dump(state_data, f, indent=2)
        logger.info(f"Settings saved (target_temp={target_temp})")
    except IOError as e:
        logger.error(f"Error saving state: {e}")

# --- 6. Telemetry Buffer ---
def buffer_telemetry(payload):
    """Store telemetry when offline"""
    global telemetry_buffer
    telemetry_buffer.append({
        'payload': payload,
        'timestamp': time.time()
    })
    if len(telemetry_buffer) > MAX_BUFFER_SIZE:
        telemetry_buffer.pop(0)

    if len(telemetry_buffer) % 10 == 0:
        logger.info(f"Buffer size: {len(telemetry_buffer)} messages")

def flush_telemetry_buffer():
    """Send buffered data when reconnected"""
    global telemetry_buffer
    if not telemetry_buffer:
        return

    logger.info(f"Flushing {len(telemetry_buffer)} buffered messages...")
    success_count = 0

    for item in telemetry_buffer[:]:
        result = mqtt_client.publish(TELEMETRY_TOPIC, item['payload'], qos=1)
        if result.rc == mqtt.MQTT_ERR_SUCCESS:
            success_count += 1
        time.sleep(0.1)

    telemetry_buffer.clear()
    logger.info(f"Flushed {success_count} messages successfully")

# --- 7. Provisioning Mode ---
HTML_TEMPLATE = r"""
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title>Device Provisioning</title>
    <style> body { font-family: sans-serif; max-width: 400px; margin: 50px auto; padding: 20px; border: 1px solid #ccc; border-radius: 5px; } label, input { display: block; margin-bottom: 10px; } input[type=text] { width: 95%; padding: 8px; } button { padding: 10px 15px; background-color: #007bff; color: white; border: none; border-radius: 3px; cursor: pointer; } .error { color: red; margin-top: 10px; } </style>
</head>
<body>
    <h1>Enter Claim Code</h1>
    <p>Device on Port: <strong>{{ port }}</strong></p>
    <p>Please get a claim code from the main application.</p>
    {% if error %} <p class="error">{{ error }}</p> {% endif %}
    <form method="post">
        <label for="claim_code">Claim Code:</label>
        <input type="text" id="claim_code" name="claim_code" pattern="\d{3}-\d{3}" required placeholder="123-456">
        <button type="submit">Claim Device</button>
    </form>
</body>
</html>
"""

@flask_app.route('/', methods=['GET', 'POST'])
def provision_form():
    global flask_server
    error = None
    if request.method == 'POST':
        claim_code = request.form.get('claim_code')
        try:
            claim_url = f"{USER_DEVICE_SERVICE_URL}/claim-with-code"
            payload = {"claimCode": claim_code}
            response = requests.post(claim_url, json=payload, timeout=10)

            if response.status_code == 200:
                credentials = response.json()
                logger.info(f"Claimed! Device ID: {credentials.get('deviceId')}")
                save_credentials_secure(credentials)

                threading.Thread(target=lambda: flask_server.shutdown(), daemon=True).start()
                return "<h1>Success!</h1><p>Device claimed. Restarting...</p>"
            elif response.status_code == 404:
                error = "Claim failed: Code not found or expired."
            else:
                error = f"Claim failed: Server returned {response.status_code}."
        except Exception as e:
            error = f"Claim failed: {e}"
        logger.error(error)

    return render_template_string(HTML_TEMPLATE, error=error, port=PROVISIONING_SERVER_PORT)

@flask_app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    status = {
        "status": "provisioning",
        "port": PROVISIONING_SERVER_PORT,
        "uptime": round(time.time() - start_time, 2)
    }
    return json.dumps(status), 200

def run_flask_app():
    """Start Flask provisioning server"""
    global flask_server
    host_ip = '127.0.0.1'
    logger.info(f"Starting provisioning server on http://{host_ip}:{PROVISIONING_SERVER_PORT}")
    flask_server = make_server(host_ip, PROVISIONING_SERVER_PORT, flask_app)
    flask_server.serve_forever()

def provisioning_mode():
    """Run provisioning web server"""
    logger.info("No credentials found. Entering provisioning mode...")
    run_flask_app()
    logger.info("Web server stopped. Restarting...")

# --- 8. MQTT Callbacks ---
def on_connect(client, userdata, flags, rc, properties):
    """Callback for MQTT connection"""
    global connected_to_mqtt
    if rc == 0:
        connected_to_mqtt = True
        logger.info("MQTT Connected")
        command_topic = f"devices/{device_id}/commands"
        client.subscribe(command_topic)
        logger.info(f"Subscribed to: {command_topic}")

        # Flush buffered telemetry
        if telemetry_buffer:
            flush_telemetry_buffer()
    else:
        connected_to_mqtt = False
        error_msg = {
            1: "incorrect protocol",
            2: "invalid client id",
            3: "server unavailable",
            4: "bad username/password",
            5: "not authorised"
        }.get(rc, f"unknown error {rc}")
        logger.error(f"MQTT Connection Failed: {error_msg}")

def on_disconnect(client, userdata, disconnect_flags, rc, properties):
    global connected_to_mqtt
    connected_to_mqtt = False
    logger.warning(f"MQTT Disconnected (Code: {rc})")

def on_message(client, userdata, msg):
    """Process incoming commands"""
    global target_temp
    try:
        payload = json.loads(msg.payload.decode())
        logger.info(f"Command received: {payload}")
        cmd = payload.get("command")
        val = payload.get("value")

        if cmd == "set_target_temp":
            value = float(val)

            # Validation
            if not (MIN_TEMP <= val <= MAX_TEMP):
                logger.warning(f"Invalid temp: {val}°C (range: {MIN_TEMP}-{MAX_TEMP})")
                return

            target_temp = value
            logger.info(f"Target temp updated: {target_temp}°C")
            save_current_state()

        elif cmd == "reset_device":
            # Security: require confirmation
            expected = device_id[:8] if device_id else ""

            if val != expected:
                logger.warning("Reset denied: Invalid confirmation code")
                return

            logger.warning("FACTORY RESET CONFIRMED")
            factory_reset_event.set()

        else:
            logger.warning(f"Unknown command: {cmd}")

    except ValueError as e:
        logger.error(f"Invalid command format: {e}")
    except Exception as e:
        logger.error(f"Error processing command: {e}")

# --- 9. Simulation ---
def simulate_climate_control():
    """Simulate heating/cooling physics"""
    global current_temp, heating_on

    if target_temp is None:
        heating_on = False
        return

    if heating_on:
        current_temp += random.uniform(0.3, 0.8)
        if current_temp >= target_temp:
            heating_on = False
    else:
        current_temp -= random.uniform(0.1, 0.4)
        if current_temp < target_temp - 0.5:
            heating_on = True

    current_temp = round(current_temp + random.uniform(-0.1, 0.1), 2)

def generate_telemetry_payload():
    """Generate telemetry JSON"""
    return json.dumps({
        "deviceId": device_id,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "data": {
            "currentTemperature": current_temp,
            "targetTemperature": target_temp,
            "heatingStatus": heating_on
        }
    })

# --- 10. Factory Reset ---
def perform_factory_reset():
    """Perform secure factory reset"""
    global mqtt_client
    logger.warning("Performing Factory Reset...")

    # Graceful MQTT shutdown
    if mqtt_client:
        mqtt_client.loop_stop()
        mqtt_client.disconnect()
        time.sleep(1)

    # Wipe data
    try:
        if os.path.exists(CREDENTIALS_FILE):
            os.remove(CREDENTIALS_FILE)
        if os.path.exists(STATE_FILE):
            os.remove(STATE_FILE)
        logger.info("Data wiped successfully")
    except OSError as e:
        logger.error(f"Error deleting files: {e}")

    logger.info("Restarting in provisioning mode...")
    os.execv(sys.executable, ['python'] + sys.argv)

# --- 11. MQTT Connection with Retry ---
def connect_mqtt_with_retry(max_retries=5):
    """Connect to MQTT with exponential backoff"""
    retry_delay = 1

    for attempt in range(max_retries):
        try:
            mqtt_client.connect(MQTT_BROKER_HOST, port_to_use, 60)
            mqtt_client.loop_start()
            logger.info(f"MQTT connection initiated (attempt {attempt+1})")
            return True
        except Exception as e:
            logger.warning(f"Retry {attempt+1}/{max_retries}: {e}")
            if attempt < max_retries - 1:
                time.sleep(retry_delay)
                retry_delay = min(retry_delay * 2, 60)

    logger.error("Failed to connect after all retries")
    return False

# --- 12. Main Loop ---
def main_loop():
    """Main device operation loop"""
    global mqtt_client, device_id, device_token, port_to_use
    telemetry_count = 0

    logger.info("Loading credentials...")

    try:
        credentials = load_credentials_secure()
        device_id = credentials.get("deviceId")
        device_token = credentials.get("deviceToken")

        if not device_id or not device_token:
            logger.error("Credentials corrupted. Deleting...")
            os.remove(CREDENTIALS_FILE)
            return
    except Exception as e:
        logger.error(f"Error loading credentials: {e}")
        try:
            os.remove(CREDENTIALS_FILE)
        except OSError:
            pass
        return

    load_initial_state()
    logger.info(f"Starting main loop for Device: {device_id}")

    factory_reset_event.clear()
    mqtt_client = mqtt.Client(
        mqtt.CallbackAPIVersion.VERSION2,
        client_id=device_id,
        protocol=mqtt.MQTTv311
    )
    mqtt_client.on_connect = on_connect
    mqtt_client.on_disconnect = on_disconnect
    mqtt_client.on_message = on_message
    mqtt_client.username_pw_set(username=device_id, password=device_token)

    port_to_use = MQTT_PORT
    if MQTT_USE_TLS:
        port_to_use = MQTT_PORT_TLS
        if not os.path.exists(MQTT_CA_CERT):
            logger.error(f"TLS Error: CA cert not found at '{os.path.abspath(MQTT_CA_CERT)}'")
            sys.exit(1)
        try:
            mqtt_client.tls_set(
                ca_certs=MQTT_CA_CERT,
                cert_reqs=ssl.CERT_REQUIRED,
                tls_version=ssl.PROTOCOL_TLSv1_2
            )
            logger.info("TLS configured")
        except Exception as e:
            logger.error(f"TLS config error: {e}")
            sys.exit(1)

    logger.info(f"Connecting to MQTT at {MQTT_BROKER_HOST}:{port_to_use}...")
    if not connect_mqtt_with_retry():
        logger.error("Cannot connect to MQTT. Will retry in background.")

    try:
        while not shutdown_event.is_set():
            if factory_reset_event.is_set():
                logger.warning("Factory reset triggered. Stopping...")
                break

            # Always simulate physics
            simulate_climate_control()

            # Send or buffer telemetry
            payload = generate_telemetry_payload()

            if connected_to_mqtt:
                result = mqtt_client.publish(TELEMETRY_TOPIC, payload, qos=1)
                if result.rc == mqtt.MQTT_ERR_SUCCESS:
                    telemetry_count += 1
                    if telemetry_count % 12 == 0:
                        logger.info(f"[{PROVISIONING_SERVER_PORT}] Heartbeat: Still sending data ({payload})")
                else:
                    logger.warning(f"Failed to send telemetry: {result.rc}")
                    buffer_telemetry(payload)
            else:
                buffer_telemetry(payload)
                if telemetry_count % 12 == 0:
                    logger.info(f"Offline: {current_temp}°C (buffered: {len(telemetry_buffer)}, Target: {target_temp})")
                telemetry_count += 1

            # Wait with interrupt support
            if shutdown_event.wait(timeout=PUBLISH_INTERVAL):
                break

    except KeyboardInterrupt:
        logger.info("Simulator stopped by user")
    finally:
        if mqtt_client:
            mqtt_client.loop_stop()
            mqtt_client.disconnect()
        logger.info("MQTT Disconnected")

    if factory_reset_event.is_set():
        perform_factory_reset()

# --- 13. Signal Handlers ---
def signal_handler(sig, frame):
    """Handle graceful shutdown"""
    logger.info(f"Shutdown signal received ({sig})")
    shutdown_event.set()

# --- 14. Entry Point ---
if __name__ == "__main__":
    logger = setup_logging()

    logger.info("=== IoT Device Simulator ===")
    logger.info(f"MQTT: {MQTT_BROKER_HOST} (TLS: {MQTT_USE_TLS})")
    logger.info(f"Backend: {USER_DEVICE_SERVICE_URL}")
    logger.info(f"Telemetry Topic: {TELEMETRY_TOPIC}")
    logger.info(f"Publish Interval: {PUBLISH_INTERVAL}s")
    logger.info(f"Buffer Size: {MAX_BUFFER_SIZE}")
    logger.info(f"Temp Range: {MIN_TEMP}°C - {MAX_TEMP}°C")
    logger.info("============================")

    # Register signal handlers
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    # Main loop
    while not shutdown_event.is_set():
        if not os.path.exists(CREDENTIALS_FILE):
            provisioning_mode()
        else:
            main_loop()

        if not shutdown_event.is_set():
            logger.info("Re-checking state...")
            time.sleep(1)

    logger.info("Simulator stopped cleanly")
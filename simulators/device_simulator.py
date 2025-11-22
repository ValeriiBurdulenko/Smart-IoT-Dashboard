import paho.mqtt.client as mqtt
import time
import json
import os
import requests
import threading
import ssl
import sys
import random
from flask import Flask, request, render_template_string
from datetime import datetime, timezone
from dotenv import load_dotenv
from werkzeug.serving import make_server

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

# Файлы теперь УНИКАЛЬНЫ для каждого порта (устройства)
CREDENTIALS_FILE = f"credentials_{DEVICE_NAME_FOR_FILES}.json"
STATE_FILE = f"state_{DEVICE_NAME_FOR_FILES}.json"

MQTT_BROKER_HOST = os.getenv('MQTT_BROKER_HOST', "localhost")
MQTT_USE_TLS = os.getenv('MQTT_USE_TLS', 'false').lower() == 'true'
MQTT_PORT_TLS = int(os.getenv('MQTT_BROKER_PORT_TLS', "8883"))
MQTT_PORT = int(os.getenv('MQTT_BROKER_PORT', "1883"))
MQTT_CA_CERT = os.getenv('MQTT_CA_CERT', "ca.crt")
TELEMETRY_TOPIC = os.getenv('TELEMETRY_TOPIC', "iot/telemetry/ingress")
USER_DEVICE_SERVICE_URL = os.getenv('USER_DEVICE_SERVICE_URL', "http://localhost:8088/api/v1/devices")
PUBLISH_INTERVAL = int(os.getenv('PUBLISH_INTERVAL', "5"))

# --- 2. Global Variables ---
mqtt_client = None
device_id = None
device_token = None
connected_to_mqtt = False
flask_app = Flask(__name__)
factory_reset_event = threading.Event()
flask_server = None


# --- 3. Simulation State Variables ---
current_temp = 22.0
target_temp = 20.0
heating_on = False

def print_config():
    """Prints the current configuration on startup."""
    print("--- Simulator Configuration ---")
    print(f"MQTT Broker Host: {MQTT_BROKER_HOST} (TLS: {MQTT_USE_TLS})")
    print(f"Backend API URL: {USER_DEVICE_SERVICE_URL}")
    print(f"Telemetry Topic: {TELEMETRY_TOPIC}")
    print(f"Publish Interval: {PUBLISH_INTERVAL}s")
    print(f"Device Port (ID): {PROVISIONING_SERVER_PORT}")
    print(f"Credentials File: {CREDENTIALS_FILE}")
    print("-----------------------------------")

# --- 4. State Management (NVM) ---

def load_initial_state():
    """Loads persistent settings (target_temp) from NVM (file)."""
    global current_temp, target_temp, heating_on

    default_target_temp = 20.0

    # current_temp is a measurement, always randomize on start
    current_temp = round(random.uniform(18.0, 23.0), 2)

    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, 'r') as f:
                state = json.load(f)
                target_temp = state.get("target_temp", default_target_temp)
                print(f"[Port {PROVISIONING_SERVER_PORT}] Settings loaded: target_temp={target_temp}")
        except (json.JSONDecodeError, IOError) as e:
            print(f"⚠️ [Port {PROVISIONING_SERVER_PORT}] Error reading state file ({e}). Using defaults.")
            target_temp = default_target_temp
    else:
        print(f"ℹ️ [Port {PROVISIONING_SERVER_PORT}] State file not found. Using defaults.")
        target_temp = default_target_temp

    # heating_on is a derived state, re-calculate on start
    heating_on = current_temp < target_temp

    print(f"[{PROVISIONING_SERVER_PORT}] Initial state: current_temp={current_temp}, target_temp={target_temp}, heating_on={heating_on}")

def save_current_state():
    """Saves persistent settings (target_temp) to NVM (file)."""
    try:
        state_data = {"target_temp": target_temp}
        with open(STATE_FILE, 'w') as f:
            json.dump(state_data, f, indent=2)
        print(f"[Port {PROVISIONING_SERVER_PORT}] Settings saved (target_temp={target_temp})")
    except IOError as e:
        print(f"[Port {PROVISIONING_SERVER_PORT}] Error saving settings: {e}")


# --- 5. Provisioning Mode ---

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
                print(f"[{PROVISIONING_SERVER_PORT}] Claimed! Device ID: {credentials.get('deviceId')}")
                with open(CREDENTIALS_FILE, 'w') as f:
                    json.dump(credentials, f)
                print(f"[{PROVISIONING_SERVER_PORT}] Credentials saved.")

                # Shutdown Flask server gracefully in a separate thread
                threading.Thread(target=lambda: flask_server.shutdown(), daemon=True).start()
                return "<h1>Success!</h1><p>Device claimed. Simulator is restarting...</p>"
            elif response.status_code == 404:
                error = "Claim failed: Code not found or expired."
            else:
                error = f"Claim failed: Server returned status {response.status_code}."
        except Exception as e:
            error = f"Claim failed: Could not connect. {e}"
        print(f"[{PROVISIONING_SERVER_PORT}] {error}")

    # Inject port number into template
    return render_template_string(HTML_TEMPLATE, error=error, port=PROVISIONING_SERVER_PORT)

def run_flask_app():
    """Starts the Flask server securely on localhost."""
    global flask_server
    host_ip = '127.0.0.1' # Secure binding
    print(f"[{PROVISIONING_SERVER_PORT}] Starting provisioning server...")
    print(f"[{PROVISIONING_SERVER_PORT}] Open http://{host_ip}:{PROVISIONING_SERVER_PORT} to provision.")
    flask_server = make_server(host_ip, PROVISIONING_SERVER_PORT, flask_app)
    flask_server.serve_forever()

def provisioning_mode():
    """Runs the web server and blocks until provisioning is complete."""
    print(f"[{PROVISIONING_SERVER_PORT}] No credentials found. Entering provisioning mode...")

    # Run Flask. This call blocks until flask_server.shutdown() is called.
    run_flask_app()

    print(f"[{PROVISIONING_SERVER_PORT}] Web server stopped. Restarting process...")
    # Go to the main loop

# --- 6. Main Loop ---
def on_connect(client, userdata, flags, rc, properties):
    """Callback for MQTT connection."""
    global connected_to_mqtt
    if rc == 0:
        connected_to_mqtt = True
        print(f"[{PROVISIONING_SERVER_PORT}] MQTT Connected.")
        command_topic = f"devices/{device_id}/commands"
        client.subscribe(command_topic)
        print(f"[{PROVISIONING_SERVER_PORT}] Subscribed to: {command_topic}")
    else:
        connected_to_mqtt = False
        # Коды ошибок остались теми же
        error_msg = {
            1: "refused - incorrect protocol",
            2: "refused - invalid client id",
            3: "refused - server unavailable",
            4: "refused - bad username/password",
            5: "refused - not authorised (ACL)",
        }.get(rc, f"Unknown error code: {rc}")

        print(f"❌ [{PROVISIONING_SERVER_PORT}] MQTT Connection Failed: {error_msg}")

        if rc == 5:
            print(f"❌ [{PROVISIONING_SERVER_PORT}] Connect Failed: rc={rc}. Retrying in background...")

def on_disconnect(client, userdata, disconnect_flags, rc, properties):
    global connected_to_mqtt
    connected_to_mqtt = False
    print(f"🔌 [{PROVISIONING_SERVER_PORT}] MQTT Disconnected (Code: {rc}).")

def on_message(client, userdata, msg):
    global target_temp
    try:
        payload = json.loads(msg.payload.decode())
        print(f"[{PROVISIONING_SERVER_PORT}] Command received: {payload}")
        cmd = payload.get("command")
        if cmd == "set_target_temp":
            target_temp = float(payload.get("value"))
            print(f"⚙️ [{PROVISIONING_SERVER_PORT}] Target temp updated to: {target_temp}°C")
            save_current_state()

        elif cmd == "reset_device":
            print(f"💀 [{PROVISIONING_SERVER_PORT}] KILL COMMAND RECEIVED! Wiping data...")
            factory_reset_event.set()

    except Exception as e:
        print(f"[{PROVISIONING_SERVER_PORT}] Error processing command: {e}")

def simulate_climate_control():
    global current_temp, heating_on

    if heating_on:
        current_temp += random.uniform(0.3, 0.8)
        if current_temp >= target_temp: heating_on = False
    else:
        current_temp -= random.uniform(0.1, 0.4)
        if current_temp < target_temp - 0.5: heating_on = True

    current_temp = round(current_temp + random.uniform(-0.1, 0.1), 2)

def generate_telemetry():
    simulate_climate_control()
    return {
        "deviceId": device_id,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "data": {
            "currentTemperature": current_temp,
            "targetTemperature": target_temp,
            "heatingStatus": heating_on
        }
    }

def perform_factory_reset():
    print(f"[{PROVISIONING_SERVER_PORT}] Performing Factory Reset...")
    try:
        if os.path.exists(CREDENTIALS_FILE): os.remove(CREDENTIALS_FILE)
        if os.path.exists(STATE_FILE): os.remove(STATE_FILE)
    except OSError as e:
        print(f"⚠️ Error deleting files: {e}")

    print(f"[{PROVISIONING_SERVER_PORT}] Restarting in provisioning mode...")
    os.execv(sys.executable, ['python'] + sys.argv)

def main_loop():
    global mqtt_client, device_id, device_token
    telemetry_count = 0
    print(f"[{PROVISIONING_SERVER_PORT}] Loading credentials...")

    # Load credentials
    try:
        with open(CREDENTIALS_FILE, 'r') as f:
            credentials = json.load(f)
            device_id = credentials.get("deviceId")
            device_token = credentials.get("deviceToken")
            if not device_id or not device_token:
                print(f"⚠[{PROVISIONING_SERVER_PORT}] Credentials corrupted... deleting.")
                os.remove(CREDENTIALS_FILE)
                return # Exit to restart provisioning
    except Exception as e:
        print(f"⚠[{PROVISIONING_SERVER_PORT}] Error reading credentials: {e}... deleting.")
        try: os.remove(CREDENTIALS_FILE)
        except OSError: pass
        return # Exit to restart provisioning

    load_initial_state()
    print(f"[{PROVISIONING_SERVER_PORT}] Starting main loop for Device ID: {device_id}")

    factory_reset_event.clear()
    mqtt_client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=device_id, protocol=mqtt.MQTTv311)
    mqtt_client.on_connect = on_connect
    mqtt_client.on_disconnect = on_disconnect
    mqtt_client.on_message = on_message
    mqtt_client.username_pw_set(username=device_id, password=device_token)

    port_to_use = MQTT_PORT
    if MQTT_USE_TLS:
        port_to_use = MQTT_PORT_TLS
        if not os.path.exists(MQTT_CA_CERT):
            print(f"[{PROVISIONING_SERVER_PORT}] TLS Error: CA cert not found at '{os.path.abspath(MQTT_CA_CERT)}'")
            sys.exit(1)
        try:
            mqtt_client.tls_set(ca_certs=MQTT_CA_CERT, cert_reqs=ssl.CERT_REQUIRED, tls_version=ssl.PROTOCOL_TLSv1_2)
            print(f"[{PROVISIONING_SERVER_PORT}] TLS configured.")
        except Exception as e:
            print(f"[{PROVISIONING_SERVER_PORT}] Error configuring TLS: {e}. Exiting.")
            sys.exit(1)

    print(f"🔌 [{PROVISIONING_SERVER_PORT}] Connecting to MQTT Broker at {MQTT_BROKER_HOST}:{port_to_use}...")
    try:
        mqtt_client.connect(MQTT_BROKER_HOST, port_to_use, 60)
        mqtt_client.loop_start()
    except Exception as e:
        print(f"[{PROVISIONING_SERVER_PORT}] Failed to connect to MQTT: {e}")
        # If connection fails immediately (e.g. auth error), on_connect might not fire.
        # We wait a bit and retry or exit? For now, we return to trigger a retry loop.
        time.sleep(5)
        return

    try:
        while True:
            if factory_reset_event.is_set():
                print(f"[{PROVISIONING_SERVER_PORT}] Authorization revoked. Stopping...")
                break

            if connected_to_mqtt:
                telemetry = generate_telemetry()
                payload = json.dumps(telemetry)
                result = mqtt_client.publish(TELEMETRY_TOPIC, payload, qos=1)
                if result.rc == mqtt.MQTT_ERR_SUCCESS:
                    telemetry_count += 1
                    if telemetry_count % 12 == 0:
                        print(f"📡 [{PROVISIONING_SERVER_PORT}] Heartbeat: Still sending data ({payload})")
                else:
                    print(f"⚠[{PROVISIONING_SERVER_PORT}] Failed to send telemetry, code: {result.rc}")
            else:
                print(f"[{PROVISIONING_SERVER_PORT}] MQTT not connected, waiting...")

            time.sleep(PUBLISH_INTERVAL)
    except KeyboardInterrupt:
        print(f"\n[{PROVISIONING_SERVER_PORT}] Simulator stopped by user.")
        sys.exit(0)
    finally:
        if mqtt_client:
            mqtt_client.loop_stop()
            mqtt_client.disconnect()
        print(f"[{PROVISIONING_SERVER_PORT}] MQTT Disconnected.")

    if factory_reset_event.is_set():
        perform_factory_reset()

# --- 7. Entry Point ---
if __name__ == "__main__":
    print_config()

    # Endless loop to handle restarts between Provisioning and Main Loop
    while True:
        if not os.path.exists(CREDENTIALS_FILE):
            provisioning_mode()
        else:
            main_loop()

        print(f"[{PROVISIONING_SERVER_PORT}] Re-checking state...")
        time.sleep(1)
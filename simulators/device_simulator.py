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

# --- 1. Configuration ---
load_dotenv()

CREDENTIALS_FILE = os.getenv('CREDENTIALS_FILE', "credentials.json")
STATE_FILE = "device_state.json"

MQTT_BROKER_HOST = os.getenv('MQTT_BROKER_HOST', "localhost")
MQTT_USE_TLS = os.getenv('MQTT_USE_TLS', 'false').lower() == 'true'
MQTT_PORT_TLS = int(os.getenv('MQTT_BROKER_PORT_TLS', "8883"))
MQTT_PORT = int(os.getenv('MQTT_BROKER_PORT', "1883"))
MQTT_CA_CERT = os.getenv('MQTT_CA_CERT', "ca.crt")
TELEMETRY_TOPIC = os.getenv('TELEMETRY_TOPIC', "iot/telemetry/ingress")
PROVISIONING_SERVER_PORT = int(os.getenv('PROVISIONING_SERVER_PORT', "9090"))
USER_DEVICE_SERVICE_URL = os.getenv('USER_DEVICE_SERVICE_URL', "http://localhost:8088/api/v1/devices")
PUBLISH_INTERVAL = int(os.getenv('PUBLISH_INTERVAL', "5"))

# --- 2. Global Variables ---
mqtt_client = None
device_id = None
device_token = None
connected_to_mqtt = False
flask_app = Flask(__name__)
provisioning_success = threading.Event()

# --- 3. Simulation State Variables ---
current_temp = 22.0
target_temp = 20.0
heating_on = False

def print_config():
    """Prints the current configuration on startup."""
    print("--- 🚀 Simulator Configuration ---")
    print(f"MQTT Broker Host: {MQTT_BROKER_HOST} (TLS: {MQTT_USE_TLS})")
    print(f"Backend API URL: {USER_DEVICE_SERVICE_URL}")
    print(f"Telemetry Topic: {TELEMETRY_TOPIC}")
    print(f"Publish Interval: {PUBLISH_INTERVAL}s")
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
                print(f"✅ Settings loaded from {STATE_FILE}: target_temp={target_temp}")
        except (json.JSONDecodeError, IOError) as e:
            print(f"⚠️ Error reading state file ({e}). Using default target_temp.")
            target_temp = default_target_temp
    else:
        print(f"ℹ️ State file {STATE_FILE} not found. Using default target_temp.")
        target_temp = default_target_temp

    # heating_on is a derived state, re-calculate on start
    heating_on = current_temp < target_temp

    print(f"Initial state set: current_temp={current_temp}, target_temp={target_temp}, heating_on={heating_on}")


def save_current_state():
    """Saves persistent settings (target_temp) to NVM (file)."""
    try:
        state_data = {"target_temp": target_temp}
        with open(STATE_FILE, 'w') as f:
            json.dump(state_data, f, indent=2)
        print(f"💾 Settings saved (target_temp={target_temp})")
    except IOError as e:
        print(f"❌ Error saving settings to {STATE_FILE}: {e}")

# --- 5. Provisioning Mode ---

HTML_TEMPLATE = r"""
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Device Provisioning</title>
    <style> body { font-family: sans-serif; max-width: 400px; margin: 50px auto; padding: 20px; border: 1px solid #ccc; border-radius: 5px; } label, input { display: block; margin-bottom: 10px; } input[type=text] { width: 95%; padding: 8px; } button { padding: 10px 15px; background-color: #007bff; color: white; border: none; border-radius: 3px; cursor: pointer; } .error { color: red; margin-top: 10px; } </style>
</head>
<body>
    <h1>Enter Claim Code</h1>
    <p>Please get a claim code from the main application and enter it below (format: XXX-XXX).</p>
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
    error = None
    if request.method == 'POST':
        claim_code = request.form.get('claim_code')
        if not claim_code:
            error = "Claim code is required."
        else:
            print(f"Attempting to claim device with code: {claim_code}")
            try:
                claim_url = f"{USER_DEVICE_SERVICE_URL}/claim-with-code"
                payload = {"claimCode": claim_code}
                response = requests.post(claim_url, json=payload, timeout=10)

                if response.status_code == 200:
                    credentials = response.json()
                    print(f"Successfully claimed! Device ID: {credentials.get('deviceId')}")
                    with open(CREDENTIALS_FILE, 'w') as f:
                        json.dump(credentials, f)
                    print(f"Credentials saved to {CREDENTIALS_FILE}")
                    provisioning_success.set()
                    return "<h1>Success!</h1><p>Device claimed. Restarting simulator...</p><script>setTimeout(() => window.location.reload(), 2000);</script>"
                elif response.status_code == 404:
                    error = f"Claim failed: Code '{claim_code}' not found or expired."
                else:
                    error = f"Claim failed: Server returned status {response.status_code}. Details: {response.text}"
            except requests.exceptions.RequestException as e:
                error = f"Claim failed: Could not connect to the server ({USER_DEVICE_SERVICE_URL}). {e}"
            except Exception as e:
                error = f"An unexpected error occurred: {e}"
            print(error)
    return render_template_string(HTML_TEMPLATE, error=error)

def run_flask_app():
    print(f"Starting provisioning server at http://localhost:{PROVISIONING_SERVER_PORT}")
    flask_app.run(host='0.0.0.0', port=PROVISIONING_SERVER_PORT, debug=False)

def provisioning_mode():
    print("No credentials found. Entering provisioning mode...")
    flask_thread = threading.Thread(target=run_flask_app, daemon=True)
    flask_thread.start()
    provisioning_success.wait()
    print("Provisioning successful. Restarting...")
    os.execv(sys.executable, ['python'] + sys.argv)


# --- 6. Main Loop ---

def on_connect(client, userdata, flags, rc):
    global connected_to_mqtt
    if rc == 0:
        connected_to_mqtt = True
        print(f"✅ MQTT Connected Successfully to {MQTT_BROKER_HOST}.")
        command_topic = f"devices/{device_id}/commands"
        client.subscribe(command_topic)
        print(f"👂 Subscribed to command topic: {command_topic}")
    else:
        connected_to_mqtt = False
        error_msg = { 1: "refused - incorrect protocol version", 2: "refused - invalid client identifier", 3: "refused - server unavailable", 4: "refused - bad username or password", 5: "refused - not authorised (ACL)", }.get(rc, f"Unknown error code: {rc}")
        print(f"❌ MQTT Connection Failed: {error_msg}")

def on_disconnect(client, userdata, rc):
    global connected_to_mqtt
    connected_to_mqtt = False
    print(f"🔌 MQTT Disconnected with result code: {rc}.")

def on_message(client, userdata, msg):
    global target_temp
    topic = msg.topic
    try:
        payload = json.loads(msg.payload.decode())
        print(f"📩 Command received on topic '{topic}': {payload}")
        command = payload.get("command")
        value = payload.get("value")

        if command == "set_target_temp":
            try:
                target_temp = float(value)
                print(f"⚙️ Target temperature updated to: {target_temp}°C")
                # Save the new setting to NVM
                save_current_state()
            except (ValueError, TypeError):
                print(f"⚠️ Invalid value for set_target_temp: {value}")
        else:
            print(f"❓ Unknown command received: {command}")
    except Exception as e:
        print(f"🚨 Error processing command: {e}")

def simulate_climate_control():
    global current_temp, heating_on

    if heating_on:
        current_temp += random.uniform(0.3, 0.8)
        if current_temp >= target_temp:
            heating_on = False
            print("🔥 Heating OFF")
    else:
        current_temp -= random.uniform(0.1, 0.4)
        if current_temp < target_temp - 0.5:
            heating_on = True
            print("🔥 Heating ON")

    current_temp = round(current_temp + random.uniform(-0.1, 0.1), 2)
    # State is only saved when target_temp changes or heating status changes (in main loop logic if needed)

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

def main_loop():
    global mqtt_client
    print(f"Starting main loop for Device ID: {device_id}")

    mqtt_client = mqtt.Client(client_id=device_id, protocol=mqtt.MQTTv311)
    mqtt_client.on_connect = on_connect
    mqtt_client.on_disconnect = on_disconnect
    mqtt_client.on_message = on_message

    mqtt_client.username_pw_set(username=device_id, password=device_token)

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
            print("🔐 TLS configured.")
        except Exception as e:
            print(f"❌ Error configuring TLS: {e}. Exiting.")
            sys.exit(1)
    else:
        print("ℹ️ TLS not enabled.")

    print(f"🔌 Connecting to MQTT Broker at {MQTT_BROKER_HOST}:{port_to_use}...")
    try:
        mqtt_client.connect(MQTT_BROKER_HOST, port_to_use, 60)
        mqtt_client.loop_start()
    except Exception as e:
        print(f"❌ Failed to connect to MQTT: {e}")
        return

    try:
        while True:
            if connected_to_mqtt:
                telemetry = generate_telemetry()
                payload = json.dumps(telemetry)
                result = mqtt_client.publish(TELEMETRY_TOPIC, payload, qos=1)
                if result.rc == mqtt.MQTT_ERR_SUCCESS:
                    print(f"📡 Sent Telemetry: {payload}")
                else:
                    print(f"⚠️ Failed to send telemetry, code: {result.rc}")
            else:
                print("⏳ MQTT not connected, waiting to reconnect...")
            time.sleep(PUBLISH_INTERVAL)
    except KeyboardInterrupt:
        print("\n🛑 Simulator stopped by user.")
    finally:
        # State is saved on command receive (on_message), so no save needed here
        if mqtt_client and mqtt_client.is_connected():
            mqtt_client.loop_stop()
            mqtt_client.disconnect()
        print("🔌 MQTT Disconnected.")


# --- 7. Entry Point ---
if __name__ == "__main__":
    print_config()

    if os.path.exists(CREDENTIALS_FILE):
        try:
            with open(CREDENTIALS_FILE, 'r') as f:
                credentials = json.load(f)
                device_id = credentials.get("deviceId")
                device_token = credentials.get("deviceToken")
                if not device_id or not device_token:
                    print(f"⚠️ Credentials file ({CREDENTIALS_FILE}) is corrupted. Deleting and entering provisioning mode.")
                    os.remove(CREDENTIALS_FILE)
                    provisioning_mode()
                else:
                    load_initial_state() # 1. Load settings
                    main_loop()          # 2. Run main loop
        except (json.JSONDecodeError, IOError) as e:
            print(f"⚠️ Error reading credentials file ({CREDENTIALS_FILE}): {e}. Deleting and entering provisioning mode.")
            try: os.remove(CREDENTIALS_FILE)
            except OSError: pass
            provisioning_mode()
    else:
        provisioning_mode()
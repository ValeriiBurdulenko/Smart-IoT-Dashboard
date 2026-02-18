import paho.mqtt.client as mqtt
import time
import json
import sys
import os
import glob
import base64
import hashlib
from datetime import datetime, timezone
from dotenv import load_dotenv

# Try importing the encryption library
try:
    from cryptography.fernet import Fernet
except ImportError:
    print("Error: Library 'cryptography' is not installed.", flush=True)
    print("Install it using: pip install cryptography", flush=True)
    sys.exit(1)

# --- Config ---
load_dotenv()
MQTT_BROKER = os.getenv('MQTT_BROKER_HOST', "localhost")
MQTT_PORT = int(os.getenv('MQTT_BROKER_PORT', "1883"))
TELEMETRY_TOPIC = os.getenv('TELEMETRY_TOPIC', "iot/telemetry")

# Global variables
CURRENT_DEVICE_ID = None
CURRENT_DEVICE_TOKEN = None

# --- Logic for Decrypting Credentials ---

def get_encryption_key(port_str):
    if port_str == "default":
        device_seed = "9090-default"
    else:
        device_seed = f"{port_str}-{port_str}"

    key = hashlib.sha256(device_seed.encode()).digest()
    return base64.urlsafe_b64encode(key)

def load_device_credentials():
    files = glob.glob("credentials_*.enc")

    if not files:
        print("No credentials_*.enc files found!", flush=True)
        print("Run device_manager.py and start a device first to generate credentials.", flush=True)
        sys.exit(1)

    print("Found registered devices:", flush=True)
    device_map = {}

    for idx, filepath in enumerate(files):
        filename = os.path.basename(filepath)
        identifier = filename.replace("credentials_", "").replace(".enc", "")
        print(f"   [{idx + 1}] Device Port/ID: {identifier}", flush=True)
        device_map[str(idx + 1)] = identifier

    while True:
        try:
            choice = input("Select device number to use for attacks: ").strip()
            if not choice:
                continue # Skip empty input (e.g. accidental Enter)

            selected_id = device_map.get(choice)
            if selected_id:
                break
            print("Invalid selection, try again.", flush=True)
        except (EOFError, KeyboardInterrupt):
            print("\nExiting.")
            sys.exit(0)

    try:
        filename = f"credentials_{selected_id}.enc"
        key = get_encryption_key(selected_id)
        fernet = Fernet(key)

        with open(filename, 'rb') as f:
            encrypted_data = f.read()

        decrypted_data = fernet.decrypt(encrypted_data)
        creds = json.loads(decrypted_data)

        global CURRENT_DEVICE_ID, CURRENT_DEVICE_TOKEN
        CURRENT_DEVICE_ID = creds.get("deviceId")
        CURRENT_DEVICE_TOKEN = creds.get("deviceToken")

        print(f"Loaded credentials for: {CURRENT_DEVICE_ID}", flush=True)

    except Exception as e:
        print(f"Error decrypting credentials: {e}", flush=True)
        sys.exit(1)

# --- MQTT Setup ---

load_device_credentials()

client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, "alert_tester_script")

def on_connect(client, userdata, flags, rc, properties=None):
    if rc == 0:
        print(f"Connected to MQTT: {MQTT_BROKER}:{MQTT_PORT}", flush=True)
    else:
        print(f"Connection failed code: {rc}", flush=True)

client.on_connect = on_connect
client.username_pw_set(username=CURRENT_DEVICE_ID, password=CURRENT_DEVICE_TOKEN)

try:
    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    client.loop_start()
except Exception as e:
    print(f"Connection Error: {e}", flush=True)
    sys.exit(1)

def get_iso_time():
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

def send_raw(payload_str):
    topic = f"{TELEMETRY_TOPIC}/{CURRENT_DEVICE_ID}"
    client.publish(topic, payload_str)
    print(f"Sent: {payload_str}", flush=True)

# --- Scenarios ---

def test_malfunction():
    print("\n--- Scenario: MALFUNCTION (Bad Data) ---", flush=True)
    print("Sending invalid JSON and schema violations...", flush=True)

    # 1. Not JSON
    for _ in range(3):
        send_raw("THIS IS NOT JSON DATA")
        time.sleep(0.5)

    # 2. JSON missing fields
    bad_json = json.dumps({"sensor": "unknown", "value": 123})
    for _ in range(3):
        send_raw(bad_json)
        time.sleep(0.5)

    print("Check Flink logs/Kafka for 'MALFUNCTION' alert.", flush=True)

def test_spam_ddos():
    print("\n--- 🌪 Scenario: SECURITY_SPAM (Rate Limit) ---", flush=True)
    print("Sending valid messages very fast (0.1s interval)...", flush=True)

    for i in range(15):
        payload = json.dumps({
            "deviceId": CURRENT_DEVICE_ID,
            "timestamp": get_iso_time(),
            "data": {
                "currentTemperature": 20.0,
                "targetTemperature": 20.0,
                "heatingStatus": False
            }
        })
        send_raw(payload)
        time.sleep(0.1)

    print("Check Flink logs/Kafka for 'SECURITY_SPAM' alert.", flush=True)

def test_critical_direction():
    print("\n--- Scenario: CRITICAL_DIRECTION ---", flush=True)
    print("Target is HIGH (30°C), but Temp goes DOWN (25 -> 20)...", flush=True)

    start_temp = 25.0
    target = 30.0

    base_payload = json.dumps({
        "deviceId": CURRENT_DEVICE_ID,
        "timestamp": get_iso_time(),
        "data": {"currentTemperature": start_temp, "targetTemperature": target, "heatingStatus": True}
    })
    send_raw(base_payload)
    time.sleep(2)

    for i in range(1, 6):
        curr = start_temp - (i * 1.0)
        payload = json.dumps({
            "deviceId": CURRENT_DEVICE_ID,
            "timestamp": get_iso_time(),
            "data": {
                "currentTemperature": curr,
                "targetTemperature": target,
                "heatingStatus": True
            }
        })
        send_raw(payload)
        time.sleep(1)

    print("Check Flink logs for 'CRITICAL_DIRECTION'.", flush=True)

def test_stuck():
    print("\n--- Scenario: STUCK (Temperature Not Moving) ---", flush=True)
    print(f"Target is 50°C, Current stuck at 20°C.", flush=True)
    print("Sending same data for 25 seconds...", flush=True)

    target = 50.0
    current = 20.0

    end_time = time.time() + 25

    while time.time() < end_time:
        payload = json.dumps({
            "deviceId": CURRENT_DEVICE_ID,
            "timestamp": get_iso_time(),
            "data": {
                "currentTemperature": current,
                "targetTemperature": target,
                "heatingStatus": True
            }
        })
        send_raw(payload)
        time.sleep(5)
        print("... tick (stuck)", flush=True)

    print("Check Flink logs for 'STUCK'.", flush=True)

def menu():
    while True:
        print("\n==============================", flush=True)
        print(f"Device: {CURRENT_DEVICE_ID}", flush=True)
        print("1. Test MALFUNCTION (Bad Data)", flush=True)
        print("2. Test SECURITY_SPAM (DDoS)", flush=True)
        print("3. Test CRITICAL_DIRECTION (Wrong Physics)", flush=True)
        print("4. Test STUCK (Frozen Value)", flush=True)
        print("5. Exit", flush=True)
        print("==============================", flush=True)

        try:
            choice = input("Select scenario: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nExiting.")
            client.loop_stop()
            sys.exit(0)

        if not choice: # Handle empty input (just Enter)
            continue

        if choice == '1': test_malfunction()
        elif choice == '2': test_spam_ddos()
        elif choice == '3': test_critical_direction()
        elif choice == '4': test_stuck()
        elif choice == '5':
            client.loop_stop()
            break
        else:
            print("Invalid input, try again.", flush=True)

if __name__ == "__main__":
    try:
        # Give some time for background threads to print connection status
        time.sleep(1)
        menu()
    except KeyboardInterrupt:
        client.loop_stop()
        print("\nExiting.", flush=True)
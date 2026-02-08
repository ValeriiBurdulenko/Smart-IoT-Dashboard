import paho.mqtt.client as mqtt
import time
import json
import sys
from datetime import datetime, timezone
import os
import glob
import base64
import hashlib
from dotenv import load_dotenv

# Try importing the encryption library (same as in the simulator)
try:
    from cryptography.fernet import Fernet
except ImportError:
    print("❌ Error: Library 'cryptography' is not installed.")
    print("👉 Install it using: pip install cryptography")
    sys.exit(1)

# --- Config ---
load_dotenv()
MQTT_BROKER = os.getenv('MQTT_BROKER_HOST', "localhost")
MQTT_PORT = int(os.getenv('MQTT_BROKER_PORT', "1883"))
TELEMETRY_TOPIC = os.getenv('TELEMETRY_TOPIC', "iot/telemetry")

# Global variables for the selected device
CURRENT_DEVICE_ID = None
CURRENT_DEVICE_TOKEN = None

# --- Logic for Decrypting Credentials (Ported from device_simulator.py) ---

def get_encryption_key(port_str):
    """
    Generate encryption key.
    Logic MUST match device_simulator.py exactly:
    seed = "{PORT}-{NAME}"
    If launched via manager, then NAME = str(PORT).
    """
    # For default case (no args)
    if port_str == "default":
        device_seed = "9090-default"
    else:
        # For ports (9091, 9092...)
        device_seed = f"{port_str}-{port_str}"
        
    key = hashlib.sha256(device_seed.encode()).digest()
    return base64.urlsafe_b64encode(key)

def load_device_credentials():
    """
    Scans the folder for credentials_*.enc files and offers a choice.
    """
    files = glob.glob("credentials_*.enc")
    
    if not files:
        print("❌ No credentials_*.enc files found!")
        print("👉 First run device_manager.py and claim at least one device.")
        sys.exit(1)
        
    print("\n🔍 Found registered devices:")
    device_map = {}
    
    for idx, filepath in enumerate(files):
        # Parse filename: credentials_9091.enc -> 9091
        filename = os.path.basename(filepath)
        identifier = filename.replace("credentials_", "").replace(".enc", "")
        print(f"   [{idx + 1}] Device (Port/ID: {identifier}) -> {filename}")
        device_map[str(idx + 1)] = identifier

    choice = input("\n🎯 Select device number to attack: ")
    selected_id = device_map.get(choice)
    
    if not selected_id:
        print("❌ Invalid selection.")
        sys.exit(1)
        
    # Decryption
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
        
        print(f"✅ Successfully loaded credentials for: {CURRENT_DEVICE_ID}")
        
    except Exception as e:
        print(f"❌ Error decrypting credentials for {selected_id}: {e}")
        print("The key generation algorithm in the simulator might have changed.")
        sys.exit(1)

# --- MQTT Setup ---

# Load credentials first, then create the client
load_device_credentials()

client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, "alert_tester_script")

def on_connect(client, userdata, flags, rc, properties=None):
    if rc == 0:
        print(f"✅ Connected to MQTT: {MQTT_BROKER}:{MQTT_PORT}")
    else:
        error_msg = {
            1: "incorrect protocol",
            2: "invalid client id",
            3: "server unavailable",
            4: "bad username/password",
            5: "not authorised"
        }.get(rc, str(rc))
        print(f"❌ Connection failed: {error_msg} (Code: {rc})")

client.on_connect = on_connect

# Use loaded credentials
client.username_pw_set(username=CURRENT_DEVICE_ID, password=CURRENT_DEVICE_TOKEN)

try:
    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    client.loop_start()
except Exception as e:
    print(f"❌ Could not connect to broker: {e}")
    sys.exit(1)

def get_iso_time():
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

def send_payload(payload_str):
    topic = f"{TELEMETRY_TOPIC}/{CURRENT_DEVICE_ID}"
    info = client.publish(topic, payload_str)
    info.wait_for_publish()
    if info.rc == mqtt.MQTT_ERR_SUCCESS:
        print(f"📤 Sent to {topic}: {payload_str}")
    else:
        print(f"⚠️ Failed to send message: {mqtt.error_string(info.rc)}")

# --- Scenarios ---

def test_malfunction():
    print("\n--- 💥 Scenario: MALFUNCTION (Bad Data) ---")
    print("Sending invalid JSON and schema violations...")
    
    for _ in range(3):
        send_payload("THIS IS NOT JSON DATA")
        time.sleep(0.5)
        
    bad_json = json.dumps({"sensor": "unknown", "value": 123})
    for _ in range(3):
        send_payload(bad_json)
        time.sleep(0.5)
    
    print("👉 Check Flink logs/Kafka for 'MALFUNCTION' alert.")

def test_spam_ddos():
    print("\n--- 🌪 Scenario: SECURITY_SPAM (Rate Limit) ---")
    print("Sending valid messages very fast (0.1s interval)...")
    
    for _ in range(15):
        payload = json.dumps({
            "deviceId": CURRENT_DEVICE_ID,
            "timestamp": get_iso_time(),
            "data": {
                "currentTemperature": 20.0,
                "targetTemperature": 20.0,
                "heatingStatus": False
            }
        })
        send_payload(payload)
        time.sleep(0.1)
        
    print("👉 Check Flink logs/Kafka for 'SECURITY_SPAM' alert.")

def test_critical_direction():
    print("\n--- 📉 Scenario: CRITICAL_DIRECTION ---")
    print("Target is HIGH (30°C), but Temp goes DOWN (25 -> 20)...")
    
    start_temp = 25.0
    target = 30.0
    
    base_payload = json.dumps({
        "deviceId": CURRENT_DEVICE_ID,
        "timestamp": get_iso_time(),
        "data": {"currentTemperature": start_temp, "targetTemperature": target, "heatingStatus": True}
    })
    send_payload(base_payload)
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
        send_payload(payload)
        time.sleep(1) 

    print("👉 Check Flink logs for 'CRITICAL_DIRECTION'.")

def test_stuck():
    print("\n--- 🧊 Scenario: STUCK (Temperature Not Moving) ---")
    print("Target is 50°C, Current stuck at 20°C.")
    print("NOTE: Requires Flink config 'logic.timeout.stuck.high' to be low (e.g., 15s) for this test.")
    
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
        send_payload(payload)
        time.sleep(5) 
        print("... tick (stuck)")

    print("👉 Check Flink logs for 'STUCK'.")

def menu():
    while True:
        print("\n=== Flink Alert Tester ===")
        print(f"Active Device: {CURRENT_DEVICE_ID}")
        print("1. Test MALFUNCTION (Bad Data)")
        print("2. Test SECURITY_SPAM (DDoS)")
        print("3. Test CRITICAL_DIRECTION (Wrong Physics)")
        print("4. Test STUCK (Frozen Value)")
        print("5. Exit")
        
        choice = input("Select scenario: ")
        
        if choice == '1': test_malfunction()
        elif choice == '2': test_spam_ddos()
        elif choice == '3': test_critical_direction()
        elif choice == '4': test_stuck()
        elif choice == '5': 
            client.loop_stop()
            break
        else:
            print("Invalid choice")

if __name__ == "__main__":
    try:
        # Connection check
        if not client.is_connected():
            print("⏳ Connecting to broker...")
            time.sleep(1)
        menu()
    except KeyboardInterrupt:
        client.loop_stop()
        print("\nExiting.")
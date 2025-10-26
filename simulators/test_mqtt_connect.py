import paho.mqtt.client as mqtt
import time

MQTT_BROKER_HOST = "192.168.178.23"  # Your Laptop's IP
MQTT_BROKER_PORT = 1883
CLIENT_ID = "simple_test_client_pc"


def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(
            f"[SUCCESS] Connected OK to {MQTT_BROKER_HOST}:{MQTT_BROKER_PORT}!")
        # Try publishing a test message
        client.publish("test/topic", "Hello from PC")
        print("[INFO] Test message published.")
    else:
        print(f"[FAIL] Failed to connect, return code {rc}")


def on_disconnect(client, userdata, rc):
    print(f"[INFO] Disconnected with result code {rc}")


def on_publish(client, userdata, mid):
    print(f"[INFO] Message {mid} published.")


client = mqtt.Client(client_id=CLIENT_ID)
client.on_connect = on_connect
client.on_disconnect = on_disconnect
client.on_publish = on_publish

print(
    f"[INFO] Attempting to connect to {MQTT_BROKER_HOST}:{MQTT_BROKER_PORT}...")
try:
    # Set a timeout for the connection attempt
    client.connect(MQTT_BROKER_HOST, MQTT_BROKER_PORT, 60)
    # Give it a few seconds to process connection and callbacks
    client.loop_start()
    time.sleep(5)  # Wait 5 seconds
    client.loop_stop()
    client.disconnect()
except Exception as e:
    print(f"[ERROR] Connection attempt failed: {e}")

print("[INFO] Test finished.")

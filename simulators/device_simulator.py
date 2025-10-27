import paho.mqtt.client as mqtt
import time
import json
import uuid
import random
from datetime import datetime, timezone

# --- Settings ---
MQTT_BROKER_HOST = "IP"
MQTT_BROKER_PORT = 1883
DEVICE_ID = f"sensor-{str(uuid.uuid4())[:8]}"

# Topics
TELEMETRY_TOPIC = "devices/telemetry"
COMMAND_TOPIC = f"devices/{DEVICE_ID}/commands"

PUBLISH_INTERVAL = 10  # Initial data transmission interval (in seconds)


def on_connect(client, userdata, flags, rc):
    """Callback that is called when connecting to the broker."""
    if rc == 0:
        print(
            f"✅ The {DEVICE_ID} device has been successfully connected to the MQTT broker.")
        client.subscribe(COMMAND_TOPIC)
        print(f"👂 Subscribed to the topic of commands: {COMMAND_TOPIC}")
    else:
        print(f"❌ Connection error, code: {rc}")


def on_message(client, userdata, msg):
    """A callback that is invoked when a message is received in a subscribed topic."""
    global PUBLISH_INTERVAL
    print(f"📩 Command received in topic '{msg.topic}': {msg.payload.decode()}")
    try:
        command = json.loads(msg.payload)
        if command.get("command") == "set_interval":
            new_interval = int(command.get("value", PUBLISH_INTERVAL))
            if new_interval > 0:
                PUBLISH_INTERVAL = new_interval
                print(
                    f"⚙️ The data transmission interval has been changed to {PUBLISH_INTERVAL} seconds.")
    except (json.JSONDecodeError, ValueError) as e:
        print(f"⚠️ Unable to process command: {e}")


def generate_telemetry():
    """Generates telemetry data with anomalies."""
    temperature = round(random.uniform(18.0, 25.0), 2)
    humidity = round(random.uniform(40.0, 60.0), 2)

    # We simulate an anomaly (a sharp jump in temperature) in 5% of cases.
    if random.random() < 0.05:
        print("🔥 Anomaly! A sharp jump in temperature.")
        temperature = round(random.uniform(50.0, 70.0), 2)

    return {
        "deviceId": DEVICE_ID,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "data": {
            "temperature": temperature,
            "humidity": humidity
        }
    }


# --- Main code ---
if __name__ == "__main__":
    client = mqtt.Client(client_id=DEVICE_ID)
    client.on_connect = on_connect
    client.on_message = on_message

    print(
        f"🔌 Device {DEVICE_ID} is attempting to connect to {MQTT_BROKER_HOST}:{MQTT_BROKER_PORT}...")
    client.connect(MQTT_BROKER_HOST, MQTT_BROKER_PORT, 60)

    # We start the message processing cycle in the background thread.
    client.loop_start()

    try:
        while True:
            telemetry_data = generate_telemetry()
            payload = json.dumps(telemetry_data)

            # Publish data in the telemetry topic
            result = client.publish(TELEMETRY_TOPIC, payload)

            # Checking the success of the delivery
            if result.rc == mqtt.MQTT_ERR_SUCCESS:
                print(f"📡 Data sent: {payload}")
            else:
                print(f"⚠️ Failed to send message, code: {result.rc}")

            time.sleep(PUBLISH_INTERVAL)
    except KeyboardInterrupt:
        print("\n🛑 The simulator has been stopped.")
    finally:
        client.loop_stop()
        client.disconnect()
        print("🔌 Disconnected from the MQTT broker.")

import asyncio
import aiomqtt
import json
import random
import os
import sys
from datetime import datetime, timezone
from dotenv import load_dotenv

load_dotenv()
MQTT_BROKER = os.getenv('MQTT_BROKER_HOST', "localhost")
MQTT_PORT = int(os.getenv('MQTT_BROKER_PORT', "1883"))
TELEMETRY_TOPIC = os.getenv('TELEMETRY_TOPIC', "iot/telemetry")

NUM_DEVICES = 1000
PUBLISH_INTERVAL = 1.0

messages_sent = 0

async def simulate_device(device_id: str):
    global messages_sent

    await asyncio.sleep(random.uniform(0, 10.0))

    client = aiomqtt.Client(
        hostname=MQTT_BROKER,
        port=MQTT_PORT,
        identifier=device_id,
        username="AnonymousIoT",
        password="dumm"
    )

    try:
        async with client:
            current_temp = random.uniform(18.0, 22.0)
            target_temp = 25.0

            while True:
                heating_on = current_temp < target_temp
                if heating_on:
                    current_temp += random.uniform(0.01, 0.05)
                else:
                    current_temp -= random.uniform(0.01, 0.05)

                payload = {
                    "deviceId": device_id,
                    "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
                    "data": {
                        "currentTemperature": round(current_temp, 2),
                        "targetTemperature": target_temp,
                        "heatingStatus": heating_on
                    }
                }

                topic = f"{TELEMETRY_TOPIC}/{device_id}"
                await client.publish(topic, payload=json.dumps(payload), qos=1)

                messages_sent += 1

                await asyncio.sleep(PUBLISH_INTERVAL)

    except aiomqtt.MqttError as e:
        print(f"[{device_id}] Connection error: {e}")
    except Exception as e:
        print(f"[{device_id}] Error: {e}")

async def stats_reporter():
    global messages_sent
    while True:
        await asyncio.sleep(5)
        print(f"[Stats] Sent {messages_sent} messages in the last 5 seconds (Throughput: {messages_sent/5:.1f} msg/sec)")
        messages_sent = 0

async def main():
    print(f"Starting load test: {NUM_DEVICES} devices -> {MQTT_BROKER}:{MQTT_PORT}")
    print("Please wait ~10 seconds for all devices to connect smoothly...")

    tasks = []
    for i in range(NUM_DEVICES):
        device_id = f"loadtest_device_{i:04d}"
        tasks.append(simulate_device(device_id))

    tasks.append(stats_reporter())

    await asyncio.gather(*tasks)

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nLoad test stopped by user.")
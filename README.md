# Smart-IoT-Dashboard

## Docker layout

- **Default stack** (no profile): Postgres, InfluxDB, Grafana, ZooKeeper, Kafka (+ ACL init), Redis, Mosquitto (go-auth), RabbitMQ, Keycloak, MQTT→Kafka bridge.
- **`apps` profile**: `user-device-service` (Spring), `frontend` (nginx + built SPA), Flink JobManager + TaskManager.
- **`flink-submit` profile** (use together with `apps`): builds the Flink fat-jar image and submits the streaming job once. Omit this profile after the job is already running to avoid duplicate submissions on `docker compose up`.
- **`sim` profile**: optional Python `device-simulator` container.

### Switching laptop vs PC vs all-in-Docker

1. Copy `.env.example` to `.env` and adjust values for the machine you are on.
2. **New variables (recommended)**  
   - `IOT_PUBLIC_HOST` — IP or DNS name that **browsers** use to reach Keycloak (`:8180`), API (`:8088`), and the UI (`FRONTEND_HOST_PORT`, default `3000`).  
   - `KAFKA_ADVERTISED_HOST` — optional; Kafka external listener; defaults to `IOT_PUBLIC_HOST` then `Laptop_IP`.  
   - `KEYCLOAK_PUBLIC_HOST` — optional; Keycloak `KC_HOSTNAME`; defaults to `IOT_PUBLIC_HOST` then `Laptop_IP`.  
   - `SPRING_HOST_HTTP` — host/DNS **as seen from Mosquitto and Keycloak** for HTTP calls into Spring: use the LAN IP of the PC when Spring runs on the host (hybrid), or `user-device-service` when Spring runs in Docker with profile `apps`.
3. **Legacy variables (still supported)**  
   - `Laptop_IP`, `PC_IP` — used as fallbacks in `docker-compose.yml` where older snippets expected them.

After changing `.env`, recreate Mosquitto so go-auth picks up the new backend host:

`docker compose up -d --build mosquitto`

### Typical commands

```bash
# Infra only (hybrid: run Spring/Flink/UI on the host, point them at Kafka on this machine)
docker compose up -d

# Full stack in Docker (Spring + UI + Flink cluster; submit job separately)
docker compose --profile apps up -d --build
docker compose --profile apps --profile flink-submit up -d --build

# Optional simulator (set SIM_USER_DEVICE_SERVICE_URL in .env if Spring is not in Docker)
docker compose --profile sim up -d --build
```

Spring in Docker loads `docker/config/spring/application-docker.yml` (mounted in the image) and expects `KEYCLOAK_ISSUER_URI` to match the `iss` claim in tokens (same host/port you use in the browser for Keycloak).

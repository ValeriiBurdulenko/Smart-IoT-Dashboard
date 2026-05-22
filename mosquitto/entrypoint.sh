#!/bin/sh
set -e
# SPRING_HOST_HTTP: Docker DNS name of backend (e.g. user-device-service) or LAN IP when Spring runs on host.
if [ -z "${SPRING_HOST_HTTP}" ]; then
  echo "SPRING_HOST_HTTP is not set. Set it in .env (e.g. same as PC_IP for hybrid, or user-device-service for all-in-docker)." >&2
  exit 1
fi
export SPRING_HOST_HTTP
TMP_CFG="/mosquitto/config/mosquitto.generated.conf"
envsubst < /mosquitto/config/mosquitto.conf.template > "${TMP_CFG}"
chown mosquitto:mosquitto "${TMP_CFG}" 2>/dev/null || true
exec gosu mosquitto /usr/sbin/mosquitto -c "${TMP_CFG}" "$@"

#!/usr/bin/env bash
set -euo pipefail

BROKER_HOST="${BROKER_HOST:-127.0.0.1}"
BROKER_PORT="${BROKER_PORT:-1883}"
REQ_TOPIC="${REQ_TOPIC:-portal/db/user/request}"
REQ_ID="dbreq-$(date +%s)"
R1="portal/db/user/response/${REQ_ID}-save"
R2="portal/db/user/response/${REQ_ID}-find-email"
R3="portal/db/user/response/${REQ_ID}-find-phone"
USER_ID="u-${REQ_ID}"
EMAIL="${REQ_ID}@example.com"
PHONE="+5255000${RANDOM}"
NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

listen_one() {
  local topic="$1"
  timeout 8s mosquitto_sub -h "$BROKER_HOST" -p "$BROKER_PORT" -t "$topic" -C 1
}

echo "[1/3] user_save"
mosquitto_pub -h "$BROKER_HOST" -p "$BROKER_PORT" -t "$REQ_TOPIC" -m "{\"requestId\":\"${REQ_ID}-save\",\"op\":\"user_save\",\"replyTopic\":\"${R1}\",\"userId\":\"${USER_ID}\",\"firstName\":\"Db\",\"lastName\":\"Mqtt\",\"age\":31,\"email\":\"${EMAIL}\",\"phone\":\"${PHONE}\",\"mobile\":\"${PHONE}\",\"address\":\"mx\",\"socialFacebook\":\"dbuser\",\"socialInstagram\":\"dbuser\",\"socialTiktok\":\"dbuser\",\"socialX\":\"dbuser\",\"passwordHash\":\"hash\",\"passwordSalt\":\"salt\",\"createdAt\":\"${NOW}\",\"updatedAt\":\"${NOW}\"}"
RESP1="$(listen_one "$R1")"
echo "$RESP1"
[[ "$RESP1" == *'"status":"ok"'* ]]

echo "[2/3] user_find_email"
mosquitto_pub -h "$BROKER_HOST" -p "$BROKER_PORT" -t "$REQ_TOPIC" -m "{\"requestId\":\"${REQ_ID}-find-email\",\"op\":\"user_find_email\",\"replyTopic\":\"${R2}\",\"email\":\"${EMAIL}\"}"
RESP2="$(listen_one "$R2")"
echo "$RESP2"
[[ "$RESP2" == *'"status":"ok"'* ]]
[[ "$RESP2" == *'"found":true'* ]]
[[ "$RESP2" == *"\"email\":\"${EMAIL}\""* ]]

echo "[3/3] user_find_phone"
mosquitto_pub -h "$BROKER_HOST" -p "$BROKER_PORT" -t "$REQ_TOPIC" -m "{\"requestId\":\"${REQ_ID}-find-phone\",\"op\":\"user_find_phone\",\"replyTopic\":\"${R3}\",\"phone\":\"${PHONE}\"}"
RESP3="$(listen_one "$R3")"
echo "$RESP3"
[[ "$RESP3" == *'"status":"ok"'* ]]
[[ "$RESP3" == *'"found":true'* ]]
[[ "$RESP3" == *"\"phone\":\"${PHONE}\""* ]]

echo "db-mqtt-e2e: OK"

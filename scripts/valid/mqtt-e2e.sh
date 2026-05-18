#!/usr/bin/env bash
set -euo pipefail

BROKER_HOST="${BROKER_HOST:-127.0.0.1}"
BROKER_PORT="${BROKER_PORT:-1883}"
REQ_ID="req-$(date +%s)"

listen_once() {
  local topic="$1"
  timeout 8s mosquitto_sub -h "$BROKER_HOST" -p "$BROKER_PORT" -t "$topic" -C 1
}

echo "[1/3] register request"
mosquitto_pub -h "$BROKER_HOST" -p "$BROKER_PORT" -t portal/register/in -m "{\"requestId\":\"${REQ_ID}-reg\",\"template\":\"casa\",\"firstName\":\"MQ\",\"lastName\":\"User\",\"email\":\"mq-user-${REQ_ID}@example.com\",\"mobile\":\"+525550001111\",\"password\":\"pwd123\"}"
listen_once "portal/register/out"

echo "[2/3] login request"
mosquitto_pub -h "$BROKER_HOST" -p "$BROKER_PORT" -t portal/login/in -m "{\"requestId\":\"${REQ_ID}-login\",\"identifier\":\"mq-user-${REQ_ID}@example.com\",\"password\":\"pwd123\"}"
listen_once "portal/login/out"

echo "[3/3] issue password request"
mosquitto_pub -h "$BROKER_HOST" -p "$BROKER_PORT" -t portal/password/issue/in -m "{\"requestId\":\"${REQ_ID}-pwd\",\"email\":\"mq-user-${REQ_ID}@example.com\"}"
listen_once "portal/password/issue/out"

#!/usr/bin/env bash
set -euo pipefail

OUT_DIR="${OUT_DIR:-security/jwt}"
CN="${CN:-portal-captive-jwt}"
DAYS="${DAYS:-3650}"
ALG="${ALG:-rsa}"

mkdir -p "${OUT_DIR}"

KEY_FILE="${OUT_DIR}/jwt-signing.key.pem"
CERT_FILE="${OUT_DIR}/jwt-signing.crt.pem"
PUB_FILE="${OUT_DIR}/jwt-signing.pub.pem"
ENV_FILE="${OUT_DIR}/jwt-signing.env"

if [[ "${ALG}" == "rsa" ]]; then
  openssl req -x509 -newkey rsa:4096 -sha256 -nodes \
    -keyout "${KEY_FILE}" \
    -out "${CERT_FILE}" \
    -days "${DAYS}" \
    -subj "/CN=${CN}"
  JWT_ALG="RS256"
elif [[ "${ALG}" == "ec" ]]; then
  openssl ecparam -name prime256v1 -genkey -noout -out "${KEY_FILE}"
  openssl req -new -x509 -key "${KEY_FILE}" -sha256 \
    -out "${CERT_FILE}" \
    -days "${DAYS}" \
    -subj "/CN=${CN}"
  JWT_ALG="ES256"
else
  echo "ALG inválido: ${ALG} (usa rsa|ec)"
  exit 1
fi

openssl x509 -in "${CERT_FILE}" -pubkey -noout > "${PUB_FILE}"

chmod 600 "${KEY_FILE}"
chmod 644 "${CERT_FILE}" "${PUB_FILE}"

KID="$(openssl x509 -in "${CERT_FILE}" -noout -fingerprint -sha256 | cut -d= -f2 | tr -d ':' | tr 'A-Z' 'a-z')"

cat > "${ENV_FILE}" <<EOF
# Variables sugeridas para firma/validación JWT
JWT_SIGNING_KEY_PATH=${KEY_FILE}
JWT_SIGNING_CERT_PATH=${CERT_FILE}
JWT_VERIFY_PUBLIC_KEY_PATH=${PUB_FILE}
JWT_KID=${KID}
JWT_ALG=${JWT_ALG}
EOF

cat <<EOF
OK: material JWT generado en ${OUT_DIR}
- Private key: ${KEY_FILE}
- Certificate: ${CERT_FILE}
- Public key:  ${PUB_FILE}
- Env file:    ${ENV_FILE}
- kid:         ${KID}

Uso sugerido:
  source "${ENV_FILE}"
EOF


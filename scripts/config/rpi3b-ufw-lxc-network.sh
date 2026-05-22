#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Este script debe ejecutarse como root (sudo)."
  exit 1
fi

EXT_IF="${EXT_IF:-eth0}"
CT_IP="${CT_IP:-10.0.3.15}"
CT_NET="${CT_NET:-10.0.3.0/24}"
LXC_IF="${LXC_IF:-lxcbr0}"

# Accept both plain values (eth0) and key=value forms (ext_if=eth0).
EXT_IF="${EXT_IF#*=}"
CT_IP="${CT_IP#*=}"
CT_NET="${CT_NET#*=}"
LXC_IF="${LXC_IF#*=}"

if ! command -v ufw >/dev/null 2>&1; then
  echo "Falta ufw. Instala primero: apt-get install -y ufw"
  exit 1
fi

if [[ ! -f /etc/ufw/before.rules ]]; then
  echo "No existe /etc/ufw/before.rules"
  exit 1
fi

echo "[ufw-lxc] EXT_IF=$EXT_IF CT_IP=$CT_IP CT_NET=$CT_NET LXC_IF=$LXC_IF"

CHANGED=0

# Forward policy
if ! grep -q '^DEFAULT_FORWARD_POLICY="ACCEPT"$' /etc/default/ufw; then
  sed -i 's/^DEFAULT_FORWARD_POLICY=.*/DEFAULT_FORWARD_POLICY="ACCEPT"/' /etc/default/ufw
  CHANGED=1
fi
if ! grep -q '^net/ipv4/ip_forward=1$' /etc/ufw/sysctl.conf; then
  echo 'net/ipv4/ip_forward=1' >> /etc/ufw/sysctl.conf
  CHANGED=1
fi
sysctl -w net.ipv4.ip_forward=1 >/dev/null

# Rebuild NAT block to avoid malformed or duplicated legacy entries.
cp /etc/ufw/before.rules /etc/ufw/before.rules.bak
awk '
  BEGIN { in_nat=0 }
  /^\*nat$/ { in_nat=1; next }
  {
    if (in_nat) {
      if ($0 == "COMMIT") { in_nat=0; next }
      next
    }
    if ($0 ~ /ct_ip=/) next
    print $0
  }
' /etc/ufw/before.rules.bak > /tmp/before.rules.clean

awk -v ct_net="$CT_NET" -v ext_if="$EXT_IF" -v ct_ip="$CT_IP" '
  BEGIN { inserted=0 }
  {
    if (!inserted && $0 ~ /^\*filter$/) {
      print "*nat"
      print ":PREROUTING ACCEPT [0:0]"
      print ":POSTROUTING ACCEPT [0:0]"
      print "-A PREROUTING -i " ext_if " -p tcp --dport 80 -j DNAT --to-destination " ct_ip ":80"
      print "-A POSTROUTING -s " ct_net " -o " ext_if " -j MASQUERADE"
      print "COMMIT"
      print ""
      inserted=1
    }
    print $0
  }
' /tmp/before.rules.clean > /tmp/before.rules.new

if ! cmp -s /tmp/before.rules.new /etc/ufw/before.rules; then
  mv /tmp/before.rules.new /etc/ufw/before.rules
  CHANGED=1
else
  rm -f /tmp/before.rules.new
fi
rm -f /tmp/before.rules.clean

# UFW rules
RULE_OUT="$(ufw allow 22/tcp 2>&1 || true)"
if [[ "$RULE_OUT" != *"Skipping adding existing rule"* ]]; then CHANGED=1; fi
RULE_OUT="$(ufw allow 80/tcp 2>&1 || true)"
if [[ "$RULE_OUT" != *"Skipping adding existing rule"* ]]; then CHANGED=1; fi
RULE_OUT="$(ufw route allow in on "$LXC_IF" out on "$EXT_IF" 2>&1 || true)"
if [[ "$RULE_OUT" != *"Skipping adding existing rule"* ]]; then CHANGED=1; fi
RULE_OUT="$(ufw route allow in on "$EXT_IF" out on "$LXC_IF" to "$CT_IP" port 80 proto tcp 2>&1 || true)"
if [[ "$RULE_OUT" != *"Skipping adding existing rule"* ]]; then CHANGED=1; fi

if [[ "$CHANGED" -eq 0 ]]; then
  echo "[ufw-lxc] Sin cambios: configuración ya aplicada."
  exit 0
fi

# Reload only if active; do not force-enable unexpectedly.
if ufw status | grep -q '^Status: active'; then
  ufw reload
else
  echo "[ufw-lxc] UFW está inactivo; reglas guardadas. Activa UFW cuando corresponda."
fi

echo "[ufw-lxc] OK"
ufw status verbose

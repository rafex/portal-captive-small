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

# Forward policy
sed -i 's/^DEFAULT_FORWARD_POLICY=.*/DEFAULT_FORWARD_POLICY="ACCEPT"/' /etc/default/ufw
grep -q '^net/ipv4/ip_forward=1' /etc/ufw/sysctl.conf || echo 'net/ipv4/ip_forward=1' >> /etc/ufw/sysctl.conf
sysctl -w net.ipv4.ip_forward=1 >/dev/null

# NAT block for LXC network + DNAT publish 80->container:80 (idempotent)
if ! grep -q "PREROUTING -i ${EXT_IF} -p tcp --dport 80 -j DNAT --to-destination ${CT_IP}:80" /etc/ufw/before.rules; then
  awk -v ct_net="$CT_NET" -v ext_if="$EXT_IF" -v ct_ip="$CT_IP" '
    BEGIN{inserted=0}
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
  ' /etc/ufw/before.rules > /tmp/before.rules.new
  mv /tmp/before.rules.new /etc/ufw/before.rules
fi

# UFW rules
ufw allow 22/tcp
ufw allow 80/tcp
ufw route allow in on "$LXC_IF" out on "$EXT_IF"
ufw route allow in on "$EXT_IF" out on "$LXC_IF" to "$CT_IP" port 80 proto tcp

# Reload
ufw --force disable
ufw --force enable

echo "[ufw-lxc] OK"
ufw status verbose

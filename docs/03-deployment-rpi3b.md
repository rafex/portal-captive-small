# Despliegue en Raspberry Pi 3B

## Requisitos del host
- Raspberry Pi OS Lite (64-bit recomendado)
- Acceso root/sudo
- Conectividad a internet

## Build de release
El release se construye en GitHub Actions con:
- matrix de runners nativos GitHub (`x86_64` y `arm64`) sin cross-compilación
- binario nativo GraalVM 25 del `auth-service` por arquitectura (`auth-service-<arch>`)
- binario Rust `db-mqtt-worker` por arquitectura (`db-mqtt-worker-<arch>`)

No se requiere `openjdk-21-jre-headless` en runtime del contenedor.

## 1) Preparar host para LXC
```bash
sudo bash scripts/install/rpi3b-prepare-lxc.sh
```

## 2) Instalar release en host
```bash
sudo bash scripts/install/rpi3b-lxc-install.sh v0.1.0
```

## 3) Arrancar stack en LXC
```bash
sudo bash scripts/install/rpi3b-direct-install.sh v0.1.0
```
Nota:
- El script intenta descargar `lxc-image-<version>-arm64.tar.gz` desde el release y usarlo como base del contenedor.
- Si el artefacto no existe o falla validación SHA256, hace fallback automático a `lxc-create -t download`.

## 4) Smoke de artifacts release
```bash
sudo VERSION=v0.1.0 bash scripts/valid/rpi3b-lxc-smoke.sh
```

## 5) Verificación
```bash
curl -sS http://127.0.0.1:8080/health
curl -sS http://127.0.0.1:8080/health/db-mqtt
curl -sS http://127.0.0.1:8080/metrics/db-mqtt
curl -sS http://127.0.0.1:8080/metrics/db-mqtt/prometheus
```

## 6) Publicar frontend del contenedor hacia la red externa
Suposiciones:
- IP del contenedor LXC: `10.0.3.15`
- Interfaz externa de la Raspi: `eth0` (cambiar a `wlan0` si aplica)
- Frontend en contenedor escuchando en `10.0.3.15:80`

### IP interna fija del contenedor
El contenedor `portal-captive` usa IP estática por configuración LXC:
- `lxc.net.0.ipv4.address = 10.0.3.15/24`
- `lxc.net.0.ipv4.gateway = 10.0.3.1`

Archivo:
- `containers/lxc/portal-captive.conf`

Validación después de instalar/recrear:
```bash
lxc-info -n portal-captive -iH
```
La primera IPv4 debe ser `10.0.3.15`.

### Opción A: iptables
```bash
sudo sysctl -w net.ipv4.ip_forward=1
grep -q '^net.ipv4.ip_forward=1' /etc/sysctl.conf || echo 'net.ipv4.ip_forward=1' | sudo tee -a /etc/sysctl.conf

# Mantener administración SSH de la Raspi
sudo iptables -A INPUT -p tcp --dport 22 -j ACCEPT

# Publicar HTTP externo -> contenedor
sudo iptables -t nat -A PREROUTING -i eth0 -p tcp --dport 80 -j DNAT --to-destination 10.0.3.15:80
sudo iptables -A FORWARD -i eth0 -o lxcbr0 -p tcp -d 10.0.3.15 --dport 80 -m state --state NEW,ESTABLISHED,RELATED -j ACCEPT
sudo iptables -A FORWARD -i lxcbr0 -o eth0 -p tcp -s 10.0.3.15 --sport 80 -m state --state ESTABLISHED,RELATED -j ACCEPT
```
Persistencia:
```bash
sudo apt-get update && sudo apt-get install -y iptables-persistent
sudo netfilter-persistent save
```

### Opción B: nftables (recomendada)
```bash
sudo tee /etc/nftables.conf >/dev/null <<'EOF'
#!/usr/sbin/nft -f
flush ruleset

table inet filter {
  chain input {
    type filter hook input priority 0; policy drop;
    iifname "lo" accept
    ct state established,related accept
    tcp dport 22 accept
  }
  chain forward {
    type filter hook forward priority 0; policy drop;
    ct state established,related accept
    iifname "eth0" oifname "lxcbr0" ip daddr 10.0.3.15 tcp dport 80 accept
  }
}

table ip nat {
  chain prerouting {
    type nat hook prerouting priority dstnat; policy accept;
    iifname "eth0" tcp dport 80 dnat to 10.0.3.15:80
  }
}
EOF

sudo sysctl -w net.ipv4.ip_forward=1
grep -q '^net.ipv4.ip_forward=1' /etc/sysctl.conf || echo 'net.ipv4.ip_forward=1' | sudo tee -a /etc/sysctl.conf
sudo systemctl enable nftables
sudo systemctl restart nftables
sudo nft list ruleset
```

### Opción C: UFW
```bash
sudo sed -i 's/^DEFAULT_FORWARD_POLICY=.*/DEFAULT_FORWARD_POLICY="ACCEPT"/' /etc/default/ufw
grep -q '^net/ipv4/ip_forward=1' /etc/ufw/sysctl.conf || echo 'net/ipv4/ip_forward=1' | sudo tee -a /etc/ufw/sysctl.conf
```
Agregar bloque NAT al inicio de `/etc/ufw/before.rules` (antes de `*filter`):
```text
*nat
:PREROUTING ACCEPT [0:0]
:POSTROUTING ACCEPT [0:0]
-A PREROUTING -i eth0 -p tcp --dport 80 -j DNAT --to-destination 10.0.3.15:80
-A POSTROUTING -d 10.0.3.15 -p tcp --dport 80 -j MASQUERADE
COMMIT
```
Aplicar políticas UFW:
```bash
# Mantener administración SSH de la Raspi
sudo ufw allow 22/tcp

# Publicar frontend hacia contenedor
sudo ufw allow 80/tcp
sudo ufw route allow in on eth0 out on lxcbr0 to 10.0.3.15 port 80 proto tcp

sudo ufw disable
sudo ufw enable
sudo ufw status verbose
```

## 7) Verificación externa
Desde otro equipo de la red:
```bash
curl -I http://IP_DE_LA_RASPI/
ssh <usuario>@IP_DE_LA_RASPI
```

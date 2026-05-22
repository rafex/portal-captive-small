# CHANGE

## v0.2.5
- feat(rpi3b): ship runtime start-services script inside release image and backend tar
- fix(rpi3b): start services via single in-container bootstrap script
- fix(rpi3b): use nohup-based detached startup in install and smoke
- fix(rpi3b): wait for mosquitto readiness before worker startup
- fix(rpi3b): replace ss dependency with mosquitto_pub broker probe
- fix(rpi3b): reuse existing mosquitto listener and validate app pids


## v0.2.4
- fix(rpi3b): remove pgrep dependency in minimal lxc image startup
- fix(rpi3b): create /etc/network before writing static interfaces


## v0.2.3
- fix(lxc-image): preinstall runtime deps and harden dns/apt fallback in installer


## v0.2.2
- fix(release-lxc): include systemd-sysv so /sbin/init exists in image


## v0.2.1
- fix(ci): remove unpacked lxc-image tree before upload-artifact


## v0.2.0
- feat(release): publish arm64 lxc image artifact and consume it on rpi install
- fix(rpi3b): enforce static container IP in lxc config and guest network
- fix(rpi3b): prefer repo LXC config over release copy for static IP
- fix(ufw): rebuild before.rules nat block and purge malformed entries
- fix(ufw): accept key=value args from just recipe
- fix(ufw): add DNAT 80->container and publish just config-ufw-lxc
- feat(just): auto-update CHANGE/RELEASE in tag-create


## Unreleased
- Documentación SpecNative del incidente de arranque LXC en Raspberry Pi 3B (`docs/09-rpi3b-lxc-incident-specnative.md`).
- Registro de causa raíz y reparaciones aplicadas en scripts de instalación/smoke.

## v0.1.0
- Initial public baseline for captive portal stack.

## v0.1.1
- CI/CD hardening for multi-arch release and validation stability.

## v0.1.3
- Release notes pending final summary.

## v0.1.4
- Fix Raspberry Pi LXC startup: ensure SQLite data directory exists before Rust worker starts.
- Improve LXC service restart sequence for mosquitto, Rust worker, and auth-service.
- Build auth-service native image with compatibility CPU target for broader ARM support.

## v0.1.5
- Fix GraalVM native-image flag for ARM compatibility build (`-march=compatibility`).

## v0.1.6
- Fix LXC startup script termination issue caused by self-matching process kill patterns.
- Stabilize direct install and smoke flow for restarting db worker/auth-service processes.

## v0.1.7
- Fix LXC restart routines to avoid killing current shell when matching process patterns.

## v0.1.8
- Force LXC container recreation on each direct-install and smoke run for deterministic clean state.

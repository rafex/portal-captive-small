# RELEASE

## v0.2.2
- Artifacts publicados para v0.2.2.
- Resumen de cambios incluidos:
- fix(release-lxc): include systemd-sysv so /sbin/init exists in image


## v0.2.1
- Artifacts publicados para v0.2.1.
- Resumen de cambios incluidos:
- fix(ci): remove unpacked lxc-image tree before upload-artifact


## v0.2.0
- Artifacts publicados para v0.2.0.
- Resumen de cambios incluidos:
- feat(release): publish arm64 lxc image artifact and consume it on rpi install
- fix(rpi3b): enforce static container IP in lxc config and guest network
- fix(rpi3b): prefer repo LXC config over release copy for static IP
- fix(ufw): rebuild before.rules nat block and purge malformed entries
- fix(ufw): accept key=value args from just recipe
- fix(ufw): add DNAT 80->container and publish just config-ufw-lxc
- feat(just): auto-update CHANGE/RELEASE in tag-create


## Unreleased
- Se agrega documentación SpecNative de incidentes de despliegue en Raspi3B/LXC.
- Se consolidan fallos reales observados, evidencia técnica, causa raíz y fixes verificados.

## v0.1.0
- Frontend and backend initial release artifacts.

## v0.1.1
- Improved release packaging, GitHub workflows, and local hook automation.

## v0.1.3
- Release artifacts for Raspberry Pi/LXC deployment updates.

## v0.1.4
- Raspberry Pi 3B installation reliability update for LXC runtime startup.
- Native-image compatibility tuning for ARM deployment targets.

## v0.1.5
- Release with corrected GraalVM ARM native-image build flag.

## v0.1.6
- LXC process restart robustness fix for Raspberry Pi deployment scripts.

## v0.1.7
- Reliable process cleanup in LXC startup scripts (self-PID exclusion).

## v0.1.8
- Deterministic LXC deploy: container is destroyed/recreated on each install run.

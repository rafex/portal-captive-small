# CHANGE

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

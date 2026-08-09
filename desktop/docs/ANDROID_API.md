# Tumira Android API Contract

This document describes the backend Socket.IO and REST contract for Android clients integrating with Tumira.

Socket.IO (Realtime)
- Connect to server via Socket.IO (ws:// or wss://) on the same port as HTTP server.
- Events:
  - `handshake` (client -> server): payload `{ deviceName, deviceType, appVersion }`, optional callback -> server responds `{ success, serverName, serverVersion, transferSupported }`.
  - `heartbeat` (client -> server): payload optional; server replies `heartbeat_ack` with timestamp.
  - `list_devices` (client -> server): callback returns `{ success: true, devices: [...] }`.
  - `request_transfer` (client -> server): `{ to, fileName, size, mimeType }` -> server validates and returns `{ success: true, sessionId }` or error.
  - `TRANSFER_START` (server -> clients): `{ sessionId, sender, receiver, fileName, size, mimeType }` when transfer initiated.
  - `TRANSFER_PROGRESS` (both ways): `{ sessionId, transferred }` emitted to sender and receiver to update progress.
  - `TRANSFER_COMPLETE` (server -> clients): session object when transfer completes.
  - `TRANSFER_FAILED` (server -> clients): session object with `reason` when failed.

REST Endpoints
- `GET /api/ping` - health check returns `{ status: 'online' }`.
- `GET /api/device` - returns server device info `{ deviceName, version }`.
- `GET /api/files` - list files metadata.
- `GET /api/files/:id` - download file (supports range requests).
- `POST /api/files/upload` - upload file (supports chunked uploads). See headers: `x-upload-id`, `x-chunk-index`, `x-total-chunks`, `x-filename`, `x-mime-type` for chunked uploads.

Security & Validation
- Server enforces `MAX_FILE_SIZE` and allowed MIME types from configuration.
- Duplicate transfer sessions for same sender/receiver/filename are rejected.

Discovery
- The server advertises the `_queryshare._tcp` compatibility service via mDNS/Bonjour. Android clients can discover the service on the local network.

Hotspot Pairing
- `GET /api/device` returns the preferred local IP address plus an `interfaces` array of available IPv4 adapters.
- The desktop dashboard encodes the selected adapter IP plus fallback adapter IPs into a QR payload: `{ deviceName, ip, alternateIps, port, protocol, version, mode: 'hotspot' }`.
- Android accepts the same QR payload shape as regular manual pairing and tries `ip` first, then each `alternateIps` value before reporting failure.
- The Android app warns when the active network is mobile data, because direct transfers require the phone to join the laptop hotspot or the same Wi-Fi network.

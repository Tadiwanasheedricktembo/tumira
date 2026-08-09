# Tumira Server

Local network file sharing backend for Android and desktop devices.

## Features

- Local LAN discovery via mDNS/Bonjour
- Hotspot pairing flow for direct transfers when the phone is not already on the same Wi-Fi
- File upload/download with metadata storage
- Socket.IO realtime upload/download events
- Resumable chunked upload support
- Static admin dashboard for connected devices and activity

## Run locally

1. Install dependencies:
   ```bash
   npm install
   ```
2. Start the server:
   ```bash
   npm start
   ```
3. Open the admin dashboard at `http://<server-ip>:3000`

## Hotspot Pairing

Tumira transfers files directly over a reachable local network. If the laptop and phone are not on the same Wi-Fi, use the laptop hotspot:

1. Start the Tumira desktop server.
2. Open the dashboard and select **Hotspot**.
3. Turn on the laptop mobile hotspot in Windows settings.
4. Connect the Android phone to that hotspot Wi-Fi.
5. Scan the dashboard QR code from the Tumira Android app.

The Hotspot screen lets you choose which local adapter/IP address is encoded in the QR code. If pairing fails, select the address that belongs to the hotspot/Wi-Fi adapter and scan again.

This mode does not relay through the internet. If the phone stays on mobile data and the laptop stays on another Wi-Fi, the phone cannot reach the laptop's private LAN address.

## Configuration

Environment variables:

- `PORT` - HTTP port (default `3000`)
- `DEVICE_NAME` - device name advertised on LAN
- `TUMIRA_VERSION` - server version
- `MAX_FILE_SIZE` - upload max size in bytes (default `10737418240` = 10GB)
- `SHARED_DIR` - directory for shared files
- `DB_PATH` - path to JSON metadata storage

## Deployment

This server supports Windows, Linux, and Raspberry Pi.

- Ensure Node.js is installed.
- Install dependencies with `npm install`.
- Set environment variables if desired.
- Run `npm start`.
- Verify local discovery via Bonjour or the `_queryshare._tcp` compatibility service.

## Socket.IO Events (Backend Contract)

- Clients should perform a `handshake` after connecting with `{ deviceName, deviceType, appVersion }` and expect `{ success, serverName, serverVersion, transferSupported }`.
- Use `request_transfer` to initiate a transfer. Server will emit `TRANSFER_START`, then `TRANSFER_PROGRESS`, and finally `TRANSFER_COMPLETE` or `TRANSFER_FAILED`.

## Service Advertisement

- The server advertises the `_queryshare._tcp` compatibility service using mDNS/Bonjour. The advertised TXT contains `deviceName` and `version`.

## Folder Structure (important files)

- `src/server.js` - starts Express + Socket.IO and discovery
- `src/socket.js` - Socket.IO event handling and connection manager integration
- `src/connectionManager.js` - tracks active clients and transfer sessions
- `src/routes` - REST endpoints for files and device info
- `docs/ANDROID_API.md` - Android contract for integration

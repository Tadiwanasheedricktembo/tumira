# Tumira

Tumira is a local-network file sharing project for Android and desktop devices. It supports discovery, pairing, and transfer of files over a local network without relying on the internet.

## Project structure

- android/: Android app source and Gradle project
- desktop/: Node.js server and web dashboard for local transfers
- docs/: integration and API notes

## Quick start

### Desktop server

```bash
cd desktop
npm install
npm start
```

### Android app

Open the Android project in Android Studio and run it on a device/emulator.

## Notes

- The desktop server uses Bonjour/mDNS discovery and a Socket.IO-based transfer flow.
- The repository includes a root .gitignore to avoid committing build artifacts, temporary files, and dependency folders.

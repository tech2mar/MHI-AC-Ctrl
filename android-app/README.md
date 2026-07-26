# OpenWebUI Client (Android)

A minimal native Android app (Kotlin + Jetpack Compose) that connects to a
self-hosted [OpenWebUI](https://github.com/open-webui/open-webui) instance on
your home network and lets you chat with it from your phone.

This app is unrelated to the MHI-AC-Ctrl firmware in the rest of this
repository — it just happens to live in the same repo/branch.

## Features

- Connect to any OpenWebUI server reachable on your LAN via `http://` or `https://`
- Auth via OpenWebUI API key (Bearer token)
- Lists available models and lets you switch between them
- Chat UI with streaming responses (Server-Sent Events)
- Connection settings are stored locally (Jetpack DataStore) and can be
  changed again later via the settings icon in the chat screen

## Requirements

- Android Studio (Koala or newer) with Android SDK 34 installed
- A device or emulator running Android 8.0 (API 26) or newer
- An OpenWebUI instance reachable on your home network
- An OpenWebUI API key: in OpenWebUI, go to **Settings → Account → API keys**
  and create one

## Getting started

1. Open the `android-app/` folder as a project in Android Studio (or run
   `./gradlew assembleDebug` from a machine with the Android SDK set up).
2. Install the app on a device connected to the same Wi-Fi/LAN as your
   OpenWebUI server.
3. On first launch, enter your server URL (e.g. `http://192.168.1.50:3000`)
   and your API key, then tap **Verbinden**. The app fetches the model list
   to confirm the connection works, then takes you to the chat screen.
4. Pick a model from the dropdown in the top bar and start chatting.
5. Tap the gear icon any time to change server URL, API key, or model.

## Notes on networking

- The app allows cleartext (plain HTTP) traffic app-wide
  (`network_security_config.xml`), since most home OpenWebUI setups are only
  reachable via `http://<local-ip>:<port>` and not HTTPS. If your instance is
  behind a reverse proxy with a valid certificate, `https://` URLs work too.
- Access from outside your home network (e.g. via VPN/Tailscale/reverse
  proxy) works the same way — just point the server URL at whatever address
  is reachable from the phone.
- This app talks directly to the OpenWebUI REST API
  (`/api/models`, `/api/chat/completions`); it is not a WebView wrapper.

## Project layout

```
android-app/
  app/src/main/java/de/tech2mar/openwebui/
    data/                 SettingsRepository (DataStore), OpenWebUiApi (OkHttp + SSE), models
    ui/settings/          Server URL / API key screen
    ui/chat/              Chat screen, model picker, streaming message list
    ui/navigation/        Nav graph (settings <-> chat)
    ui/theme/              Material 3 theme
```

## Known limitation of this sandbox

This project was authored in a network-restricted sandbox without access to
the Android SDK or Google's Maven repository, so the Gradle build could not
be executed here. The Gradle wrapper is included and configured correctly;
opening the project in Android Studio (or running `./gradlew assembleDebug`
with network access) will download the Android Gradle Plugin, Compose
dependencies, and the Android SDK components as needed.

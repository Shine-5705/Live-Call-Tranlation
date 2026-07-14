# Live Call Translator

Real-time bilingual voice calling with server-side speech-to-text, translation, and text-to-speech. Built as an in-app VoIP (WebRTC) solution — **not** carrier/PSTN call interception.

## Architecture

```
[User A mic] ──WebRTC + PCM WS──► [Backend Pipeline] ──TTS + captions──► [User B]
[User B mic] ──WebRTC + PCM WS──► [Backend Pipeline] ──TTS + captions──► [User A]

Pipeline (per direction):
  PCM audio → Deepgram STT → Gemini Flash translation → Deepgram Aura TTS → listener
```

### Components

| Layer | Tech |
|-------|------|
| Android client | Kotlin, Jetpack Compose, WebRTC (`stream-webrtc-android`) |
| Backend | Node.js / TypeScript, Express, WebSocket |
| STT | Deepgram Nova-2 streaming |
| Translation | Gemini 2.0 Flash |
| TTS | Deepgram Aura |
| Signaling | WebSocket (`/signaling`) |
| Pipeline | WebSocket (`/pipeline`) |
| Auth | Short-lived JWT (`POST /auth/token`) |
| Local settings | EncryptedSharedPreferences |

### Android modules

```
app/src/main/java/com/gnani/livetranslation/
├── call/          WebRTC + signaling
├── settings/      Language preferences
├── captions/      Live transcript overlay
├── consent/       Mandatory first-use disclosure
├── pipeline/      Backend translation WebSocket client
├── audio/         PCM capture + TTS playback
└── service/       Foreground notification during calls
```

## Prerequisites

- Android Studio Ladybug+ (or compatible AGP 8.7)
- JDK 17
- Node.js 20+
- API keys: [Deepgram](https://deepgram.com), [Google AI (Gemini)](https://aistudio.google.com)

## Backend setup

```bash
cd backend
cp .env.example .env
# Edit .env with your API keys and JWT_SECRET
npm install
npm run dev
```

Server runs on `http://0.0.0.0:3000` with:
- `POST /auth/token` — issue session JWT
- `ws://host:3000/signaling` — WebRTC signaling
- `ws://host:3000/pipeline` — audio + captions

### Optional TURN server

For NAT traversal beyond STUN, configure coturn or a managed TURN provider in `.env`:

```
TURN_URL=turn:your-turn.example.com:3478
TURN_USERNAME=...
TURN_CREDENTIAL=...
```

## Android setup

1. Open the project root in Android Studio.
2. Default backend host for emulator: `10.0.2.2:3000` (set in `app/build.gradle.kts` → `BACKEND_HOST`).
3. For a physical device, change `BACKEND_HOST` to your machine's LAN IP (e.g. `192.168.1.42:3000`).
4. Build and run on two devices/emulators.

## Usage

1. **Consent** — Accept the mandatory disclosure on first launch.
2. **Settings** — Set source language (what you speak) and target language (what you want to hear). Save.
3. **Call** — User A creates a call with a room ID. User B joins the same room ID.
4. When both peers connect, translation starts automatically. Live captions appear on the call screen; a foreground notification persists while active.

## Important limitations

- **Not for regular phone calls.** Android blocks recording/injecting audio on carrier calls since Android 10. This app uses in-app WebRTC VoIP only.
- **Latency:** Expect ~1.5–3 seconds end-to-end per utterance (STT → translate → TTS).
- **Legal:** Both parties must consent to transcription/translation. Comply with recording laws in your jurisdiction.
- **No API keys in the APK.** All provider calls go through the backend.

## Security

- JWT auth with configurable secret
- Rate limiting on `/auth/token`
- TLS required in production (use HTTPS/WSS behind a reverse proxy)
- ProGuard/R8 enabled for release builds
- Minimal permissions: `RECORD_AUDIO`, `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`

## Release build

```bash
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/`

## License

Proprietary — Gnani.ai

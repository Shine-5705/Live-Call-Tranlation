# AI Call Translator — App to Real Phone Number

Call any real phone number with live AI translation. **Only you need the app** — the person who answers uses their normal phone.

## Architecture

```
[Android App — Twilio Voice SDK]     [Real phone — PSTN, no app]
         Leg A (VoIP)                        Leg B (PSTN)
              \                              /
               \                            /
            [Backend Orchestrator]
         Media Stream WS (per leg, NOT shared conference)
         STT → Translate → TTS → inject to other leg only
         Raw audio never passed between legs
```

## How it works

1. You enter a phone number (e.g. `+919876543210`) and tap **Call with Translation**
2. Backend places a **PSTN call** to that number (Leg B)
3. App connects via **Twilio Voice SDK** (Leg A)
4. Each leg gets its own **bidirectional Media Stream** to the backend
5. When they answer, they hear an **AI disclosure** message first
6. Your Hindi → translated to English → injected into their leg only
7. Their English → translated to Hindi → you hear via your app leg

## Setup

### 1. Twilio account

1. Create a [Twilio](https://www.twilio.com) account
2. Buy a phone number with Voice capability
3. Create API Key + Secret (Console → API Keys)
4. Create a **TwiML App** with Voice Request URL:
   ```
   https://YOUR_PUBLIC_URL/twiml/client
   ```
5. Note: Account SID, Auth Token, API Key, API Secret, Phone Number, TwiML App SID

### 2. Public URL (required)

Twilio must reach your backend for webhooks and media streams. In development:

```bash
ngrok http 3000
# Set PUBLIC_BASE_URL=https://abc123.ngrok-free.app in backend/.env
```

### 3. Backend

```bash
cd backend
cp .env.example .env
```

Fill in `.env`:

```env
PUBLIC_BASE_URL=https://your-ngrok-url.ngrok-free.app
TWILIO_ACCOUNT_SID=AC...
TWILIO_AUTH_TOKEN=...
TWILIO_API_KEY=SK...
TWILIO_API_SECRET=...
TWILIO_PHONE_NUMBER=+1...
TWILIO_TWIML_APP_SID=AP...
DEEPGRAM_API_KEY=...
GOOGLE_API_KEY=...
TRANSLATION_PROVIDER=google
TTS_PROVIDER=google
GOOGLE_TTS_SAMPLE_RATE=8000
```

```bash
npm install
npm run dev
```

### 4. Android

1. Open project in Android Studio
2. Settings → set backend host (`10.0.2.2:3000` for emulator, LAN IP for device)
3. **Call a Phone Number** → enter E.164 number → call

## Language settings (Hindi ↔ English example)

| Setting | Value |
|---------|-------|
| My language | Hindi |
| Their language | English |
| What I want to hear | Hindi |

## API endpoints

| Endpoint | Description |
|----------|-------------|
| `POST /auth/token` | App JWT |
| `POST /v1/calls/start` | Start session + dial PSTN + return Twilio access token |
| `POST /v1/calls/end` | End session |
| `POST /twiml/client` | TwiML for app VoIP leg |
| `POST /twiml/pstn` | TwiML for PSTN leg (disclosure + stream) |
| `WS /media` | Twilio Media Streams (audio in/out) |
| `WS /call-events` | Live captions to app |

## Compliance

- PSTN party hears disclosure at connect (no app UI available)
- One-to-one calls only (no bulk dialing)
- PSTN calls cost real money per minute via Twilio
- Confirm telecom/recording laws for your markets with legal counsel

## Legacy modes

- **In-App Call** — both users need the app (WebRTC, legacy)
- Old speakerphone mode removed in favor of PSTN architecture

## Cost note

Every PSTN call bills Twilio per-minute for the outbound leg plus any AI API usage (Deepgram, Google Translate/TTS).

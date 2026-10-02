# AGENTS.md - PairingTranslator (ペアリング翻訳)

Repository instructions for AI assistants working on the **ペアリング翻訳 (`PairingTranslator`)** project: two nearby Android devices connect over Google Play Services **Nearby Connections** (P2P) and stream speech to **Soniox** for real-time translation.

## Identity & Rules

- **applicationId**: `com.navypool.pairingtranslator` (v0.2.5+; older v0.2.4 and below used a different ID and coexist as a separate app). Single build, no productFlavors.
- **No dedicated backend**: the app uses the existing MultiTranslator backend at `https://multitranslator.dev.x-tools.biz/`. **Never modify the server from this repo** — server work happens in `D:\MultiTranslator` (see `specs/サーバー仕様.md`). APIs used: `/api/device_auth.php` (self-registration, admin approval required) and `/api/temp_key.php`.
- Temp key: the settings screen exposes 一時キー有効時間 (1–60 min, default 60). `BackendClient` sends the clamped `duration` and surfaces `expires_in_seconds` / `expires_at`; `VoiceSessionManager` re-rotates the session ~5 min before expiry (response `expires_at` considered) instead of a fixed 55-minute cycle.
- Language mixing fix (v0.2.4+): both devices' languages are hints, strict language enforcement is disabled; non-target utterances (e.g. English on a ja device) are filtered; only finalized text is sent — see README and `specs/アプリ仕様.md`.

## Build & Test

- `.\gradlew.bat :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (JDK 17, SDK 36, minSdk 26; needs Google Play Services).
- Versioned test APKs (`ペアリング翻訳-vX.Y.Z-test.apk`) live in the repo root (gitignored, debug-signed, same-ID overwrite install).
- Real-device test procedure: [TESTING.md](TESTING.md). Server-side device approval via the MultiTranslator admin UI is required before use.
- Known lint issue: existing `InvalidFragmentVersionForActivityResult` warning.

## Git

- Remote: `https://github.com/dxa-jp/Pairing-Translator.git` (branch `main`).
- `*.apk` and `soniox_api_key_*.txt` are gitignored — never commit artifacts or keys.

## Specs Map

| Path | Content |
|---|---|
| `specs/アプリ仕様.md` | App architecture, P2P protocol, voice session, translation finalize logic |
| `specs/API仕様.md` | Backend REST API + Soniox WebSocket + P2P protocol reference |
| `specs/サーバー仕様.md` | Server usage rules (read-only for this project) |
| `specs/キッティングツール仕様.md` | Manual kitting procedure (no dedicated tool yet) |
| `TESTING.md` | Two-device field test procedure |

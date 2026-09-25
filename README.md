# StegaText 💬🔐

> **Text-in-text steganography** — hide a secret message or **any file** inside ordinary text (Persian / English / Russian, even mixed), protected by a mandatory key. No one can extract it without the key.

[![Build APK](https://github.com/rezaarbab/testapp/actions/workflows/android.yml/badge.svg)](https://github.com/rezaarbab/testapp/actions/workflows/android.yml)

---

## How it works

```
Secret text / any file ──AES-256-GCM──► encrypted bytes
                                            │
        bits scattered by a key-driven PRNG over the carrier text
                                            ▼
              "سلام رضا جطوری خوبی؟"  (looks 100% normal)
                                            │
        only the same key can rebuild the bits ──► decrypt ──► secret
```

### Embedding channels (unlike image-stego apps)

| Channel | How | Survives sanitizers? |
|---|---|---|
| Homoglyph swap | visually identical letter twins: `a↔а`, `ی↔ي`, `ک↔ك`, … | ✅ always (they are real letters) |
| Zero-width joiner | invisible bit between adjacent letters | ⚠️ depends on platform |
| Persian morphology | `خانه‌ها` vs `خانهها`, `نمی‌دانست` vs `نمیدانست` — both are normal spellings | ⚠️ depends on platform |

- **Robust mode (default)** uses only homoglyphs — the output contains **zero invisible characters**, so nothing can be detected or stripped.
- Bit order is shuffled with a **key-derived PRNG (xorshift64\* with rejection sampling)** — even if someone finds the bits, without the key the stream is meaningless.
- Wrong key → clean "Invalid key" error (AES-GCM tag check + magic header), never garbage text.

## Features

- 🌍 **Any script carrier** — Persian, English, Russian, even all mixed in one text
- 📎 **Payload = text or any file** (PDF/MP3/image… via any format, size limited by carrier capacity)
- ⚡ **Instant carrier generation** — carrier text is sized exactly to the payload (bits-per-char measured once), no more long waits
- 📂 **Reveal from file** — open a saved stego `.txt` file directly in the Reveal tab; the File Vault can open a stego file at any time (not only right after hiding)
- 🔐 AES-256-GCM + PBKDF2 (200,000 iterations), mandatory key
- 📊 Live capacity meter (bits/bytes) before hiding
- 🇮🇷 Persian (RTL) / English UI with instant switch (persisted)
- Copy / Share / Save stego text, extract with original filename
- 🧪 CI unit tests: round-trips (fa/en/mixed), robust-mode survives zero-width stripping, wrong-key rejection

## ⚠️ Honest limits

- Text carriers have small capacity (~1 bit per letter). A short paragraph carries a short secret; use longer carrier text for bigger payloads. The app shows live capacity before hiding.
- Zoomed hex inspection could still notice homoglyph usage; the goal is surviving real-world scanners/sanitizers, not a targeted forensic analyst.

## Download

1. Go to the [Actions](https://github.com/rezaarbab/testapp/actions) tab → latest run → download `StegaText-debug-apk` (or a Release APK when tagged).
2. Allow "install from unknown sources" and install. No root needed. Android 8.0+.

## Build

```bash
gradle assembleDebug        # or gradle testDebugUnitTest
```

Requires JDK 17, Android SDK 35, Gradle 8.9.

## Roadmap

- [ ] Emoji variation-selector channel
- [ ] Dictionary-driven natural story generator (carrier auto-completion)
- [ ] Desktop/CLI companion with the same format

## License

MIT — see [LICENSE](LICENSE)

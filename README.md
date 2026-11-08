# SMART TRADER Android v0.3.0

Personal Android app foundation in Kotlin + Jetpack Compose.

## Implemented in this build
- Premium dark dashboard and bottom navigation
- Live public MEXC Spot prices (BTCUSDT, ETHUSDT, MXUSDT), refresh every 15 seconds
- MEXC Spot read-only account verification using signed `/api/v3/account`
- HMAC-SHA256 signature according to current MEXC Spot V3 docs
- API credentials encrypted using AES-256-GCM with Android Keystore; ciphertext stored in private app SharedPreferences
- Delete credentials action
- Paper / Real mode selector UI, with Real mode intentionally locked
- Risk policy starter module and unit tests
- Internet permission; TLS-only requests

## Not yet implemented / not safe to use for live trading
- No live order placement or cancellation
- No futures private account integration or order execution
- No automated strategy engine / AI signal generation
- No complete Paper Trading ledger or order simulator
- No foreground trading service or guaranteed background execution

Do not enable trading permissions on the API key yet. For the first connection test, create a key with Spot account read permission only; do not enable withdrawals.

## Build
Open this directory in Android Studio (stable), allow Gradle sync, then Build > Build APK(s).
The debug APK is normally created at `app/build/outputs/apk/debug/app-debug.apk`.

## MEXC notes
- Spot API base: https://api.mexc.com
- Signed Spot V3 requests use X-MEXC-APIKEY and HMAC SHA256 of the parameter string.
- MEXC documents that API keys without IP whitelist expire after 90 days; IP whitelisting is recommended.
- A mobile network's public IP may change. Do not whitelist a changing mobile IP unless you understand the connectivity consequences.

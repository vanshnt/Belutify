# Changelog

## 1.0.5

- Fixed immediate startup crash in the isolated `:gecko` login browser (`GeckoLoginActivity`) caused by sharing the main-process app theme (`SpotuiTheme` / `DynamicColorPref`) before `Graph.app` was initialized
- Prevented duplicate `GeckoLoginActivity` launches when session expiry and logout listeners trigger together

## 1.0.4

- Added isolated GeckoView (Firefox engine) login browser for reliable Spotify sign-in & adding multiple accounts without Android WebView's `X-Requested-With` / HTTP 400 error
- Automatically captures the signed-in session (`sp_dc`) via built-in browser extension and terminates the `:gecko` login process immediately afterward so zero browser RAM stays active during playback
- Improved WebView fallback login: clears stale anonymous startup cookies before login, skips AdBlock during sign-in, and permits challenge/reCAPTCHA subframes
- Updated Help dialog browser fallback instructions and diagnostic report branding (`Belutify diagnostic report`)
- Raised in-app updater APK download ceiling (`400 MB`) for future larger builds

## 1.0.3

- Fixed in-app update checker to target `vanshnt/Belutify` GitHub releases and detect `Belutify-*.apk` assets
- Fixed direct in-app APK download and seamless installation flow
- Completely purged legacy Orpheon migration prompts, queries, and dead code
- Fixed extension compatibility: community extensions (Lossless FLAC, Culture, Lyrics Romanizer, etc.) now activate properly
- Rebranded remaining user-facing text, diagnostic reports, and logs to Belutify across all supported locales
- Based on Myspoti (originally by kota-zo and upstream open-source contributors)

## 1.0.2

- **Belutify Initial Revival Release** maintained by **vanshnt**
- Updated app icon to modern 3D Spotify aesthetic with standard Android mipmap & adaptive icon support
- Fixed Spotify in-app login: restored native user-agent switching and email magic links from v1
- Added browser sign-in fallback (`importSession` via `sp_dc` cookie in the Help dialog)
- Updated UI branding and app naming to Belutify
- Cleaned up obsolete migration prompts
- Based on Myspoti (originally by kota-zo and upstream open-source contributors)

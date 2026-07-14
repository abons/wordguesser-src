# Word Guesser — production to-do

Outstanding items before publishing (e.g. Google Play). Personal sideloading already works.

## 🔴 Blockers (required to distribute)

- [x] **Release signing** — `release.keystore` (alias `wordguesser`, RSA-2048) + a
  `signingConfig` reading `RELEASE_*` from `local.properties` (keystore + creds gitignored).
  `assembleRelease` now outputs a signed `app-release.apk`; `install-release.bat` builds +
  sideloads it. ⚠️ Keep `release.keystore` + passwords backed up — losing them means no more
  updates under the same identity.
- [x] **Test the release build on a device** — the signed, R8-minified release runs fine on
  device (nothing stripped).
- [ ] **Play Store listing requirements** (if publishing there):
  - [ ] Data Safety form (the app makes network calls to third-party sources on language download).
  - [ ] Privacy policy URL (even if "no data collected").
  - [ ] Current `targetSdk` per Play's latest requirement.
  - [ ] Store assets: icon, feature graphic, screenshots, description.

## 📦 Distribution (prepared 2026-07-15 — needs your push)

- [x] **Self-hosted F-Droid repo** built by hand (`dist/fdroid/repo/` — signed
  `index-v1.jar`) + public landing page (`dist/index.html`). Fingerprint
  `C74E…456EE`. Rebuild with `dist/fdroid/rebuild-index.sh`.
- [x] **Published (2026-07-15).** Public dist repo `abons/wordguesser` on GitHub Pages →
  https://abons.github.io/wordguesser/ (direct APK download + working F-Droid repo).
  Private source repo `abons/wordguesser-src` (no secrets pushed). Live URLs verified 200.
- [ ] **Official F-Droid catalogue** (optional, later) — metadata ready in
  `dist/fdroid-official/`; needs public source + a FOSS LICENSE first. See its NOTES.md.

## 🟠 Recommended (quality / robustness)

- [ ] **Word-list hosting** — lists are fetched from third-party GitHub raw URLs
  (dwyl, OpenTaal, lorenbrichter, enz, wooorm). These can rate-limit, move or disappear.
  For a real product, mirror/host them yourself (and re-check licenses when doing so).

## 🟢 Optional / nice-to-have

- [ ] Bundle the full GPL/LGPL/CC-BY license texts in-app (currently linked, not bundled —
  chosen to keep the APK tiny; linking is acceptable since the data isn't redistributed).
- [ ] Custom app icon / branding (current icon is a placeholder mini-board).
- [ ] Landscape / tablet layout (currently portrait-locked).
- [ ] Win/lose screen, share result. (Statistics ✅ done — see below.)
- [ ] More languages (Tier 3 needs a non-Latin keyboard: Cyrillic, Greek, etc.).

## ✅ Done

- [x] Native, dependency-free Kotlin app; builds + runs (debug) on device.
- [x] `applicationId` + `namespace` = `com.hrbons.wordguesser` (no `com.example`).
- [x] Language switching with on-demand download + offline cache.
- [x] Strict mode (downloaded languages only); word-length setting (per-language range).
- [x] Accent folding + accented display on match; German ß / Nordic Æ Ø / PL Ł / HR Đ keys.
- [x] Word lookup ("?" → Translate / Wiktionary / other apps).
- [x] Accessibility: TalkBack labels on tiles (letter + colour state), keys, "?" and header
  buttons, plus a spoken per-guess summary.
- [x] Ko-fi "support the developer" link in settings.
- [x] Statistics (⚙ → Statistics): wins with guess-count distribution + losses, tracked per
  language and settings bucket (length / strict / hard); with a reset option.
- [x] Daily puzzle — a **separate** shared English puzzle for **each word length (4–8)**; the
  📅 button opens a length picker showing today's status per length ("play" / "solved in N" /
  "not solved"). Play-once with replay of the finished board, and a public dreamlo leaderboard
  (per day + length: name + fewest guesses + fastest time). Exiting a daily (New / Close)
  restores the normal game at the language's preferred length.
- [x] "?" lookup via the on-device Gemini app (no API key) with a device-language reply;
  API-key fallback; definition-only when the word is in the device language.
- [x] Reveal the missed word (with "?") under the board after a loss.
- [x] Download robustness: retry with backoff on network reads (word lists + leaderboard),
  and lifecycle-safe UI callbacks (`ifAlive`) so late results can't crash a closed screen.
- [x] Unit tests (19, JVM/JUnit): `Wordle.evaluate` duplicate handling, `WordLists`
  fold/ß/clean filtering, `Leaderboard` name sanitising + rank parse/sort. Run with
  `gradlew.bat testDebugUnitTest`. (Core logic extracted into pure, testable classes.)
- [x] Licensing: all sources permitted; in-app **Sources & licenses** with attribution,
  license links and a modifications note; README attribution section. No list bundled in APK.
- [x] Release build compiles with R8 + resource shrinking (unsigned); `lintVital` passes.
- [x] **minSdk 21** (Android 5.0, ~99% of devices) with an all-XML launcher-icon fallback
  (adaptive on API 26+, vector on 21–25) and an `ACTION_PROCESS_TEXT` guard for API < 23.
  targetSdk stays 34.

# Word Guesser — production to-do

_Laatst herzien: 2026-07-19._ Personal sideloading werkt al; de app is publiek uitgebracht
(F-Droid + GitHub Pages). Play Store is bewust uitgesteld (zie memory `play-account-strategy`).

## 🔴 NU: v2 uitbrengen (afgerond werk bereikt nog geen gebruikers)

De gepubliceerde APK draait nog op **`versionCode 1` / `versionName "1.0"`** en haalt de
**oude** data op. Al het onderstaande is klaar + op device getest, maar zit alleen in source /
lokale builds — geen enkele gebruiker heeft het. Dit is de belangrijkste openstaande taak.

Nog niet uitgebracht:
- Zelf-gehoste NL-woordenlijsten (`nl.txt` answer-pool + `nl-accept.txt` recognition).
- Offline definities (`nl-defs.json`, 100% coverage) + form-of-resolutie.
- Two-tier accept/answer-lijst.
- **Online duel vs speler** (Firebase RTDB) + rematch.
- Duel vs computer + timed-modus UI.
- Layout-tweak (board-hoogte, `keyHeightPx`/`tileSizeFor`).

Release-checklist:
- [x] **Layout-tweak op device geverifieerd** (2026-07-20) — length 5, 8 én DE+ß (worst case),
  geen overflow; ß-toets volledig zichtbaar.
- [x] `versionCode` 1 → 2 + `versionName` "2.0" in `app/build.gradle`.
- [x] Cache-suffixen al correct (`words_nl_v8`/`accept_nl_v1`/`defs_nl_v4`) — geen bump nodig.
- [x] Signed release-APK gebouwd (v2.0, versionCode 2 geverifieerd, ~98 KB).
- [x] Release-APK op device getest — start, rendert, gameplay ok, "Duel vs player" aanwezig
  (firebase-gate werkt in R8-release).
- [x] APK in `dist/fdroid/repo/` (`_2.apk`) → `rebuild-index.sh` (suggestedVersion nu dynamisch) → gepusht.
- [x] Landing page `dist/index.html` bijgewerkt naar v2 (versie/URL/SHA-256/grootte).
- [ ] Optioneel: GitHub release-tag `v2.0` op de source-repo (self-hosted F-Droid heeft geen
  changelog-files, dus enkel een tag/notes indien gewenst).

## 🟠 Kwaliteit / afronden

- [ ] **Screenshots opnieuw** — 13 result/badge-shots zijn na de UX-redesign verwijderd
  (memory `screenshots-pending`); win-gated, met de hand vastleggen per
  `docs/screenshots/README.md`. Geen harness-arm voor de duel waiting/opponent-turn-states.
- [ ] Screenshot-harness-arm toevoegen voor duel-states (waiting / opponent-turn) zodat die
  ook offline/deterministisch te schieten zijn.
- [ ] **Word-list hosting** — NL is zelf-gehost; de overige lijsten (dwyl, lorenbrichter, enz,
  wooorm) komen nog van third-party GitHub raw-URLs. Mirror die desgewenst (her-check
  GPL/LGPL/CC-BY per taal).

## 🟢 Optioneel / nice-to-have

- [ ] Volledige GPL/LGPL/CC-BY-licentieteksten in-app bundelen (nu gelinkt, niet gebundeld).
- [ ] Custom app-icon / branding (huidige icon is een placeholder mini-board).
- [ ] Landscape / tablet-layout (nu portrait-locked).
- [ ] Win/lose-scherm, resultaat delen. (Statistieken ✅ al gedaan.)
- [ ] Meer talen (Tier 3 vereist een niet-Latijns keyboard: Cyrillisch, Grieks, enz.).

## 🟡 Later: Google Play (uitgesteld)

Bewust uitgesteld tot er ~5 apps zijn (memory `play-account-strategy`). Wanneer opgepakt:
- [ ] Data Safety-formulier (de app doet netwerk-calls naar third-party bronnen bij download).
- [ ] Privacy policy-URL (ook bij "geen data verzameld").
- [ ] `targetSdk` naar Play's actuele minimumeis.
- [ ] Store-assets: icon, feature graphic, screenshots, beschrijving.
- [ ] Eenmalige Play developer-fee betalen.

## ✅ Done

- [x] Native, dependency-vrije Kotlin-app; bouwt + draait op device.
- [x] Release signing (`release.keystore`) + `signingConfig` uit `local.properties`; release-build
  op device getest (R8 + resource shrinking, `lintVital` groen).
- [x] **Publiek uitgebracht (2026-07-15, = v1)**: self-hosted F-Droid repo (`dist/fdroid/repo/`,
  fingerprint `C74E…456EE`) + landing page op GitHub Pages
  (https://abons.github.io/wordguesser/). Private + public source-repo, geen secrets gepusht.
- [x] `applicationId` + `namespace` = `com.hrbons.wordguesser`.
- [x] Taalwissel met on-demand download + offline cache; strict mode; word-length 4–8.
- [x] Accent-folding + accented display; DE ß / Nordic Æ Ø / PL Ł / HR Đ keys.
- [x] Word lookup ("?"); TalkBack-accessibility; Ko-fi-link.
- [x] Statistieken (per taal + settings-bucket, met reset).
- [x] Daily puzzle per word-length (4–8) + dreamlo-leaderboard per dag/length.
- [x] Loss-reveal; download-robuustheid (retry + lifecycle-safe callbacks).
- [x] Unit-tests (JUnit, JVM-only); licensing/attributie in-app ("Sources & licenses").
- [x] minSdk 21 (adaptief/vector icon-fallback), targetSdk 34.

### Klaar maar nog NIET uitgebracht (zie "NU: v2 uitbrengen")
- [x] Zelf-gehoste NL answer-pool + accept-lijst + 100%-coverage offline definities.
- [x] Online duel vs speler (Firebase RTDB, REST-only) + rematch; duel vs computer; timed-modus.
- [x] Height-aware layout-tweak (gebouwd; device-verificatie staat nog open).

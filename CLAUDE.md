# Word Guesser — project summary (for a fresh session)

A **tiny, native Android Wordle-style word game** in **pure Kotlin** using only the Android
framework — no AppCompat / Compose / Material / third-party runtime libs. The whole UI is
built in code so the release APK stays ~72 KB. Package: `com.hrbons.wordguesser`.

## Build / run (no Android Studio)

- JDK 17: `C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot` (set `JAVA_HOME`).
- Gradle 8.2 (committed wrapper `gradlew.bat`), AGP 8.2.2, Kotlin 1.9.22.
- Android SDK is reused from the Unity install via `local.properties` `sdk.dir`
  (`…/Unity/Hub/Editor/6000.3.13f1/…/AndroidPlayer/SDK`); compileSdk 34, minSdk 21, targetSdk 34,
  buildToolsVersion 36.0.0.
- `install.bat` = debug build + `adb install -r`. `install-release.bat` = signed release (uninstalls first).
- Tests: `gradlew.bat testDebugUnitTest` (JUnit, JVM-only; not in APK).
- Physical test device: Motorola edge 30 neo, `adb` serial `ZY22GD8FG4` (1080×2400).
- **Regenerate `docs/screenshots/`:** `.\update-screenshots.ps1` (build + install + all 35 shots).
  Driven by a debug-only harness: `am start … --es shot <name>` → `MainActivity.applyShot(name)`
  (behind `BuildConfig.DEBUG`, so R8 strips it from release) drops the app straight into each
  state; leaderboard/definition dialogs use stub data so shots stay offline & deterministic. The
  debug build has `applicationIdSuffix ".debug"` so it installs alongside the release (no signature
  clash, keeps its stats). New UI state → add one `when` arm in `applyShot` + one row in the
  script's `$shots` map. `-Only <names>` re-shoots a subset; `-SkipBuild` reuses the install.

## Layout of the code

- `app/src/main/java/com/hrbons/wordguesser/`
  - `MainActivity.kt` — everything: UI built in code, game logic, keyboard, daily puzzle,
    leaderboard hooks, word-list + definition download/cache, settings, stats.
  - `WordLists.kt` — `Language` catalog + on-demand list download + `clean()` filtering
    (diacritic folding, reject uppercase = proper nouns, length filter). `typeableForm()`.
  - `Wordle.kt` — pure `evaluate(guess,target)` scoring (duplicate-letter correct).
  - `Leaderboard.kt` — dreamlo client (HTTP-only free board). Note: the dreamlo PRIVATE write
    key is a constant here and ships in the APK (accepted casual-game limitation).
  - `Gemini.kt` — optional API fallback for word lookup (key from BuildConfig, gitignored).
  - `WordBank.kt` — built-in English list.
- Unit tests in `app/src/test/…`.

## Features (all implemented)

Wordle scoring; 17 languages via downloadable word lists; strict mode; hard mode; variable
word length 4–8 (board resizes); accent folding + accented display on match; language extra
keys (DE ß, Nordic Æ/Ø, PL Ł, HR Đ); TalkBack; per-language/settings statistics; **daily
puzzle per language + word length** (📅 picker; uses the current language's answer pool, and
counts toward that language's stats) + public dreamlo leaderboard **scoped per language**;
**word lookup via "?"**;
loss reveal; Ko-fi nudge (once/day, no toggle); download retry + lifecycle-safe callbacks.

Keyboard: DEL bottom-left (before Z), ENTER bottom-right (user-chosen order — don't reorder).
Transient messages use a custom pill ABOVE the keyboard (not Toast).

**Duel modes:** turn-by-turn on one shared board, first to solve wins, board full = draw.
- *vs computer* (`startDuel`): NPC picks a clue-consistent word (`Wordle.consistentCandidates`).
- *vs player (online)* (`Duel.kt`): the NPC replaced by a human over **Firebase Realtime Database,
  REST only** (no client SDK — kept dependency-free/F-Droid-safe, like `Leaderboard`). Host creates
  a room (short code, unambiguous alphabet) → shares code → guest joins. `rooms/<CODE>` holds
  lang/len/target/starter/status/moves; both apps know `target` and score their own guess locally
  (`Wordle.evaluate`), so only the guessed *word* + who-played crosses the wire. Short-poll every
  ~1.5s **only** while it's the opponent's turn / waiting for a join. Move index == board row. Host
  deletes the room ~4s after game-over (Spark plan has no auto-cleanup). Cheating (guest reading
  `target`) is the same accepted trade-off as the shipped dreamlo write key.
  **Gated on `local.properties` `firebase.dbUrl`** → `BuildConfig.FIREBASE_DB_URL`; empty hides the
  whole "Duel vs player" menu, so nothing ships without a backend. See `Duel.kt` header + memory.
  Firebase project is live (`wordguesser-42b54`, europe-west1, open rules on `rooms/`). **Verified
  end-to-end on device** (app-as-guest vs curl-as-host): create+share code, cancel→host-cleanup,
  join→language/length adopt+board resize, opponent-move polling+render (🧑/👤 icons, correct
  colours), own winning move push + `finish(winner)` sync, win dialog. Note: RTDB returns
  contiguous 0-based `moves` keys as a JSON **array**, so `Duel.parseRoom` handles array *and*
  object. **Rematch** reuses the same room code: host does a full PUT (new word, `round`++, coin
  toss swapped `1 - starter`, moves cleared), guest polls (`round` up + status "waiting") and
  auto-joins — verified end-to-end on device (2-round cycle). Room is kept after game-over for
  rematch and torn down only on "Done"/Cancel (host, 3s grace). No screenshot-harness arm for the
  waiting/opponent-turn states yet; move-send is fire-and-forget with 3× retry.

## Distribution (LIVE)

- GitHub account `abons`. `gh` CLI at `C:\Program Files\GitHub CLI\gh.exe` (not always on PATH),
  authed as `abons`.
- **Public dist repo** `github.com/abons/wordguesser` → GitHub Pages
  **https://abons.github.io/wordguesser/** (download page + self-hosted F-Droid repo + word data).
- **Private source repo** `github.com/abons/wordguesser-src` (now PUBLIC too, for F-Droid; MIT
  `LICENSE`, tag `v1.0`). No secrets are tracked.
- `dist/` is **gitignored in the source repo** and is its own git repo (the public one). Keep
  them separate.
- Self-hosted F-Droid add URL:
  `https://abons.github.io/wordguesser/fdroid/repo?fingerprint=C74E4BC48DBE3CCF800A859BC5A9118B23A19BA38C8B33573DBA1BDEB7E456EE`
  Rebuild its index with `dist/fdroid/rebuild-index.sh` after dropping a new APK.
- Official F-Droid submission is PREPPED (`dist/fdroid-official/`) but not submitted (needs a
  GitLab MR/RFP).
- Two keystores live OUTSIDE git (gitignored): `release.keystore` (app signing) and
  `fdroid-repo.keystore` (F-Droid index). **Back them up.** Secrets are in `local.properties`.

## Dutch word + definition pipeline (self-hosted)

Built by scripts in `dist/wordlists/`, hosted at `…/wordlists/`. Two-tier:
- `nl.txt` — answer pool (~30.8k): OpenTaal words that have a real Wiktionary definition.
- `nl-accept.txt` — recognition list (~65.85k): all valid OpenTaal words incl. conjugations
  (accepted as guesses, show "?", but never used as target answers).
- `nl-defs.json` — {word: definition} for ALL 65,851 accept words (100% coverage): 60,367 from
  Dutch Wiktionary (CC BY-SA 3.0, credited) + 5,484 original CC0 defs written for the gaps,
  **plus ~2,801 extra base-word defs** (out-of-range infinitives/nouns like `verkennen`, `rol`)
  used only as form-of resolution targets — never guesses/answers (68,652 entries total).
- **Form-of resolution:** many accept words are pure grammatical pointers (e.g. `verkende` →
  "enkelvoud verleden tijd van verkennen"). `MainActivity.formOfBase`/`resolveFormOf` detect these
  via the `FORM_OF_WORDS` token whitelist (every word before the final "… van <base>" must be
  grammatical, so "gemaakt van beton" / "het vormen van bubbels" stay untouched) and show the
  base word's real definition instead, keeping the grammatical note. Bases outside the 4-8 filter
  are supplied by `augment-nl-defs.py` (and now also by `build-nl-defs.py` on a full rebuild).
- App: `Language` has `url` (answers), `acceptUrl` (recognition), `defsUrl`+`defsCredit`.
  "?" shows the definition directly & offline (`showDefinition`), Gemini only as a fallback
  when a word has no gloss. Caches: `words_nl_v8.txt`, `accept_nl_v1.txt`, `defs_nl_v4.json`.
- See memory `nl-defs-pipeline.md`. LESSON: generating all 65k defs by LLM hit the session
  limit; only gap-filling (~5.5k) is affordable.

## Conventions & gotchas

- Keep the APK tiny: no new runtime dependencies.
- `upperNoExpand` uppercases per-char so `ß` stays `ß` (not `SS`) — used instead of `uppercase()`.
- Bump the cache-version suffix in `MainActivity.cacheFile` / defs / accept when list content
  or filtering changes, so devices re-download.
- dreamlo is HTTP-only → `res/xml/network_security_config.xml` permits cleartext for dreamlo.com
  only. HTTPS to `abons.github.io` is fine.
- Never commit keystores or `local.properties`.
- adb on this Windows/Git-Bash setup: prefix device-path commands with `MSYS_NO_PATHCONV=1`;
  the phone dozes/locks during long sequences (screenshots go black / taps hit the lockscreen) —
  `adb shell input keyevent KEYCODE_WAKEUP` first, and `svc power stayon true` while testing.
  Screenshot reliably via `screencap -p /sdcard/x.png` + `adb pull` (exec-out piping can glitch).
- The uv Python (for build scripts) is at `~/AppData/Roaming/uv/python/cpython-3.14*/python.exe`;
  system `python` is a Windows Store stub. Pass `PYTHONIOENCODING=utf-8` for unicode output.

## Current state / open items

- Distribution is live; app builds clean, tests pass.
- **v2.0 released (2026-07-20, versionCode 2).** The self-hosted NL lists, offline definitions,
  two-tier accept list, online/computer duels, timed mode and the height-aware layout tweak are
  all shipped: signed APK in `dist/fdroid/repo/com.hrbons.wordguesser_2.apk`, F-Droid index
  rebuilt (`suggestedVersion` now dynamic = highest versionCode), landing page updated, both
  repos pushed, GitHub release tag `v2.0` on the source repo (APK attached). Live-verified:
  APK 200, landing page shows v2.0 + matching SHA, F-Droid offers v2.
- **Layout tweak (committed & device-verified):** board container weighted+centred, taller keys
  via `keyHeightPx()`, height-aware `tileSizeFor()`, larger margins. Verified on device at
  length 5, 8 and DE+ß (worst case) — no overflow, ß key fully visible.
- Next release: bump `versionCode`/`versionName`, bump cache suffixes if list content/filtering
  changed, build signed APK, drop into `dist/fdroid/repo/`, run `rebuild-index.sh`, push both repos.
- See `todo.md` for the production checklist.

# Word Guesser

A tiny, **native** Android word-guessing game (Wordle-style) written in **pure Kotlin**
using only the Android framework — no AppCompat, no Compose, no Material, no third-party
libraries. The whole UI is built in code, so the APK stays small.

## 📥 Download

**[abons.github.io/wordguesser](https://abons.github.io/wordguesser/)** — direct APK or add
the self-hosted F-Droid repo for automatic updates:

```
https://abons.github.io/wordguesser/fdroid/repo?fingerprint=C74E4BC48DBE3CCF800A859BC5A9118B23A19BA38C8B33573DBA1BDEB7E456EE
```

The public download page + F-Droid repo live in the separate **`dist/`** folder (published as
its own public repo). See [dist/PUBLISH.md](dist/PUBLISH.md) for how it's hosted and how to
ship an update.

## How to play

- Guess the hidden **5-letter word** in 6 tries.
- After each guess, tiles reveal:
  - 🟩 **green** — right letter, right spot
  - 🟨 **yellow** — right letter, wrong spot
  - ⬛ **gray** — letter not in the word
- Tap **New** (top-right) or the game auto-picks a fresh word each launch.
- A **?** appears to the right of any guess that is a real word (in the current list).
  Tap it to **translate** it (Google Translate, source = game language → device language)
  or **define** it (Wiktionary), or hand it to any other text app on the device.
- **Word length** is adjustable in **⚙ settings** (the board resizes to fit). Each language
  has a range chosen so every length still has thousands of words — 4–8 for most, 5–8 for
  Français/Italiano/Hrvatski; the built-in English list is fixed at 5.
- **Hard mode** (⚙ settings): tiles and keyboard are **not** coloured; instead each guess
  shows only two numbers underneath — how many letters are in the word, and how many are in
  the right place.
- **Statistics** (⚙ → Statistics): wins (with a per-guess-count distribution) and losses,
  tracked separately per language and settings (length / strict / hard); resettable.

## Project layout

```
wordguesser/
├─ settings.gradle
├─ build.gradle              # plugin versions
├─ gradle.properties         # android.useAndroidX=false (no AndroidX)
├─ app/
│  ├─ build.gradle           # zero dependencies
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/hrbons/wordguesser/
│     │  ├─ MainActivity.kt  # UI + full game logic
│     │  └─ WordBank.kt      # in-app 5-letter word list
│     └─ res/                # theme, launcher icon (all XML, no PNGs)
```

## Building / sideloading — no Android Studio needed

This machine is already set up for a lightweight, IDE-free build:

- **JDK 17** — Microsoft OpenJDK, at `C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot`
- **Android SDK** — reused from the Unity install (`local.properties` → `sdk.dir`);
  provides `android-34` + `build-tools 36.0.0`
- **adb** — Android platform-tools (installed via winget)
- **Gradle** — the committed wrapper (`gradlew.bat`) downloads Gradle 8.2 automatically

The produced debug APK is **~800 KB**.

### One command: build + sideload

Enable **USB debugging** on the phone (see below), connect via USB, then run:

```bat
install.bat
```

This builds `app-debug.apk` and runs `adb install -r` onto the connected device.

### Manual steps

```bat
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

### Signed release build

`app/build.gradle` has a release `signingConfig` that reads the keystore from
`local.properties` (gitignored). With that set, `gradlew.bat assembleRelease` (or
`install-release.bat`) produces a **signed** `app-release.apk` — installable/shareable with
no Play account:

```
RELEASE_STORE_FILE=release.keystore
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=wordguesser
RELEASE_KEY_PASSWORD=...
```

Generate a keystore once with `keytool -genkeypair -keystore release.keystore -alias
wordguesser -keyalg RSA -keysize 2048 -validity 10000`. **Back up the keystore + passwords** —
losing them means you can't ship updates under the same app identity. Without these
properties the release build is simply unsigned.

### Tests

Pure JVM unit tests (JUnit, test-only — not in the APK) cover the core logic:

```bat
gradlew.bat testDebugUnitTest
```

`Wordle.evaluate` (duplicate-letter scoring), `WordLists` folding/`clean` filtering, and
`Leaderboard` name-sanitising + ranking are extracted into dependency-free classes so they
test without an emulator.

### Enable USB debugging on the phone (one-time)

1. **Settings → About phone** → tap **Build number** 7 times (unlocks Developer options).
2. **Settings → System → Developer options** → turn on **USB debugging**.
3. Plug in via USB; on the phone tap **Allow** when asked to trust this computer.
4. Verify from the PC: `adb devices` should list your device.

## Languages (downloadable word lists)

Tap the **language button** (top-left, shows `EN`/`NL`/…) to switch language:

- **English (built-in)** — ships in-app, works offline immediately. (No strict mode.)
- **English (large — dwyl)** — [dwyl/english-words](https://github.com/dwyl/english-words) `words_alpha.txt` (~16k five-letter words).
- **Nederlands (OpenTaal)** — [OpenTaal/opentaal-wordlist](https://github.com/OpenTaal/opentaal-wordlist) `wordlist.txt` (~5.5k).
- **Français** — [lorenbrichter/Words](https://github.com/lorenbrichter/Words) `fr.txt` (~6.8k). Accents fold to base letters for typing (é→e, ç→c, à→a …) and the accented spelling is shown once a word matches.
- **Deutsch** — [enz/german-wordlist](https://github.com/enz/german-wordlist) `words` (~9k). Umlauts are folded to their base letter (ä→a, ö→o, ü→u) so words stay typeable on the A–Z keyboard; **ß** is kept and gets its own on-screen key (shown only for German).

Plus, from [wooorm/dictionaries](https://github.com/wooorm/dictionaries) (unless noted),
all with accent-folding + accented display on match:

| Language | Source | ~words | Extra key(s) |
|---|---|---|---|
| Español | lorenbrichter `es.txt` | ~10.7k | — (ñ→n) |
| Italiano · Português · Català · Română · Svenska · Čeština · Slovenčina | wooorm | ~3–15k | — |
| Dansk · Norsk | wooorm | ~7–10k | Æ, Ø (å→a) |
| Polski | wooorm | ~9.5k | Ł |
| Hrvatski | wooorm | ~3.5k | Đ |

Downloadable lists are fetched on first pick, filtered, and cached. A `⬇` in the picker
marks a language that still needs downloading. The APK itself stays tiny (~820 KB) — the
lists live online, not in the app.

How it works ([WordLists.kt](app/src/main/java/com/hrbons/wordguesser/WordLists.kt)):

1. A list without a URL uses the built-in `WordBank`.
2. A list with a URL is downloaded once (background thread) and filtered to 5-letter words.
   With `fold = true`, diacritics are stripped to the base letter to decide length/validity
   (é/ä→e/a), while the **original** accented spelling is stored so a matched word shows its
   accents. Letters in `extra` (ß, Æ, Ø, Ł, Đ) are kept and get their own key. Words across
   the language's whole length range (`minLen..maxLen`) are **cached** to
   `filesDir/words_<CODE>_v4.txt`; the game filters to the chosen length at play time. Later
   launches load the cache — no network.
3. The chosen language is remembered via `SharedPreferences`.
4. A `⬇` next to a language in the picker means it still needs downloading.

**Add another language**: append a `WordLists.Language(code, badge, label, url, extra, fold)`
to `LANGUAGES` in `WordLists.kt` — `url` is a plain-text file with one word per line
(hunspell `word/FLAGS` is fine). Set `fold = true` to strip accents to base letters, and
`extra` to any letters that should be kept verbatim and shown as their own keys (e.g.
`"ß"`). Bump the `_v4` suffix in `MainActivity.cacheFile` if you change filtering so old
caches are re-downloaded. No other code changes needed.

> Requires the `INTERNET` permission (declared in the manifest) for the initial download.

## Attribution & licenses

All word-list sources are used within their license. Importantly, **no third-party list is
bundled in the APK** — each is downloaded to the device from its own source at runtime, so
the app doesn't redistribute their data. In-app, **⚙ → Sources & licenses** lists every
source with its license, a **Source ›** link, and a **Full licence text ›** link (direct to
the canonical license), plus a note that lists are modified (filtered by length; accents
folded where noted) — covering the CC BY "indicate modifications" requirement. Summary:

| Source | Languages | License | Credit required |
|---|---|---|---|
| [dwyl/english-words](https://github.com/dwyl/english-words) | EN (large) | Unlicense | No (public domain) |
| [lorenbrichter/Words](https://github.com/lorenbrichter/Words) | ES, FR | CC0 1.0 | No (public domain) |
| [enz/german-wordlist](https://github.com/enz/german-wordlist) | DE | CC0 1.0 | No (public domain) |
| [OpenTaal](https://github.com/OpenTaal/opentaal-wordlist) | NL | CC BY 3.0 / BSD | **Yes — attribution** |
| [wooorm/dictionaries](https://github.com/wooorm/dictionaries) | IT, PT, CA, RO, SV, CS, SK, DA, NB, PL, HR | GPL / LGPL (per language) | **Yes — attribution + license** |

The built-in English list ships with the app. Word lists are the property of their
respective authors; this project only fetches and filters them at runtime.

## Word lookup (translate / define)

Tapping the **?** (next to a real guess, or under the board after a loss) opens a small
menu (see `lookUpWord` in `MainActivity`):

- **Define & translate (Gemini)** — sends a "define + translate" prompt to the **on-device
  Gemini app** (via a share intent, using its own login — no API key needed). If the Gemini
  app isn't installed, it falls back to the Gemini API when a key is configured (see below),
  otherwise it says so and the other options still work.
- **Translate (web)** — opens `translate.google.com` with `sl=<game language>` and
  `tl=<device language>`, so the word isn't mis-detected. Forced into a browser because the
  Translate *app* claims the link but ignores the query params.
- **Define (Wiktionary)** — opens `en.wiktionary.org/wiki/<word>` (definitions +
  translations, with a section per language).
- **Other apps…** — the system `ACTION_PROCESS_TEXT` chooser (Translate, dictionaries,
  assistants — whatever is installed).

**Gemini API key (optional — only needed as a fallback when the Gemini app isn't installed):**
get a free key at
[aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey), then add it to
`local.properties` (gitignored):

```
GEMINI_API_KEY=AIza...
```

It's exposed via `BuildConfig.GEMINI_KEY` (so it stays out of source control). Note: the key
still ships inside the APK and is extractable — fine for a personal build, but for a public
release the AI call should go through your own backend instead.

The `<queries>` block in the manifest grants the Android 11+ package visibility needed to
see those apps and a browser.

## Customizing

- **Word list**: edit `WordBank.ANSWERS` in `WordBank.kt`.
- **Strict mode**: tap the **⚙ settings button** (header) and enable *Strict mode* to
  reject non-words — only guesses present in the current language's list are accepted
  ("Not in word list"). **Only available on a downloaded language** (the built-in list is
  too small); on the built-in list the ⚙ dialog explains this. Off by default. The choice
  is saved in `SharedPreferences`.
- **Colors / sizing**: constants at the top of `MainActivity`.

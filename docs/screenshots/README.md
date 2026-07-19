# Word Guesser — screenshots

Device: Motorola edge 30 neo (1080×2400), NL unless noted. Dark theme.
Captured with `adb shell screencap` after driving the app via on-screen-keyboard taps.

## Present (22) — up to date with the redesign

| File | Scenario |
|------|----------|
| 01-empty-board | Fresh game, gold length 5, empty board |
| 02-midgame | Two guesses (BOTER, SPITS) with colours |
| 04-loss-reveal | Board full after a loss, revealed word + "?" |
| 05-hard-mode | Hard mode: no colours, "N in word · N in right place" lines |
| 06-strict-reject | Strict mode, non-word (ZXCVB) → "Not in word list" pill |
| 07-length-4 | Length 4 board (HUIS/BOOM) |
| 08-length-8 | Length 8 free play (VAKANTIE/COMPUTER) |
| 08b-length-8-extrakeys | Length 8 in a language with extra keys (Dansk: Æ/Ø row) |
| 09-daily-fresh | Fresh daily, blue chip, "Daily · N letters" pill |
| 12-highcontrast | **NEW** high-contrast palette (orange/blue) on board + keyboard |
| 13-timed-bar | Timed mode: "⏱ 4:58 · 0 solved" + "Go!" pill |
| 16-duel-your-turn | Duel start, "🤖 Duel · your turn" + "You start" pill |
| 17-duel-comp-turn | Duel with 👤 player + 🤖 computer rows |
| 18-modal-newgame | New game menu (2-line + chevron + dividers) |
| 19-modal-language | Language picker (list style, "tap to get it" subtitle, recent on top) |
| 20-modal-settings | Settings incl. High-contrast toggle + "How to play ›" |
| 21-modal-stats | Statistics with horizontal guess-distribution bars |
| 22-modal-stats-reset | "Reset statistics?" confirm dialog |
| 23-modal-sources | Sources & licenses |
| 24-modal-definition | Offline definition (TAFEL) |
| 25-modal-lookup | Lookup menu (list style) for a no-defs language (Dansk) |
| 35-modal-howto | **NEW** How to play (first-run + Settings) |

## MISSING (13) — deleted because they were pre-redesign style; RE-CREATE these

All of these are **result/submit modals or badge states gated behind actually finishing a
game** (winning/losing a specific hidden word), so they can't be forced with blind taps —
play them out by hand on the device, then screencap.

| File | Scenario | How to reproduce |
|------|----------|------------------|
| 03-win | Normal-game win | Solve a normal game. NB: a normal win now shows a **win dialog once/day** (`maybeShowWinSupport`, title = "Impressive!" etc. + "☕ Support" button); on later wins that day it's just the toast. Capture the dialog. |
| 10-daily-won-badge | Chip with green ✓ after a solved daily | Finish a daily with a win → the length chip gets a green ✓ badge. Capture the board/chip row. |
| 11-daily-lost-badge | Chip with red – after a lost daily | Finish a daily as a loss → chip gets a red – badge. |
| 14-timed-solved | Timed mode just after solving a word | Start Timed, solve one word → score ticks up + winMessage toast, timer running. |
| 26-modal-loading | Word-list download spinner | Pick a **not-yet-downloaded** language (e.g. Français) → the loading dialog shows during fetch. Needs network + a language whose cache is absent. |
| 27-modal-timeup | "Time's up!" dialog | Start Timed and let the 5-min clock run out (or solve some first). Dialog: score + Leaderboard/Done. |
| 28-modal-timed-submit | Timed leaderboard name entry | From the Time's-up dialog tap "Leaderboard" (score > 0) → name EditText dialog. |
| 29-modal-timed-board | Timed board mid-play | Start Timed, enter a couple of guesses, capture with the timer bar visible. |
| 30-modal-duel-result | Duel end dialog | Play a duel to the end → "You win! 🎉" / "Computer wins" / "Draw — board full" with Rematch/Done. |
| 31-modal-daily-submit | Daily win submit | Win a daily → leaderboard submit dialog (`promptSubmit`), name optional + Skip. |
| 32-modal-daily-failed | Daily loss dialog | Lose a daily → "Daily · N letters — Not solved, the word was …" + Leaderboard/Close. |
| 33-modal-daily-played | Re-open an already-played daily | Tap a length chip whose daily you've already finished today → `showDailyResult` dialog. |
| 34-modal-daily-board | Daily board mid-play | Enter a daily (blue chip) and play a few guesses; capture the board. |

## Reproduction notes (driver)

- Header buttons are at **original** pixels = displayed×1.2. NL≈(103,192), 📊≈(678,192),
  ⚙≈(823,192), New≈(971,192).
- Length chips ≈ y 330: 4=(271,330) 5=(404,330) 6=(539,330) 7=(673,330) 8=(808,330).
- **Keyboard letter taps depend on how many rows the keyboard has.** With the normal 3-row
  keyboard (no extra keys): row1 y≈1800, row2 y≈1978, row3 y≈2154. With an **extra-key
  language** (e.g. Dansk Æ/Ø → 4 rows) the whole keyboard shifts UP: row1 y≈1700, row2≈1849,
  row3≈2004, extra row≈2156. Recalibrate per language or the wrong letters get typed.
- Screencap/pull on this Git-Bash setup: use `screencap -p //sdcard/s.png` then
  `adb pull //sdcard/s.png <dest>` (double-slash avoids MSYS path mangling); wake first with
  `input keyevent KEYCODE_WAKEUP` and `svc power stayon true`.
- To force a **win** you must match the hidden word; there's no in-app cheat, so the win/result
  screens above are easiest to capture by playing manually.

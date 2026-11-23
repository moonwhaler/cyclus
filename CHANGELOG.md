# Changelog

Version scheme: `major.minor.patch`; Android `versionCode = major*10000 + minor*100 + patch`.

## Unreleased

- **Today, five new cards** (in „Karte hinzufügen“, off by default): **Zyklus vs. üblich**, **Temperaturkurve**,
  **Stimmung & Energie** (7 days against your usual on the same cycle days), **Fragen für die Ärztin** (your own list,
  tick off after the appointment; open questions prefill the Arztbericht; travels in backups, merged by question) and
  **Phasen-Hinweis** (current phase, one neutral line, your own patterns for this part of the cycle).

## 1.0.1 — 2026-11-23

- **Über Cyclus** (Einstellungen › Mehr): version, „Wichtiger Hinweis“ with every confirmation, how predictions
  work, and **Open-Source-Lizenzen**: every bundled component with its full license text, generated at build time
  (AboutLibraries); SQLCipher's license and the bundled fonts (OFL) added from checked-in files.

## 1.0.0 — 2026-11-23

First stable milestone. Android is feature-complete for v1 (phases A0–A5) and the reference implementation;
iOS follows with 100% parity per [`docs/IOS_PLAN.md`](docs/IOS_PLAN.md).

### Tracking & insight
- Today: cycle ring with live explainer (tap the ring or ⓘ), status line, Heute eingetragen, Demnächst, Woche.
- **Customizable Today:** edit in place („Ansicht anpassen“), drag to reorder with auto-scroll, hide into a card
  library, Groß/Kompakt ring, reset; timed cards appear only when relevant; layout travels in the backup.
- Deine Schnellauswahl: up to 8 one-tap symptoms, feelings or tags.
- Log sheet, Calendar, Insights, „Deine Muster“, „Ausblick“; Kinderwunsch mode (fertile window, LH plan, tests).
- Engines (prediction, fertility, learning, patterns) ported from the prototype and checked against its golden vectors.

### Trust & privacy
- No servers, no accounts, no tracking, no `INTERNET` permission; all logic on the device.
- Encrypted store (SQLCipher, hardware-held key, recovery after unlock); app lock (biometrics + Cyclus PIN).
- „Bevor du startest“ notice in onboarding: no responsibility for fertility or pregnancy outcomes, **not for
  contraception**; explicit consent recorded with date in Datenschutz, re-asked yearly, on a new notice version,
  on a restored phone and before fertility or Kinderwunsch is switched on.
- Datenschutz-Center, crypto-shred erase.

### Data
- Encrypted `.cyclus` export/import, cross-platform (Android ↔ iOS); passphrases normalized to NFC,
  older files still open. Format: [`docs/export/FORMAT.md`](docs/export/FORMAT.md).
- E2E-only device backup with restore.
- Arztbericht (PDF, DE/EN).
- Health Connect: import + opt-in write, foreground only.

### Platform
- Material 3 Expressive, colour themes incl. Material You, dark theme, tablets/foldables.
- Widgets, Quick Settings tile, opt-in shortcut, discreet opt-in notifications.
- German first, English complete; onboarding lets you go back and change an answer.

### Known limits
- Opening `.cyclus` files from other apps and the „Auf ein neues Handy umziehen“ guide: next (PARITY S-005).
- CSV export: next. iOS: planned, not built.
- The attached APK is the **optimized test build** (release code, R8, debug key, app ID
  `com.nordinary.cyclus.debug`): it installs over the debug app and keeps its data. A Play-signed release needs
  the release keystore (`keystore.properties`).

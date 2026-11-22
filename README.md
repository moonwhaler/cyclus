# Cyclus

Private, local-first cycle tracker. **German first**, iOS first, Android with full parity.
"Dein Zyklus, verstanden. Nie hochgeladen." No servers, no accounts, no tracking. All intelligence runs on the device.

## Documents
| File | What it is |
|---|---|
| [`docs/DESIGN_SPEC.md`](docs/DESIGN_SPEC.md) | **Main spec** (product, UX/UI, threat model, engines, delivery). Start with §0 (decisions D1–D10) |
| [`docs/ANDROID_PLAN.md`](docs/ANDROID_PLAN.md) | Android design & plan: parity rules, KMP architecture, Material 3 Expressive mapping, Health Connect, Play |
| [`docs/REVIEW.md`](docs/REVIEW.md) | Sceptic review of the first plan (12 MAJOR / 24 MINOR), all resolved in the spec (§0.3) |
| [`docs/PARITY.md`](docs/PARITY.md) | Parity matrix: one row per feature, Android and iOS status |
| [`docs/PLAN.md`](docs/PLAN.md) | First plan, superseded, kept for history |
| [`prototype/`](prototype/) | Clickable HTML prototype (DE/EN) with live period, fertility, pattern and outlook engines. **Visual and behavioural reference for both apps** |

## Prototype
```sh
cd prototype && python3 -m http.server 8765   # open http://127.0.0.1:8765/
```
Published (private) link: see `prototype/README.md`.

## Status (2026-11-22)
- Spec v1.0 + amendments:
  - fertility in v1;
  - German first;
  - no Apple Watch on hand;
  - „Deine Muster“ + „Ausblick“ in v1.
- **Android first** (ANDROID_PLAN D11 revised), Kotlin Multiplatform shared core confirmed (D10). iOS follows on the same `shared/` module.
- Trust rules: no `INTERNET` permission, no third-party services, all intelligence on-device, encrypted at rest (ANDROID_PLAN §0.1).
- Slice 1 (A0 + A1) done: engines ported and verified against the prototype (138 golden cases, JVM + Android), encrypted store (SQLCipher + Keystore), Today + Log sheet, deploy script.
- Slice 2 (A2) done: Kalender + Tagesansicht, Einblicke + Deine Muster, Kinderwunsch-Modus, Warum-/Methoden-Sheets, Einstellungen (Sprache, Ziel, Lebensphase). Next (A3): Arztbericht, Datenschutz-Center, Onboarding, App-Sperre, Export. See `docs/PARITY.md`.
- Slice 3 (A3a, trust core) done: encrypted `.cyclus` export/import, end-to-end-only device backup with restore, Datenschutz-Center, crypto-shred erase, app lock (biometrics + Cyclus PIN). 
- Slice 4 (A3b) done: Arztbericht (PDF, DE/EN, A4/Letter, real preview, share) and onboarding.
- Slice 5 (A4a) done: notifications (opt-in, discreet), Glance widgets, Quick Settings tile, opt-in shortcut, tablet/foldable layouts. Next (A4b): Health Connect (every write category off by default).
- Open owner decisions:
  - legal entity + organization developer accounts (Apple, Google);
  - regulatory specialist;
  - clinician reviewer.

## Build & run (Android)
```sh
./gradlew :shared:jvmTest        # engine golden tests (JVM)
./gradlew check                  # lint, no-INTERNET check, tests
tools/deploy.sh                  # build, install, launch on the local emulator (AVD cyclus-pixel9pro)
tools/deploy.sh --phone          # same, on the paired phone (wireless adb)
```
Debug builds (`com.nordinary.cyclus.debug`) hold **throwaway test data** until export/backup lands.

Engines are checked against golden vectors exported from the prototype (`tools/golden/`). App strings are generated from the prototype's translation table: `node tools/strings/export.mjs`.

**Never run the instrumented tests (`connected…Test`) on your own phone:** they uninstall the app afterwards, which deletes its data.

Backups: `.cyclus` format in `docs/export/FORMAT.md`; reference decoder `tools/export/cyclus_decode.py`.

Debug builds: long-press the screen title („Heute“) for the demo-data menu (prototype scenarios, erase).

Troubleshooting: if Gradle reports `Plugin ... was not found` or `Could not find androidx...` although the version exists,
Google Maven may be failing over IPv4 on your network (seen behind a VPN). Run Gradle with IPv6 preferred:
`./gradlew --stop && JAVA_TOOL_OPTIONS=-Djava.net.preferIPv6Addresses=true ./gradlew --refresh-dependencies build`.

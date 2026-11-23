# Cyclus for Android — Design & Delivery Plan

> Date: 2026-11-22 · Status: **Signed off 2026-11-22 (Android first, KMP confirmed)** · Companion to `DESIGN_SPEC.md` (iOS-first spec, v1.0 + amendments).
> Goal (owner): **100% feature parity with iOS**, built to **current Google standards (Material 3 Expressive)**, elegant, and visually "more or less 100% the same" as the HTML prototype (`prototype/index.html`).

---

## 0. Decisions

| # | Decision | Recommendation | Why |
|---|---|---|---|
| **D10** | Code-sharing strategy | ✅ **Confirmed 2026-11-22.** **Kotlin Multiplatform (KMP) shared core + native UI on each platform.** The shared Kotlin module holds the domain model, storage schema, cycle/fertility engines, „Deine Muster“, „Ausblick“, report model, backup format/crypto and all strings. **SwiftUI** on iOS, **Jetpack Compose + Material 3 Expressive** on Android. | One algorithm = one validation, and one medical-device software item instead of two diverging copies (regulatory, §8). Native UI keeps the Apple feel on iOS and Google standards on Android. No iOS code exists yet, so switching costs nothing now. |
| D10-alt A | Two fully native code bases (Swift + Kotlin) | ✗ | Engine written and validated twice; parity drifts; double regulatory evidence. |
| D10-alt B | Compose Multiplatform UI on both | ✗ | One UI, but iOS loses the native feel (Liquid Glass, SwiftUI behaviours). It conflicts with the iOS principle "feels like Apple built it". |
| D10-alt C | Flutter / React Native | ✗ | Rewrite of everything; neither platform's standard look; more third-party runtime code (SOUP). |
| **D11** | Platform order | ✅ **Revised 2026-11-22: Android first.** Shared core from day 1; Android UI is built first, iOS (SwiftUI on the same `shared/` module) follows. Launch rule unchanged: a release ships when the parity matrix is complete on both platforms, or a feature is flagged off on both (§10). | Owner has the Android toolchain and a Pixel 9 Pro for daily testing. Parity is guaranteed by the shared core and the parity matrix, not by build order. |
| **D12** | Android baseline | ✅ `minSdk 29` (Android 10), `targetSdk 36` (Android 16, **required by Play since 31 Aug 2026**), compile with the latest SDK. Re-check API 37 requirements before launch. | Covers the vast majority of DACH devices; Health Connect is available from Android 9 via Play and built into 14+. |
| **D13** | Material You dynamic colour | ✅ **Revised 2026-11-22 (owner):** part of Settings › Erscheinungsbild next to curated palettes, custom colours and Light/Dark/System; default stays Cyclus Rosé (= prototype). Wallpaper colours drive the **accent roles and surfaces** (bg = `surface`, cards = `surfaceContainerLowest`, surface2 = `surfaceContainer`, ink = `onSurface`, ink2 = `onSurfaceVariant`, line = `outlineVariant`, mid = `outline`, inverse accent = `inversePrimary`). Period, pre-period, fertile, success, warning and danger never change, because they carry meaning. | Google-standard personalisation without touching what the cycle colours mean. |
| **D14** | Play account | ✅ **Organization account** (same legal entity as D3, D-U-N-S). | Required anyway for health apps (credibility, liability). New *personal* accounts (after 13 Nov 2023) must run a 12-tester, 14-day closed test before production. Organization accounts are widely reported as exempt (verify in Play Console). |

### 0.1 Trust rules (hard, owner 2026-11-22)
Local first, data security and trust beat every feature.
1. **No network:** the manifest never contains `INTERNET` or `ACCESS_NETWORK_STATE`. A build check (`verifyNoInternet`) fails the build otherwise.
2. **No third-party runtime services:** no Google Play services, Firebase, analytics, ads or crash SDKs.
3. **Intelligence is on-device:** prediction, fertility, „Deine Muster“ and „Ausblick“ are deterministic local statistics. The language model (v1.5) runs on-device only (Gemini Nano / Foundation Models), templates otherwise. Any cloud AI would be a separate owner decision with explicit opt-in, and it would break rule 1, so it is not planned.
4. **Encrypted at rest from the first build:** SQLCipher with a Keystore-wrapped key (§2.4).
5. **Never lose data silently:** key or DB errors show a blocking error; the app never deletes or recreates an existing database on its own.
6. **No backup or device transfer** of the database until §6.4 is decided (`allowBackup=false` + `dataExtractionRules` excluding everything).

**Impact on `DESIGN_SPEC.md`:** §11.1/§11.2/§11.3 change from "Swift packages + SwiftData" to "KMP shared module + SQLDelight + native shells". Everything user-visible stays the same. See §2.5.

---

## 1. Parity principle

**Same features, same data, same algorithms, same words. Native interaction.**

1. **Feature parity:** every feature in DESIGN_SPEC (incl. fertility, „Deine Muster“, „Ausblick“, Doctor Visit Pack, Privacy Center) ships on both platforms in the same release, behind the same feature flags (e.g. `fertility(region:)`).
2. **Data parity:** the same `.cyclus` encrypted backup opens on both platforms. **iPhone → Android migration (and back) works.** CSV columns are identical.
3. **Algorithm parity:** one Kotlin implementation (shared module). Golden test vectors run on JVM, Android and iOS.
4. **Copy parity:** one German-first string source generates both platforms' resources. Banned-term lint runs once.
5. **Visual parity:** same tokens (colours, type roles, spacing, radii), same custom visuals (ring, droplets, body map, heat grids, charts, fertility shading), same screen structure and order. The **HTML prototype is the visual source of truth** for both apps.
6. **Native interaction:** navigation, back behaviour, sheets, system integrations, haptics and accessibility follow each platform's conventions (§1.1).

### 1.1 Allowed platform differences (exhaustive)
| Area | iOS | Android |
|---|---|---|
| Chrome material | Liquid Glass | Material 3 tonal surfaces (no blur glass) |
| Navigation | Tab bar, swipe-back, push | Navigation bar (rail on tablets), **predictive back**, edge-to-edge |
| Health store | HealthKit | Health Connect (fewer types → fewer categories mirrored, §6.1) |
| Lock-screen surface | Lock Screen widgets | **Quick Settings tile**. Phones have no lock-screen widgets; Glance home-screen widgets are on both |
| Control Center control | iOS 18+ control | Quick Settings tile (same function, same auth rule) |
| Siri / Shortcuts | App Intents (no auto App Shortcuts) | App shortcuts **off by default**; App Functions (Gemini) later |
| On-device LLM (v1.5) | Foundation Models | Gemini Nano (ML Kit GenAI Prompt API) on supported devices; templates otherwise |
| Hide the app entirely | iOS 18 "Hide and Require Face ID" | Android 15+ **Private Space** |
| Screenshot blocking | Not possible (switcher blur only) | Optional `FLAG_SECURE` „Bildschirmfotos verhindern“ + recents thumbnail hidden by default |
| Alternate icon | `setAlternateIconName` | `activity-alias` switch |
| Watch (v1.5) | watchOS app | Wear OS app (Compose for Wear, M3 Expressive) |
| Typeface | SF Pro (+ SF Pro Rounded numerals) | Roboto Flex (bundled) + **Nunito** numerals (as the prototype). See §3.3 |
| Touch target | ≥ 44 pt | ≥ 48 dp |

Anything not in this table must be identical. A difference that isn't listed is a **parity bug**.

### 1.2 Parity matrix (process)
- `docs/PARITY.md` (to be created at the start of Android work, phase A1): one row per feature ID (`F-001 Today ring`, `F-002 Log flow` …), with columns *Spec §*, *Prototype screen*, *iOS status*, *Android status*, *Screenshot pair*, *Notes*.
- Every PR touching UI updates the row. Release gate: all rows "done" on both platforms, or explicitly flagged off on both.

---

## 2. Architecture

### 2.1 Repository layout (monorepo)
```
cyclus/
├── docs/                         PLAN, REVIEW, DESIGN_SPEC, ANDROID_PLAN, PARITY
├── prototype/                    HTML prototype = visual + behavioural reference
├── shared/                       Kotlin Multiplatform module (commonMain / androidMain / iosMain)
│   ├── core/                     LocalDate, DayLog, Episode, Tag, enums (pure)
│   ├── engine/                   cycle detection, prediction (§11.5), fertility (§11.11), learning
│   ├── insights/                 „Deine Muster“ (§11.12), „Ausblick“ (§11.13), templates
│   ├── store/                    SQLDelight schema + migrations, repositories, pending-event queue
│   ├── report/                   ReportModel (what goes into the PDF; rendering is native)
│   ├── backup/                   .cyclus format, CSV, crypto (expect/actual → platform crypto)
│   └── i18n/                     strings source (de = source, en) + generator tasks
├── androidApp/                   Compose UI, Health Connect, Glance, QS tile, WorkManager, Biometric
├── iosApp/                       SwiftUI UI, HealthKit, WidgetKit, App Intents, LocalAuthentication
└── tools/                        banned-term lint, golden-vector generator, screenshot diff
```

### 2.2 What is shared vs native
| Concern | Shared (Kotlin) | Native |
|---|---|---|
| Domain model, validation | ✅ | — |
| Cycle / fertility / pattern / outlook engines | ✅ (pure, deterministic, seedable RNG) | — |
| Persistence | ✅ SQLDelight schema, queries, migrations | Driver + encryption: Android (SQLCipher + Keystore), iOS (Data Protection `.complete`) |
| View state | ✅ Presenters exposing immutable `UiState` (one per screen) + intents | Rendering (Compose / SwiftUI) |
| Health store sync rules (ownership, dedup, precedence) | ✅ state machine (§11.4 of spec) | HealthKit / Health Connect adapters |
| Notifications schedule (what & when) | ✅ | Delivery (UNUserNotificationCenter / NotificationManager + WorkManager) |
| Report content | ✅ `ReportModel` | PDF drawing (PDFKit vs `android.graphics.pdf.PdfDocument`) |
| Backup format, crypto | ✅ format; PBKDF2-HMAC-SHA256 + AES-256-GCM via `expect/actual` (javax.crypto / CommonCrypto + CryptoKit) | File pickers (SAF / UIDocumentPicker) |
| Strings | ✅ single source → generated `strings.xml` (+ plurals) and `Localizable.xcstrings` | — |
| LLM phrasing (v1.5) | ✅ prompt + post-checks | Model call (Foundation Models / Gemini Nano) |

### 2.3 Key libraries (Android)
Pin exact versions at project start and record them in the SOUP list.
- Kotlin 2.x (K2), Coroutines, kotlinx-datetime, kotlinx-serialization.
- **SQLDelight** (KMP) with **SQLCipher** Android driver (encryption at rest).
- **Compose Material 3 1.5** (stable; Expressive APIs such as `ButtonGroup`, `MotionScheme.expressive()` and `MaterialExpressiveTheme` are public from 1.5, not 1.4) and `material3-adaptive` (navigation suite, list-detail).
- **Navigation 3** (decided 2026-11-22, slice 2): saveable back stack of `@Serializable` keys for pushed screens (Settings, later Privacy/Arztbericht), predictive back; tabs stay a `NavigationSuiteScaffold` state.
- **Glance** (widgets), **WorkManager**, **Biometric** (`BiometricPrompt`), **Health Connect client** (`androidx.health.connect`).
- Testing: JUnit 5 (JVM shared tests), Compose UI test, **Roborazzi** or Compose Preview Screenshot Testing, Accessibility Test Framework.
- iOS bridge: **SKIE** (Flows → AsyncSequence, sealed classes → Swift enums). Revisit Kotlin **Swift Export** when it leaves alpha.
- **No** analytics, ads, crash SDKs or network libraries. Android manifest **without `INTERNET` permission** (stronger than iOS: enforced by the OS).

### 2.4 Process & storage (Android counterpart of spec §11.2 / §3.3)
- **Single writer:** the app process. Widgets and QS tile intents run in the app process, behind an unlock requirement.
- **Encryption at rest:** the SQLDelight DB uses SQLCipher. The 256-bit DB key is random, stored **wrapped by an Android Keystore AES key** (StrongBox if available). On **Android 15+** that key is created with `setUnlockedDeviceRequired(true)`: while the device is locked the key cannot be unwrapped, the Android equivalent of iOS `.complete`. **Android 10–14 get the key without that rule** (decision 2026-11-22, reproduced on emulators): there Keystore fails to create such a key on a phone without a screen lock, and deletes it when the screen lock is removed later, which would make the data unreadable. The wrapped file's version byte records the rule; a mismatch is re-wrapped on the next open (existing installs on 10–14, OS upgrades). A „device locked“ failure is detected via the keyguard and retried automatically on unlock (`ACTION_USER_PRESENT`) and when the app returns to the foreground.
- **Widget snapshot:** `files/widget/snapshot.sealed` (AES-GCM, Keystore key `cyclus_side_kek_v1`, usable while locked) with days only (last start, range, last bleeding day, paused), written **only while „Details im Widget“ is on**; the widget computes the cycle day from today, so it stays right after midnight. Readable without the DB key.
- **Reminder plan:** `files/notify/plan.sealed` (same key; reminder kinds, days, local times, discreet flag). Plaintext `.json` files of earlier versions are read once after an update and replaced on the next write. Planning happens only in the app with the DB open; one DB-free inexact alarm re-arms from this file (boot, update, clock and time-zone change, midnight). Receivers never open the DB.
- **No pending-event queue on Android (implemented 2026-11-22):** widget, tile and shortcut actions open the app through the non-exported alias `.ActionEntry` and run there after the app lock, exactly once (stripped from the intent, dropped during onboarding). Nothing writes outside the app UI, so there is nothing to queue. The exported launcher entry ignores action extras.
- **App lock:** `BiometricPrompt` (Class 3, device credential fallback) **plus** optional Cyclus PIN and "PIN only" (same as iOS).
- **Recents:** `setRecentsScreenshotEnabled(false)` (API 33+; `FLAG_SECURE` fallback on older versions) by default. Optional full `FLAG_SECURE`.

### 2.5 Changes to the iOS spec caused by D10
| Spec § | Before | After |
|---|---|---|
| 11.1 | Swift packages CycleCore, CycleEngine, CycleInsights, CycleBackup | KMP `shared/*` consumed via SKIE; Swift packages only for UI (CycleDesign), HealthKit adapter, Report renderer |
| 11.2–11.3 | SwiftData models | SQLDelight schema (same fields). The file protection rules for the SQLite file and its `-wal`/`-shm` stay as specified |
| 12.1 | Swift Testing for engine | Kotlin `commonTest` (JVM, fast) + run on iOS simulator in CI; Swift tests for UI and adapters |
| 13.1 | — | KMP setup added to iOS phases 2–3 (§10, A0) |

---

## 3. Android design system — "Cyclus on Material 3 Expressive"

**Principle:** structure, tokens and custom visuals come from the prototype. Components, motion, elevation and interaction come from Material 3 Expressive. The result should be recognisably *Cyclus* and recognisably *Android*.

### 3.1 Colour → M3 colour roles (same hex values as spec §5.1)
| Prototype token | M3 role | Light | Dark |
|---|---|---|---|
| `period` | `primary` | `#B3264E` | `#FF8FA8` |
| `onPeriod` | `onPrimary` | `#FFFFFF` | `#141114` |
| period 14% on surface | `primaryContainer` | `#F4E1E6` | `#43202B` |
| — | `onPrimaryContainer` | `#5C0F26` | `#FFD9E1` |
| `prePeriod` | `secondary` | `#6A4C9C` | `#BBA6EC` |
| `fertile` | `tertiary` | `#13706F` | `#5FD0C8` |
| `bg` | `background`, `surface` | `#FBF8F5` | `#141114` |
| `surface` | `surfaceContainerLowest` / card colour | `#FFFFFF` | `#1E1A1E` |
| `surface2` | `surfaceContainer` / `surfaceContainerHigh` | `#F3EEEA` | `#29242A` |
| `ink` | `onSurface`, `onBackground` | `#1D1A1C` | `#F4EFF1` |
| `ink2` | `onSurfaceVariant` | `#6B6267` | `#ABA1A6` |
| `line` | `outlineVariant` | `#E6DED8` | `#3A3438` |
| `mid` | `outline` (non-text only) | `#8A7F76` | `#8F858A` |
| `success` / `warning` | custom `CyclusColors` extension | as spec | as spec |

- **Accent vs meaning (2026-11-22):** `primary`/`secondary` (+ containers, `inversePrimary`) come from the chosen theme (`CyclusColors.accent*`): buttons, links, selection, FAB. `period`, `pre`, `fertile`, `success`, `warning` stay fixed and are used only where something means bleeding / a cycle phase / a status. `error` = fixed `danger` (today's period red) for errors and destructive actions. Theme palettes, OKLCH contrast correction and tests: `shared/.../presenter/Appearance.kt`. The table above is the default theme (Rosé).
- Appearance is a device setting in SharedPreferences (`appearance`), not health data: read before the encrypted DB opens (no theme flash on the lock screen), never exported or backed up, reset by erase. Night mode: `UiModeManager.setApplicationNightMode` on Android 12+, a base-context `uiMode` override (+ recreate) before.
- Today layout (spec §7.2): `today.layout`, `today.ring` and `quick.picks` are user prefs (`SettingKeys.USER_PREFS`: exported, merged, in device snapshots) but not engine settings (`SettingKeys.ENGINE` = mode, fertile window, muted patterns), so editing Today never recomputes the engine; they are also excluded from `SystemSurfaces.sync` (no reminder/widget replanning). Edit-mode drags are kept locally and saved on drop.
- Notice consent (`consent.notice`, spec §7.1) lives in the encrypted DB settings as device bookkeeping: `version|millis;…` (newest last, ≤ 20), not in `USER_PREFS` (never exported) nor `SECURITY` (not in the device snapshot), kept by replace-import, removed by erase. `Consent.needed` (shared) drives the gate in `MainActivity` after the lock, re-checked on resume.
- **Contrast:** values are already WCAG-verified (spec §5.1). Provide M3 *medium* and *high* contrast schemes (Android 14+ contrast setting) by darkening fills 15% (light) / lightening 10% (dark), mirroring iOS "Increase Contrast".
- Dynamic colour and themes per D13 (Settings › Erscheinungsbild).

### 3.2 Shape, elevation, spacing
- **Shapes:** M3 shape scale: `extraSmall 8` (scale buttons), `medium 16` (cards = prototype 16), `large 24` (sheets = prototype 24), `full` (chips, FAB, badges). Use M3 Expressive **shape morphing** only on press for the FAB and button groups.
- **Elevation:** tonal (surface container levels). Shadows only on FAB and sheets. No glass/blur.
- **Spacing:** the prototype's 4-dp grid (4/8/12/16/24/32) and 16-dp side gutter. Edge-to-edge with `WindowInsets` (status bar and gesture bar insets).

### 3.3 Typography
| Role | Prototype / iOS | Android (M3 role) | Face |
|---|---|---|---|
| Screen title („Heute“) | 32 bold | `headlineLarge` in **LargeFlexibleTopAppBar** with date as subtitle | Roboto Flex 700 |
| Ring big value | 40–46 rounded, shrink-to-fit | `displayMedium` + `TextAutoSize` (min 18 sp), single line | **Nunito 800** |
| Ring kicker / sub | 13 | `labelLarge` / `bodySmall` | Roboto Flex |
| Range | 18–20 rounded | `titleLarge` | Nunito 700 |
| Card title | 15–16 semibold | `titleMedium` | Roboto Flex 600 |
| Body | 14–15 | `bodyMedium` / `bodyLarge` | Roboto Flex |
| Section label (uppercase) | 11–12, tracking .06em | `labelSmall`, letterSpacing 0.6 sp | Roboto Flex 700 |
| Numbers in stats/charts | rounded, tabular | `fontFeatureSettings = "tnum"` | Nunito |

- **Bundle Roboto Flex and Nunito** (both OFL) so OEM system fonts (e.g. Samsung One UI) don't change the look.
- Honour font scale up to 200% (non-linear scaling, Android 14+). The ring rules from spec §6 apply: three lines max, big value shrinks, details below the ring.

### 3.4 Motion & haptics
- `MaterialTheme(motionScheme = MotionScheme.expressive())` for components. Custom ring/sheet animations use spring specs matching iOS (`dampingRatio ≈ 0.85`, `stiffness ≈ Spring.StiffnessMediumLow`).
- Respect "Remove animations" (`ANIMATOR_DURATION_SCALE == 0`): cross-fades only, like iOS Reduce Motion.
- **Haptics:** chip/flow select → `HapticFeedbackConstants.SEGMENT_TICK` / `TOGGLE_ON`; save → `CONFIRM`; errors → none. This mirrors spec §5.7.

### 3.5 Iconography
- **Material Symbols Rounded** (variable: weight 500, fill toggles for the selected state) replace SF Symbols.
- **Custom visuals are shared assets:** droplet (0–4 fill levels + dotted spotting), ring glyph, body map regions, ◎ ovulation, ✦ peak. They are exported once from the prototype's SVG into Android vector drawables and iOS SF-template symbols.

---

## 4. Component mapping (prototype → Compose Material 3 Expressive)

| Prototype / iOS | Android (Compose M3) | Notes |
|---|---|---|
| Tab bar (glass, 3 tabs) | `NavigationBar` (Expressive "short" bar) via `NavigationSuiteScaffold` → `NavigationRail` on ≥ 600 dp | Labels always shown (Heute / Kalender / Einblicke) |
| Floating „+ Eintragen“ | `ExtendedFloatingActionButton` (medium), above the nav bar, collapses to icon on scroll | Same label, same position (bottom-end) |
| Nav header (date + title + 2 icon buttons) | `LargeFlexibleTopAppBar` (title + subtitle) with two `IconButton`s (Privacy shield, Settings) | Collapses on scroll |
| Cycle ring | Custom `Canvas` composable, identical geometry (270°, segments, dashed predicted arc, knob, ◎/✦) | Semantics: one node with the full sentence + custom actions |
| Bottom sheets (Log, Day, Why, Method, Erase) | `ModalBottomSheet` with drag handle; Log sheet opens expanded at large font scale | Predictive back closes the sheet |
| Pushed screens (Privacy, Settings, Arztbericht) | Full-screen destinations with `LargeFlexibleTopAppBar` + back arrow | Predictive back animation |
| Chips (symptoms, feelings, tags, LH) | `FilterChip` (selected = check icon + `primaryContainer`; LH uses the `tertiary` variant) | Suggested chips: dashed outline (custom border) |
| Flow droplets (5) | Custom toggle tiles in a 5-column row (`selectable` semantics, role = RadioButton) | Same labels incl. „Schmier­blutung“ hyphenation |
| 1–5 scale | Expressive `ButtonGroup` (connected), single select | End labels below, as prototype |
| Segmented control (Ziel, Sprache, Zeitraum, PDF-Sprache) | `SingleChoiceSegmentedButtonRow` | — |
| iOS switch rows | `ListItem` + `Switch` (thumb icon) | Whole row clickable |
| Status / tier badges | Custom pill `Surface` (non-interactive). Pattern statuses: filled / dashed / success text | Same wording |
| Grouped lists | `Card` containing `ListItem`s with dividers (`outlineVariant`) | — |
| Toast with „Rückgängig“ | `Snackbar` with action (5 s for undo) | Above FAB and nav bar |
| Pain map | Custom `Canvas` with region hit-testing + M3 `Slider` (11 stops, value label) | TalkBack: list fallback |
| Date header arrows in log sheet | `IconButton`s + horizontal swipe | — |
| Onboarding date | `DatePicker` (M3, dialog or inline) | Locale de-DE, Monday first |
| Calendar month grid | Custom `LazyVerticalGrid` (7 columns), identical cell semantics | Same legend |
| Charts (cycle bars, temperature, heatmaps, Ausblick grid) | Custom `Canvas` (no chart library, so pixel parity with prototype and less SOUP) | Semantics summary text, same as iOS audio-graph text |
| „Warum?“ disclosure | Expandable row with `AnimatedVisibility` | — |
| App lock screen | Full-screen composable + `BiometricPrompt` | — |
| Progress (import) | `LinearWavyProgressIndicator` (Expressive) | — |

---

## 5. Screens (Android specifics only; content = DESIGN_SPEC §7 + prototype)

| Screen | Same as | Android specifics |
|---|---|---|
| Onboarding (6 steps) | §7.1 | Health Connect availability check. If missing (Android ≤ 13): "Health Connect installieren" → Play link, or skip. Import requests read + `READ_HEALTH_DATA_HISTORY` (otherwise only 30 days are readable). Notification permission (`POST_NOTIFICATIONS`, API 33+) asked in context later, as on iOS |
| Today (Track / Conceive / Period / Paused / Empty) | §7.2, §7.14, prototype | Ring + "Periode hat heute begonnen" button; Ausblick card; "N Muster gefunden" link. Top app bar collapses; FAB shrinks on scroll |
| Log sheet | §7.3 | Same stable section order incl. Gefühle and Lust auf Sex. `imePadding()` for the note field |
| Calendar + Day sheet | §7.4 | Long-press → context menu "Als Periodenbeginn markieren" |
| Insights („Deine Muster“, charts, Arztbericht, history) | §7.5, §11.12 | On ≥ 600 dp: list-detail (history list ↔ cycle detail) |
| Why this window? | §7.14 | — |
| Doctor Visit Pack | §7.7 | `PdfDocument` + `Canvas`, A4 / Letter by locale. Shared via `FileProvider` URI from `cacheDir/share/` (the same file is previewed), deleted at the next build, at app start and on erase — not on the share result, because the target app may read it later. Metadata: only Skia's /Producer (spec m11) |
| Privacy Center | §7.8 | Extra rows: „Bildschirmfotos verhindern“ (FLAG_SECURE), „In Private Space verschieben“ (explainer, Android 15+), Health Connect categories (§6.1), backup (§6.4) |
| Settings | spec §4 | Language via per-app locale (`LocaleManager` / AppCompat). Follow system theme. Dynamic colour toggle (D13) |
| App lock | §7.12 | `BiometricPrompt` + PIN pad |
| Large screens | — | Two-pane: Today + Calendar on tablets/foldables. Log sheet becomes a side sheet. No orientation lock (Android 16 ignores it on large screens anyway) |

---

## 6. Platform services

### 6.1 HealthKit → Health Connect mapping
**Mirror default (decided 2026-11-22, both platforms): every write category is off until the user switches it on.** The last column shows the planned v1 defaults before that decision; reading (import) is offered separately.

| Cyclus field | iOS HealthKit | Android Health Connect | Mirror default before 2026-11-22 (now: off) |
|---|---|---|---|
| Flow light/medium/heavy | `menstrualFlow` (+ cycle start flag) | `MenstruationFlowRecord` (LIGHT/MEDIUM/HEAVY/UNKNOWN) **+ `MenstruationPeriodRecord`** (start–end of the period) | On |
| Spotting | `intermenstrualBleeding` | `IntermenstrualBleedingRecord` | On |
| Discharge | `cervicalMucusQuality` | `CervicalMucusRecord` (appearance incl. EGG_WHITE, UNUSUAL; sensation) | Off |
| LH test | `ovulationTestResult` | `OvulationTestRecord` (POSITIVE / HIGH / NEGATIVE / INCONCLUSIVE) | Off |
| BBT | `basalBodyTemperature` | `BasalBodyTemperatureRecord` | On |
| Sex | `sexualActivity` | `SexualActivityRecord` (protection used) | Off |
| Wrist/skin temperature | `appleSleepingWristTemperature` (read) | `SkinTemperatureRecord` (read; **baseline + deltas**; Pixel Watch, Galaxy Watch, Oura …) | Read only, display only (same rule as iOS: not used by the fertility engine until validated on real devices) |
| Symptoms, feelings, mood, libido, pain, tags, notes | Symptom types / `HKStateOfMind` | **No equivalent types** (verify against the current data-type list at project start) | Android: Cyclus only |
| Pregnancy / lactation / contraceptive | HK types | No equivalent (verify) | Android: Cyclus only |
| Sleep, resting HR, HRV (v1.5) | read | `SleepSessionRecord`, `RestingHeartRateRecord`, `HeartRateVariabilityRmssdRecord` | read |

**Implemented 2026-11-22 (slice 6).** Owner decisions: import + opt-in write; sync **only while the app is open** (app start/resume, 3 s after the person's own entries or the Health Connect toggles change, „Jetzt synchronisieren“); no background permission, no WorkManager jobs. Notes from the implementation:
- Health Connect values are merged **before** `CycleData` (`HealthMerge`), so the engine and its golden vectors are unchanged; they fill only fields the person left empty. A user `flow = none` hides an external flow; other external values can't be hidden yet.
- External `MenstruationPeriodRecord` days become bleeding without a level, so the engine finds the start itself (no external START overrides).
- Writes come from the person's own entries only; `clientRecordVersion = max(previous + 1, now)` so imports with older timestamps still update. Local deletes keep a `deleting` tombstone until Health Connect confirms.
- Reinstall recovery (rule 7) only into an empty store and only after an explicit „In Cyclus übernehmen“ in the Privacy Center (offer shown once per connection), so deleted, erased or deliberately unmirrored entries never come back on their own.
- Reads run outside the erase lock; each page is stored together with its changes token in one transaction; writes run inside the lock for the current store only.
- Imported samples, links and tokens are device data: not in `.cyclus` exports or device snapshots; gone with erase and „Trennen“.
- Release task: Play **Health apps declaration** with the per-type justification (read: import; write: opt-in mirror).

**Sync rules** are the same shared state machine (spec §11.4). Health Connect equivalents:
- `Metadata.clientRecordId` = sync identifier (`cyclus:<key>`);
- `clientRecordVersion` = sync version (an upsert replaces lower versions);
- **Changes API** (`getChangesToken` / `getChanges`) replaces anchored queries and gives deletions of own records → `removedInHealth` review, as on iOS;
- `dataOrigin` = source app → precedence: Cyclus > Samsung Health / Google Fit / Fitbit > other apps.

**Permissions:**
- Health Connect uses granular per-type permissions; request them per category when the user enables mirroring.
- `READ_HEALTH_DATA_HISTORY` is needed for a full history import.
- `READ_HEALTH_DATA_IN_BACKGROUND` is only needed if background sync is wanted (default: sync on app open + WorkManager when unlocked).
- Play requires the **Health apps declaration** plus a justification for each Health Connect type.

### 6.2 Widgets, tiles, shortcuts (implemented 2026-11-22, slice 5)
- **Glance widgets:** small (2×2: ring, „Tag N“, range) and medium (4×2: plus „Periode eintragen“ / „Eintragen“). **„Details im Widget“ is off by default** (Privacy Center › Sichtbarkeit): the widget then shows only the app glyph and „Cyclus“, and no widget file exists. Buttons open the app (app lock applies); „Periode eintragen“ logs the period start with Undo, or opens the log sheet if today already has bleeding.
- **Quick Settings tile** „Cyclus · Eintragen“ → `startActivityAndCollapse` (the system asks for the device unlock on the lock screen) → log sheet behind the app lock. Note: wrapping it in `unlockAndRun` made SystemUI drop the start after unlocking (API 36).
- **App shortcut** „Eintragen“ (long-press icon): **off by default**, opt-in in Privacy Center › Sichtbarkeit (parity with iOS no-auto-App-Shortcuts). App Functions / Gemini integration is a v2 consideration for both platforms.
- Glance brings WorkManager; its `ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE` permissions are removed in the manifest (`verifyNoInternet` checks the merged manifest).

### 6.3 Notifications (implemented 2026-11-22, slice 5)
- **Opt-in on every API level:** nothing is planned until the user says yes (Today card „Vor deiner Periode erinnern?“ or Einstellungen › Mitteilungen). Android 13+ then asks for `POST_NOTIFICATIONS`. A restore or new phone starts without reminders (the `notify.*` settings are device settings, not in exports or snapshots).
- **Defaults after opt-in:** „Periode bald“ (2 days before the earliest expected start, 09:00) on; „Später als sonst“ off (spec m9); daily reminder, backup reminder off; Conceive: fertile window and LH test on, temperature off.
- **Discreet by default:** title „Cyclus · Check-in“ / „Cyclus · Erinnerung“ / „Cyclus · Sicherung“, text „Tippe, um Cyclus zu öffnen.“ Detailed mode shows a fixed sentence, never dates, numbers or symptoms. `VISIBILITY_PRIVATE` with a public version that shows only the neutral title.
- Channels „Periode“, „Fruchtbarkeit“, „Erinnerungen“, „Sicherung“.
- **Scheduling:** one `setAndAllowWhileIdle` (inexact, up to about an hour late) for the next item of the plan file; **no exact-alarm permission**, no WorkManager jobs. Missed reminders older than 12 h are dropped instead of shown late.

### 6.4 Backup & transfer (decided 2026-11-22, implemented in slice 3)
- **Decision (owner):** spec D5 parity. An encrypted snapshot goes into Android backup **only** when the transport is end-to-end encrypted (client-side encryption with the screen lock) or a device-to-device transfer. Toggle „Nur auf dem Gerät“ turns it off. Plus passphrase-encrypted `.cyclus` export/import (`docs/export/FORMAT.md`).
- **Mechanism:** `CyclusBackupAgent` (full backup, custom) reads files only, never the database (it runs restricted, often while locked):
  - `files/backup/snapshot.cyclus` = mode-2 `.cyclus` of all data incl. app-lock settings, written when the app goes to the background after a change;
  - the 256-bit backup key, wrapped by Keystore key `cyclus_backup_kek_v1` (no unlocked-device requirement, so nightly backups work);
  - `.device-only` marker, `snapshot.absent` marker, `status.json` (shown in the Privacy Center).
- **Backup run:** device-only, no E2E/D2D flag or intentionally no snapshot → nothing written. Everything (key unwrap + full snapshot authentication) is validated before the first byte; on failure nothing is written and the status says „failed“. Only then the snapshot and a transient plain copy of the key are streamed (deleted in `finally`; stale copies swept in `Application.onCreate`). Observed with LocalTransport: an empty run is rejected by the transport, so no empty set is committed (not promised to users).
- **Restore:** `onRestoreFile` wraps the backup key from the stream for the new device (no plaintext key on disk) into `restore/`; `onRestoreFinished` marks `restore/pending` only if snapshot and key both arrived. The first launch imports atomically into a fresh encrypted DB, promotes the key, and on failure deletes only that fresh DB (Retry / „Neu beginnen“).
- **Never backed up:** the database, its Keystore-wrapped key, preferences. Rules XML include exactly the two backup files (defence in depth).
- **Encrypted export/import** via the Storage Access Framework (`CreateDocument` / `OpenDocument`). Same `.cyclus` v1 format → cross-platform migration. Exports never contain app-lock/PIN settings.

### 6.5 On-device language model (v1.5, parity with iOS)
- Gemini Nano via the **ML Kit GenAI Prompt API**. Device list is limited (e.g. Pixel 10, Galaxy S26, recent vivo/Xiaomi). Inference runs only while the app is in the foreground.
- Same rules as spec §11.8: templates primary, the model only rephrases, number/term post-checks, fallback to template. Shared prompt + checks in KMP.

### 6.6 Wear OS (v1.5, parity with Apple Watch app)
Compose for Wear OS + Wear M3 (Expressive): log period/symptom, glance at cycle day and range, tile + complication. Same privacy defaults.

---

## 7. Threat model deltas (Android vs spec §3)
| Adversary / surface | Android behaviour | Mitigation |
|---|---|---|
| A2/A3 person with device PIN | Can open apps | Cyclus PIN / "PIN only"; recommend **Private Space** (Android 15+) |
| A4 seizure | FBE credential-encrypted storage + SQLCipher key bound to unlocked device (Android 15+; 10–14: hardware-bound only, see §2.4) | `setUnlockedDeviceRequired(true)` on 15+ |
| A5 cloud | Google backup is E2E with the screen lock | Toggle „Nur auf dem Gerät“; honest copy |
| Other apps with Health Connect access | The user may have granted other apps cycle read access | Every write category off by default (both platforms); Privacy Center says Health Connect has no own lock and that other apps with access can read what Cyclus writes |
| Health Connect permission rationale | Must be reachable by the system and reviewers | Static `HealthRationaleActivity` with text only (no data, no app lock); the `VIEW_PERMISSION_USAGE` alias requires `START_VIEW_PERMISSION_USAGE` |
| Screenshots / screen sharing | Possible | Optional `FLAG_SECURE`; recents thumbnail hidden by default |
| OEM cloud features (e.g. Samsung) | OEM backup/sync of app data varies | Same `dataExtractionRules`; document tested OEMs |
| Rooted devices | Keystore can be weaker | No blocking (accessibility/fairness); disclosure in FAQ |
| Network | **No `INTERNET` permission** | OS-enforced "no data leaves via the app" (privacy label claim is verifiable) |
| Device backup snapshot | The snapshot key is hardware-wrapped and not usable from a disk image, but **code running as Cyclus on a locked phone after first unlock can decrypt the snapshot** (the DB key needs an unlocked device) | Accepted for automatic backups; only sent over E2E/D2D; „Nur auf dem Gerät“ removes snapshot and key |
| Reminder plan / widget file | `files/notify/plan.sealed` (reminder kinds and days, which hint at the predicted period) and, only with „Details im Widget“, `files/widget/snapshot.sealed` are outside the encrypted DB, sealed with their own Keystore key that works while locked, so alarms and widgets work while the phone is locked; readable by code running as Cyclus after first unlock (the DB is not) | Sealed + credential-encrypted storage (a disk image alone can't read them), never in backups (include-only rules), files and key deleted on erase; honest note in Privacy Center › Sichtbarkeit; AlarmManager holds the same times anyway |
| Widget / tile / shortcut actions | Other apps could try to send actions to the exported launcher activity | Actions are honoured only through the non-exported `.ActionEntry` alias; they run after the app lock, once |
| Cyclus PIN | UI gate only: its hash lives inside the encrypted DB | Back-off after 5 wrong tries (elapsedRealtime); not presented as data encryption |

---

## 8. Regulatory & store
- **One medical device, two platforms.** The technical documentation covers both app variants as configurations of the same software: shared core = one software item; platform shells = separate items. With KMP the safety-relevant algorithms are validated **once**. The SOUP list adds Kotlin stdlib, kotlinx, SQLDelight, SQLCipher, AndroidX/Compose, Health Connect client and SKIE.
- **Google Play:**
  - **organization account** (D14);
  - target API 36;
  - **Health apps declaration** and Health Connect data-type justifications (with a demo video);
  - **Data safety: "No data collected / shared"** (backed by the missing `INTERNET` permission);
  - privacy policy and Consumer Health Data policy links;
  - IARC content rating;
  - DE-first store listing;
  - medical-device information as requested by the declaration;
  - the same intended-purpose wording and banned terms as the App Store (spec §1.4, §9.3).
- **Play Integrity, Firebase and Google Play services dependencies: none** (no server, works on de-Googled devices).
- **Release:** internal → closed (≥ 12 testers × 14 days if a personal account is used) → production with a staged rollout (10% → 50% → 100%).

---

## 9. Quality & parity testing
| Layer | Tooling | Runs |
|---|---|---|
| Shared engine (prediction, fertility, patterns, outlook, backup crypto) | `commonTest` on JVM + iOS simulator + Android instrumented smoke. **Golden vectors generated from the prototype harness** (same inputs → same outputs) | Every push |
| Null-simulation & recall gates (spec §11.7, §11.12) | JVM | Nightly |
| Store migrations | SQLDelight migration verification + fixtures | Every push |
| Compose UI | Compose UI tests (onboarding, log, export/import, erase, lock) | Nightly |
| Visual parity | Roborazzi / Compose Preview screenshots at **412 × 915 dp** (light/dark × font 100%/200% × DE/EN), compared side by side with prototype screenshots at the same width | Every UI PR |
| Accessibility | ATF checks in tests + manual TalkBack, Switch Access, Voice Access, font 200%, display size max, high-contrast text | Release checklist |
| Health Connect | Device tests (write/edit/delete own, Samsung Health data, history permission denied, changes token expiry) | Release checklist |
| Locked-device | QS tile and widget while locked, Keystore unwrap failure UX, pending-queue drain | Release checklist |
| No network | `INTERNET` permission absent (manifest lint) | Every push |
| Device matrix | Pixel (6a, 8, 10), Samsung A-series + S-series (One UI), one Xiaomi (battery-killer behaviour), one foldable, one tablet; Android 10, 12, 14, 16, latest | Release |

---

## 10. Delivery plan
Phases with exit criteria, no effort or time estimates (same rule as DESIGN_SPEC §13).

### 10.1 Android work packages
1. **KMP setup** (first): shared module, SQLDelight, golden-vector pipeline from the prototype. SKIE bridge and iOS CI when iOS work starts.
2. **Android foundations:** scaffold, Gradle conventions, lint (banned terms, no `INTERNET`), CI.
3. **Design system:** theme, tokens, type, shapes, ring, droplets, body map, charts, heat grids.
4. **Core screens:** Today (Track, Conceive, Period, Paused, Empty; Ausblick card), Log sheet, Calendar + Day sheet.
5. **Insights:** „Deine Muster“, charts, history, Why/Method sheets.
6. **Completeness:** onboarding (Health Connect availability and permissions), Privacy Center, app lock (Biometric + PIN), FLAG_SECURE, erase, settings, episodes, per-app language, dynamic-colour toggle, Doctor Visit Pack PDF + share.
7. **Platform services:**
   - Health Connect adapter (history import, write-own, changes API) + device tests;
   - SQLCipher + Keystore;
   - backup agent/rules;
   - SAF export/import;
   - Glance widgets + QS tile;
   - notifications.
8. **Large screens:** navigation rail, two-pane, side sheet.
9. **Readiness:**
   - accessibility pass;
   - localisation;
   - Play Console listing (DE/EN), Data safety, Health declarations and video;
   - device matrix and OEM quirks.
10. **Beta & release:** closed testing ≥ 2 cycles, fixes, staged rollout.

### 10.2 Phases (Android first, D11 revised)
| Phase | Content | Exit criteria |
|---|---|---|
| A0 | KMP shared module; engines/insights ported from `prototype/index.html`; golden vectors from the prototype harness | Golden vectors green on JVM **and** on device |
| A1 (slice 1) | Scaffold, theme/tokens, encrypted store (SQLCipher + Keystore), Today + Log sheet, deploy script; create `docs/PARITY.md` | Runs on the owner's Pixel; `verifyNoInternet` green |
| A2 | Calendar + Day sheet, Insights, Deine Muster, Ausblick detail, Why/Method sheets, Conceive UI | Screenshot pairs with prototype approved |
| A3 | Arztbericht, Privacy Center, Settings, Onboarding, app lock, backup/export (§6.4 decided) | Parity rows done |
| A4 | Health Connect, widgets, QS tile, notifications, adaptive layouts | Device checklist passes |
| A5 | Accessibility, localisation, device matrix | **Internal testing** track |
| I1–I3 | iOS: add `iosArm64` targets + SKIE, SwiftUI shell, HealthKit, WidgetKit, against the same parity rows | Parity rows done on iOS |
| A6 | Closed testing (≥ 2 cycles of beta on both platforms), fixes, Play declarations | All parity rows done on both platforms |
| A7 | Staged production rollout (10% → 50% → 100%) | Crash-free ≥ 99.5% at each stage |

### 10.3 Risks
| Risk | Mitigation |
|---|---|
| KMP ↔ Swift friction (Flows, sealed types, generics) | SKIE; keep the shared API small and presenter-shaped; Swift Export later |
| Health Connect gaps (no symptoms/mood types) and OEM differences | Cyclus remains source of truth; honest Privacy Center copy; test Samsung Health |
| Fragmentation, OEM battery killers delaying notifications | Inexact alarms + WorkManager; "Benachrichtigungen kommen zu spät?" help page per OEM |
| Font/OEM rendering differences | Bundled fonts; screenshot tests on Samsung + Pixel |
| Gemini Nano availability | Templates are the product; LLM is polish |
| Backup key handling bug → data loss | Spike + device-transfer and restore tests before beta |
| Play health policy review delays | Prepare declarations and video early; organization account |

---

## 11. Handoff checklist (continue on another machine)
1. `git clone https://github.com/moonwhaler/cyclus` (private).
2. Read in order: `docs/DESIGN_SPEC.md` §0 (decisions) → this file §0–§2 → `prototype/README.md`.
3. Open the prototype (`python3 -m http.server` in `prototype/`), or use the published link in `prototype/README.md`.
4. Owner decisions still open:
   - D3/D14 legal entity + organization accounts (Apple + Google);
   - D10 KMP confirmation;
   - regulatory specialist booked;
   - clinician reviewer named;
   - App Review question on guideline 5.1.3(ii).
5. Toolchain: latest stable Android Studio + Kotlin Multiplatform plugin, JDK 21, Xcode (latest), SKIE, Ruby/Bundler only if fastlane is wanted.
6. First engineering task: **port the prototype's JS engines to Kotlin `shared/engine` + `shared/insights`**, with golden vectors exported from the prototype harness:
   - `predict`;
   - `fertility`;
   - `learnFromHistory`;
   - `patternWatch`;
   - `forecast`.

---

## Sources
- Compose Material 3 releases (1.4 stable with Expressive components): https://developer.android.com/jetpack/androidx/releases/compose-material3 · component overview: https://composables.com/material-3-expressive
- Health Connect data types and history/background permissions: https://developer.android.com/health-and-fitness/health-connect/data-types · records package: https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/package-summary · skin temperature: https://developer.android.com/health-and-fitness/health-connect/features/skin-temperature
- Play target API requirements (API 36 by 31 Aug 2026): https://support.google.com/googleplay/android-developer/answer/11926878 · publishing health apps: https://developer.android.com/health-and-fitness/health-connect/publish
- Personal account testing requirement (12 testers × 14 days): https://support.google.com/googleplay/android-developer/answer/14151465
- ML Kit GenAI / Prompt API (Gemini Nano devices): https://developers.google.com/ml-kit/genai · https://android-developers.googleblog.com/2025/10/ml-kit-genai-prompt-api-alpha-release.html
- Kotlin Multiplatform (Android guidance, Swift export status): https://developer.android.com/kotlin/multiplatform · https://kotlinlang.org/docs/native-swift-export.html · https://kotlinlang.org/docs/multiplatform/kmp-for-ios.html

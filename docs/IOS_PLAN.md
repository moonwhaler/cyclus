# Cyclus for iOS — Design & Delivery Plan

> Date: 2026-11-23 · Status: **Draft for owner sign-off** · Companion to `DESIGN_SPEC.md` (product spec) and `ANDROID_PLAN.md`.
> Goal (owner): **100% parity with the Android app as built** (slices 1–6 + everything after, see `PARITY.md`), on the same Kotlin Multiplatform core, with a native SwiftUI UI that feels like Apple built it.
> Rule of thumb: **the Android app is the reference implementation**, the HTML prototype is the visual and copy reference, and `DESIGN_SPEC.md` is the product spec. Where the spec's original iOS details (written before D10/D11) differ from what Android ships, this plan wins for iOS and §10 lists every superseded spec section.

---

## 0. Decisions

| # | Decision | Status | Why |
|---|---|---|---|
| **I1** | Baseline | ✅ **Owner 2026-11-23: deployment target iOS 26.0**, built with the current Xcode (iOS 27 SDK); newer APIs behind `if #available`. iPhone 11 and newer. | Native Liquid Glass, Foundation Models for v1.5, reach comparable to Android 10. Same as spec D7. |
| **I2** | Database encryption | ✅ **Owner 2026-11-23: SQLCipher + Keychain**, same as Android (§2.4). The store is encrypted with a random 256-bit key held in the Keychain, usable only while the iPhone is unlocked; iOS file protection on top. | One store code path, one recovery behaviour (locked detection + automatic retry) on both platforms; defence in depth beyond file protection. |
| **I3** | Device backups | ✅ **Owner 2026-11-23: end-to-end only, key in the Keychain** (§6.4). The database is excluded from every backup; a sealed snapshot goes into iCloud/computer backups, but its key lives only in **iCloud Keychain** (end-to-end encrypted), so a backup Apple can read is useless without it. Needs a spike on real devices before it ships (§6.4.3). | Parity with Android's rule "snapshot only into end-to-end encrypted transports"; iOS can't tell whether Advanced Data Protection is on, so the key, not the backup, carries the protection. |
| **I4** | Platform surfaces | ✅ **Owner 2026-11-23: same function, native surface.** Every function and privacy default is identical; each lives on the platform's own surface (QS tile ↔ Control Center control, Glance ↔ WidgetKit, Private Space ↔ Hide app …). The canonical list of allowed differences is in `PARITY.md`. | Parity of what people can do and what is protected, without fake Android widgets on iPhone. |
| **I5** | Swift ↔ Kotlin bridge | ✅ Recommended: **SKIE** on top of the Kotlin/Native framework (Flows → `AsyncSequence`, sealed classes → Swift enums, suspend → async). Re-evaluate Kotlin **Swift Export** when it is stable. Verify SKIE supports the project's Kotlin version (currently 2.4.x) before I0; if not, pin the newest Kotlin it supports for the iOS framework build or use plain Kotlin/Native headers behind a thin Swift facade. | Smallest, most idiomatic Swift API with today's tools. |
| **I6** | Shared-first refactor | ✅ Recommended: before iOS UI work, move the platform-neutral logic that today lives in `androidApp` into `shared` (§2.3). Android keeps working the same way, but iOS writes no business logic twice. | Parity by construction instead of by re-implementation. |
| **I7** | Health store categories | ✅ Recommended for v1: **the same six write categories as Android** (bleeding/periods, spotting, temperature, discharge, LH tests, sex) and the same import set. HealthKit-only types (symptoms, State of Mind, contraceptive/pregnancy/lactation) stay "Cyclus only" in v1; adding them later is an allowed difference that needs owner approval. | Identical Privacy Center and identical promises on both platforms. |

### 0.1 Trust rules (hard, identical to ANDROID_PLAN §0.1)
1. **No network:** no `URLSession`, `Network`, `WebKit`, `AsyncImage`, `WKWebView`, no `Link`/`openURL` to non-allow-listed URLs. Lint fails the build (`tools/ios/verify-no-network`); release check: App Privacy Report on a device after the full UI-test run shows **zero domains**. (iOS has no permission that blocks networking, so the check is ours to enforce.)
2. **No third-party runtime services:** no analytics, crash, ads or tracking SDKs. Runtime third-party code is limited to the KMP framework (Kotlin stdlib, kotlinx, SQLDelight, SKIE runtime) and SQLCipher. Test-only: snapshot testing.
3. **Intelligence on-device:** engines from `shared`; the v1.5 language model is Foundation Models, templates otherwise.
4. **Encrypted at rest from the first build:** SQLCipher + Keychain (§2.4).
5. **Never lose data silently:** key or DB errors show the blocking error screen; the app never deletes or recreates an existing database on its own.
6. **The database is never in a backup** (`isExcludedFromBackup` on the DB files); only the sealed snapshot of §6.4.

---

## 1. Parity principle
Same as ANDROID_PLAN §1: same features, same data (the same `.cyclus` file opens on both; Android → iPhone and back works), same algorithms (one Kotlin implementation, golden vectors also on the iOS simulator), same words (one string source), same visuals (tokens, ring, droplets, body map, charts), **native interaction**.

### 1.1 Allowed differences
Canonical, exhaustive table: **`PARITY.md` › Allowed platform differences** (moved there from ANDROID_PLAN §1.1 and extended for I4). Anything not listed there is a parity bug.

### 1.2 Reference order
1. Behaviour as the Android app does it today (code in `shared/` and `androidApp/`, tests, `PARITY.md` notes).
2. Visuals and copy as in `prototype/index.html` (strings via the generator, never retyped).
3. Product intent and constraints from `DESIGN_SPEC.md`.

---

## 2. Architecture

### 2.1 Repository layout
```
cyclus/
├── shared/                 KMP: commonMain (+ new iosMain), jvmCommon, androidMain
├── androidApp/             Compose UI + Android services
├── iosApp/
│   ├── Cyclus.xcodeproj
│   ├── Cyclus/             SwiftUI app (single writer), App Intents, LocalAuthentication, HealthKit, PDF
│   ├── CyclusWidgets/      WidgetKit extension + Control Center control (reads sealed side files only)
│   ├── Packages/CyclusDesign   tokens, ring, droplets, body map, charts, chips (SwiftUI)
│   └── Shared.xcframework  built from shared/ (SKIE), via a local Swift package wrapper
└── tools/
    ├── strings/export.mjs  + Localizable.xcstrings output (X-001)
    └── ios/                build/deploy scripts, no-network lint, screenshot pairing
```

### 2.2 Kotlin targets
- Add `iosArm64` and `iosSimulatorArm64` to `shared` (Apple targets need a macOS host for linking; the Linux machine keeps building Android and JVM).
- New `iosMain` actuals:

| Expect (commonMain) | Android/JVM today | iOS actual |
|---|---|---|
| `CyclusCrypto` (random, PBKDF2-HMAC-SHA256, AES-256-GCM, raw DEFLATE) | `javax.crypto`, `java.util.zip` (`jvmCommon`) | `SecRandomCopyBytes`; `CCKeyDerivationPBKDF` (CommonCrypto); AES-GCM via CommonCrypto's `CCCryptorGCM*` or a tiny Swift CryptoKit shim injected at startup; `compression_encode_buffer`/`decode` with `COMPRESSION_ZLIB` (raw DEFLATE). Must pass `shared/golden/export/` vectors byte for byte. |
| SQLDelight driver | `AndroidSqliteDriver` + SQLCipher | `NativeSqliteDriver` linked against **SQLCipher** instead of system SQLite (spike I0: link order, `PRAGMA key` before first statement, `cipher_version` check in a test) |
| DB key manager (`DbKeyManager`, androidMain) | Keystore-wrapped key file, version byte, locked detection | `KeychainKeyManager` (§2.4) behind the same interface |
| `Health.kt` source precedence (package names) | Samsung Health / Google Fit / Fitbit | Make the "trusted sources" list platform-provided: iOS = Apple Health / Apple Watch (`com.apple.health*`) > other apps |

- Anything in `commonMain` that still uses JVM-only APIs is a bug found by the first iOS compile; fix in `shared`, not around it.

### 2.3 Shared-first refactor (I6, before iOS UI)
Platform-neutral logic that currently lives in `androidApp` moves into `shared` (commonMain, coroutines + Flow), with thin platform hooks. Android's behaviour must not change (its tests stay green); iOS then only renders and adapts.

| Today (androidApp) | Moves to shared as | Platform hooks left native |
|---|---|---|
| `ui/AppViewModel.kt` model pipeline (store flows, engine recompute only on `SettingKeys.ENGINE`, today/midnight flow, `AppModel`), log actions (update day, period started + undo, quick flow, mark start, quick picks, LH today), consent, onboarding finish, demo data | `AppSession` (+ `AppModel`) | "day changed" signal (Android: receivers/lifecycle; iOS: `NSCalendarDayChanged`, `significantTimeChange`, scene activation) |
| `ui/PrivacyActions.kt`, `ui/ReportActions.kt`, `ui/HealthActions.kt` (state machines, not pickers) | `PrivacyActions`, `ReportActions`, `HealthActions` | file pickers, share, PDF drawing, health store client |
| `lock/AppLock.kt` (lock after, back-off, pause when nothing can unlock, suspend for system UI) | `LockPolicy` | biometric/passcode prompt; "can authenticate" check |
| `store/StoreHolder.kt` (open → retry while unlocked → error states, lease, erase waits for users, restore pending) | `StoreHolder` | key manager, "device locked?" signal, file deletion |
| `backup/BackupPlanner.kt`, snapshot parts of `BackupCoordinator.kt` | `BackupPlanner` | where files live, how the key is stored (§6.4) |
| `surfaces/SystemSurfaces.kt` (what goes into widget snapshot / reminder plan, when to rewrite) | `SurfacesPlanner` | writing sealed files, reloading widgets, scheduling notifications |
| `i18n/Fmt.kt` rules (CLDR skeletons `MMMd`, `EEEMMMd`, ranges „3.–8. Dez.“, half-up rounding, `{b}` markers, „Nov..“ fix) | stays native, but both implement the same **format spec** with shared golden strings (`shared/golden/format/`: inputs → expected DE/EN output) | `DateFormatter.dateFormat(fromTemplate:)` / ICU on iOS |
| `report/ReportPdf.kt` page layout (boxes, page breaks, sizes) | `ReportLayout` (geometry + text runs, no drawing) | drawing: `android.graphics.pdf` / Core Graphics PDF context |

### 2.4 Process & storage (iOS counterpart of ANDROID_PLAN §2.4)
- **Single writer:** only the app process opens the database. Widgets and the Control read sealed side files; their buttons open the app (§6.2). **No pending-event queue** (same as Android; spec §3.3 superseded).
- **Database:** `Application Support/cyclus.db` (+ `-wal`, `-shm`) in the app container (not the App Group), SQLCipher, file protection **`completeUnlessOpen`** so an open database keeps working when the phone locks (Android parity: the key is in memory once opened) while a new open needs an unlocked device. `isExcludedFromBackup = true` on all three files, verified in a test.
- **DB key (`KeychainKeyManager`):** random 256-bit key, generic-password item, `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, not synchronizable, **wrapped by a Secure Enclave P-256 key** (ECIES, `SecKeyCreateEncryptedData`, access control `.privateKeyUsage`) for hardware binding like StrongBox. Version byte in the wrapped value, same re-wrap-on-mismatch rule as Android.
- **Locked detection and recovery:** `errSecInteractionNotAllowed` or `UIApplication.isProtectedDataAvailable == false` → reason DEVICE_LOCKED → retry loop while protected data is available, plus retry on `protectedDataDidBecomeAvailableNotification` and on scene activation (Android: `ACTION_USER_PRESENT` + `onStart`). Wording identical to Android.
- **Store error screen:** same states and copy (locked / key unavailable / other), Retry, "Nothing was deleted".
- **Side files** (App Group container, `completeUntilFirstUserAuthentication`, sealed AES-GCM with a CryptoKit key in a shared Keychain access group, `AfterFirstUnlockThisDeviceOnly`):
  - `widget/snapshot.sealed`: days only, written **only while „Details im Widget“ is on**, removed otherwise;
  - `notify/plan.sealed`: reminder kinds, days, local times, discreet flag (used to rebuild pending notifications without the DB, §6.3).
  - Never in backups (`isExcludedFromBackup`), deleted with their key on erase.
- **Appearance:** `UserDefaults` (app domain), not health data, read before the DB opens (lock screen and first frame already themed), never exported or backed up, reset on erase.
- **App switcher:** a privacy cover (app glyph on `bg`) whenever the scene is not active — always on, shown as info in the Privacy Center (= Android "recents preview always hidden").

---

## 3. iOS design system — "Cyclus on Liquid Glass"
Structure, tokens and custom visuals from the prototype (as on Android); chrome, motion and interaction from iOS 26.

- **Colours:** `CyclusColors` roles exactly as Android (`accent`, `onAccent`, `accentContainer`, `accent2`, `inverseAccent`, `period`, `onPeriod`, `pre`, `fertile`, `success`, `warning`, `danger`, `bg`, `surface`, `surface2`, `ink`, `ink2`, `line`, `mid`), computed by the shared `Appearance.kt` (palettes, OKLCH correction, contrast tests). Increase Contrast = the Android "high contrast" scheme (fills darkened 15% / lightened 10%, solid outlines instead of dashed).
- **Glass:** tab bar, navigation bars, the floating „Eintragen“ button (`.glassEffect(.regular.interactive())`), sheet chrome. Content (cards, ring, calendar, charts) stays on solid `surface`. `reduceTransparency` → solid.
- **Typography:** SF Pro text styles (Dynamic Type to AX5); ring numbers and stats in **SF Pro Rounded** with monospaced digits (Android: Roboto Flex + Nunito — listed difference). Same type roles and the same ring rules (big value shrinks to fit, min 18 pt, three lines max).
- **Shapes/spacing:** cards 16 continuous, sheets system, chips capsule; 4-pt grid, 16-pt gutter.
- **Motion:** springs `response 0.35, dampingFraction 0.85` (= Android spring); Reduce Motion → cross-fades; push/pop are the native slide (the Android app switched to slide for the same reason).
- **Haptics:** `.sensoryFeedback` — selection on chip/flow/drag swap, success once on log dismiss, none on errors (= ANDROID_PLAN §3.4).
- **Icons:** SF Symbols for system glyphs; the custom visuals (droplet 0–4 + dotted spotting, ring glyph, body map, ◎, ✦, eye-off, drag handle, info) come from the same SVG sources as the Android vectors.

---

## 4. Component mapping (Android → SwiftUI)
| Android (Compose M3E) | iOS (SwiftUI) | Notes |
|---|---|---|
| `NavigationSuiteScaffold` (bar / rail ≥ 600 dp) | `TabView` with `.tabViewStyle(.sidebarAdaptable)` | 3 tabs Heute / Kalender / Einblicke, labels always |
| `LargeFlexibleTopAppBar` + date subtitle + shield/gear | `NavigationStack` large title + `navigationSubtitle` (date, only on Heute) + toolbar buttons | Collapses on scroll natively |
| Extended FAB „Eintragen“, shrinks on scroll | Floating glass capsule button bottom-trailing, collapses to the + icon on scroll down (`onScrollGeometryChange`) | Same label and position |
| Navigation 3 back stack, predictive back | `NavigationStack` path in an `@Observable` router (survives the app lock, §5) | Native swipe-back |
| `ModalBottomSheet` (Log, Why, Method, ring explainer, quick picks, consent, export/import, erase, PIN) | `.sheet` with detents (`.large` at AX sizes), drag indicator | Same content and order |
| `Snackbar` with „Rückgängig“ (5 s) | Custom toast overlay above the tab bar with Undo; VoiceOver announcement | Undo also in the day detail |
| `FilterChip` (selected = check + container) | Custom `CyclusChip` (capsule, check glyph, accent container) | Suggested = dashed outline |
| Flow tiles (5, RadioButton semantics) | Same custom tiles, `.accessibilityAddTraits(.isSelected)` | Same labels and hyphenation |
| `ButtonGroup` 1–5 scale | Segmented `Picker` styled like the prototype | End labels below |
| `SingleChoiceSegmentedButtonRow` | `Picker(.segmented)` | Ziel, Ring Groß/Kompakt, Zeitraum, PDF-Sprache, Mode |
| `ListItem` + `Switch` rows | `Toggle` rows in grouped cards | Whole row tappable |
| Custom `Canvas` ring / heatmap / outlook grid / body map | SwiftUI `Canvas` with the same geometry (300-unit viewBox) | Pixel-compared with the prototype |
| Custom `Canvas` cycle bars / temperature | **Swift Charts** styled to the prototype + `AXChartDescriptor` (audio graph) | Visual diff against the prototype; custom Canvas if Charts can't match |
| `DatePicker` (onboarding) | Graphical `DatePicker`, Monday first by locale | Future dates disabled |
| Long-press menu on calendar day | `.contextMenu` „Als Periodenbeginn markieren“ + accessibility custom action | Past/today only |
| Today edit mode (drag handle, eye, TalkBack actions) | `List` in `EditMode.active` with `.onMove` (native handles, auto-scroll, VoiceOver move actions built in), eye button per row, library section with + | Saved on drop; same hints and library |
| `BiometricPrompt` + PIN pad | `LAContext.evaluatePolicy(.deviceOwnerAuthentication)` + same PIN pad view | Back-off after 5 tries (shared) |
| `LinearWavyProgressIndicator` | `ProgressView` (linear) | Import / export / report |

---

## 5. Screens and behaviours (complete parity list)
Content = DESIGN_SPEC §7 + prototype + the PARITY notes. iOS-specific notes only where the platform needs them.

| Area | What iOS must do (as Android) | iOS specifics |
|---|---|---|
| **Onboarding** (F-012, F-020) | 7 steps: welcome · privacy promises · **„Bevor du startest“** (required, checkbox, consent recorded at the tap) · import (`.cyclus` backup or Apple Health; with Apple Health the last-period step is skipped) · last period (+ still bleeding / duration chips, nothing invented) · life stage + Kinderwunsch · app lock offer (biometrics, else PIN setup). Birth year **not** collected. | Apple Health availability (`HKHealthStore.isHealthDataAvailable`); notification permission only later, in context |
| **Consent gate** (F-020) | Full-screen re-ask after the app lock when the last confirmation of the current wording is > 12 months old, the wording version changed, or on a restored phone; Datenschutz reachable from the gate; sheet before switching on the fertile window or Kinderwunsch; history in Datenschutz › Rechtliches (≤ 20, date + time + version) | Re-checked on scene activation |
| **App lock** (F-015) | Biometrics/passcode + optional Cyclus PIN + „Nur PIN“ + lock after (sofort / 1 / 5 Min.) + lock paused with notice when nothing could unlock; **the lock keeps your place** (tab, pushed screens, open sheet, selected day, scroll) and nothing of the content is rendered behind it | Content removed from the hierarchy while locked, state kept in the router/`@SceneStorage`; system Face ID sheet doesn't count as leaving the app |
| **Store errors / restore** | Blocking error screen with Retry; restore failure screen (Retry / „Neu beginnen“, only the restored copy is discarded) | §2.4 |
| **Today** (F-001–F-004, F-021) | Ring (Groß/Kompakt), tap or ⓘ → „So liest du den Ring“ (live legend, link to method); status line, „Periode hat heute begonnen“ + Undo, quick flow tiles during the period; reminder offer card; cards in the person's order, timed cards only when relevant: Heute eingetragen + **Deine Schnellauswahl** (≤ 8 symptoms/feelings/tags), Kinderwunsch cards (earliest test, best days, LH plan, LH today), Ausblick, Fruchtbares Fenster, Tage vor der Periode, **Demnächst**, Muster link, **Woche** (library by default); fixed footer „Warum dieses Fenster?“ + „Nicht zur Verhütung geeignet“; „Ansicht anpassen“ edit mode with library and reset; debug builds: long-press title → demo data | Wide (iPad / landscape regular width): Today and Calendar side by side, Woche/Demnächst taps focus the calendar there |
| **Log sheet** (F-005, F-006) | Day switcher ‹ › (no future), flow tiles, LH (Kinderwunsch), Schnellauswahl row = the symptom quick picks (+ Sex in Kinderwunsch), suggestions „Oft um diese Zeit“, mucus + BBT ±0.05 (Kinderwunsch), Stimmung, optional sections that open when used in the last 120 days or filled that day, rest under „Weitere Angaben“, pain body map + 0–10, tags, note, „Sonst nichts heute“, „Fertig“; every change saved at once, atomic per day | Note field keyboard avoidance; `.large` detent at AX sizes |
| **Calendar** (F-007) | Month grid with swipe between months, selected day's detail inline under the grid (today first), „Bearbeiten“ / „Periodenbeginn“, long-press → mark period start, legend, fertile tiers + ◎ ✦, focus requests from Today | VoiceOver rotor „Periodentage“, „Tage mit Einträgen“ |
| **Insights** (F-008, F-009) | Summary card (top 3 + „Alle N Muster ansehen“), Arztbericht entry, stats, charts (cycle lengths with typical band, period lengths, heatmap, temperature, Kinderwunsch charts), history 3 + „Alle anzeigen“; Muster screen with Gefunden/Beobachtet, area filters, hide/unhide (persisted) | Two columns at regular width |
| **Why / Method sheets** (F-010) | Evidence trail, method copy (Android override texts apply to iOS too, no device-specific claims) | — |
| **Kinderwunsch** (F-011) | All conceive behaviour incl. LH planner, two-week wait, regional flag | Same flag mechanism |
| **Doctor Visit Pack** (F-017) | Builder (range, sections with privacy defaults, questions, DE/EN, optional name remembered on this device only), **the preview is the real PDF**, A4/Letter by locale, share; the shared file lives in a share cache that is cleared at the next build, at app start and on erase (not on share completion) | Core Graphics PDF context from `ReportLayout`; preview with PDFKit; `ShareLink` with the file URL; document info: only what Core Graphics forces (verify Producer, strip Creator/Author) |
| **Privacy Center** (F-013) | Wo deine Daten sind (Auf diesem Gerät, In Apple Health, In Backups with status, Exporte) · Schutz (App-Sperre, PIN, Nur PIN, Sperren nach, switcher cover info, „App ausblenden“ explainer) · Sichtbarkeit (discreet notifications, widget details off, shortcut off, side-file note) · Apple Health section · Backups (Nur auf dem Gerät, export, import) · Rechtliches (notice + confirmations) · Alles löschen | „Bildschirmaufnahmen verbergen“ instead of „Bildschirmfotos verhindern“ (§7, allowed difference); „App ausblenden“ explains iOS *Hide and Require Face ID* |
| **Notifications screen** (P-004) | Master switch (off = no reminders planned), per kind toggles with the same defaults, daily time, „Töne und Kanäle“ → system settings, permission-denied row | Opens `UIApplication.openNotificationSettingsURLString` |
| **Settings** (F-014, F-018, F-019) | Erscheinungsbild (palettes, Eigene Farben with OKLCH correction + live preview, Hell/Dunkel/System), Sprache, Ziel (Kinderwunsch asks for the notice first), fruchtbares Fenster (asks first), Lebensphase (pause/resume predictions, undoable), Mitteilungen, intended purpose text | No „Hintergrund“ (wallpaper) palette; Sprache opens the app's page in iOS Settings (per-app language is a system setting on iOS); light/dark via the window's `overrideUserInterfaceStyle` |
| **Erase everything** (F-016) | Two-step, typed confirmation; crypto-shred: DB + key + side files + their key + backup snapshot + backup key + notifications + widget reload + appearance reset; optional (default on, only if anything was written) delete Cyclus's own Apple Health entries first; a Health error never blocks the shred; back to onboarding | HealthKit permissions can't be revoked by apps: the done screen explains Settings › Health › Data Access |
| **Large screens** (P-005) | Today + Calendar side by side at regular width, Insights in two columns, other screens ≤ 640 pt centred | iPad in v1 (spec had v1.1) for parity |

---

## 6. Platform services

### 6.1 HealthKit (parity with ANDROID_PLAN §6.1)
- **Import + opt-in write, everything off by default**, sync **only while the app is open** (launch/activation, 3 s after the person's own edits or category toggles, „Jetzt synchronisieren“). **No `enableBackgroundDelivery`** (spec §11.4.1 rule 9 superseded for parity).
- **Write categories (I7):** Blutung und Perioden (`menstrualFlow` + `HKMetadataKeyMenstrualCycleStart`), Schmierblutung (`intermenstrualBleeding`), Temperatur (`basalBodyTemperature`), Ausfluss (`cervicalMucusQuality`), LH-Tests (`ovulationTestResult`), Sex (`sexualActivity` + protection metadata). Permissions requested per category when switched on.
- **Read set** = the same types + `appleSleepingWristTemperature` (display only, flag-gated, not used by the engine).
- **Sync rules** = the shared state machine (`shared/.../health/Health.kt`): sync identifier `cyclus:<key>` (`HKMetadataKeySyncIdentifier`) and version (`HKMetadataKeySyncVersion`, `max(previous + 1, now)`), time-zone metadata, anchored queries with anchors persisted per type (= Changes API), deletions of own samples → „In Apple Health gelöscht“ review (keep / remove too), local deletes keep a tombstone until HealthKit confirms.
- **Merge before the engine** (`HealthMerge`): external values fill only empty fields; a user `flow = none` hides external flow; external period days become bleeding without a level. Precedence: person > Apple Health / Apple Watch > other apps.
- **Reinstall recovery** only into an empty store and only after „In Cyclus übernehmen“ (offer once per connection).
- Imported samples, links, anchors are device data: not in exports or snapshots; gone with erase and „Trennen“ (which on iOS stops syncing and removes imported data; the permission itself is revoked by the person in Settings, as the row says).
- Read denial is undetectable on iOS: status lines and help text say so instead of showing an error.
- Usage strings (`NSHealthShareUsageDescription`, `NSHealthUpdateUsageDescription`) = the Android rationale text (read: import; write: opt-in mirror); privacy policy URL in App Store Connect (required for HealthKit).

### 6.2 Widgets, Control, quick actions (parity with ANDROID_PLAN §6.2)
- **WidgetKit** small (ring, „Tag N“, range) and medium (+ „Periode eintragen“ / „Eintragen“), same content as Glance. **„Details im Widget“ off by default:** then only the glyph and „Cyclus“ and no snapshot file. Timeline: entry at each local midnight (the widget computes the cycle day from the snapshot's days) + reload after changes.
- **Lock Screen** accessory widgets (circular, rectangular): the same function on the lock-screen surface (I4) — neutral ring + „Cyclus“ unless details are on; `.privacySensitive()` always.
- **Buttons** are App Intents with `openAppWhenRun` that hand an action token to the app: the action runs **after the app lock, exactly once**, dropped during onboarding (= Android `.ActionEntry`). „Periode eintragen“ logs the start with Undo, or opens the Log sheet if today already has bleeding.
- **Control Center control** „Cyclus · Eintragen“ (iOS 18+ `ControlWidget`, also placeable on the Lock Screen) = Android Quick Settings tile: opens the Log sheet behind the device unlock and the app lock.
- **Home Screen quick action** „Eintragen“ (long-press icon) = Android app shortcut: dynamic `UIApplicationShortcutItem`, **off by default**, opt-in in Privacy Center › Sichtbarkeit. **No `AppShortcutsProvider`** (nothing in Spotlight/Siri suggestions).
- StandBy shows the small widget under the same detail rule.

### 6.3 Notifications (parity with ANDROID_PLAN §6.3)
- **Opt-in on every path:** nothing is scheduled until the person says yes (Today card or Mitteilungen); then `requestAuthorization`. Restore/new phone starts without reminders (device settings).
- **Same kinds and defaults** (Periode bald on; Später als sonst off; täglich, Sicherung off; Kinderwunsch: Fenster + LH on, Temperatur off), same discreet titles and fixed detailed sentences, never dates, numbers or symptoms.
- **Scheduling:** the shared planner builds the plan in the app; iOS turns it into `UNNotificationRequest`s (calendar triggers; daily items as repeating triggers; LH series as single requests up to the plan's end), keeping well below the 64-pending limit; rebuilt from `notify/plan.sealed` on activation and after time-zone/day changes. Category with `hiddenPreviewsBodyPlaceholder` = the discreet text.
- Android channels ↔ per-kind toggles in the app (iOS has no per-app channels); „Töne und Kanäle“ opens the system notification settings.
- Settings note: notifications also appear on a paired Apple Watch.

### 6.4 Backup & transfer (I3)
#### 6.4.1 Design
- **Never backed up:** the database and its key (`ThisDeviceOnly`), side files and their key, imported Health data, notification/appearance settings.
- **Snapshot:** `Application Support/backup/snapshot.cyclus` = mode-2 `.cyclus` (all data incl. app-lock settings, same as Android), written when the app goes to the background after a change; included in iCloud and computer backups.
- **Backup key:** random 256-bit, Keychain item **`kSecAttrSynchronizable = true`** (iCloud Keychain, end-to-end encrypted) with `kSecAttrAccessibleAfterFirstUnlock` (so encrypted computer backups also carry it). An iCloud backup without the key can't be opened by anyone; Apple never holds the key.
- **Toggle „Nur auf dem Gerät“** deletes the snapshot and the backup key (synced deletion).
- **Restore:** on first launch with an empty store, if a snapshot exists and the key is available → atomic import into a fresh encrypted DB (same code as Android, Retry / „Neu beginnen“). Snapshot present but no key yet (iCloud Keychain still syncing or off) → „Sicherung gefunden, Schlüssel fehlt“ with Retry and an explanation, never silent deletion.
- **Status line** (Privacy Center): when the snapshot was last updated + an honest sentence that iOS doesn't tell apps whether iCloud Backup ran (Android shows the transport result; listed difference).
- **Encrypted export/import:** `fileExporter`/`fileImporter`, same `.cyclus` v1 file, never contains app-lock settings. **Cross-platform test:** export on Android → import on iPhone and back.

#### 6.4.2 Threat notes
Code running as Cyclus on an unlocked-after-first-unlock phone can open the snapshot (as on Android); the backup copy alone can't. Unencrypted computer backups contain the snapshot but not the key (unusable).

#### 6.4.3 Spike before I4 (real devices, not simulators)
1. iCloud backup → restore to a second iPhone: is the synchronizable key available at first launch, and how late can it arrive? 2. Quick Start device-to-device transfer. 3. Encrypted Finder backup restore. 4. iCloud Keychain off: behaviour and copy. 5. "Nur auf dem Gerät" removes the key from the other device too. **If the spike fails, fall back to export-only for automatic transfer** and tell the owner before shipping.

### 6.4.4 Cross-platform transfer (owner requirement 2026-11-23)
**Backups and restores work across platforms: a `.cyclus` export from Android opens on iPhone and vice versa** (both directions, merge or replace, onboarding import or Privacy Center import).
- **One format, one implementation:** the shared `CyclusFile` + `ExportFile` code on both; iOS only supplies the crypto actuals (§2.2), which must reproduce `shared/golden/export/` byte for byte (incl. `vector-mode1-umlaut.cyclus`).
- **Passphrase = NFC** before key derivation (FORMAT.md), with a fallback to the passphrase as typed for older files: the same visible passphrase works from any keyboard.
- **What travels:** all entries, overrides, episodes, tags, user prefs (goal, fertile window, muted patterns, Today layout, ring size, quick picks). **What doesn't:** app-lock/PIN (never in user exports), notification and appearance settings, consent history, Health store links and imported samples (device data on both platforms; the new phone asks again where it must).
- **Device backups stay on their platform** (Apple restores to iPhone, Google to Android; the snapshot key never leaves the platform's backup system). Moving between Android and iPhone always uses a passphrase file; the Privacy Center says so in the backup section.
- **Opening a file from outside:** tapping a `.cyclus` file (Files, mail, AirDrop / Nearby Share, cloud apps) offers Cyclus and leads into the same import flow, after the app lock (during onboarding: the import step). iOS: exported UTType `com.nordinary.cyclus.backup` + `onOpenURL`. Android: matching `VIEW`/`SEND` intent filters (to add, PARITY S-005).
- **„Auf ein neues Handy umziehen“** (Privacy Center › Backups, both platforms): short guide — export with passphrase → send the file to yourself → install Cyclus → „Sicherung importieren“ in onboarding.
- **Tests:** committed fixture files exported by the Android app and (once it exists) by the iOS app (`shared/golden/export/cross/`), imported by both test suites in CI; manual release check: a real export on the Pixel imported on an iPhone and back, compared day by day.

### 6.5 On-device language model (v1.5)
Foundation Models with the shared prompt and post-checks (spec §11.8); availability-gated; templates otherwise (= Gemini Nano on Android).

### 6.6 Apple Watch (v1.5)
watchOS app = Wear OS app scope (log period/symptom, glance, complication), same privacy defaults.

---

## 7. Threat model deltas (iOS vs Android)
| Surface | iOS behaviour | Mitigation |
|---|---|---|
| Network | No OS permission blocks it | Lint + App Privacy Report release check (§0.1); privacy label "Data Not Collected" |
| Screenshots | Can't be blocked | „Bildschirmaufnahmen verbergen“ (content covered while `UIScreen.isCaptured`), switcher cover always; honest copy that screenshots can't be blocked on iPhone |
| Person with the passcode | Opens apps | Cyclus PIN / „Nur PIN“; „App ausblenden“ explainer (*Hide and Require Face ID*) |
| Health app | No per-app lock; Health Sharing can forward data | Every write category off; Privacy Center copy (as Health Connect) |
| Notifications | Lock screen previews follow the system setting | Discreet copy by default; detailed copy has no dates/numbers |
| Backups | iCloud Backup not E2E without ADP | §6.4: DB never backed up, snapshot useless without the iCloud Keychain key |
| Seizure (BFU/AFU) | Data Protection + SQLCipher key `WhenUnlockedThisDeviceOnly` behind the Secure Enclave | §2.4 |
| Widget/Control actions | Intents could be triggered by Shortcuts | Actions only open the app; they run after the app lock, once |
| Cyclus PIN | UI gate, hash inside the encrypted DB | Back-off after 5 tries (monotonic clock); not presented as encryption |

---

## 8. Regulatory & App Store
- Same medical-device software; SOUP adds SKIE runtime, SQLCipher (iOS build), Apple frameworks as platform.
- App Store: organization account (D3), primary locale `de-DE`, App Privacy **Data Not Collected**, privacy manifest (spec §11.10: UserDefaults `CA92.1` + `1C8F.1` for the App Group, file timestamp `C617.1`; re-run the Xcode privacy report with the KMP framework), HealthKit privacy policy, age rating, intended-purpose wording and banned terms (spec §1.4, §9.3), App Review question on 5.1.3(ii) (spec §14).
- Release: TestFlight internal → external (Beta App Review) for ≥ 2 cycles on both platforms (= Android closed testing), phased release.

---

## 9. Quality & parity testing
| Layer | Tooling | Runs |
|---|---|---|
| Shared engines, golden vectors, export vectors, format strings | `commonTest` on JVM + **`iosSimulatorArm64Test`** | Every push (macOS runner) |
| iOS actuals (crypto, SQLCipher driver, Keychain key manager) | Kotlin/Native tests on the simulator + XCTest on a device for Keychain/SE | Every push / release |
| SwiftUI | XCUITest scripts mirroring the Android UI tests (onboarding, log, export/import, erase, lock, consent, Today edit mode incl. move) | Nightly, **simulator only** (never on the owner's iPhone) |
| Visual parity | Snapshot tests at 402 × 874 pt (iPhone 17 Pro) next to Android 412 × 915 dp and the prototype: light/dark × 100 %/AX3 × DE/EN | Every UI PR |
| Accessibility | VoiceOver, Voice Control, Dynamic Type AX5, Increase Contrast, Reduce Motion/Transparency | Release checklist |
| HealthKit | Device tests: write/edit/delete own, Apple Watch data, read denied, anchors, removed review, reinstall recovery | Release checklist |
| Backup | §6.4.3 spike, then a release check on two devices | Release |
| Cross-platform data | Fixture files from each app imported by the other's tests (CI); a real `.cyclus` Android → iPhone → Android round trip compared day by day; NFC passphrase vector | Every push / release |
| No network | Lint + App Privacy Report | Every push / release |
| Device matrix | iPhone 11 (oldest on iOS 26), iPhone SE-class small screen, current Pro, Pro Max, one iPad | Release |

---

## 10. Changes to DESIGN_SPEC for iOS (superseded sections)
| Spec § | Was (iOS-first draft) | Now |
|---|---|---|
| 0.1 D5 | Store included in backups + "Device only" | I3 (§6.4) |
| 2.1 v1.1 | iPad layout, Control Center control, App Intents in v1.1 | iPad layout, Control and opt-in quick action in v1 (parity with Android large screens, QS tile, shortcut) |
| 3.2 | SwiftData store, pending queue, Health mirror defaults on | SQLCipher store, no pending queue, all mirror categories off |
| 3.3 | Pending-event queue for widget/intent writes | Actions open the app (§6.2) |
| 4 | Settings tree with Logging/Backup screens | Android IA: Settings (Erscheinungsbild, Sprache, Ziel, Lebensphase, Mitteilungen) + Privacy Center (Health, backups, legal, erase) |
| 7.1 | Birth year step, Apple Health import only | 7 steps incl. the notice; `.cyclus` or Apple Health import; no birth year |
| 7.4 | Day detail sheet | Inline day detail under the grid |
| 7.11 | Separate Backup & Export screen, CSV | In the Privacy Center; CSV still pending on both platforms |
| 8.1–8.2 | Lock Screen widget details, intents writing in-process | §6.2 |
| 11.1–11.3 | Swift packages, SwiftData models | KMP `shared` + SQLDelight + SQLCipher (ANDROID_PLAN §2.5) |
| 11.4.1 rule 9 | Background delivery | Foreground-only sync |
| 11.9 | `isExcludedFromBackup` toggle on the store | §6.4 |

---

## 11. Delivery plan (phases with exit criteria, no estimates)
| Phase | Content | Exit criteria |
|---|---|---|
| **S0** Shared-first | §2.3 refactor on Android; Android behaviour unchanged | Android tests + device checks green; shared API reviewed for Swift friendliness |
| **I0** KMP on iOS | iOS targets, iosMain actuals (crypto, SQLCipher driver, key manager), SKIE, XCFramework, golden + export + format vectors on the simulator | All vectors green on iOS; SQLCipher `cipher_version` test |
| **I1** Shell | Xcode project, CI (macOS), no-network lint, strings → `Localizable.xcstrings` (X-001), design system package (tokens, ring, droplets, chips, body map), app lock, store error screen, deploy script | Component gallery vs prototype approved |
| **I2** Core screens | Today (all states, Kompakt, edit mode, Schnellauswahl, Demnächst, Woche, ring explainer), Log sheet, Calendar | Screenshot pairs with Android + prototype |
| **I3** Insights & trust | Insights, Muster, Why/Method, Kinderwunsch, onboarding + notice + consent gate, Privacy Center, settings + appearance, PIN, export/import, erase, Arztbericht | Parity rows F-001…F-021 done on iOS |
| **I4** Platform services | HealthKit, widgets + Control + quick action, notifications, backup (after the §6.4.3 spike), iPad layouts | Device checklist passes; backup spike signed off |
| **I5** Readiness | Accessibility, localisation, device matrix, App Store listing (DE/EN), privacy manifest, review notes | Internal TestFlight |
| **I6** Joint beta & release | ≥ 2 cycles TestFlight + Play closed testing in parallel, fixes on both | **All PARITY rows done on both platforms** (or flagged off on both), then phased release on both |

### 11.1 Risks
| Risk | Mitigation |
|---|---|
| SKIE / Kotlin version gap | Verify in I0; fallback: pinned Kotlin for the iOS framework or a thin Swift facade |
| SQLCipher linking with the SQLDelight native driver | I0 spike with a `cipher_version` test before any UI |
| iCloud Keychain timing on restore | §6.4.3 spike; clear "Schlüssel fehlt" state; export-only fallback |
| Pixel-parity of Swift Charts | Snapshot pairs; switch a chart to `Canvas` when it can't match |
| Logic drift between platforms | S0 refactor; shared golden strings for formatting; parity rows updated per PR |
| No Mac in the current setup | iOS work needs macOS + Xcode (local Mac or macOS CI runner); Android work continues on Linux |

---

## 12. Handoff checklist (iOS)
1. macOS with current Xcode, JDK 21, Kotlin Multiplatform plugin; SKIE; `xcodes`/`devicectl` for devices.
2. Read: this file §0–§2 → `PARITY.md` (rows + allowed differences) → `ANDROID_PLAN.md` §2.4/§6 → `prototype/README.md`.
3. Owner decisions still open for iOS: Apple organization account (D3), App Review 5.1.3(ii) question, approval of HealthKit-only categories later (I7), test devices (an iPad, an older iPhone; Apple Watch for v1.5).
4. First task: **S0**, then I0 with the SQLCipher and SKIE spikes.

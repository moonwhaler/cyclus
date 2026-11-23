# Cyclus — Product & Delivery Plan (v0.1, draft for review)

> Codename **Cyclus**. Final name TBD. Date: 2026-11-22.
> Status: **SUPERSEDED** by `DESIGN_SPEC.md` (after sceptic review in `REVIEW.md`). Kept for history.

---

## 1. Decisions locked (from interview)

| Topic | Decision |
|---|---|
| Audience | Broad: anyone who menstruates, with switchable **life-stage modes** |
| Business model | **Fully free.** No ads, no IAP, no subscription, no tracking SDKs |
| Data & AI | **Local-first.** Data never leaves the user's devices / Apple's E2E Health sync. AI runs **on-device only** |
| Team | **Solo developer + Claude.** Lean MVP |
| Platform | iOS first (iPhone), watchOS later. No Android/web |

Assumptions (not yet confirmed by owner — see §14):
- Launch markets: **EU + US**. Languages at launch: **English + German**.
- Deployment target: **iOS 26.0** (Liquid Glass design, Foundation Models). Re-evaluate iOS 27 APIs before launch.
- Owner has an iPhone with Apple Intelligence for testing; Apple Watch Series 8+ for temperature testing is "nice to have" in v1.

---

## 2. Vision

**"The smartest cycle tracker that knows nothing about you."**

Every competitor makes users choose. Flo is smart but leaky. Euki and drip are private but basic. Apple Health is private but shallow. Cyclus is the first app that is both **intelligent** (personal patterns, honest predictions, plain-language insights) and **private by architecture** (no server exists to leak from). Because it is free, it can also be the most trustworthy app in the category.

### Product principles (ranked; earlier wins conflicts)
1. **Trust first.** No dark patterns. No fake precision. No data leaves the device. Never shame or guilt the user.
2. **3-second logging.** Any common entry takes ≤ 3 s and ≤ 2 taps from the Lock Screen, widget or app.
3. **Useful on day 1.** Import history, honest priors, no "come back in 3 months".
4. **Honest intelligence.** Statistics compute, language models only *phrase*. Every insight is traceable to the user's own data and shows its confidence.
5. **Calm, native, beautiful.** It should feel like Apple built it, with more warmth. Accessible by default.

---

## 3. Target users & modes

| Mode | Who | Emphasis | Release |
|---|---|---|---|
| **Track** (default) | General cycle awareness, ~13–45 | Period prediction, symptoms, phase awareness | v1 |
| **Conceive** | Trying to conceive | Fertile window, BBT/wrist temp, ovulation tests, intercourse log | v1 |
| **Perimenopause** | ~38–55, irregular cycles | Symptom trends (hot flashes, sleep, mood), irregularity tolerance, cycle-gap tracking | v1.5 |
| **Birth control** | Pill/patch/ring/IUD users | Reminders, withdrawal bleeds (not "periods"), spotting | v1.5 |
| **Pregnancy** | Pregnant | Week-by-week, symptom log; cycle predictions paused | v2 |
| **Postpartum / breastfeeding** | After birth | Return-of-cycle detection, lactation | v2 |

All modes share **one data model and one engine**. A mode only changes (a) which log items show first, (b) what the Today screen emphasises, (c) prediction parameters, and (d) copy.

Conditions overlay (any mode, v1.5): **PCOS / endometriosis / irregular** flags widen prediction intervals and add condition-relevant log items (pain mapping, etc.).

---

## 4. Scope by release

### v1.0 — MVP
**Core**
- Onboarding ≤ 60 s: privacy promise → optional Apple Health import → last period date (or "don't know") → typical length (or "not sure") → mode → done. Every step skippable.
- **Today screen**: cycle day, phase, next-period range with confidence, fertile window (Conceive mode, or optional in Track), a large "Log" button, and one contextual insight card.
- **Logging sheet**: flow (none/spotting/light/medium/heavy), symptoms, mood, energy, sleep quality, discharge/cervical mucus, sex (protected/unprotected), ovulation test, BBT, notes, custom tags. Smart ordering: items most often logged on this cycle day show first.
- **Timeline/Calendar**: month calendar plus horizontal cycle timeline. Past = logged, future = predicted (visually distinct, with ranges).
- **Cycle history**: list of cycles with length, period length, and a mini symptom heatmap.
- **Predictions v1**: period range, fertile window, ovulation estimate (see §6.2).
- **HealthKit two-way sync** for all supported types (see §6.4).
- **Retrospective ovulation confirmation** from wrist-temperature or BBT shift.
- **Clinician report (PDF)**: last 6–12 cycles, stats, symptom frequency, flags. Shared via share sheet. Free, always.
- **Privacy**: Face ID / passcode lock, discreet notifications, alternate app icons, "erase everything" button.
- **Widgets**: Home (S/M), Lock Screen (circular + rectangular), StandBy. Interactive "log period" button.
- **App Intents / Siri / Shortcuts**: "Log period started", "When is my next period?", "Log symptom X". Control Center control (iOS 18+).
- **Notifications** (local only): period expected, fertile window start, late period, log reminder (optional).
- **Data export/import**: full JSON + CSV export; import from own export. Covers device migration and backup.
- **Languages**: EN, DE. Units: °C/°F.

**Explicitly NOT in v1**: Apple Watch app, partner sharing, AI chat, pregnancy/postpartum, iPad-optimised layout, iCloud sync (see §6.5 risk), community features, content library.

### v1.5
- **Pattern engine** (§6.3): personal symptom ↔ phase correlations.
- **On-device weekly digest** via Foundation Models, with a template fallback.
- **Perimenopause** and **Birth control** modes, plus the conditions overlay.
- Anomaly nudges: "3 cycles shorter than 21 days" → suggest a clinician visit (non-diagnostic).
- Apple Watch app (log + glance + complication).

### v2
- "Ask your data" (on-device LLM with tool calling over local stats).
- Partner sharing (granular, E2E). Mechanism TBD after the iCloud question is resolved.
- Pregnancy and postpartum modes.
- iPad and Mac (Catalyst / Designed for iPad).
- Live Activity during period (day X of period, quick log).

---

## 5. UX plan (summary — detail goes in the design spec)

- **Navigation**: 3 tabs — **Today**, **Calendar**, **Insights**. Settings live behind an avatar/gear in Today's toolbar. Logging is a sheet reachable from everywhere.
- **Visual language**: iOS 26 Liquid Glass chrome. Warm neutral base. Phase colours are muted, not pink: menstrual = deep rose, follicular = sage, ovulatory = warm amber, luteal = lavender. Full dark mode. SF Pro plus a rounded variant for numerals.
- **Cycle visual**: a "cycle ring" on Today (current day marker, predicted range as a soft gradient arc), plus the calendar.
- **Haptics**: light tap on selection, success haptic on save, none on errors (use a visual cue instead).
- **Copy voice**: warm, direct, never cutesy, never alarming. Use "period", not euphemisms. Gender-neutral by default ("you").
- **Accessibility**: Dynamic Type up to AX5, VoiceOver labels on every chart (with an audio graph where useful), colour never the only signal (phase icons + labels), Reduce Motion respected, minimum 44 pt targets.
- **No-nos**: streaks/guilt, popups on launch, rating prompts before 3 cycles, upsells (there are none anyway), confetti.

---

## 6. Architecture

### 6.1 Stack
- **Swift 6, SwiftUI, Observation**, structured concurrency.
- **Persistence: SwiftData** (local store, file protection `.complete`).
- **HealthKit**: read/write cycle types; read wrist temp, HRV, resting HR, sleep.
- **WidgetKit, App Intents, ActivityKit (v2), UserNotifications** (local).
- **Foundation Models** (v1.5) for language generation only.
- **Swift Charts** for all charts.
- **LocalAuthentication** for app lock.
- **PDFKit / ImageRenderer** for the clinician report.
- **No third-party dependencies** in v1, where possible. Swift Package Manager only.

Module layout (local SPM packages inside one Xcode project):
```
Cyclus/                  App target (SwiftUI views, navigation)
CyclusWidgets/           Widget extension
CyclusIntents/           App Intents (shared via App Group)
Packages/
  CycleCore/             Domain models, pure Swift, no UI. 100% unit tested
  CycleEngine/           Prediction, ovulation detection, pattern stats. Pure functions
  CycleStore/            SwiftData models, repository, migrations
  CycleHealth/           HealthKit adapter (import/export/observe)
  CycleInsights/         Insight generation, LLM phrasing, templates
  CycleReport/           PDF clinician report
  CycleDesign/           Design tokens, components
```

### 6.2 Prediction engine (CycleEngine)

**Inputs**: period start dates (from flow logs), optional temp series, optional ovulation tests, mode, condition flags.

**Cycle detection**: a period starts on the first day of flow ≥ light after ≥ 7 days without flow (spotting alone does not start a period). The user can always override ("this was / wasn't a period start").

**Cycle length estimate** (per user):
- Population prior: mean 29.3 d, SD ~ 5 d (Bull et al., 2019, *npj Digital Medicine*, ~600k cycles).
- Shrinkage: `μ = (k·μ_prior + n·x̄_w) / (k + n)` with k = 2. `x̄_w` = exponentially weighted mean of the last ≤ 12 cycles (half-life 4 cycles), using median/MAD outlier rejection (cycles > 3 MAD from the median are excluded but kept in history and marked).
- Spread: weighted SD with the same shrinkage, floor 1.5 d.
- **Prediction interval**: next start = last start + μ ± z·σ, with an 80% interval shown by default. Displayed as a range ("Nov 28 – Dec 1"). A single "most likely" day is shown smaller.
- Irregular flag (SD > 7 d, or condition flag) → widen the interval and change the copy ("Your cycles vary a lot — showing a wider window").

**Ovulation & fertile window**:
- Luteal-phase prior: 12.4 d (SD ~ 2). It is personalised once ≥ 2 temp-confirmed ovulations exist.
- Estimated ovulation = predicted next start − luteal length.
- Fertile window = ovulation −5 … +1 days.
- Positive LH test → ovulation likely within 12–36 h, so shift the estimate.
- **Retrospective confirmation**:
  - BBT: "3-over-6" rule (3 consecutive temps ≥ 0.2 °C above the max of the previous 6).
  - Wrist temp: HealthKit `appleSleepingWristTemperature` gives a deviation from baseline. The same rule is applied to nightly deviations, which need ≥ 5 of 6 prior nights present.
  - Once confirmed: mark the ovulation day, update the luteal estimate, and narrow the next-period prediction (next start ≈ ovulation + luteal ± 2).
- **Always labelled "Not a method of contraception."**

**Quality**: back-test against public datasets (if licences allow) and synthetic generators. Metric: % of actual starts inside the 80% interval (target 75–85%, i.e. calibration) and mean absolute error of the most-likely day. This is a CI test suite.

### 6.3 Pattern engine (CycleInsights, v1.5)
- Align every log to **cycle phase** (menstrual, follicular, ovulatory, early luteal, late luteal = last 5 days before the next period). Phase is computed retrospectively using actual next starts.
- For each symptom S and phase P: occurrence rate in P vs. rate outside P. Beta-binomial posterior; report only if P(lift > 1.5) ≥ 0.9 AND the symptom was logged in ≥ 3 distinct cycles. Apply Benjamini–Hochberg across all S×P tests.
- Also: cycle length trend (Theil–Sen slope over 6+ cycles), period length trend, sleep/HRV/resting HR by phase (from HealthKit), and symptom co-occurrence (top pairs by lift).
- Output = **structured `Insight`** (type, subject, phase, effect size, confidence, sample size, supporting dates). Fully deterministic and unit-tested.

### 6.4 HealthKit mapping

| Cyclus field | HealthKit type | R/W |
|---|---|---|
| Flow | `HKCategoryTypeIdentifier.menstrualFlow` (+ `HKMetadataKeyMenstrualCycleStart`) | R/W |
| Spotting | `.intermenstrualBleeding` | R/W |
| Cervical mucus | `.cervicalMucusQuality` | R/W |
| Ovulation test | `.ovulationTestResult` | R/W |
| Sexual activity | `.sexualActivity` (+ protection metadata) | R/W |
| BBT | `HKQuantityTypeIdentifier.basalBodyTemperature` | R/W |
| Wrist temp | `.appleSleepingWristTemperature` | R |
| Contraceptive | `.contraceptive` | R/W |
| Pregnancy / lactation | `.pregnancy`, `.lactation`, `.pregnancyTestResult`, `.progesteroneTestResult` | R/W (v2) |
| Symptoms | `.abdominalCramps`, `.bloating`, `.breastPain`, `.headache`, `.acne`, `.moodChanges`, `.fatigue`, `.hotFlashes`, `.nightSweats`, `.sleepChanges`, `.lowerBackPain`, `.pelvicPain`, `.appetiteChanges`, `.nausea`, … | R/W |
| Sleep, HRV, RHR | `.sleepAnalysis`, `.heartRateVariabilitySDNN`, `.restingHeartRate` | R |

- Import: on onboarding (user-consented), last 24 months.
- Ongoing: `HKObserverQuery` + anchored queries to pick up Apple Health / Watch entries.
- **Source of truth**: the local SwiftData store. HealthKit is mirrored. Conflict rule: an explicit user edit in Cyclus wins; entries authored by other apps are imported read-only (shown with a source badge).
- Write only user-entered data (Guideline 5.1.3: never write inferred or false data, so predictions are **never** written).

### 6.5 Sync & backup — OPEN RISK
- App Review Guideline **5.1.3(ii)**: apps "may not store personal health information in iCloud." Apple has published no definition of whether this covers CloudKit with E2E `encryptedValues`.
- **v1 decision**: no iCloud/CloudKit sync. Instead:
  1. HealthKit mirror: Apple Health syncs E2E across the user's devices and iCloud backups, so core data survives device loss via Health. Re-import is supported.
  2. Encrypted local backup is included in the standard (encrypted) iPhone backup.
  3. Manual export (JSON/CSV) to Files, which the user controls.
- Before v1.5: ask App Review (developer forums / App Review contact) whether CKSyncEngine with `encryptedValues` in the private DB is acceptable. If yes, add sync in v1.5.

### 6.6 On-device LLM layer (v1.5)
- `SystemLanguageModel.default`. Check `availability`. Unavailable on non-Apple-Intelligence devices, when AI is disabled, or in an unsupported language.
- Input: structured `Insight`s only, never raw notes. Output: `@Generable struct InsightText { title, body (≤ 2 sentences), suggestion? }`.
- Instructions enforce: no diagnosis, no medical claims, mention confidence, gentle tone, the user's language.
- Context budget: 4096 tokens per session. Use one session per insight batch and keep the prompt under ~1.5k tokens. Read `contextSize` at runtime.
- **Fallback**: hand-written localised templates per insight type, so the identical feature works without the LLM. The LLM is polish, not a dependency.
- Safety check: post-generation keyword filter for diagnostic terms ("you have PCOS") → fall back to the template.

### 6.7 Security & privacy
- File protection `.complete` on the store. App Group container for widgets uses `.completeUntilFirstUserAuthentication` (widgets must read while locked), and the widget store contains only minimal derived data (cycle day, next range), never logs.
- App lock: Face ID / passcode, with a blur in the app switcher.
- **Zero network**: no URLSession calls in v1 at all. Verified by a CI check (grep plus `NSAllowsArbitraryLoads` absent). Privacy label: **Data Not Collected**.
- **No analytics.** Feedback comes from TestFlight, App Store reviews, an in-app "Send feedback" mailto, and Apple's opt-in App Analytics (aggregate, provided by Apple).
- Discreet notification copy (configurable): "Cyclus: reminder" instead of "Your period starts tomorrow".
- "Erase all data": deletes the store, widget cache, and optionally Cyclus-authored HealthKit samples.
- Privacy manifest (`PrivacyInfo.xcprivacy`) declaring required-reason APIs (UserDefaults, file timestamps).

---

## 7. Legal & regulatory
- **Positioning: general wellness.** No claims of contraception, diagnosis, or treatment. Fertile-window estimates carry a persistent disclaimer.
  - EU MDR: software intended for contraception or diagnosis is a medical device (Rule 11). Avoid any intended-purpose language that implies that.
  - US FDA: general wellness guidance (2019/2022). Avoid disease claims.
- **GDPR**: the developer processes no data (all local), so minimal controller obligations. A privacy policy is still required and must say exactly that.
- **US state laws** (WA My Health My Data Act, etc.): largely not triggered when nothing is collected. Still publish a consumer health data policy, because WA requires one if you are a "regulated entity". Counsel to confirm.
- **Age rating**: 13+ (12+ in some regions). Teens are a real audience, and nothing is collected, so COPPA risk is minimal.
- **Medical content**: all educational copy is reviewed by a clinician (gynecologist / midwife) before launch, with sources cited in-app.
- **Budget**: one-off legal review of the privacy policy, disclaimer, and App Store text (~€500–1,500).

---

## 8. Quality plan
- **Unit tests**: CycleCore, CycleEngine, CycleInsights at ≥ 90% line coverage. Property-based tests for cycle detection (fuzzed logs).
- **Engine back-test** as above; calibration is a CI gate.
- **Snapshot tests** of key screens: light/dark, Dynamic Type XS / L / AX3, EN/DE.
- **UI tests**: onboarding, log flow, export.
- **Manual accessibility audit**: VoiceOver full pass, Accessibility Inspector, Voice Control.
- **Performance**: cold start < 400 ms to an interactive Today screen. The log sheet opens < 100 ms. Ten years of daily logs (3,650 days) is the perf fixture.
- **Beta**: TestFlight with 20–50 testers across modes, recruited from friends, Reddit (r/TwoXChromosomes rules permitting), and a midwife network.

---

## 9. Delivery
Ordered steps:
1. prototype + validation;
2. foundations;
3. engines;
4. store;
5. core screens;
6. HealthKit;
7. onboarding / settings;
8. widgets / report;
9. localisation and accessibility;
10. beta and submission.

(Effort and time estimates removed on owner request; see DESIGN_SPEC §13.)

---

## 10. Success metrics (no analytics, so proxy measures)
- App Store rating ≥ 4.7 after 100 ratings.
- Retention: Apple App Analytics (opt-in users only), 30-day retention ≥ 40%.
- Prediction calibration on beta testers' self-reported data (opt-in manual export sent by mail): 75–85%.
- Qualitative: "logging takes < 3 s" confirmed by 5 / 5 usability testers.
- Zero privacy incidents (structurally guaranteed).

---

## 11. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| iCloud guideline blocks sync | Med | High | v1 without sync; HealthKit as backup; ask Apple |
| Foundation Models unavailable on many devices | High | Low | Template fallback; LLM is polish |
| Prediction quality worse than competitors | Med | High | Honest ranges, back-test gate, temp integration |
| Scope creep (solo dev) | High | High | Hard v1 cut list (§4); modes share one engine |
| Medical-claim / regulatory trouble | Low | High | Wellness positioning, clinician copy review, legal check |
| Burnout / no revenue (free) | Med | High | Keep infra cost at €0 (no servers); optional tip jar later? (would break "no IAP") |
| App Review rejection on health grounds | Med | Med | Clear disclaimers, HealthKit usage strings, privacy policy, demo video |

---

## 12. Cost
- Apple Developer Program: €99/yr.
- Legal review: ~€1k one-off.
- Clinician review: ~€500 or pro bono.
- Servers: €0.
- Design assets: €0 (SF Symbols + custom).

---

## 13. Deliverables after this plan
1. `REVIEW.md` — sceptic review (MAJOR/MINOR).
2. `DESIGN_SPEC.md` — UX/UI + system design spec incorporating the review.
3. HTML prototype.
4. Xcode scaffold.

## 14. Open questions for owner
1. Name / brand vibe.
2. Apple Watch Series 8+ available for testing?
3. Confirm launch markets (EU + US) and languages (EN + DE).
4. Tip jar acceptable later, or strictly zero money?

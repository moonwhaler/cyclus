# Cyclus — Design Spec v1.0

> Date: 2026-11-22 · Status: **Draft for owner sign-off** · Supersedes `PLAN.md` v0.1.
> Incorporates the sceptic review (`REVIEW.md`: 12 MAJOR, 24 MINOR). §0.3 maps every finding to its resolution.
> Codename "Cyclus". Final name TBD.

---

## 0. Decisions & review resolution

### 0.1 Decisions the owner must confirm
Defaults are marked ✅. The rest of this spec is written against them. Changing D1 or D4 changes the scope materially.

| # | Decision | Default (recommended) | Alternative |
|---|---|---|---|
| **D1** | Fertility features (fertile window, Conceive mode, ovulation estimate) | ✅ **IN v1 — owner decision 2026-11-22.** Multi-signal fertility engine (§11.11) + Conceive mode (§7.14). Shipped per region behind a feature flag once the regulatory path for that region is cleared (§1.4). Cycle-deviation nudges stay gated. | — (decided) |
| **D2** | Positioning vs. iOS 27 Apple Health | ✅ Three wedges (§1.2): **deep fast logging**, **condition-first** (PCOS/endo/irregular), **Doctor Visit Pack**. Validate in phase 1 with current Apple Health users. | Pick one wedge only |
| **D3** | Publisher | ✅ **Legal entity + Organization developer account** before submission (UG (haftungsbeschränkt) or e.V.; D-U-N-S number). Start now, because it takes 4–8 weeks. | Individual account (rejection risk under guideline 5.1.1(ix), personal liability) |
| **D4** | Delivery | ✅ **Phase-based plan, no effort or time estimates** (owner decision 2026-11-22: built AI-assisted, effort numbers don't apply). Full v1 scope in ordered phases with exit criteria (§13.1); only outside lead times are planned in calendar time. Fertility can go live per region later than the core app (feature flag). | Fallback scope list (§13.1) |
| **D5** | Backup | ✅ Store **included** in device backups (so phone migration just works), with honest disclosure plus a "Device only" toggle that sets `isExcludedFromBackup`, plus **encrypted export**. | Exclude by default (max privacy, real data-loss risk) |
| **D6** | Money | ✅ Strictly free. **No tip jar in v1** (it would make the developer a DSA trader, whose address is published). | Tip jar via IAP after the entity exists |
| **D7** | Platform baseline | ✅ **Deployment target iOS 26.0, built with the iOS 27 SDK.** Use iOS 27 APIs behind `if #available`. | iOS 27 minimum (smaller audience, simpler code) |
| **D9** | Patterns & outlook | ✅ **In v1 (owner decision 2026-11-22).** „Deine Muster“ (§11.12) watches body, mood & feelings, lifestyle, fertility and cycle health. „Ausblick“ (§11.13) turns patterns into a 7-day outlook. Fertility and cycle-health watchers follow the same regulatory gate as fertility (§1.4). | — (decided) |
| **D10** | Platforms & code sharing | ✅ **Confirmed 2026-11-22, Android first (ANDROID_PLAN D11):** Android with 100% feature parity. **Kotlin Multiplatform shared core** (model, store schema, all engines, patterns, outlook, report model, backup, strings) + native UI (SwiftUI / Compose M3 Expressive). Changes §11.1–11.3 and §12.1 as listed in `ANDROID_PLAN.md` §2.5. | Fully native twice, or Compose Multiplatform UI (see ANDROID_PLAN §0) |
| **D8** | Markets & languages | ✅ **German first** (owner decision 2026-11-22): DACH is the primary market, all copy is authored in German (source language), App Store primary locale `de-DE`, German screenshots first, in-app language selector (Deutsch / English). English second. EU + US. | — (decided) |

### 0.2 What changed vs. PLAN.md (summary)
- **Removed from v1:** anomaly/deviation nudges, Siri/App Shortcuts, Control Center control, LLM features.
- **Fertility (D1 = in, revised 2026-11-22):** calendar-only "ovulation = period − 14" replaced by a **daily Bayesian multi-signal engine** (§11.11): cycle history + LH tests (positive *and* negative) + cervical mucus + BBT + Apple Watch wrist temperature, with personal learning, a smart LH-test planner and "Why this window?" explanations.
- **Added to v1:**
  - life-stage *episodes* in the data model;
  - pain map;
  - "Nothing else today";
  - Doctor Visit Pack (promoted to a hero feature);
  - Privacy Center;
  - optional app-specific PIN;
  - encrypted export;
  - threat model;
  - HealthKit sync state machine;
  - posterior-predictive engine with age-band priors and missed-log detection.
- **Re-positioned:** from "smartest tracker" to *"Your cycle, understood. Never uploaded."* The intelligence is honest stats now and on-device language later.

### 0.3 Review resolution map
| Finding | Resolution | Where |
|---|---|---|
| M1 Regulatory | Owner chose fertility in v1 (D1). Treated as a medical-device function: regulatory path per region, feature flag, intended-purpose wording, copy guardrails, budget | §1.4, §9.3, §13 |
| M2 Apple Health parity | Wedges + validation with Apple Health users | §1.2, §13 |
| M3 Wrist temp / BBT | Own baseline on raw values; Sensiplan-correct BBT rule; wrist used as weaker evidence than BBT, never double-counted; "likely occurred", never "confirmed" | §11.6, §11.11 |
| M4 File protection vs. lock screen | Single-writer process; intents require authentication; pending-event queue; principle rewritten | §3.3, §11.2 |
| M5 Threat model | Full threat model; per-category HealthKit write opt-in; widget redaction; PIN; discreet defaults | §3 |
| M6 Backup honesty | D5 + encrypted export + full HealthKit re-import + backup indicator + honest copy | §3.4, §11.9 |
| M7 HealthKit sync | Sync state machine, ownership, dedup, time zones, incremental permissions; own delivery phase with device tests | §11.4, §13.1 |
| M8 Fake precision | Log-normal NIG posterior-predictive, robust weights, missed-log detection, age-band priors, hormonal-factor suppression | §11.5 |
| M9 Circular validation | On-device retrospective back-test, aggregate-only output; no raw-data collection; no public accuracy claims | §12.2 |
| M10 Timeline | Phase plan with exit criteria, all missing work items added, outside lead times, iOS 27 baseline | §13 |
| M11 Entity / liability | D3, insurance, legal budget €3–10k, no tip jar | §0.1, §13.3 |
| M12 Pattern stats | One Bayesian paradigm, cycle as unit, observed-day denominator, tiers, null-simulation gate | §11.7 |
| m1 FM AUP/guardrails | Template-primary; FM only rephrases; `guardrailViolation` → template; `contextSize` availability-gated | §11.8 |
| m2 Age rating | 13+ under the new tiers; teen priors; Declared Age Range API reviewed | §1.5, §11.5 |
| m3 Modes naive | Episodes model in v1 schema | §2.2, §11.3 |
| m4 Cycle detection | Precedence: override > start flag > heuristic; `unspecified` flow counts | §11.5.1 |
| m5 LH timing | First positive → ovulation mostly +1/+2 days; negatives informative; estrogen-surge results handled; lag personalised | §11.11.3 |
| m6 HK mapping loss | Lossless mapping table + "not mirrored" fields | §11.4.2 |
| m7 Zero-network check | Lint bans `Network`/`WebKit`/`URLSession`; App Privacy Report pass in release QA | §12.1 |
| m8 Privacy manifest | Reason codes listed | §11.10 |
| m9 Notifications | Discreet default; "late" notification opt-in; Watch mirroring noted | §8.3 |
| m10 Erase | Honest confirmation listing what can't be erased; HK deletion default on | §7.9 |
| m11 PDF leak | Watermark, per-section exclusion, metadata stripped, in-memory render | §7.7 |
| m12 Log ordering | Stable order + pinned + separate "Suggested" row | §7.3 |
| m13 Snapshot deps / Xcode Cloud | Snapshot lib allowed as a *test-only* dependency; heavy suites run locally/nightly | §12.1 |
| m14 Coverage misses risk | Device integration tests for HealthKit, migration tests, locked-device checklist | §12.1 |
| m15 Perf context | Baseline device iPhone 12, measured to "first frame after auth" | §12.3 |
| m16 Onboarding vs. day-1 | No fake 29-day guess; honest wide range labelled "rough" | §7.1, §11.5 |
| m17 Metrics | +90-day observable metrics | §12.4 |
| m18 Clinician review | Named reviewer in phase 1; scope listed | §13.1 |
| m19 DE localisation | Terminology glossary, DE assets, Impressum check | §9.4, §13 |
| m20 Inclusivity | Gender-affirming hormones episode; neutral copy audit; no partner assumption | §2.2, §9 |
| m21 Palette a11y | Contrast-verified tokens; shape + label redundancy; VoiceOver chart descriptors | §5.1, §10 |
| m22 MHMDA | Counsel to confirm; Consumer Health Data Privacy Policy published anyway | §1.4 |
| m23 GDPR flows | Privacy policy covers feedback mail, TestFlight, crash logs | §1.4 |
| m24 Sustainability | Public versioned export schema + end-of-life pledge | §11.9 |

---

## 1. Product definition

### 1.1 Positioning
**"Your cycle, understood. Never uploaded."**
A free, beautiful, deeply private cycle and symptom diary for iPhone. It is the best tool to *understand your own patterns* and *walk into a doctor's appointment with evidence*. There are no servers, accounts, ads or trackers. The developer never receives your data.

### 1.2 Wedges vs. Apple Health (iOS 27)
Apple Health is the default and it is good. Cyclus wins only where Apple structurally won't go:

| Wedge | Apple Health iOS 27 | Cyclus |
|---|---|---|
| **1. Logging depth & speed** | Fixed symptom list, presence/severity only, buried in Health › Browse | ≤ 2 taps after unlock for common entries. Mood, energy, sleep quality, **pain map** (location × 0–10), custom tags, notes, "nothing else today" |
| **2. Condition-first** (PCOS, endometriosis, irregular, perimenopause, gender-affirming hormones) | General cycle model; perimenopause support aimed at 40+ | Episodes that change the model (no "late" language, wider ranges); condition-relevant log sets; a pain and bleeding diary built for diagnosis conversations (endometriosis diagnosis takes ~7–10 years on average) |
| **3. Doctor Visit Pack** | Generic PDF export of cycle history | Clinician-oriented summary: bleeding calendar, pain trends, symptom-by-cycle-day heatmap, the user's own questions, EN or DE output independent of UI language, FIGO reference ranges printed for context |
| **4. Privacy controls** | Health data E2E in iCloud; no per-app lock | App lock + optional app-only PIN, per-category control over what goes into Health, discreet widgets/notifications, Privacy Center |
| **6. Fertility that explains itself** | Calendar estimate, adjusted by LH tests; retrospective wrist-temperature ovulation estimate (Watch S8+) | Daily probability from **all** signals (LH ±, mucus, BBT, wrist temp), learns your personal luteal length and LH→ovulation lag, **tells you when to start LH tests**, shows *why* the window moved |
| **5. Insights in German on any iPhone** (v1.5) | AI Insights depend on Apple Intelligence and language rollout | Deterministic insights with localised templates; on-device LLM polish where available |

**Validation gate (phase 1):** 6–8 interviews with *current Apple Health cycle-tracking users* plus 2 people with endometriosis or PCOS. Each wedge needs ≥ 50% "would switch / use alongside" to stay in v1. Otherwise cut it (D4 alternative).

### 1.3 Principles (ranked; earlier wins conflicts)
1. **Trust first.** No dark patterns, fake precision or guilt. The developer never receives data.
2. **Honest numbers.** Every number shows its uncertainty or isn't shown. "Not enough data yet" is a valid answer.
3. **≤ 2 taps after unlock** for period start/end and any pinned symptom. Logging works one-handed.
4. **Useful on day 1** via Apple Health import. If there's no history, be honest rather than invent.
5. **Calm, native, beautiful, accessible.** Feels like Apple built it, with more warmth.

### 1.4 Regulatory & legal guardrails
- **Intended purpose (draft, to be finalised with the regulatory specialist; used verbatim in the App Store, website and in-app About):** "Cyclus helps you record your periods, symptoms and wellbeing, see patterns in your own records, estimate your fertile days **as an aid to conception**, and share a summary with your healthcare provider. Cyclus is **not intended for contraception** and does not diagnose any condition."
- **Fertility = medical-device function (D1 = in).** MDR Art. 2(1) covers software for "control or support of conception". FDA General Wellness guidance was reissued 2026-01-06. Precedents:
  - Apple's Cycle Tracking IFU labels it a medical device "to facilitate conception".
  - Clue's Conceive mode is listed in FDA GUDID under product code **LHD** ("fertility diagnostic, proceptive"), and BioWink declares it a regulated device under EU law.

  **Consequences:**
  1. A specialist qualification and classification opinion (EU class under Annex VIII Rule 11, and US product code / registration and listing duties) is **required before release in each region**. Budget €3–10k.
  2. Likely EU work: QMS-lite + technical documentation (ISO 14971 risk management, IEC 62304 software lifecycle, IEC 62366 usability, clinical evaluation from literature + own validation), EUDAMED registration, a Person Responsible for Regulatory Compliance (PRRC), and post-market surveillance. If classified IIa, a notified body is needed (cost and timeline jump; specialist to confirm).
  3. US: FDA establishment registration and device listing likely, with an **annual registration user fee** (around US$10k in recent fiscal years; verify the current amount). A real cost for a free app.
  4. **Engineering consequence:** fertility ships behind `FeatureFlags.fertility(region:)`. The app can launch period/symptom tracking in a region while fertility waits for clearance there. Code is written to IEC 62304-friendly standards from day one: requirements traceability (spec § → tests), a risk register, SOUP list (none at runtime).
  - Contraception claims remain **out of scope** (Annex VIII Rule 15, FDA Class II special controls). Never "safe days".
- **Grey zone (same opinion):** whether the **FIGO reference ranges in the PDF** are acceptable. Default: printed *without* flagging the user's values.
- **Copy guardrails:** see the banned-terms list in §9.3. App Store screenshots and text count as "intended purpose" evidence.
- **Privacy law:**
  - Privacy policy states no data is received.
  - It explains optional flows where the developer *does* become controller: feedback e-mail (Art. 9 health data in unsolicited mail → delete after resolution, max 90 days), TestFlight tester identity, opt-in Apple crash logs.
  - Publish a WA MHMDA-style Consumer Health Data Privacy Policy regardless. Counsel to confirm applicability (WA, NV, CT).
- **Impressum** (DDG §5) on the support site, via the entity's address (D3), not a home address.
- **Insurance:** product/professional liability quote for the entity.

### 1.5 Audience
Anyone who menstruates, 13+. App Store age rating: **13+** (2025 tier system; medical/wellness questionnaire answered truthfully). Teens: teen priors (§11.5), copy reviewed for minors, threat model includes parents with device access. Review Apple's Declared Age Range API and US state app-store age laws (TX/UT/LA) with counsel. No age gate collected beyond optional birth year (stored locally only).

### 1.6 Non-goals (v1)
Contraception or conception support, diagnosis, community/social features, content library/articles, partner sharing, Android/web, accounts, cloud sync, astrology.

---

## 2. Scope

### 2.1 Releases
**v1.0**
- Onboarding with Apple Health import.
- Today, Log sheet (incl. pain map, tags, "nothing else today"), Calendar + Day detail, Insights (stats & charts, no pattern engine).
- Period prediction (§11.5). Temperature *display*: BBT (logged) in v1. Wrist temperature (Apple Watch) is **read and displayed only if present, behind a flag, and not used by the fertility engine** until it has been tested on a real device (§14, owner has no Apple Watch). The fertility engine runs on cycle history + LH + mucus + BBT.
- HealthKit: import of all history, plus write-own for user-selected categories.
- Doctor Visit Pack (PDF).
- Privacy Center, app lock + optional PIN, discreet notifications, encrypted export/import, erase.
- Home Screen widget (S/M), Lock Screen widget (redacted by default).
- Local notifications.
- EN + DE.

**v1.1:** Control Center control, App Intents (auth-required, no automatic App Shortcuts), StandBy widget, perimenopause symptom set refinements from iOS 27 HealthKit types, iPad layout.

**Also v1.0:** „Deine Muster“ (§11.12) and „Ausblick“ (§11.13) with template text; feelings and libido logging.

**v1.5:** on-device LLM phrasing of pattern text (§11.8); HealthKit sleep/resting-HR watchers; pill/patch/ring schedule within the hormonal-contraception episode; Apple Watch app (log + glance).

**Also v1.0 (behind the per-region fertility flag, §1.4):**
- Fertility engine (§11.11);
- **Conceive mode** (§7.14);
- optional "Show fertile window" in Track mode (off by default);
- LH-test planner;
- ovulation estimate (predicted → likely → likely occurred);
- temperature shift markers;
- Conceive section in the Doctor Visit Pack.

**Gated:** cycle-deviation nudges (Apple ships these as a regulated feature).

**v2:** "Ask your data" (on-device), pregnancy & postpartum experiences, partner sharing (mechanism depends on Apple's answer about 5.1.3(ii)), Live Activity during period.

### 2.2 Life-stage episodes (in v1 data model)
Episodes are time-bounded factors with `start` and optional `end`. They change engine behaviour, not just copy.

| Episode | Engine effect (v1) | UI effect |
|---|---|---|
| Hormonal contraception (method: combined pill / progestin-only / patch / ring / implant / injection / hormonal IUD) | Bleeds inside the episode = "bleeding", not cycle starts. **Predictions suppressed.** | Today shows "Predictions paused while using hormonal birth control" + bleeding log |
| Copper IUD | No change (non-hormonal) | — |
| Pregnancy | Predictions suppressed | Neutral "Pregnancy" status; bleeding still loggable |
| Postpartum / breastfeeding | Predictions suppressed until 2 cycles after first post-episode period | "Waiting for your cycle to return" |
| Perimenopause | Perimenopause prior (§11.5); no "late" wording; gaps ≥ 60 d treated as expected | Hot flashes, night sweats, sleep changes surfaced as Suggested |
| Gender-affirming hormones (testosterone) | Predictions off by default (user can enable) | Neutral copy; bleeding/spotting log; no "period" push language |
| PCOS / Endometriosis (conditions; may be open-ended) | PCOS: wide prior, missed-log detection relaxed | Condition log set pinned (pain map, bleeding heaviness, fatigue) |

Mapped to HealthKit `.contraceptive` / `.pregnancy` / `.lactation` where a type exists, write opt-in (§3.2).

---

## 3. Threat model & data-flow map

### 3.1 Adversaries
| ID | Adversary | Capability | Main mitigations |
|---|---|---|---|
| A1 | Shoulder-surfer / stranger | Sees screen, lock screen, StandBy | Redacted Lock Screen widget by default; discreet notifications by default; app-switcher blur |
| A2 | Partner/family member **who knows the device passcode** (incl. coercive partner) | Unlocks phone, opens apps, reads Health app, may enrol own Face ID appearance | Optional **app-only PIN** with "PIN only, no Face ID" setting; sensitive categories **not** mirrored to Health by default; Privacy Center explains Health Sharing |
| A3 | Parent of a teen | Same as A2, plus Screen Time visibility | Same; app name and icon are visible in Screen Time and Settings › Health (documented honestly, not "hidden") |
| A4 | Device seizure / forensic extraction (BFU/AFU) | Physical access, extraction tools | `.complete` file protection on the store (keys evicted ~10 s after lock); no plaintext caches outside the protected container |
| A5 | Legal process against Apple (iCloud) | Obtains non-E2E iCloud data (iCloud Backup without ADP, iCloud Drive files) | D5 disclosure + "Device only" toggle; encrypted export; recommend Advanced Data Protection in Privacy Center |
| A6 | The developer / supply chain | Could add telemetry | No network code (lint + release check); no third-party runtime deps; open export schema |
| A7 | Recipient of shared artefacts | Has the PDF/export forever | Per-section exclusion, watermark, encrypted export default |

### 3.2 Data-flow map
| Surface | Data | Storage / protection | Default | Leak notes |
|---|---|---|---|---|
| Main store (SwiftData) | Everything | App container, `NSFileProtectionComplete` on `.store`, `-wal`, `-shm` | — | Unreadable while locked (intended) |
| Pending-event queue | Event type + date only (e.g. "periodStart 2026-11-22") | App Group file, `.complete` | Written only after authenticated intents | Tiny; drained on next app activation |
| Widget snapshot | Cycle day, next-period range, status enum | App Group JSON, `.completeUntilFirstUserAuthentication` | Home widget shows; Lock Screen widget **redacted** (`.privacySensitive()`) | Readable AFU; minimal |
| Notifications (pending requests) | Trigger date + copy | System | **Discreet copy** ("Cyclus · Check-in"); "late period" **off** | Content mirrors to Apple Watch |
| Apple Health mirror | Per category | HealthKit (E2E iCloud sync) | Flow, spotting, symptoms, BBT **on**; mood (`HKStateOfMind`) **off**; **sexual activity, contraceptive, pregnancy/lactation off** | Health has no per-app lock; Health Sharing may forward to others |
| Device backup | Main store | iCloud Backup (Apple-held keys unless ADP) / encrypted local backup | Included (D5), "Device only" toggle | Disclosed in Privacy Center + onboarding privacy page |
| Encrypted export `.cyclus` | Everything | User-chosen location | Default export format | Safe at rest if passphrase strong |
| Plain CSV export | Everything chosen | User-chosen location | Secondary option behind a warning | Largest leak vector; warning copy |
| Doctor Visit Pack PDF | Chosen sections | Rendered in memory, shared via share sheet | Sexual activity, contraception, notes **excluded** by default | Watermark; metadata stripped |
| App Intents (v1.1) | Query/log | Run in app process | `authenticationPolicy = .requiresAuthentication` for all; **no AppShortcutsProvider** (avoids Spotlight/Siri suggestions) | User adds shortcuts manually |
| Spotlight / donations | — | — | None | — |
| Feedback mail | Whatever the user writes | Developer inbox | User-initiated | Privacy policy covers it; delete ≤ 90 d |

### 3.3 Lock-state behaviour (resolves M4)
- **Single writer:** only the main app process opens the SwiftData store.
- Lock Screen widget / Control (v1.1) "Log period" button → intent with `requiresAuthentication` → system unlock prompt → intent runs **in the app process** (intent defined in the app target; widget references it). If the app process can't take the write synchronously, it appends to the pending-event queue, and the app drains it on activation.
- Principle 3 is defined as **"≤ 2 taps after unlock"**. Lock Screen tap + Face ID glance counts as one tap.
- Failure UX: if a write fails, the widget shows "Couldn't save — open Cyclus" and the event stays queued. Data is never silently dropped.
- HealthKit is unavailable while locked. The observer handler defers sync to the next unlocked activation.

### 3.4 Honest privacy copy (resolves M6, m10)
- Use "We never receive your data. There are no Cyclus servers." **Never** "your data never leaves your phone".
- Privacy Center states, in plain language, where each copy lives (§7.8).

---

## 4. Information architecture

```
Tab bar (Liquid Glass, 3 tabs)
├── Today            (default)
│   ├── toolbar: Privacy shield (→ Privacy Center), Settings gear
│   └── floating Log button (glass) → Log sheet
├── Calendar
│   └── Day → Day detail sheet → Edit (Log sheet prefilled)
└── Insights
    ├── Cycle overview (stats)
    ├── Charts: Cycle lengths · Period lengths · Symptom heatmap · Pain · Temperature
    ├── Cycle history list → Cycle detail
    └── Doctor Visit Pack → Builder → Preview → Share
Settings (pushed from Today)
├── Life stage & conditions (episodes)
├── Logging (pinned items, custom tags, units)
├── Notifications
├── Apple Health (categories, import, sync status)
├── Privacy Center
├── Backup & Export
├── Language · About · Intended purpose · Feedback
```
The Log sheet is reachable from Today (floating button), Calendar (day), widgets and notifications (deep link `cyclus://log?date=`).

---

## 5. Visual design system

### 5.1 Colour tokens
Contrast verified (WCAG 2.2) against `bg` and `surface`. Text tokens ≥ 4.5:1; non-text ≥ 3:1.

| Token | Light | Dark | Use | Contrast L / D (vs bg) |
|---|---|---|---|---|
| `bg` | `#FBF8F5` | `#141114` | Screen background (warm neutral) | — |
| `surface` | `#FFFFFF` | `#1E1A1E` | Cards, sheets | — |
| `textPrimary` | `#1D1A1C` | `#F4EFF1` | Body, titles | 16.3 / 16.5 |
| `textSecondary` | `#6B6267` | `#ABA1A6` | Captions, metadata | 5.6 / 7.5 |
| `period` | `#B3264E` | `#FF8FA8` | Bleeding days, period range, primary accent | 6.0 / 8.7 |
| `prePeriod` | `#6A4C9C` | `#BBA6EC` | 7-day window before predicted period | 6.4 / 8.7 |
| `midCycle` | `#8A7F76` | `#8F858A` | Neutral ring segment (non-text only) | 3.7 / 5.3 |
| `success` | `#2E7D5B` | `#6FCF9F` | Saved states | 4.7 / 9.9 |
| `warning` | `#8F5600` | `#F2B65A` | Attention (never alarm-red) | 5.7 / 10.4 |
| `onPeriod` | `#FFFFFF` | `#141114` | Text on `period` fills | 6.4 / 8.7 |

Rules:
- **Colour is never the only signal.** Bleeding days have a filled droplet glyph. Predicted days use a dashed outline + "predicted" label. The pre-period window uses a dotted pattern on the ring.
- **Fertility tokens:** `fertile` `#13706F` / `#5FD0C8` (5.6:1 / 10.1:1 vs bg; white text on light fill 5.9:1, ink on dark fill 10.1:1). Fertility is shown in **3 intensities** (Possible 30% fill, High 65%, Peak 100% + ✦ glyph) **plus a dot-density pattern**. `fertile` and `period` have near-equal luminance (1.1:1), so they are distinguished by hue + glyph + pattern, never by lightness alone. Ovulation marker: ◎ glyph (dashed = predicted, solid = likely / likely occurred).
- **Never green-coded "low"**: "Lower chance" days get no fill at all, to avoid a "safe" reading.
- High-contrast mode (`colorSchemeContrast == .increased`): darken light-mode fills 15%, use solid outlines instead of dashed.
- **Themes (2026-11-22):** Settings › Appearance lets the person pick a palette (Rosé = these tokens, Lavendel, Salbei, Ozean, Terrakotta, Graphit), custom primary/secondary colours (lightness corrected in OKLCH until WCAG holds) and Light/Dark/System (Android also offers wallpaper colours). Themes change only the **accent** (interaction: buttons, links, selection) and the tint of the neutrals. `period`, `prePeriod`, `fertile`, `success`, `warning` and the error/destructive colour (= `period`) are identical in every theme, so a colour always means the same thing.

### 5.2 Typography
System **SF Pro** text styles only, so Dynamic Type works up to AX5. Numerals use **SF Pro Rounded** with `.monospacedDigit()`.

| Role | Style | Notes |
|---|---|---|
| Hero number (cycle day) | `.largeTitle` rounded, `.bold`, scaled ×1.6 via `@ScaledMetric` | Caps at AX3 then wraps |
| Screen title | `.largeTitle` | — |
| Card title | `.headline` | — |
| Body | `.body` | — |
| Range text | `.title3` rounded | "Nov 28 – Dec 2" |
| Caption | `.footnote` / `.caption` | Never below `.caption` for meaningful text |

### 5.3 Spacing, shape, elevation
- 4-pt grid. Tokens: `xs 4 · s 8 · m 12 · l 16 · xl 24 · xxl 32`. Screen gutter 16 (20 on Pro Max).
- Corner radius: cards 16 (continuous), chips 999, sheets = system.
- Elevation: no drop shadows on content. Depth comes from Liquid Glass on chrome only.

### 5.4 Liquid Glass usage
- **Glass:** tab bar, navigation bars, floating Log button (`.glassEffect(.regular.interactive())`), sheet grabbers.
- **Not glass:** content cards, charts and the calendar grid, which stay on solid `surface` for legibility. Respect `reduceTransparency` (glass → solid `surface`).

### 5.5 Iconography
SF Symbols (hierarchical rendering). Custom symbols (SF Symbols template format): `cyclus.droplet` (flow: 0–4 fill levels), `cyclus.ring`, `cyclus.body.front` (pain map). Each custom symbol needs light/dark/high-contrast variants and an accessibility label.

### 5.6 Motion
- Standard spring: `response 0.35, dampingFraction 0.85`.
- Ring progress on appear: 0.6 s ease-out. Day change: cross-fade 0.2 s.
- **Reduce Motion:** no ring sweep, no spring overshoot; cross-fades only.
- No confetti, streak flames or celebratory bursts.

### 5.7 Haptics
| Event | Haptic |
|---|---|
| Chip toggle / flow level select | `.selection` |
| Save log | `.success` (once per sheet dismissal, not per chip) |
| Calendar scrub crosses month | `.impact(.light)` |
| Error | none (visual + VoiceOver announcement instead) |

---

## 6. Components (CycleDesign package)

| Component | Spec |
|---|---|
| **CycleRing** | 270° arc (gap at bottom for the label). Segments: period (solid `period`), mid (`midCycle`, 40% opacity), pre-period (`prePeriod`, dotted). Today marker = 12-pt knob with 2-pt `bg` stroke. Predicted period = dashed outer arc spanning the 80% range. Centre: max **three short lines** (kicker · big value · subtitle). Rules:
  - the big value never wraps: it shrinks to fit, down to 18 pt (SwiftUI `minimumScaleFactor`), and DE is the length reference;
  - kicker and range are single-line;
  - the subtitle is at most 2 lines;
  - anything longer (ovulation estimate, explanations) goes **below** the ring, never inside it.

  Copy budget for the big value: ≤ 12 characters (e.g. "Hoch", "Tag 3", "Pausiert"). Size: min(80% width, 320 pt). VoiceOver: one element, label "Cycle day 12. Next period expected between November 28 and December 2." Custom actions: "Log period", "Open calendar". |
| **RangeText** | Formats an interval with locale-aware `Date.IntervalFormatStyle` ("28. Nov. – 2. Dez." in DE). If the range > 10 days, append "(wide range — cycles vary)". |
| **ConfidenceBadge** | Tiers: *Rough estimate* (prior-dominated, n_eff < 2) · *Getting there* (2–5 cycles) · *Based on your history* (≥ 6). Text label, never a percentage in v1. |
| **FlowPicker** | 5 droplets: None · Spotting · Light · Medium · Heavy. 56-pt targets in an HStack (wraps to a 2-row grid at AX sizes). Spotting shows a dotted outline. |
| **Chip** | Toggle; icon + label; 44-pt min height; selected = tinted fill + checkmark glyph (not colour alone). Long-press → severity (mild/moderate/severe) where the symptom supports it. |
| **ScaleRow** | 5-step scale for mood/energy/sleep quality, with labelled end points ("Low"…"High"), as a segmented control. Never emoji-only. |
| **PainMap** | Front silhouette with 8 tap regions: head, breasts/chest, upper abdomen, lower abdomen L/C/R, pelvis, lower back (toggle to back view), thighs. Tap → 0–10 slider sheet. Regions are a VoiceOver list ("Lower abdomen, left: pain 6"). |
| **InsightCard** | Title, 1–2 sentence body, tier badge ("Early signal" / "Consistent pattern"), "Why am I seeing this?" disclosure → data explanation. |
| **EmptyState** | Symbol + one sentence + one primary action. Never blames the user. |
| **PrivacyRow** | Icon, title, status ("On this iPhone only" / "Also in Apple Health"), chevron. |

---

## 7. Screen specifications

### 7.1 Onboarding (≤ 60 s, every step skippable except the notice)
| # | Screen | Content | Primary / secondary |
|---|---|---|---|
| 1 | Welcome | Wordmark, "Your cycle, understood. Never uploaded." | **Get started** |
| 2 | Privacy promise | 3 rows: "No account. No servers.", "We never see your data.", "You decide what goes into Apple Health." Link: "What about backups?" (opens §7.8 explainer) | **Continue** |
| 2a | Notice „Bevor du startest“ (required) | Four points: **Not birth control** (never rely on Cyclus to avoid pregnancy; low or no fertility shown ≠ can't get pregnant), **Estimates only**, **Not a medical device**, **Your decision** (developer not liable for consequences, except intent, gross negligence, injury to life/body/health and product liability law). Checkbox „I have read and understood this. I will not use Cyclus to avoid pregnancy.“ | **Agree and continue** (disabled until ticked; no skip) |
| 3 | Import | "Bring your history from Apple Health so predictions work from day one." | **Import from Apple Health** / Skip |
| 3a | Import progress | Read-only permission sheet (menstrual flow, spotting, symptoms, BBT, wrist temp, cervical mucus, contraceptive, pregnancy, lactation). Progress → "Found 37 periods since 2021." | auto-advance |
| 4 | Last period (if no import) | "When did your last period start?" Inline graphical date picker | **Continue** / "I don't remember" |
| 5 | Birth year (optional) | "Cycles change with age. This stays on your iPhone." Wheel | **Continue** / Prefer not to say |
| 6 | Anything affecting your cycle? | Multi-select chips: Hormonal birth control · Pregnant · Postpartum / breastfeeding · Perimenopause · PCOS · Endometriosis · Testosterone / gender-affirming hormones · None of these | **Continue** |
| 7 | App lock | "Lock Cyclus with Face ID?" | **Turn on** / Not now |
| 8 | Done | Today screen with first-run coach mark on the Log button | — |

**Notice consent (2026-11-23).** Agreeing stores version + time (device only: not in exports, not in device snapshots; erase removes it). It is asked again, full screen after unlock, when the last confirmation of the current wording is older than 12 months, when the wording version is raised, and on a restored or new phone. That screen keeps „Datenschutz“ reachable (export, erase), so nobody is locked out of their own data by not agreeing. Switching on the fertile window or Kinderwunsch shows the notice as a sheet first; the switch only changes after agreeing. Datenschutz › Rechtliches › „Wichtiger Hinweis“ shows the last confirmation; its sheet lists every confirmation (date, time, version). The wording is not legal advice and needs a lawyer's review before public release.

- **Android (2026-11-22):** step 3 imports a `.cyclus` backup (Health Connect import arrives with A4); step 4 asks „Blutest du noch?“ / „Wie viele Tage ungefähr?“ and logs only the stated days as bleeding without a level (no invented flow levels); birth year is not asked until the engine uses age-band priors.
- Write permissions for Apple Health are **not** requested here. They are requested in context when the user turns on mirroring for a category (Settings › Apple Health), avoiding a 35-toggle sheet.
- Notification permission is requested when the first prediction exists ("Want a heads-up 2 days before your period?").
- With no history: Today shows "Log your next period to start predictions". If a last-period date is given, it shows a **Rough estimate** range from the prior (wide, labelled).

### 7.2 Today
```
┌──────────────────────────────────────┐
│ Today, Sun 22 Nov        🛡  ⚙︎        │  nav (glass)
│                                      │
│          ╭───────────────╮           │
│        ╱    Day 12         ╲         │  CycleRing
│       │  Next period        │        │
│        ╲ Nov 28 – Dec 2    ╱         │
│          ╰───── ◦ ───────╯           │
│       [Based on your history]        │  ConfidenceBadge
│                                      │
│ ┌──────────────────────────────────┐ │
│ │ Today's log                      │ │  summary card
│ │ 💧 —   Headache · Tired   Mood 3 │ │  (tap → Log sheet)
│ └──────────────────────────────────┘ │
│ ┌──────────────────────────────────┐ │
│ │ Pre-period window starts Wed     │ │  context card (1 max)
│ │ Last 3 cycles you logged cramps  │ │
│ │ 1–2 days before. (Early signal)  │ │  (v1.5 insight)
│ └──────────────────────────────────┘ │
│                         ╭─────────╮  │
│                         │ + Log   │  │  floating glass button
│                         ╰─────────╯  │
│   Today      Calendar      Insights  │  tab bar (glass)
└──────────────────────────────────────┘
```

**Ring explainer (2026-11-23).** The ring is a tap target; a quiet ⓘ (18 dp glyph, 44 dp touch target, `ink2` at 75 %) sits in its empty bottom gap so the tap is discoverable without adding a row. Both open „So liest du den Ring“: one intro line (one segment per day, clockwise, starting bottom left with day 1) and a legend of only the elements the ring shows right now, each swatch a slice of the ring drawn with the same colours, strokes and dashes, then a link to „So funktionieren Prognosen“. TalkBack: the ring node is a button „Ring erklären“; its description stays the spoken summary.
States:
| State | Ring centre | Subtitle |
|---|---|---|
| Normal | Day N | Next period · "around <most likely date>" (median cycle length), "possible: <range>" below (2026-11-22: the range's first day was read as *the* forecast) |
| In period | Period day N | "Tap to log flow" + quick flow chips inline |
| Pre-period window | Day N | "Period expected in X–Y days" |
| Past upper bound | Day N | "Later than your usual range" (neutral, no alarm, no push unless opted in) |
| Predictions paused (episode) | Day N since last bleed or episode label | Explanation + link to Life stage |
| No data | — | "Log your period to begin" + big **Log period** button |

- Period-start quick action: when no period is open and today ≥ predicted lower bound − 3 (status pre-period, expected or later), show a **"Period started today"** one-tap button directly under the ring. One tap = log (undo toast for 5 s). During a period, the ring area shows inline flow droplets instead.

### 7.3 Log sheet
- Presented as a `.sheet` with detents `.medium` / `.large`. Date header with ‹ › arrows; swipe between days.
- **Stable section order:** Flow → Pinned → Suggested → Symptoms → Pain → Mood & energy → Sleep → Discharge → Sex (hidden unless enabled) → Temperature → Tags → Note.
- **Pinned row** (UI label „Schnellauswahl“ / „Quick picks“; „Angeheftet“ was unclear): user-chosen (default: Cramps, Headache, Tired, Bloating). Fixed positions → muscle memory.
- **Suggested row:** separate, up to 4 chips, "Often logged around this time". Never reorders other sections.
- **"Nothing else today"** button at the bottom marks the day as *observed*, which the pattern engine needs (§11.7). It is **explicit only**, never auto-set on save, because an absent entry ≠ no symptom. Dismissing with "Done" without it leaves unlogged symptoms *unknown*.
- Save is implicit: every change persists immediately. A "Done" button dismisses. The success haptic fires once.
- Edits on past days show "Edited" in the day detail. Undo is via the toast.
- At AX sizes, chips wrap and the sheet opens at `.large`.

### 7.4 Calendar
- Month grid, vertical infinite scroll, today ringed.
- Day cell (min 44×44): number. Bleeding = filled `period` circle + droplet glyph at AX. Predicted period = dashed `period` outline. Pre-period window = dotted underline. Logged-anything = small dot below.
- Legend button (top right) opens a key.
- Tap a day → **Day detail** sheet: summary of the log, source badges for imported entries ("From Apple Watch"), **Edit** → Log sheet. Long-press a day → "Mark as period start / Not a period start" (override).
- v1.1: horizontal timeline view toggle.

### 7.5 Insights
1. **Cycle overview card:** typical cycle length *range* (e.g. "27–31 days"), typical period length, variability in words ("Your cycles vary by about 4 days"), confidence badge, number of cycles used. Tap → explanation of the method in plain language.
2. **Charts** (Swift Charts, every chart has an audio graph + VoiceOver summary):
   - Cycle lengths: bar per cycle + shaded "your typical range" band; excluded/flagged cycles hatched with a reason.
   - Period lengths: bar chart.
   - **Symptom heatmap:** rows = symptoms, columns = days relative to period start (−10…+10), cell = % of observed days with the symptom. Requires ≥ 2 cycles.
   - Pain: line of max daily pain, period days shaded.
   - Temperature: BBT (logged) and wrist temperature (from Apple Watch) as separate series with gaps shown. A coverline and "temperature rise" marker appear where the shift detector (§11.6) fires (fertility flag on).
   - Conceive (fertility flag on): ovulation day per cycle (dot + 80% range bar), luteal length per cycle, first positive LH test vs estimated ovulation.
3. **Cycle history:** list rows (start date, length, period length, mini heatmap). Tap → Cycle detail.
4. **Doctor Visit Pack** entry card.
5. (v1.5) **Patterns** section with InsightCards.

### 7.6 Cycle detail
Header: dates, length, period length, episode badges. Day strip with logs. Actions: "Exclude from predictions" (with reason: missed log / illness / medication / other), "Merge with next" / "Split".

### 7.7 Doctor Visit Pack
**Builder** (one screen):
- Range: last 3 / 6 / 12 cycles / custom.
- Include toggles: Bleeding calendar ✓, Cycle & period lengths ✓, Symptoms ✓, Pain ✓, Mood/energy ✓, Temperature ☐, Notes ☐, Contraception ☐, Sexual activity ☐.
- "Questions for my appointment" (free text list, optional).
- Output language: EN / DE (independent of UI).

**PDF** (A4 / US Letter by locale):
| Page | Content |
|---|---|
| 1 Summary | Name field left blank for handwriting (we store no name). Date range; cycles: n, length median & range, period length median & range, longest/shortest. Bleeding heaviness distribution. Top symptoms by frequency. Max pain by cycle. **Reference box:** "FIGO 2018 normal limits: frequency 24–38 days, duration ≤ 8 days, variation ≤ 7–9 days." User values are shown neutrally, never flagged. |
| 2 Bleeding calendar | Month grids with flow levels (pattern + shade, grayscale-printable) |
| 3 Symptoms & pain | Heatmap relative to period start; pain chart |
| 4 Notes & questions | If included |
| Footer (all) | "Confidential health information · Generated by Cyclus from self-reported records · Not a diagnosis" + page x/y |

Rendering: `ImageRenderer` → `CGContext` PDF **in memory**. Document-info metadata stripped (no author, creator "Cyclus" only). Shared via `ShareLink` with a `Transferable` data representation; no temp file outside the protected container.

### 7.8 Privacy Center
Sections:
1. **Where your data is.** PrivacyRows:
   - "On this iPhone: encrypted when locked ✓";
   - "In Apple Health: Flow, Spotting, Symptoms, BBT" (tap → category toggles, each with a one-line consequence, e.g. "Anyone you share Health data with may see this.");
   - "In backups: Included. With iCloud Backup, Apple holds the keys unless Advanced Data Protection is on." with buttons [Learn about ADP] and toggle "Device only (exclude from backups)" — warning: "If you lose this phone, data not in Apple Health or an export is gone.";
   - "Exports you've made: 2 (last Nov 3). We can't delete copies you've shared."
2. **Protection:** Tip row: „App ausblenden“ → explains iOS 18 *Hide and Require Face ID* (Android: Private Space, see ANDROID_PLAN §1.1). App lock (Face ID / passcode) · **Cyclus PIN** (6 digits, independent of device passcode) · "Require PIN, don't allow Face ID" · Lock after: immediately / 1 min / 5 min · Blur in app switcher (always on, shown as info).
3. **Visibility:** Notification style: Discreet ✓ / Detailed · Lock Screen widget: Hidden details ✓ / Show · StandBy (v1.1).
4. **Erase everything** (§7.9).

### 7.9 Erase everything
Two-step confirmation. Step 1 lists what happens:
- ✓ All Cyclus data on this iPhone.
- ✓ Data Cyclus wrote to Apple Health (toggle, **on** by default).
- ✗ Cannot erase: iCloud/computer backups made earlier, exports and PDFs you shared, Apple Health entries made by other apps.

Step 2 requires typing "ERASE" (localised) or authenticating. The app then resets to onboarding.

### 7.10 Settings › Apple Health
Status line ("Last synced 2 min ago · 1,204 entries imported"), category list (Read ✓ / Write toggle), "Re-import everything", "Entries removed in Apple Health: 3 — Review" (§11.4), denied-permission help: "If nothing imports, open Settings › Health › Data Access & Devices › Cyclus."

### 7.11 Backup & Export
- **Encrypted backup (.cyclus)** → passphrase (min 10 chars, strength meter) → share sheet. A "Last backup: 34 days ago" row plus an optional monthly reminder.
- **CSV (unencrypted)** → warning sheet → choose fields.
- **Import .cyclus** → passphrase → preview counts → merge or replace.

### 7.12 App lock screen
Full-screen `bg` with the app glyph, "Cyclus is locked", and an [Unlock] button that triggers Face ID / PIN. No data rendered behind it; the snapshot for the app switcher is blurred.

### 7.13 Error & edge states
| Case | Copy (EN) |
|---|---|
| HealthKit unavailable (iPad w/o Health) | "Apple Health isn't available on this device. Everything else works." |
| Write failed (locked) | "Couldn't save while locked. Open Cyclus to finish." |
| Import found nothing | "We didn't find period data. If you expected some, check Health access for Cyclus." |
| Wrong export passphrase | "That passphrase didn't work. Nothing was changed." |
| Storage full | "Your iPhone is out of space, so we couldn't save. Free up space and try again." |

### 7.14 Conceive mode (fertility flag on)
Selected in onboarding step 6 ("Trying to get pregnant") or in Settings › Life stage. In Track mode, "Show fertile window" (off by default) shows the band and the ovulation marker only: no "Lower chance", no planner.

**Today (Conceive):**
```
┌──────────────────────────────────────┐
│          ╭───────────────╮           │
│        ╱  ✦ Peak day      ╲          │  ring: fertile arc shaded by P_fertile
│       │   Cycle day 14     │         │  ◎ ovulation marker (dashed=predicted)
│        ╲ Ovulation likely ╱          │
│          ╰ today or tmrw ╯           │
│   [Updated by your LH test]          │  evidence chip
│ ┌──────────────────────────────────┐ │
│ │ Best days: Today · Tomorrow      │ │
│ │ Sex every 1–2 days in High/Peak. │ │
│ └──────────────────────────────────┘ │
│ ┌──────────────────────────────────┐ │
│ │ LH test · today      [–]  [+]    │ │  one-tap result
│ │ Positive yesterday ✓             │ │
│ └──────────────────────────────────┘ │
│  Why this window? ›                  │
│  Not for birth control.              │
└──────────────────────────────────────┘
```
- **Centre text by tier:** "✦ Peak day" / "High chance today" / "Possible today" / "Lower chance · window opens in ~3 days".
- **Evidence chip:** "Calendar estimate" / "Updated by your LH test" / "Updated by your mucus" / "Temperature rise detected".
- **LH card:** shown from planner start day until a positive or a temperature shift. Buttons are text-labelled (Negative / Positive / Surge), not just ±.
- **Why this window?** sheet: timeline of the `EvidenceTrail`, e.g.
  - "From your last 8 cycles: Nov 9–15"
  - → "Positive LH Nov 12: narrowed to Nov 11–13"
  - → "Temperature rise Nov 14: ovulation likely occurred around Nov 13."

  Plus a "How Cyclus estimates fertility" explainer (clinician-reviewed).
- **Two-week wait** (after *Likely occurred*): ring centre "Day 6 after ovulation", card "Earliest reliable test: Nov 28". No countdown pressure, no baby imagery.
- **Log sheet pinned (Conceive default):** LH test · Mucus · BBT · Sex. The BBT field remembers the last value and offers a ±0.05 stepper; the °C/°F unit follows settings.
- **Calendar:** fertility shading (3 intensities + dot pattern), ◎ ovulation marker, ✦ peak day, LH-positive tick.

---

## 8. System surfaces

### 8.1 Widgets
| Widget | Family | Content (unlocked) | Locked / redacted |
|---|---|---|---|
| Ring | systemSmall | Mini ring, "Day 12", "Period in 6–10 d" | Home Screen is visible only unlocked |
| Ring + log | systemMedium | Ring + "Log period" / "Log symptom" buttons (auth-required intents) | — |
| Lock circular | accessoryCircular | Ring progress + day number | **Default "Hidden details":** abstract ring, no numbers (`.privacySensitive()`) |
| Lock rectangular | accessoryRectangular | "Period in 6–10 days" | Redacted to "Cyclus" |
| StandBy (v1.1) | — | Off by default | — |

Timeline: one entry per day at local midnight, plus a reload after each save (`WidgetCenter.reloadAllTimelines()`).

### 8.2 App Intents (v1.1)
`LogPeriodStartIntent`, `LogPeriodEndIntent`, `LogSymptomIntent(symptom)`, `NextPeriodIntent`. All use `authenticationPolicy = .requiresAuthentication`. **No `AppShortcutsProvider`**, so nothing appears in Spotlight/Siri suggestions automatically. Settings › Logging › "Add to Shortcuts" explains how to opt in.

### 8.3 Notifications (local only)
| Notification | Default | Discreet copy (default) | Detailed copy |
|---|---|---|---|
| Period expected soon (2 d before lower bound) | On (after permission) | "Cyclus · Check-in" | "Your period may start in the next few days." |
| Log reminder (daily, user time) | Off | "Cyclus · Reminder" | "How are you feeling today?" |
| Later than usual (1 d past upper bound) | **Off** | "Cyclus · Check-in" | "Your period is later than your usual range." |
| Backup reminder (monthly) | Off | "Cyclus · Backup" | "It's been a month since your last backup." |
| LH test reminder (Conceive, planner days) | On in Conceive | "Cyclus · Reminder" | "Time for today's LH test." |
| Window opening (Conceive) | On in Conceive | "Cyclus · Check-in" | "Your fertile window is likely starting." |
| BBT morning reminder (Conceive, if BBT used) | Off | "Cyclus · Reminder" | "Take your temperature before getting up." |

**Opt-in (owner decision 2026-11-22, both platforms):** no notification is scheduled until the user accepts the in-context offer (Today: „Vor deiner Periode erinnern?“) or turns notifications on in Settings; the „Default“ column applies after that. Detailed copy never contains dates, numbers or symptoms; the lock screen only ever shows the discreet title.

Copy note in settings: "Notifications also appear on your Apple Watch."

---

## 9. Content & voice

### 9.1 Voice
Warm, plain, direct; second person; short sentences. Say "period", "bleeding", "discharge", not euphemisms. Never cute, never alarming, never moralising. Gender-neutral by default; no assumed partner gender; no "ladies".

### 9.2 Patterns
- Uncertainty first-class: "likely", "usually", "in your last 6 cycles".
- Attribute to data: "Based on your records…".
- Suggestions: "Some people find it useful to mention this to a doctor." Never "You should see a doctor" in v1 (deviation nudges are gated).

### 9.3 Banned / restricted terms (v1, all surfaces incl. App Store)
| Term | Rule |
|---|---|
| safe days, infertile days, unsafe days, "can't get pregnant", "low risk", protection, birth control (as a claim) — DE: *sichere Tage, unfruchtbare Tage, „kann nicht schwanger werden“, geringes Risiko, Schutz, Verhütung (als Aussage)* | **Banned everywhere.** Only allowed: "Not for birth control." / „Nicht zur Verhütung geeignet.“ |
| DE: *Diagnose, diagnostizieren, erkennen (bei Erkrankungen), bestätigter Eisprung* | Banned (same rules as EN) |
| fertile, fertile window, ovulation, conceive | Allowed **only** when the fertility flag is on (Conceive mode or Track with "Show fertile window"), always with uncertainty ("likely", "estimated") |
| "confirmed ovulation", "you ovulated" | Banned. Use "ovulation likely occurred around …" |
| % chance of pregnancy | Banned (age-dependent, a claim). Use tiers: Lower chance / Possible / High / Peak |
| "Lower chance" | Conceive mode only; never shown in Track mode; never with a fill colour |
| follicular, luteal | UI uses "before ovulation" / "after ovulation". Technical terms only in the Doctor Visit Pack (clinician audience) |
| diagnose, detect, screen, "signs of PCOS/endometriosis" | Banned |
| irregular | Only in user-chosen condition labels or as a neutral description of the user's own data ("your cycles vary"), never as a verdict |
| accurate / % accuracy | Banned until validated (§12.2) |
| AI / smart | Allowed in v1.5 only to describe phrasing ("summaries written on your iPhone") |

A CI lint scans `Localizable.xcstrings` and App Store metadata for banned terms (§12.1).

### 9.4 Glossary (DE is the source language; EN translation), to be reviewed by a DE-speaking clinician
Address the user with informal **„du“** throughout (gender-neutral, standard for consumer health apps in DACH). Avoid gendered nouns for the user; for clinicians use both forms („deine Gynäkologin oder dein Gynäkologe“).
| EN | DE |
|---|---|
| Period | Periode |
| Bleeding / spotting | Blutung / Schmierblutung |
| Flow: light / medium / heavy | Blutung: leicht / mittel / stark |
| Discharge | Ausfluss |
| Cervical mucus | Zervixschleim |
| Cramps | Krämpfe / Unterleibskrämpfe |
| Pre-period window | Tage vor der Periode |
| Hot flashes / night sweats | Hitzewallungen / Nachtschweiß |
| Perimenopause | Perimenopause (Wechseljahre-Übergang) |
| Hormonal birth control | Hormonelle Verhütung |
| Doctor Visit Pack | Arztbericht |
| Trying to conceive (mode) | Kinderwunsch |
| Egg-white mucus | Spinnbarer Zervixschleim (chip: „Spinnbar“) |
| Fertility tiers: Lower / Possible / High / Peak | Geringer / Möglich / Hoch / Am höchsten |
| Fertile window (estimate) | Fruchtbares Fenster (Schätzung) |
| Ovulation likely occurred | Eisprung wahrscheinlich um den … |
| Tags | Stichworte |
| Nothing else today | Sonst nichts heute |
| Not for birth control. | Nicht zur Verhütung geeignet. |

---

## 10. Accessibility spec
- **Dynamic Type** XS…AX5. Layouts switch to vertical stacks at `.accessibility1+`. The ring shrinks and the number moves above it.
- **VoiceOver:**
  - Ring = one element with full sentence + custom actions.
  - Calendar days: "November 24, period day 2, heavy flow, 3 symptoms logged" / "November 30, predicted period".
  - Rotor: "Period days", "Logged days".
  - Charts: `AXChartDescriptor` + audio graph. Summary sentence: "Cycle lengths over 12 cycles, typically 27 to 31 days, shortest 25, longest 34."
  - Pain map: list fallback.
- **Voice Control:** every chip has a visible text label = spoken name.
- **Colour:** tokens per §5.1; shape/pattern redundancy; tested with Sim Daltonism (protan/deutan/tritan) and Increased Contrast.
- **Motion:** §5.6. **Transparency:** §5.4.
- **Targets:** ≥ 44×44 pt; FlowPicker 56 pt.
- **Cognitive:** one primary action per screen; no time-limited UI except the 5 s undo toast (also announced, with undo also available in the day detail).
- **Audit:** full VoiceOver pass per release; Accessibility Inspector; Voice Control script for the logging flow.

---

## 11. Technical design

### 11.1 Stack & modules
Swift 6 (strict concurrency), SwiftUI, Observation, SwiftData, HealthKit, WidgetKit, App Intents, Swift Charts, LocalAuthentication, CryptoKit + CommonCrypto (PBKDF2), UserNotifications. Foundation Models from v1.5. No third-party runtime dependencies. Test-only: `swift-snapshot-testing`.

```
Cyclus.xcodeproj
├── Cyclus (app)            UI, navigation, intents (single writer)
├── CyclusWidgets (ext)     Reads widget snapshot only
└── Packages/
    ├── CycleCore           LocalDate, value types, episodes. Pure
    ├── CycleEngine         Cycle detection, prediction, stats, (gated) temp shift. Pure
    ├── CycleInsights       Pattern engine (v1.5), templates, FM phrasing
    ├── CycleStore          SwiftData schema (versioned), repository, pending-event queue drain
    ├── CycleHealth         HealthKit adapter + sync state machine
    ├── CycleReport         Doctor Visit Pack renderer
    ├── CycleBackup         .cyclus format, crypto, CSV
    └── CycleDesign         Tokens, components
```

### 11.2 Process & storage layout
- Main store: `Application Support/Cyclus.store` in the **app container** (not the App Group). File protection `.complete` set via the `com.apple.developer.default-data-protection` entitlement = `NSFileProtectionComplete`, plus an explicit `FileManager.setAttributes` on the `.store`/`-wal`/`-shm` files at creation (verified in a test).
- App Group `group.<bundle>.shared`:
  - `widget-snapshot.json` (`.completeUntilFirstUserAuthentication`): `{cycleDay, status, nextRange:{lo,hi}, confidenceTier, redactionPreference, generatedAt}`.
  - `pending-events.jsonl` (`.complete`): `{id, type, localDate, createdAt}`. Drained by the app on `scenePhase == .active` and on launch; idempotent by `id`.
- Only the app process writes the store. Intents are defined in the app target so they run in-process.

### 11.3 Data model (CycleStore, schema v1)
```swift
struct LocalDate: Codable, Hashable, Comparable, Sendable { // civil date, no time zone
    var year: Int; var month: Int; var day: Int
}

@Model final class DayLog {
    #Unique<DayLog>([\.date])
    var date: LocalDate
    var flow: Flow?                  // none, spotting, light, medium, heavy, unspecified
    var symptoms: [SymptomEntry]     // id, severity?
    var pain: [PainEntry]            // region, score 0...10
    var mood: Int?                   // 1...5
    var emotions: [Emotion]          // irritable, sad, anxious, sensitive, calm, happy (+ user-defined later)
    var libido: Int?                 // 1...5
    var energy: Int?                 // 1...5
    var sleepQuality: Int?           // 1...5
    var discharge: Discharge?        // superset of HK cervical mucus
    var ovulationTest: LhResult?     // negative, positive, surge (high/estrogen). Added 2026-11-22: used by the fertility engine and the prototype
    var sex: SexEntry?               // protected: Bool?
    var bbt: Measurement<UnitTemperature>?
    var tagIDs: [UUID]
    var note: String?
    var observedComplete: Bool       // "Nothing else today"
    var updatedAt: Date
}

@Model final class CycleOverride { var date: LocalDate; var kind: Kind } // .isStart / .notStart / .exclude(reason)
@Model final class Episode { var id: UUID; var kind: EpisodeKind; var start: LocalDate; var end: LocalDate? }
@Model final class Tag { var id: UUID; var name: String; var symbol: String?; var archived: Bool }
@Model final class HealthLink {      // local ↔ HealthKit
    var localKey: String             // "\(date)|\(field)"
    var syncIdentifier: String       // "cyclus:\(localKey)"
    var syncVersion: Int
    var state: State                 // .synced, .pendingWrite, .pendingDelete, .removedInHealth
}
@Model final class ExternalSample {  // read-only, from other sources
    var hkUUID: UUID; var field: Field; var date: LocalDate
    var value: Int?; var sourceBundleID: String; var sourceName: String
    var isCycleStart: Bool?
}
@Model final class Profile { var birthYear: Int?; var unitsTemperature: UnitChoice; var preferences: Data }
```
- Cycles are **derived**, not stored (recomputed in < 50 ms for 10 years; cached in memory).
- Schema versioning via `VersionedSchema` + `SchemaMigrationPlan` from day one. Every schema change ships with a migration test (§12.1).

### 11.4 HealthKit sync (CycleHealth)

#### 11.4.1 Rules
1. **Ownership.** A sample is *own* if its `sourceRevision.source.bundleIdentifier == ours` **and** its metadata has `HKMetadataKeySyncIdentifier` with the prefix `cyclus:`. Everything else is *external*, stored as `ExternalSample`, read-only, and shown with a source badge.
2. **Write-own.** Only for categories the user enabled. On create/edit: save a sample with `HKMetadataKeySyncIdentifier = syncIdentifier`, `HKMetadataKeySyncVersion = ++syncVersion`, `HKMetadataKeyTimeZone`. HealthKit replaces lower versions with the same identifier, so edits need no manual delete. On local delete: delete own samples matching the sync identifier predicate.
3. **Day mapping.** A day sample spans local midnight → next midnight in the zone of logging and carries `HKMetadataKeyTimeZone`. On import, use the sample's time-zone metadata if present, else the current zone. Day = civil date of the start.
4. **Precedence per (day, field):** Cyclus user entry > Apple Health / Apple Watch entry > other apps. Lower-precedence values are kept and shown in day detail but not counted. **Never double-count flow.**
5. **Cycle start flag.** On write, `HKMetadataKeyMenstrualCycleStart` (required) = Cyclus's detected start (§11.5.1). On import, an external start flag ranks above Cyclus's heuristic and below the user override.
6. **Deletion of own samples in Health** (seen via the anchored query's `deletedObjects`): keep local data and set `HealthLink.state = .removedInHealth`. Show a non-blocking review in Settings: "3 entries were removed in Apple Health — Keep in Cyclus only / Remove from Cyclus too". Never auto-rewrite.
7. **Reinstall / recovery.** Own samples (prefix match) found on import can be reconstructed as local `DayLog` fields, which restores data after reinstall for mirrored categories. Only into an empty store and **only after the person agrees** („Frühere Einträge gefunden … In Cyclus übernehmen / Nicht übernehmen“): after an erase that kept the Health Connect records, reconnecting must never silently bring erased entries back.
8. **Permissions.**
   - Read set requested at import.
   - Write per category, requested when toggled.
   - Read denial is undetectable, so the UX shows help text only.
9. **Import.** All history, batched by year with progress; `HKAnchoredObjectQuery` anchors persisted per type; `HKObserverQuery` + `enableBackgroundDelivery(.immediate)` for flow and symptoms, deferred until unlocked.

**Android notes (2026-11-22):** values from health stores fill only fields the person left empty and are listed separately with their source in the day sheet; Cyclus writes only the person's own entries; reconstruction from own records (rule 7) only into an empty store; sync only while the app is open. These apply to the iOS implementation as well.

#### 11.4.2 Field mapping
**Owner decision 2026-11-22 (both platforms): every mirror (write) category starts off; the user switches each on in the Privacy Center. The defaults below are the earlier plan and apply only as the suggested order of the switches.**

| Cyclus field | HealthKit | Mirror default (superseded: all off) | Loss / note |
|---|---|---|---|
| flow light/medium/heavy/unspecified | `menstrualFlow` (`.light/.medium/.heavy/.unspecified`) + `HKMetadataKeyMenstrualCycleStart` | On | — |
| flow spotting | `intermenstrualBleeding` | On | Spotting is never written as `menstrualFlow` |
| symptoms (severity) | Matching symptom category types, `HKCategoryValueSeverity` | On | Cyclus-only symptoms (custom) not mirrored |
| moodChanges / sleepChanges (HK presence types) | Imported as symptom presence | — | Import-only mapping |
| mood (1–5) + emotions | `HKStateOfMind` (dailyMood, valence mapped −1…+1; emotions → `HKStateOfMind.Label` where one exists, e.g. irritated, sad, anxious, calm, happy) | **Off** | „Empfindlich“ has no HK label → Cyclus only |
| libido | — | Not mirrored | Cyclus only |
| energy, sleepQuality, pain map, tags, notes | — | Not mirrored | Shown in Privacy Center as "Cyclus only" |
| discharge | `cervicalMucusQuality` (dry/sticky/creamy/watery/eggWhite) | Off | Cyclus-only options (e.g. "unusual") not mirrored |
| LH test | `ovulationTestResult` (negative/LHSurge/estrogenSurge/indeterminate) | Off (Conceive status is sensitive; turning it on improves Apple Health's own estimates) | — |
| sex | `sexualActivity` + `HKMetadataKeySexualActivityProtectionUsed` | **Off** | — |
| BBT | `basalBodyTemperature` | On | — |
| wrist temperature | `appleSleepingWristTemperature` | Read only | Treated as raw series; baseline computed by Cyclus |
| episodes | `contraceptive`, `pregnancy`, `lactation` | **Off** | Episode kinds without an HK type are Cyclus only |

iOS 27 SDK review item (phase 2): new perimenopause/menopause-related identifiers → add to mapping with `#available`.

### 11.5 Prediction engine (CycleEngine)

#### 11.5.1 Cycle detection
- Bleeding day = `flow ∈ {light, medium, heavy, unspecified}`. Spotting alone never starts a period.
- **Precedence per candidate start day:** user override (`isStart`/`notStart`) > external `HKMetadataKeyMenstrualCycleStart` > heuristic.
- **Heuristic:** a bleeding day starts a new period if the previous bleeding day is ≥ 5 days earlier **and** the previous start is ≥ 14 days earlier. If the gap is ≥ 5 days but the start is < 14 days earlier, it becomes "bleeding between periods" plus a gentle prompt in day detail: "Is this a new period?"
- Period end = last bleeding day before ≥ 2 non-bleeding days.
- Cycles overlapping a suppressing episode (§2.2) are tagged and excluded from fitting.

#### 11.5.2 Model
Work on log length `y = ln(L)` (cycle lengths are right-skewed).
- **Prior (Normal-Inverse-Gamma) by age band.** Values are *provisional*, to be calibrated in back-test and reviewed by the clinician.

  | Band | Prior median L | σ_y prior mean (within-person) | κ₀ | α₀ |
  |---|---|---|---|---|
  | Teen (< 20 or unknown + teen indicated) | 30 | 0.15 | 1 | 2 |
  | Adult 20–39 / unknown | 29 | 0.09 | 1 | 2 |
  | 40+ | 28 | 0.13 | 1 | 2 |
  | Perimenopause episode | 30 | 0.25 | 0.5 | 2 |
  | PCOS condition | 33 | 0.25 | 0.5 | 2 |

  `μ₀ = ln(median)`, `β₀ = σ²_prior · (α₀ − 1)`. σ is the *within-person* spread (≈ 2–3 days for adults), not the population spread (Bull et al. SD 5.2 d includes between-person variation). Changed in v1.0.1 after the prototype showed 0.14 made ranges ~8 days wide for very regular users.
- **Robust, recency-weighted update.** For the last ≤ 12 eligible cycles, the weight `w_i = r_i · t_i`:
  - recency `r_i = 0.5^(age_i / 6)` (half-life 6 cycles);
  - robustness `t_i = (ν + 1) / (ν + z_i²)`, with ν = 4 and `z_i` the standardised residual from the current fit. Iterate 3× (IRLS ≈ Student-t likelihood).
  - `n_eff = Σw_i`.
  - NIG update with weighted sufficient statistics: `κ_n = κ₀ + n_eff`, `μ_n = (κ₀μ₀ + Σw_i y_i)/κ_n`, `α_n = α₀ + n_eff/2`, `β_n = β₀ + ½Σw_i(y_i − ȳ_w)² + κ₀ n_eff (ȳ_w − μ₀)²/(2κ_n)`.
- **Posterior predictive:** `y* ~ t_{2α_n}(μ_n, β_n(κ_n + 1)/(α_n κ_n))`. The 80% interval in y is exponentiated to days, giving an **asymmetric** interval in days. Next-start range = last start + [⌊lo⌋, ⌈hi⌉], minimum width 3 days. Most-likely day = last start + round(exp(μ_n)).
- **Confidence tier** (§6): from `n_eff` (< 2 rough, 2–5 getting there, ≥ 6 history).
- **No last start known:** no prediction.

#### 11.5.3 Missed-log detection
For a completed cycle with `L ≥ 1.6 · exp(μ_n)` **and** user logging activity during that cycle ≥ 1 entry per 10 days **and** no suppressing episode:
- Exclude the cycle from fitting provisionally.
- Ask once in Cycle detail: "This cycle was about twice your usual length. Did you have a period around Nov 3 you didn't log?" → [Add period] [No, it was long] [Not sure].
- "No" → include, with robust weighting. "Not sure" → keep excluded, shown hatched.
- L > 90 d → always excluded ("long gap"), shown in history.

#### 11.5.4 Status logic
| Condition | Status |
|---|---|
| In period | `.inPeriod(day)` |
| today < lo − 7 | `.between` |
| lo − 7 ≤ today < lo | `.prePeriod` |
| lo ≤ today ≤ hi | `.expectedNow` |
| today > hi | `.laterThanUsual` (perimenopause/PCOS: `.between` with wording "No period logged for N days") |
| Suppressing episode | `.paused(reason)` |

#### 11.5.5 Pre-period window
Displayed as 7 days before the predicted **lower bound**. This is a calendar convenience, not a physiological claim; copy: "the days before your period".

### 11.6 Temperature (display + shift detection feeding §11.11)
- **v1:** display BBT (user-logged) and wrist temperature (read). Wrist values are plotted as **deviation from a Cyclus-computed baseline**, defined as the rolling median of nights −35…−6 with ≥ 10 nights present. This works whether HealthKit returns absolute values (as the developer docs indicate) or relative ones, because subtracting a baseline is idempotent for our purpose. Gaps are shown as gaps. No markers or inference.
- **Shift detection (feeds §11.11 as likelihoods, never as hard truth):**
  - **BBT (Sensiplan-style):** a shift is confirmed when 3 consecutive readings are each higher than the highest of the 6 preceding readings, and the 3rd is ≥ 0.2 °C above that maximum. Apply the Sensiplan exception rules (verify against the official Arbeitsgruppe NFP handbook before implementation).
  - **Wrist:** an own detector on baseline-corrected deviations (persistent elevation ≥ 0.25 °C for ≥ 3 of 4 nights after ≥ 5 of 7 lower nights), labelled "possible temperature shift". Never fed into period prediction until back-tested on real wrist data.
  - **LH:** ovulation typically 24–48 h after the *first* positive.

### 11.7 Pattern engine — statistical core (used by „Deine Muster“, §11.12)
- **Observed day** = any day with `observedComplete == true`, **or** with ≥ 3 logged fields across ≥ 2 sections (heuristic proxy, documented). The denominator is observed days only; symptom absence on non-observed days is *missing*.
- **Windows (no ovulation dependence):** `P` = period days; `PRE` = days −7…−1 before next start (retrospective); `MID` = the rest.
- **Unit of analysis = cycle**, which handles within-cycle autocorrelation. For symptom s, window W, cycle c: `k_cW` hits out of `n_cW` observed days.
- **Model (single Bayesian paradigm):** hierarchical beta-binomial per (s, W) with a cycle-level random effect, fitted by a moment-matching/empirical-Bayes approximation (closed form, on-device).
  - Quantity: risk ratio `RR = p_W / p_rest`, posterior via 4,000 Monte Carlo draws.
  - Eligibility: ≥ 2 cycles with `n_cW ≥ 2` and `n_c,rest ≥ 4`.
  - **Tiers:**
    - *Early signal:* ≥ 2 eligible cycles, P(RR > 1.25) ≥ 0.85, same direction in ≥ 2/2 cycles.
    - *Consistent pattern:* ≥ 4 eligible cycles, P(RR > 1.5) ≥ 0.9, same direction in ≥ 75% of cycles.
  - Ranking: by the posterior 10th percentile of RR. Show at most 3 insights.
  - No thresholded p-values and no BH.
- Also computed: cycle-length trend over ≥ 8 cycles (posterior slope from the §11.5 model with a time covariate; reported only if the 80% CI excludes 0, as "trending shorter/longer"); HealthKit sleep/RHR by window (v1.5).
- **Release gate:** null simulation (symptoms independent of window, realistic sparsity 20–60% observed days, 2–12 cycles, 30 symptoms) must yield ≤ 5% of simulated users seeing any *Consistent pattern*, and ≤ 15% any *Early signal*. Recall check on planted effects (RR 2.0) ≥ 70% at 6 cycles.
- Output type:
```swift
struct Insight: Sendable, Codable {
  enum Kind { case symptomWindow, cycleTrend, periodLengthTrend }
  let kind: Kind; let subject: String; let window: Window?
  let effect: Double; let interval: ClosedRange<Double>
  let tier: Tier; let cyclesUsed: Int; let supportingCycleIDs: [UUID]
}
```

### 11.8 On-device language (v1.5)
- **Templates are primary and complete.** Every `Insight.Kind × Tier` has EN/DE templates reviewed by the clinician.
- Foundation Models (if `SystemLanguageModel.default.availability == .available` and the language is supported) **only rephrases** the template output for variety and context. Guided generation: `@Generable struct InsightText { @Guide(description: "≤ 90 chars") var title; @Guide(description: "≤ 2 sentences") var body }`.
- **Inputs:** the template text and structured fields only. Never notes, never raw logs.
- **Post-checks:**
  - every number in the output must appear in the input;
  - banned-terms lint (§9.3, EN/DE);
  - length limits.

  Any failure or `guardrailViolation` → template.
- `contextSize` read only under `if #available(iOS 26.4, *)`, else assume 4,096. Prompt budget ≤ 1,000 tokens.
- **AUP compliance note** (Apple FM acceptable-use, medical domain): the model never generates medical claims; it paraphrases pre-approved, data-grounded text. Documented in App Review notes.

### 11.9 Backup, export, end-of-life
- **`.cyclus` format v1** (normative: `docs/export/FORMAT.md`, revised 2026-11-22): `magic "CYCLUS1" | mode(1) | salt(16) | iterations(u32 BE) | nonce(12) | AES-256-GCM(raw DEFLATE(JSON)) + tag(16)`, **AAD = the 40 header bytes**. Mode 1 = passphrase, key = PBKDF2-HMAC-SHA256 (CommonCrypto `CCKeyDerivationPBKDF`), 600,000 iterations; mode 2 = raw 256-bit key (device backups). Compression is raw DEFLATE (Apple `COMPRESSION_ZLIB`). JSON per `docs/export/cyclus-export.schema.json` v1; user exports never contain app-lock settings. Test vectors in `shared/golden/export/`; reference decoder `tools/export/cyclus_decode.py`.
- **CSV:** one row per day, documented columns.
- **The export schema is published** on the support site under CC-BY. End-of-life pledge: if Cyclus is discontinued, a final update keeps export working, and the schema + a reference decoder stay public.
- **Backup inclusion:** per D5. The "Device only" toggle sets `URLResourceValues.isExcludedFromBackup` on the store files.

### 11.10 Privacy manifest
`NSPrivacyTracking = false`, `NSPrivacyTrackingDomains = []`, `NSPrivacyCollectedDataTypes = []`. Required-reason APIs:
- UserDefaults `CA92.1` (app) + `1C8F.1` (App Group);
- File timestamp `C617.1`;
- Disk space `E174.1` (if a storage check is used);
- System boot time — audit via an Xcode privacy report; declare if triggered by dependencies.

App Store privacy label: **Data Not Collected** (optional user-initiated feedback mail is not "collection" by the app; re-verify at submission).

### 11.11 Fertility engine (CycleEngine/Fertility, D1 = in)

**Goal.** For every day in the current cycle, compute:
- the probability it is inside the fertile window;
- a ranked "best days" list;
- an ovulation-day estimate with uncertainty.

Every new data point updates them, and each finished cycle personalises the model. "Intelligent" here means **multi-signal evidence fusion + personal calibration + honest uncertainty**, not calendar arithmetic.

#### 11.11.1 Physiology the model encodes
- The fertile window is the **6 days ending on ovulation day** (Wilcox et al., NEJM 1995). Per-day conception chance (intercourse on that day only) is ≈ 0.10, 0.16, 0.14, 0.27, 0.31, 0.33 for days −5…0. Only the **relative** shape is used, never shown as %.
- Ovulation timing varies a lot between and within people. Only ~30% have the window entirely within cycle days 10–17 (Wilcox, BMJ 2000). Hence a distribution, not a date.
- After ovulation (luteal phase) is **less variable within a person** than before ovulation. Population mean 12.4 ± 2.4 d (Bull et al. 2019). Learning the personal luteal length is the biggest accuracy gain.
- Signals:
  - urinary LH surge precedes ovulation (~24–48 h after the first positive);
  - fertile-quality cervical mucus (watery / egg-white) appears in the days before ovulation; "peak day" ≈ ovulation ±2;
  - basal temperature rises after ovulation (retrospective);
  - wrist skin temperature and resting heart rate rise in the luteal phase (weaker, noisier).

#### 11.11.2 State & prior
- Hidden state per cycle: `O ∈ {6 … D_max}` (cycle day of ovulation, `D_max = max(60, ⌈1.5 × predicted length⌉)`) **plus** `A` = anovulatory cycle.
- **P(A) prior**:

  | Context | P(A) |
  |---|---|
  | Adult | 0.05 |
  | Teen / < 2 yrs since first period | 0.15 |
  | First 3 cycles after hormonal contraception or birth | 0.15 |
  | 40+ | 0.10 |
  | Perimenopause | 0.25 |
  | PCOS | 0.30 |

  Provisional; clinician review + back-test.
- **Prior over O:**
  - **(a)** If ≥ 2 past cycles have a *good* ovulation estimate (80% HPD ≤ 3 days and temperature- or LH-supported): log-normal NIG model (as §11.5.2) on *follicular length* `F = O`. Posterior predictive → discrete pmf.
  - **(b)** Otherwise, `O = L* − Lut` (luteal length defined as days after ovulation until the next period starts), with `L*` ~ the cycle-length posterior predictive (§11.5) and `Lut` ~ personal luteal predictive (prior N(12.4, 2.4²) truncated to 7…17; NIG update from good past cycles). Discrete convolution.
  - **(c)** No cycle history: population prior from (b) with the age-band cycle prior. Labelled "Calendar estimate — very uncertain".

#### 11.11.3 Evidence likelihoods `P(observation | O = d)`
Applied to today's cycle on every recompute. `t` = cycle day of the observation.

| Signal | Likelihood model (provisional, personalised over time) |
|---|---|
| **LH positive (first of a run)** | Lag `k = d − t`: P(k = 0) 0.15, P(1) 0.45, P(2) 0.30, P(3) 0.07, else 0.03 spread. Later positives in the same run add no extra evidence |
| **LH negative** | If `d − t ∈ {1, 2}` (the surge should have been visible) → 0.20 (missed or short surges happen); else 0.95 |
| **LH "estrogen surge" / high** (HK `.estrogenSurge`) | `d − t ∈ 1…5` → 0.8; else 0.2 |
| **Mucus egg-white / watery on day t** | `t ∈ [d−5, d]` → 0.8; `t ∈ {d+1, d+2}` → 0.4; else 0.1 |
| **Mucus dry / sticky on day t** | `t ∈ [d−2, d]` → 0.2; else 0.8 |
| **Mucus peak day** (last fertile-quality day, identified ≥ 1 day later) | `d − peak ∈ {−1, 0, +1, +2}` → 0.2, 0.35, 0.3, 0.1; else 0.05 spread |
| **BBT shift** (Sensiplan, §11.6; first high reading on day h) | `d ∈ {h−3, h−2, h−1, h}` → 0.1, 0.3, 0.45, 0.15; **no-shift evidence:** if ≥ 6 valid lows after day `d+2` → 0.1 |
| **Wrist-temp shift** (§11.6 detector; first elevated night h) | Flatter: `d ∈ {h−4 … h}` → 0.1, 0.2, 0.3, 0.3, 0.1. **Used only if no BBT that cycle** (avoid double counting) |
| **Resting HR rise** (v1.5) | Likelihood ratio capped at 1.5, only as a tiebreaker |
| **Positive pregnancy test** | Ends fertility outputs; suggests Pregnancy episode |

- **Conditional independence** given O is assumed, except that BBT and wrist are mutually exclusive as above, and repeated same-type observations within 1 day collapse to one.
- **Anovulation likelihood:** `P(obs | A)`:
  - LH negatives → 0.95;
  - no temperature shift for the whole cycle → high;
  - fertile mucus → 0.4 (it occurs in anovulatory cycles too).

#### 11.11.4 Posterior & outputs
`post(d) ∝ prior(d) · Π likelihoods`, normalised jointly with `A`.

- **P_fertile(t)** = Σ_d post(d) · 1[t ∈ [d−5, d]], the probability that day t is in the window.
- **Conception weight** `c(t) = Σ_d post(d) · w(t−d)` (relative Wilcox shape) → used for **Peak** days and "best days".
- **Tiers** (thresholds calibrated in back-test):
  - **Peak:** P_fertile ≥ 0.6 **and** c(t) within top 2 days of the cycle;
  - **High:** ≥ 0.5, **or** the MAP ovulation day and the 2 days before it when P_fertile ≥ 0.3 (owner feedback 2026-11-22: with an uncertain estimate the ≥ 0.5 days stopped two days short of the ◎ marker, which read as a contradiction; this adds at most 3 days, largest high window in the golden set 7 days);
  - **Possible:** 0.15–0.5;
  - **Lower chance:** < 0.15 (shown in Conceive mode only, unfilled).
- **Window band** = contiguous days with P_fertile ≥ 0.15. Its width *is* the uncertainty: it narrows visibly as evidence arrives.
- **Ovulation estimate:** MAP day + 80% HPD. Status:
  - *Predicted*: prior-dominated;
  - *Likely soon*: LH positive or fertile mucus, and HPD ≤ 4 d;
  - *Likely occurred*: temperature shift or day after peak + HPD ≤ 3 d.
- **Next-period refinement:** once *likely occurred* and the back-test gate has passed, `L* = O + Lut` (personal luteal predictive), mixed with the §11.5 prediction by HPD width. The interval only ever narrows when evidence supports it.
- **Anovulation copy:** if P(A) > 0.5 and today > personal 95th-percentile O + 3: "No sign of ovulation yet this cycle. That happens sometimes, especially with [context]." Never diagnostic.
- **Explanations:** every output carries an `EvidenceTrail` (ordered list of `(signal, date, effect: .narrowed/.shiftedEarlier/.shiftedLater/.supported)`). This powers "Why this window?" (§7.14).

#### 11.11.5 Personal learning (on cycle close)
For each closed cycle with a *good* ovulation estimate:
1. Update the follicular-length and luteal-length NIG posteriors (recency-weighted, robust; same machinery as §11.5).
2. Update the **personal LH→O lag** distribution (Dirichlet over lags 0…3, prior = the table above × 10 pseudo-counts).
3. Update the **mucus-peak→O offset** the same way.
4. Update the personal P(A) (Beta prior from the band, counting cycles judged anovulatory with high confidence).

Learning is per user, on device, deterministic, and fully reproducible from the logs (no stored model weights to migrate).

#### 11.11.6 Smart assistance (Conceive mode)
- **LH-test planner:** recommended start = first day `t` where P(O ≤ t + 2) ≥ 0.10. Testing continues daily (twice daily optional if P(O ∈ [t+1, t+2]) ≥ 0.3) until the first positive or a temperature shift. Shows: "Start testing **Thu, Nov 6** · usually 4–6 tests this cycle", with expected tests computed from the posterior. Reminder at a user time (default 14:00, per typical test instructions).
- **Best days:** the top 3 days by `c(t)`, plus clinician-reviewed guidance: "Sex every 1–2 days during High and Peak days gives the best chance."
- **Pregnancy test timing:** "Earliest reliable test: around **Nov 28**" = ovulation estimate + 14 or the predicted period lower bound, whichever is later.
- **Time-to-pregnancy context** (educational, clinician-reviewed): after 12 months of trying (6 months if 35+), "many people talk to a fertility specialist". Shown once, dismissible, framed as general information.
- **Doctor Visit Pack (Conceive section):** cycles tracked while trying, ovulation estimates with ranges, luteal lengths, first positive LH days, temperature charts. Neutral presentation; short luteal phases are listed without labels.

#### 11.11.7 Safety rules
- **Not birth control:** banned terms (§9.3); no "Lower chance" in Track mode; no fill on lower-chance days; a persistent "Not for birth control" footer on fertility surfaces.
- If onboarding or settings indicate "avoiding pregnancy", show a one-time explainer: Cyclus can't be used for that; talk to a clinician about methods.
- **Suppressed** when: a hormonal-contraception episode is active; pregnancy; postpartum/breastfeeding until 2 natural cycles; gender-affirming testosterone (unless the user enables it); the region flag is off.
- **Irregular / PCOS / perimenopause:** prior (c) widened, P(A) raised. The calendar-only window is labelled "Very uncertain — LH tests or temperature help a lot".

#### 11.11.8 Validation (feeds the regulatory clinical evaluation)
| Metric | Reference | Target (provisional) |
|---|---|---|
| Ovulation-day error | First positive LH + 1 day; or Sensiplan BBT (day before first high) | ≥ 80% of cycles within ±2 days once LH or temperature is present (cf. Apple IFU endpoint) |
| Calendar-only ovulation error | same | Reported honestly (expected ±3–4 d) |
| Window coverage | Reference window (ref O −5…0) | ≥ 90% of reference fertile days inside the displayed band |
| Window width | — | Median ≤ 9 days calendar-only, ≤ 6 with LH/temperature |
| Calibration of P_fertile | Reference membership | Reliability-diagram slope 0.8–1.2; Brier score reported |
| Null/robustness | Synthetic anovulatory and noisy cycles | No *Likely occurred* in ≥ 95% of simulated anovulatory cycles |

Runs via the on-device back-test (§12.2), aggregate-only. Any public claim needs the regulatory clinical evaluation first.

#### 11.11.9 Performance
The posterior over ≤ 60 days × ≤ 40 observations is < 1 ms. The full recompute on every log save is trivial.

---

### 11.12 „Deine Muster“ — pattern watching (v1, D9)
User-facing name: **„Deine Muster“** / "Your patterns". (Rejected: „Musterwache“, which sounds odd in German.)

**Principle.** Cyclus watches a fixed catalogue of patterns. Each watcher has a minimum amount of data, a statistical test and pre-written wording. A result is shown only if it repeats across cycles. Every result is a *description of the user's own records* with its evidence ("Warum?"), never a diagnosis.

**Status per watcher:**
- *Deutliches Muster* (consistent);
- *Erstes Signal* (early);
- *Stabil* (watched, nothing changes: also good news);
- *Kein Muster*;
- *Braucht mehr Daten*.

Watchers without a pattern stay visible under „Wird beobachtet“, so the user sees what is being checked.

**Windows** (no ovulation needed, so this also works in Track mode):
- **P** = period days;
- **PRE** = 7 days before the next start;
- **MIDC** = cycle days L−16…L−11 ("um die Zyklusmitte");
- **REST**.

In Conceive mode with good ovulation estimates, MIDC can be replaced by an ovulation-relative window (v1.1).

| Area | Watcher | Data | Method | Minimum | Category |
|---|---|---|---|---|---|
| Körper | Symptom × window (P / PRE / MIDC), best window per symptom, top 6 | symptoms, observed days | §11.7 cycle-unit beta-binomial risk ratio; consistent: ≥ 4 cycles, P(RR>1.5) ≥ 0.9, same direction ≥ 75%; early: P(RR>1.25) ≥ 0.85, direction ≥ 75% | 2 cycles with ≥ 2 window + ≥ 4 other observed days | A (wellness) |
| Stimmung & Gefühle | Feelings (gereizt, traurig, ängstlich, empfindlich, ausgeglichen, glücklich) × window | emotions | as above | as above | A |
| Stimmung & Gefühle | Mood / energy / sleep quality / libido × window | 1–5 scales | per-cycle mean difference window vs rest; consistent: ≥ 4 cycles, abs. diff ≥ 0.5, same sign ≥ 75% | 2 cycles | A |
| Lebensstil | Tag → symptom/feeling **on the next day** (e.g. "Kopfschmerzen am Tag nach Alkohol") | tags | pooled risk ratio with design effect 1.2; stricter threshold P(RR>1.5) ≥ 0.9 (many combinations), plus **leave-one-cycle-out**: still P(RR>1.25) ≥ 0.8 without any single cycle. Top 2 | ≥ 6 exposure days in ≥ 3 cycles | A |
| Lebensstil | Stress → cycle length | tags, lengths | ≥ 3 stress days in first 14 days = exposed; early: mean difference ≥ 2 days with ≥ 2 exposed cycles; consistent: ≥ 3 exposed, all longer than every unexposed | 2 + 2 cycles | A |
| Fruchtbarkeit | Ovulation day range, luteal length, LH→ovulation lag, mucus early-signal offset, stress → later ovulation, temperature rise present | retrospective per-cycle posteriors (§11.11) | only *good* cycles (80% HPD ≤ 3 days, LH or temperature supported). Ovulation range = shortest window covering ≥ ⅔ of cycles | 2 good cycles | B (device scope) |
| Zyklusgesundheit | Cycle length trend, strongest period pain trend, period length | lengths, pain map | OLS slope with t-based 80% interval; shown as a trend only if the interval excludes 0, otherwise "stabil" | 6 cycles | C (device scope, neutral) |

**Wording & safety rules:**
- Category **A** text may suggest nothing beyond self-knowledge.
- **B** follows the fertility copy rules (§9.3).
- **C** is strictly neutral ("gestiegen von etwa 3 auf 7"). It is **never pushed** as a notification, it is marked "Im Arztbericht enthalten", and it is listed in the Doctor Visit Pack.
- No watcher says "abnormal", "zu kurz" or names a condition.
- Every pattern card has „Warum?“ (method, number of cycles, probability) and „Ausblenden“. Hidden patterns stay hidden until the user shows them again; local only.
- Correlation copy always includes „Ein Zusammenhang ist kein Beweis für eine Ursache.“

**Release gates (CI, extends §11.7):**
- Null simulation per watcher type with realistic logging sparsity: ≤ 5% of simulated users get any *Deutliches Muster*, ≤ 15% any *Erstes Signal*.
- The tag watcher is simulated with all 5 default tags × all items.
- Recall ≥ 70% for planted effects of RR 2 at 6 cycles.

**Where it appears:**
- Insights › „Deine Muster“: pattern cards, then „Wird beobachtet“.
- Today: a link "N Muster gefunden".
- Doctor Visit Pack: an optional section „Beobachtete Muster (selbst erfasst)“, C first.
- No push notifications in v1. An optional weekly digest comes in v1.5.

### 11.13 „Ausblick“ — 7-day outlook (v1, D9)
Turns the patterns that passed §11.12 into a forecast for today + 6 days, so patterns become **predictions** the user can plan around ("Schlechterer Schlaf heute · Krämpfe ab Di.").

- **Profiles:** for every item or scale with a pattern, learn a per-person day profile from past cycles:
  - bins **P0…P6** (period days);
  - **R−14…R−1** (days before the next start);
  - **X** (earlier).

  Each bin = (hits + 2·overall rate) / (observations + 2), smoothed 50/25/25 with its neighbours.
- **Timing uncertainty:** for each future day, mix over the posterior-predictive distribution of the next period start (§11.5). So the outlook is sharp for tomorrow and soft for day 6, by construction and not by decoration.
- **Inclusion:**
  - an item appears on a day if p ≥ 0.30 **and** p ≥ 1.5 × the person's overall rate;
  - a scale appears if the expected value differs from the person's mean by ≥ 0.5 in the pattern's direction;
  - only items backed by a „Deine Muster“ result are eligible (no forecast without an explainable pattern).
- **Lifestyle carry-over:** if yesterday (or today) has a tag with a next-day pattern, today/tomorrow show the item with the tag's observed rate and the reason („nach „Alkohol““).
- **Fertility:** in Conceive mode the fertile window stays in its own cards (§7.14); the outlook covers body and mood items only.
- **UI (Today card):**
  - a two-line summary (first two rows, "ab Do.");
  - a compact grid of 7 day columns × ≤ 5 rows, darker = more likely;
  - a footnote with the number of cycles and "Je weiter weg, desto unsicherer";
  - VoiceOver reads the summary sentences.
  - Hidden when predictions are paused or there are < 2 complete cycles.
- **Validation:** on-device back-test (§12.2) adds Brier score and calibration of outlook probabilities for day +1 and day +6, aggregate only.

## 12. Quality & validation

### 12.1 Testing
| Layer | Approach | Where it runs |
|---|---|---|
| CycleCore / Engine / Insights | Swift Testing unit + property-based (fuzzed logs, time zones, DST, leap days); ≥ 90% coverage | Every push (Xcode Cloud, small) |
| Pattern null-sim & recall | §11.7 gate | Nightly / local |
| Store migrations | Fixture stores for every schema version → migrate → assert | Every push |
| HealthKit sync | Integration tests on a **physical device** (write/edit/delete, external samples, reinstall recovery, time-zone shift) | Pre-release checklist |
| Locked-device behaviour | Manual checklist: widget tap while locked, intent auth, pending-queue drain, notification copy | Pre-release |
| UI snapshots | `swift-snapshot-testing`, key screens × light/dark × {L, AX3} × {EN, DE} | Local / nightly (protects 25 h Xcode Cloud quota) |
| UI tests | Onboarding, log, export/import round trip, erase | Nightly |
| Lints | Banned terms (§9.3); forbidden imports `Network`, `WebKit`; no `URLSession`/`AsyncImage`/`WKWebView`; no `Link` to non-allowlisted URLs | Every push |
| Network audit | App Privacy Report on device after full UI-test run → zero domains | Release checklist |
| Accessibility | §10 audit | Release checklist |

### 12.2 Prediction validation (no raw-data collection)
- **Synthetic** data = regression tests only, not accuracy evidence.
- **Retrospective on-device back-test:** a hidden screen in TestFlight builds replays the user's own history sequentially (predict cycle i from cycles < i). It shows **only aggregates**: coverage of the 80% interval, MAE of the most-likely day, n cycles. Testers may voluntarily type the three numbers into a feedback form (consent text: aggregate numbers only). The developer never receives logs.
- Targets: coverage 72–88% (calibration, not maximisation); report the MAE distribution.
- **No accuracy claims** in the App Store or marketing (guideline 1.4.1).

### 12.3 Performance budgets (baseline device: iPhone 12, iOS 26)
- Cold start → first frame after auth: < 600 ms (excluding Face ID time).
- Log sheet open: < 100 ms; chip toggle persist: < 16 ms main-thread.
- Cycle recompute (10 years): < 50 ms.
- HealthKit import of 10 years: < 20 s with progress UI.
- PDF render 12 cycles: < 1.5 s.

### 12.4 Success metrics (observable by launch + 90 days)
| Metric | Target | Source |
|---|---|---|
| Wedge validation | ≥ 50% of interviewees "would use" per wedge | Phase 1 interviews |
| Logging speed | Median period-start log ≤ 3 s after unlock; 3 symptoms ≤ 6 s | Moderated usability tests (n ≥ 8) |
| SUS score | ≥ 80 | Same sessions |
| Beta retention | ≥ 60% of testers active after 2 cycles | TestFlight sessions |
| Calibration | 72–88% coverage across testers' self-reported aggregates | §12.2 |
| Crash-free sessions | ≥ 99.5% | App Store Connect (opt-in) |
| Rating | ≥ 4.6 once ≥ 30 ratings | App Store |

---

## 13. Delivery plan
Delivery is planned as **ordered phases with exit criteria**, not as effort or time estimates (the app is built AI-assisted; effort numbers don't apply). The only durations in this plan are **outside lead times** that coding speed cannot change.

### 13.1 Phases
| Phase | Content | Exit criteria |
|---|---|---|
| 1 Validate | Prototype + wedge interviews (§1.2); start legal entity, D-U-N-S, organization accounts; request regulatory qualification opinion; name clinician reviewer | Wedges confirmed or cut; reviewer agreed |
| 2 Foundations | Scaffold, CI, lints (banned terms, no network), design tokens & components; iOS 27 SDK HealthKit review | Green CI; component gallery |
| 3 Core | Core model + engines (§11.5) + store with migrations + pending queue | Engine tests pass; migration test harness |
| 4 Core screens | Today, Log sheet (incl. pain map, feelings, libido), Calendar | Usability test #1 (n = 5): period start logged ≤ 3 s |
| 5 Health sync | HealthKit import, write-own, sync state machine (§11.4) | Device integration checklist passes |
| 6 Intelligence | Fertility engine + Conceive UI (§11.11, §7.14); „Deine Muster“ + „Ausblick“ (§11.12–11.13) | Property tests, null-simulation and recall gates pass |
| 7 Completeness | Insights, onboarding, episodes, Privacy Center, lock/PIN, backup/export, Doctor Visit Pack, widgets, notifications | **Internal TestFlight** (incl. on-device back-test screen) |
| 8 Readiness | DE/EN localisation review, accessibility audit, website & legal docs, regulatory technical file (traceability, risk register, IEC 62304/62366 docs) | Audit checklists pass; regulatory opinion received; clinician review of copy, PDF, templates, fertility explainer |
| 9 Beta & release | External TestFlight (Beta App Review) for **≥ 2 cycles**; back-test aggregates; store assets; submission (fertility flag per region) | Metrics §12.4 + §11.11.8; privacy report clean |

**Outside lead times** (start in phase 1, run in parallel):
- legal entity + D-U-N-S: ~4–8 weeks;
- regulatory qualification opinion: ~4–8 weeks;
- clinician review turnaround;
- the beta needs **at least two real menstrual cycles**;
- App Review / Beta App Review.

**Fallback scope** (only if validation or regulatory timing demands it):
- move the pain map to v1.1;
- drop the temperature chart;
- keep the episodes UI to basic toggles;
- drop the Lock Screen widget;
- ship the Doctor Visit Pack as a fixed template.

The pain map is part of wedge 2, so drop it only if interviews don't confirm that wedge.

### 13.2 Budget
Apple Developer (Organization) €99/yr · FDA establishment registration fee if US fertility launch (~US$10k/yr, verify) · EU MDR costs per classification (Class I self-declaration: mainly own effort; IIa: notified body, five figures) · Entity formation (UG) ~€800–1,200 · Regulatory qualification opinion €3–10k · Privacy/terms review ~€1–2k · Liability insurance (quote) · Clinician review €500–1,500 or pro bono · Servers €0.

---

## 14. Open questions
1. Sign-off of all open decisions in §0.1.
2. ~~Apple Watch for testing?~~ **Answered 2026-11-22: no Apple Watch.** Consequences:
   - wrist temperature is not used by the fertility engine in v1 (it is display-only and flag-gated);
   - the wrist likelihood (§11.11.3) and the Watch app (v1.5) need a test device, meaning a used Series 8+ / Ultra (~€250–400; SE models have no temperature sensor), or ≥ 3 TestFlight testers with one using the on-device back-test;
   - decide before v1.1.
3. Name & brand: needs trademark search EU/US before assets are made.
4. Clinician reviewer: candidate names (gynaecologist + midwife, DE-speaking).
5. Ask App Review in writing about guideline 5.1.3(ii) for (a) CloudKit `encryptedValues` and (b) the store in iCloud Backup. Keep the response; it decides sync in v2.

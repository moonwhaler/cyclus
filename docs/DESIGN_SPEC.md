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
| **D1** | Fertility features (fertile window, Conceive mode, ovulation estimate/confirmation, cycle-deviation flags) | ✅ **Not in v1.** v1 = period tracking, symptom/condition diary, period prediction, data display. Fertility goes behind a regulatory gate (§1.4). | Get a written MDR/FDA qualification opinion first (€3–10k, 4–8 weeks), then build to what it allows |
| **D2** | Positioning vs. iOS 27 Apple Health | ✅ Three wedges (§1.2): **deep fast logging**, **condition-first** (PCOS/endo/irregular), **Doctor Visit Pack**. Validate in week 1–2 with current Apple Health users. | Pick one wedge only |
| **D3** | Publisher | ✅ **Legal entity + Organization developer account** before submission (UG (haftungsbeschränkt) or e.V.; D-U-N-S number). Start now, because it takes 4–8 weeks. | Individual account (rejection risk under guideline 5.1.1(ix), personal liability) |
| **D4** | Schedule | ✅ **Honest ~34 weeks** for the full v1 scope in §2 at ~20 h/week (≈ 680 h incl. 25% buffer, §13.1). Internal TestFlight at ~week 18. | Apply the §13.1 cut list (≈ −100 h) → ~29 weeks |
| **D5** | Backup | ✅ Store **included** in device backups (so phone migration just works), with honest disclosure plus a "Device only" toggle that sets `isExcludedFromBackup`, plus **encrypted export**. | Exclude by default (max privacy, real data-loss risk) |
| **D6** | Money | ✅ Strictly free. **No tip jar in v1** (it would make the developer a DSA trader, whose address is published). | Tip jar via IAP after the entity exists |
| **D7** | Platform baseline | ✅ **Deployment target iOS 26.0, built with the iOS 27 SDK.** Use iOS 27 APIs behind `if #available`. | iOS 27 minimum (smaller audience, simpler code) |
| **D8** | Markets & languages | ✅ EU + US. EN + DE at launch | — |

### 0.2 What changed vs. PLAN.md (summary)
- **Removed from v1:** Conceive mode, fertile window, ovulation estimate/confirmation, anomaly nudges, "phase" names that imply ovulation (follicular/luteal), Siri/App Shortcuts, Control Center control, LLM features.
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
| M1 Regulatory | Fertility features gated (D1); copy guardrails; citations fixed | §1.4, §9.3 |
| M2 Apple Health parity | Wedges + validation with Apple Health users | §1.2, §13 |
| M3 Wrist temp / BBT | v1 = temperature *display only*. Shift detection specified for the gated phase, on its own baseline, Sensiplan-correct | §11.6 |
| M4 File protection vs. lock screen | Single-writer process; intents require authentication; pending-event queue; principle rewritten | §3.3, §11.2 |
| M5 Threat model | Full threat model; per-category HealthKit write opt-in; widget redaction; PIN; discreet defaults | §3 |
| M6 Backup honesty | D5 + encrypted export + full HealthKit re-import + backup indicator + honest copy | §3.4, §11.9 |
| M7 HealthKit sync | Sync state machine, ownership, dedup, time zones, incremental permissions; 2.5 weeks budgeted | §11.4 |
| M8 Fake precision | Log-normal NIG posterior-predictive, robust weights, missed-log detection, age-band priors, hormonal-factor suppression | §11.5 |
| M9 Circular validation | On-device retrospective back-test, aggregate-only output; no raw-data collection; no public accuracy claims | §12.2 |
| M10 Timeline | Bottom-up hours, missing items added, iOS 27 baseline | §13 |
| M11 Entity / liability | D3, insurance, legal budget €3–10k, no tip jar | §0.1, §13.3 |
| M12 Pattern stats | One Bayesian paradigm, cycle as unit, observed-day denominator, tiers, null-simulation gate | §11.7 |
| m1 FM AUP/guardrails | Template-primary; FM only rephrases; `guardrailViolation` → template; `contextSize` availability-gated | §11.8 |
| m2 Age rating | 13+ under the new tiers; teen priors; Declared Age Range API reviewed | §1.5, §11.5 |
| m3 Modes naive | Episodes model in v1 schema | §2.2, §11.3 |
| m4 Cycle detection | Precedence: override > start flag > heuristic; `unspecified` flow counts | §11.5.1 |
| m5 LH timing | Deferred with fertility. When built: first positive → 24–48 h | §11.6 |
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
| m18 Clinician review | Named reviewer by week 3; scope listed | §13.2 |
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
| **5. Insights in German on any iPhone** (v1.5) | AI Insights depend on Apple Intelligence and language rollout | Deterministic insights with localised templates; on-device LLM polish where available |

**Validation gate (week 1–2):** 6–8 interviews with *current Apple Health cycle-tracking users* plus 2 people with endometriosis or PCOS. Each wedge needs ≥ 50% "would switch / use alongside" to stay in v1. Otherwise cut it (D4 alternative).

### 1.3 Principles (ranked; earlier wins conflicts)
1. **Trust first.** No dark patterns, fake precision or guilt. The developer never receives data.
2. **Honest numbers.** Every number shows its uncertainty or isn't shown. "Not enough data yet" is a valid answer.
3. **≤ 2 taps after unlock** for period start/end and any pinned symptom. Logging works one-handed.
4. **Useful on day 1** via Apple Health import. If there's no history, be honest rather than invent.
5. **Calm, native, beautiful, accessible.** Feels like Apple built it, with more warmth.

### 1.4 Regulatory & legal guardrails
- **Intended purpose (v1), to be used verbatim in the App Store, website and in-app About:** "Cyclus helps you record your periods, symptoms and wellbeing, see patterns in your own records, and share a summary with your healthcare provider. Cyclus is not a medical device, is not intended for contraception or to support conception, and does not diagnose any condition."
- **Fertility gate (D1).** Fertile-window, ovulation and conception features are built only after a written qualification opinion. Basis: MDR Art. 2(1) "control or support of conception" → software in scope; contraception = Annex VIII Rule 15; software informing decisions = Rule 11; FDA General Wellness guidance reissued 2026-01-06. Apple's own Cycle Tracking is labelled a medical device.
- **Grey zone to confirm in the same opinion:** whether **period prediction** alone and the **FIGO reference ranges in the PDF** keep v1 outside MDR. Default stance: predictions are framed as "based on your past records", and the PDF prints reference ranges *without* flagging the user's values.
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
**v1.0 (≈ week 34)**
- Onboarding with Apple Health import.
- Today, Log sheet (incl. pain map, tags, "nothing else today"), Calendar + Day detail, Insights (stats & charts, no pattern engine).
- Period prediction (§11.5). Temperature *display* (BBT logged + wrist temp read).
- HealthKit: import of all history, plus write-own for user-selected categories.
- Doctor Visit Pack (PDF).
- Privacy Center, app lock + optional PIN, discreet notifications, encrypted export/import, erase.
- Home Screen widget (S/M), Lock Screen widget (redacted by default).
- Local notifications.
- EN + DE.

**v1.1 (+6 weeks):** Control Center control, App Intents (auth-required, no automatic App Shortcuts), StandBy widget, perimenopause symptom set refinements from iOS 27 HealthKit types, iPad layout.

**v1.5 (+10 weeks):** Pattern engine (§11.7) with template insights; on-device LLM phrasing (§11.8); pill/patch/ring schedule within the hormonal-contraception episode; Apple Watch app (log + glance).

**Gated (needs D1 opinion):** fertile window, Conceive mode, LH tests → ovulation, temperature-shift detection, cycle-deviation nudges.

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
- Fertility colours (amber "ovulatory") are reserved. They are **not used** in v1 (gate D1).
- High-contrast mode (`colorSchemeContrast == .increased`): darken light-mode fills 15%, use solid outlines instead of dashed.

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
| **CycleRing** | 270° arc (gap at bottom for the label). Segments: period (solid `period`), mid (`midCycle`, 40% opacity), pre-period (`prePeriod`, dotted). Today marker = 12-pt knob with 2-pt `bg` stroke. Predicted period = dashed outer arc spanning the 80% range. Centre: "Day 12" + subtitle. Size: min(80% width, 320 pt). VoiceOver: one element, label "Cycle day 12. Next period expected between November 28 and December 2." Custom actions: "Log period", "Open calendar". |
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

### 7.1 Onboarding (≤ 60 s, every step skippable)
| # | Screen | Content | Primary / secondary |
|---|---|---|---|
| 1 | Welcome | Wordmark, "Your cycle, understood. Never uploaded." | **Get started** |
| 2 | Privacy promise | 3 rows: "No account. No servers.", "We never see your data.", "You decide what goes into Apple Health." Link: "What about backups?" (opens §7.8 explainer) | **Continue** |
| 3 | Import | "Bring your history from Apple Health so predictions work from day one." | **Import from Apple Health** / Skip |
| 3a | Import progress | Read-only permission sheet (menstrual flow, spotting, symptoms, BBT, wrist temp, cervical mucus, contraceptive, pregnancy, lactation). Progress → "Found 37 periods since 2021." | auto-advance |
| 4 | Last period (if no import) | "When did your last period start?" Inline graphical date picker | **Continue** / "I don't remember" |
| 5 | Birth year (optional) | "Cycles change with age. This stays on your iPhone." Wheel | **Continue** / Prefer not to say |
| 6 | Anything affecting your cycle? | Multi-select chips: Hormonal birth control · Pregnant · Postpartum / breastfeeding · Perimenopause · PCOS · Endometriosis · Testosterone / gender-affirming hormones · None of these | **Continue** |
| 7 | App lock | "Lock Cyclus with Face ID?" | **Turn on** / Not now |
| 8 | Done | Today screen with first-run coach mark on the Log button | — |

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
States:
| State | Ring centre | Subtitle |
|---|---|---|
| Normal | Day N | Next period · range |
| In period | Period day N | "Tap to log flow" + quick flow chips inline |
| Pre-period window | Day N | "Period expected in X–Y days" |
| Past upper bound | Day N | "Later than your usual range" (neutral, no alarm, no push unless opted in) |
| Predictions paused (episode) | Day N since last bleed or episode label | Explanation + link to Life stage |
| No data | — | "Log your period to begin" + big **Log period** button |

- In-period quick action: when cycle day ≤ 10 and no period is open, show a **"Period started today"** one-tap button directly under the ring. One tap = log (undo toast for 5 s).

### 7.3 Log sheet
- Presented as a `.sheet` with detents `.medium` / `.large`. Date header with ‹ › arrows; swipe between days.
- **Stable section order:** Flow → Pinned → Suggested → Symptoms → Pain → Mood & energy → Sleep → Discharge → Sex (hidden unless enabled) → Temperature → Tags → Note.
- **Pinned row:** user-chosen (default: Cramps, Headache, Tired, Bloating). Fixed positions → muscle memory.
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
   - Temperature: BBT (logged) and wrist temperature (from Apple Watch) as separate series with gaps shown. **No shift markers in v1** (gate D1).
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
2. **Protection:** App lock (Face ID / passcode) · **Cyclus PIN** (6 digits, independent of device passcode) · "Require PIN, don't allow Face ID" · Lock after: immediately / 1 min / 5 min · Blur in app switcher (always on, shown as info).
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
| fertile, fertility window, ovulation, ovulating, safe days, conceive, TTC | **Banned** (gate D1). Exception: "Not for contraception or conception." |
| follicular, luteal, ovulatory phase | Banned in UI (implies ovulation estimation). Use "period", "pre-period window", "between periods". |
| diagnose, detect, screen, "signs of PCOS/endometriosis" | Banned |
| irregular | Only in user-chosen condition labels or as a neutral description of the user's own data ("your cycles vary"), never as a verdict |
| accurate / % accuracy | Banned until validated (§12.2) |
| AI / smart | Allowed in v1.5 only to describe phrasing ("summaries written on your iPhone") |

A CI lint scans `Localizable.xcstrings` and App Store metadata for banned terms (§12.1).

### 9.4 Glossary (EN → DE), to be reviewed by a DE-speaking clinician
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
    var energy: Int?                 // 1...5
    var sleepQuality: Int?           // 1...5
    var discharge: Discharge?        // superset of HK cervical mucus
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
7. **Reinstall / recovery.** Own samples (prefix match) found on import are reconstructed as local `DayLog` fields, which restores data after reinstall for mirrored categories.
8. **Permissions.**
   - Read set requested at import.
   - Write per category, requested when toggled.
   - Read denial is undetectable, so the UX shows help text only.
9. **Import.** All history, batched by year with progress; `HKAnchoredObjectQuery` anchors persisted per type; `HKObserverQuery` + `enableBackgroundDelivery(.immediate)` for flow and symptoms, deferred until unlocked.

#### 11.4.2 Field mapping
| Cyclus field | HealthKit | Mirror default | Loss / note |
|---|---|---|---|
| flow light/medium/heavy/unspecified | `menstrualFlow` (`.light/.medium/.heavy/.unspecified`) + `HKMetadataKeyMenstrualCycleStart` | On | — |
| flow spotting | `intermenstrualBleeding` | On | Spotting is never written as `menstrualFlow` |
| symptoms (severity) | Matching symptom category types, `HKCategoryValueSeverity` | On | Cyclus-only symptoms (custom) not mirrored |
| moodChanges / sleepChanges (HK presence types) | Imported as symptom presence | — | Import-only mapping |
| mood (1–5) | `HKStateOfMind` (dailyMood, valence mapped −1…+1) | **Off** | Labels not mapped in v1 |
| energy, sleepQuality, pain map, tags, notes | — | Not mirrored | Shown in Privacy Center as "Cyclus only" |
| discharge | `cervicalMucusQuality` (dry/sticky/creamy/watery/eggWhite) | Off | Cyclus-only options (e.g. "unusual") not mirrored |
| sex | `sexualActivity` + `HKMetadataKeySexualActivityProtectionUsed` | **Off** | — |
| BBT | `basalBodyTemperature` | On | — |
| wrist temperature | `appleSleepingWristTemperature` | Read only | Treated as raw series; baseline computed by Cyclus |
| episodes | `contraceptive`, `pregnancy`, `lactation` | **Off** | Episode kinds without an HK type are Cyclus only |

iOS 27 SDK review item (week 2): new perimenopause/menopause-related identifiers → add to mapping with `#available`.

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

  | Band | Prior median L | σ_y prior mean | κ₀ | α₀ |
  |---|---|---|---|---|
  | Teen (< 20 or unknown + teen indicated) | 30 | 0.22 | 1 | 2 |
  | Adult 20–39 / unknown | 29 | 0.14 | 1 | 2 |
  | 40+ | 28 | 0.20 | 1 | 2 |
  | Perimenopause episode | 30 | 0.35 | 0.5 | 2 |
  | PCOS condition | 33 | 0.35 | 0.5 | 2 |

  `μ₀ = ln(median)`, `β₀ = σ²_prior · (α₀ − 1)`.
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

### 11.6 Temperature (v1 display; detection gated)
- **v1:** display BBT (user-logged) and wrist temperature (read). Wrist values are plotted as **deviation from a Cyclus-computed baseline**, defined as the rolling median of nights −35…−6 with ≥ 10 nights present. This works whether HealthKit returns absolute values (as the developer docs indicate) or relative ones, because subtracting a baseline is idempotent for our purpose. Gaps are shown as gaps. No markers or inference.
- **Gated (D1), specified for later:**
  - **BBT (Sensiplan-style):** a shift is confirmed when 3 consecutive readings are each higher than the highest of the 6 preceding readings, and the 3rd is ≥ 0.2 °C above that maximum. Apply the Sensiplan exception rules (verify against the official Arbeitsgruppe NFP handbook before implementation).
  - **Wrist:** an own detector on baseline-corrected deviations (persistent elevation ≥ 0.25 °C for ≥ 3 of 4 nights after ≥ 5 of 7 lower nights), labelled "possible temperature shift". Never fed into period prediction until back-tested on real wrist data.
  - **LH:** ovulation typically 24–48 h after the *first* positive.

### 11.7 Pattern engine (v1.5; logging affordances in v1)
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
- **`.cyclus` format v1:** `magic "CYCLUS1" | salt(16) | iterations(u32) | nonce(12) | AES-GCM-256(ciphertext + tag)`. Key = PBKDF2-HMAC-SHA256 (CommonCrypto `CCKeyDerivationPBKDF`), ≥ 600,000 iterations. Plaintext = zlib(JSON per public schema `cyclus-export.schema.json` v1).
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

---

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
| Wedge validation | ≥ 50% of interviewees "would use" per wedge | Week 1–2 interviews |
| Logging speed | Median period-start log ≤ 3 s after unlock; 3 symptoms ≤ 6 s | Moderated usability tests (n ≥ 8) |
| SUS score | ≥ 80 | Same sessions |
| Beta retention | ≥ 60% of testers active after 2 cycles | TestFlight sessions |
| Calibration | 72–88% coverage across testers' self-reported aggregates | §12.2 |
| Crash-free sessions | ≥ 99.5% | App Store Connect (opt-in) |
| Rating | ≥ 4.6 once ≥ 30 ratings | App Store |

---

## 13. Delivery plan

### 13.1 Bottom-up estimate (effort hours, AI-assisted coding)
| Work item | h |
|---|---|
| Wedge interviews (recruit, run, synthesise) + HTML prototype | 30 |
| Entity setup, D-U-N-S, Org account, insurance (calendar 4–8 wks) | 12 |
| Regulatory/legal coordination (opinion, privacy policy, MHMDA policy, terms) | 12 |
| Scaffold, packages, CI, lints, design tokens & components | 30 |
| CycleCore + CycleEngine (detection, NIG model, missed-log, status) + property tests | 45 |
| CycleStore (schema, migrations, pending queue, file protection) | 25 |
| Today + CycleRing | 25 |
| Log sheet (flow, pinned/suggested, symptoms, pain map, scales, tags, note) | 35 |
| Calendar + Day detail + overrides | 25 |
| Insights (overview, 5 charts, history, cycle detail) | 30 |
| HealthKit import, write-own, sync state machine, device tests | 45 |
| Onboarding | 15 |
| Episodes UI (life stage & conditions) | 10 |
| Privacy Center, app lock, PIN, erase | 25 |
| Backup & export (crypto, CSV, import/merge) | 20 |
| Doctor Visit Pack | 30 |
| Widgets (home S/M, lock screen, redaction, snapshot) | 18 |
| Notifications | 8 |
| DE localisation + glossary review + DE screenshots | 20 |
| Accessibility pass & fixes | 18 |
| Website: privacy policy, support, Impressum, export schema | 10 |
| App Store: screenshots EN/DE, metadata, age rating, privacy details, review notes, demo video | 15 |
| TestFlight: Beta App Review, 2-cycle beta (calendar ~8 wks), fixes | 40 |
| **Subtotal** | **543** |
| Buffer 25% (rounded) | **~135** |
| **Total** | **~680 h** |

At 20 h/week → **~34 weeks** (D4 default); at 17.5 h/week → ~39 weeks; at 25 h/week → ~27 weeks.

**Cut list** (≈ −78 h subtotal, ≈ −100 h incl. buffer → ~29 weeks at 20 h/week):
- move the pain map to v1.1 (−20);
- drop the temperature chart (−8);
- keep episodes UI to basic toggles (−5);
- drop the Lock Screen widget (−8);
- ship the Doctor Visit Pack as a fixed template without builder options (−12);
- overlap the beta with localisation (−15);
- reuse the HTML prototype for interviews (−10).

The pain map is part of wedge 2, so cut it only if interviews don't confirm that wedge. Owner confirms weekly hours, then the cut list is finalised.

### 13.2 Milestones (at ~20 h/week, full scope)
| Week | Milestone | Exit criteria |
|---|---|---|
| 1–2 | Prototype + wedge interviews; start entity + legal opinion request; name clinician reviewer | Wedges confirmed or cut; reviewer agreed |
| 3–4 | Scaffold, CI, lints, tokens; iOS 27 SDK HealthKit review | Green CI; component gallery |
| 5–7 | Core + Engine + Store | Engine tests pass; migration test harness |
| 8–11 | Today, Log sheet, Calendar | Usability test #1 (n = 5): log ≤ 3 s |
| 12–14 | HealthKit sync | Device integration checklist passes |
| 15–16 | Insights, Onboarding, Episodes | — |
| 17–18 | Privacy Center, lock/PIN, backup/export | **Internal TestFlight** |
| 19–21 | Doctor Visit Pack, widgets, notifications | Clinician review of copy/PDF/templates |
| 22–23 | DE, a11y, website, legal docs final | Audit checklists pass |
| 24–34 | External TestFlight (Beta App Review) for ≥ 2 cycles; fixes; store assets; submission | Metrics §12.4 (beta rows); privacy report clean |

### 13.3 Budget
Apple Developer (Organization) €99/yr · Entity formation (UG) ~€800–1,200 · Regulatory qualification opinion €3–10k · Privacy/terms review ~€1–2k · Liability insurance (quote) · Clinician review €500–1,500 or pro bono · Servers €0.

---

## 14. Open questions
1. D1–D8 sign-off (§0.1).
2. Weekly hours available (finalises the §13.1 cut list).
3. Apple Watch Series 8+ / Ultra for wrist-temperature testing? (v1 needs it only for the display chart.)
4. Name & brand: needs trademark search EU/US before assets are made.
5. Clinician reviewer: candidate names (gynaecologist + midwife, DE-speaking).
6. Ask App Review in writing about guideline 5.1.3(ii) for (a) CloudKit `encryptedValues` and (b) the store in iCloud Backup. Keep the response; it decides sync in v2.

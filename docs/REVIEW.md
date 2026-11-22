# Sceptic Review — Cyclus PLAN.md v0.1

Verdict: The plan is a list of features with the hard parts left out. Five problems stand out.

1. **The regulatory footing is wrong.** EU MDR Art. 2(1) treats "support of conception" software as a medical device. Apple ships its own Cycle Tracking as a labelled medical device "to facilitate conception". Cyclus plans a Conceive mode under a "wellness" label.
2. **The differentiation against Apple Health is mostly gone.** Apple Health is free, E2E-synced and validated. It already has fertile window, wrist-temperature ovulation estimates, deviation alerts and PDF export. iOS 27 adds perimenopause/menopause support and an AI Insights tab that covers cycle data. Cyclus v1 ships with no Watch app, no sync and no AI.
3. **The core architecture contradicts itself.** A `.complete`-protected store cannot back lock-screen logging, Control Center controls or locked-device Siri.
4. **The wrist-temperature algorithm rests on a false premise.** HealthKit gives absolute temperatures, not deviations from baseline.
5. **"Data never leaves the device" is not true as designed.** The privacy story ignores the HealthKit mirror, Health Sharing, iCloud Backup, plaintext exports and lock-screen surfaces.

On top of that, the statistics are under-specified to the point of producing fake precision. Validation depends on data that does not exist under a usable licence. The timeline is about 3× too short for 180–240 hours of solo work. The privacy architecture is genuinely good. But the product as scoped is "Apple Health with mood logging and a nicer ring", sitting in a regulated gray zone, built by an individual without a legal entity. Do not write the design spec until M1, M2, M4, M5 and M10 are resolved.

---

## MAJOR issues

### M1. Conceive mode / fertile window is very likely a regulated medical device; the "general wellness" framing does not hold  (§3, §4 v1.0, §6.2, §7)
- **Problem:** §7 claims general-wellness positioning while v1 ships three things:
  - a Conceive mode for "trying to conceive";
  - fertile-window estimates, also optional in Track;
  - LH-test-adjusted ovulation estimates and retrospective ovulation confirmation.

  The legal citations are also wrong or out of date:
  - Rule 11 is the classification rule for software that informs diagnostic or therapeutic decisions. The contraception rule is Rule 15 (Class IIb).
  - More importantly, MDR Art. 2(1) brings "devices for the control or support of conception" into scope without any disease claim. A disclaimer does not exempt a product whose intended purpose is to help users conceive.
  - The FDA guidance cited as "2019/2022" was reissued in January 2026. Wellness exemptions turn on the claimed intended use, and "help you get pregnant" plus ovulation dating is not a lifestyle claim.
- **Why it matters:** Placing an unmarked medical device on the EU market is unlawful. Apple Review applies 1.4.1 scrutiny to such apps and requires validated accuracy methodology. Personal liability falls on an individual developer (see M11).
- **Evidence:**
  - Apple's own Cycle Tracking IFU (US & EU/EEA/CH/UK) states: "Cycle Tracking is a software-only application intended to track and predict menstrual cycles, including ovulation, in order to inform users about their menstrual health and to facilitate conception". It carries pre-specified clinical endpoints (period prediction ±2 d equivalence, ovulation ±2 d vs LH), serious-incident reporting to competent authorities and a CH-REP. Apple's version of this feature is therefore a regulated device. https://www.apple.com/legal/ifu/cycle-tracking/4-0/ct-ifu-4-0-en_GB.pdf
  - MDR Art. 2(1): https://www.legislation.gov.uk/eur/2017/745/article/2 . MDR Annex VIII Rules 11 and 15: https://eur-lex.europa.eu/eli/reg/2017/745/oj
  - MDCG 2019-11 (software qualification): https://health.ec.europa.eu/document/download/b45335c5-1679-4c71-a91c-fc7a4d37f12b_en
  - DLA Piper (Mar 2026): marketing that suggests an app helps "predict fertility" can tip it into medical-device status. https://www.dlapiper.com/en/insights/publications/2026/03/the-legal-landscape-of-femtech
  - FDA General Wellness guidance, reissued 6 Jan 2026: https://www.fda.gov/regulatory-information/search-fda-guidance-documents/general-wellness-policy-low-risk-devices ; https://www.cov.com/en/news-and-insights/insights/2026/01/fda-issues-revised-guidance-on-general-wellness-products
  - Natural Cycles (the closest comparator) is an FDA De Novo/510(k) Class II device. https://www.biospace.com/natural-cycles-receives-fda-clearance-to-integrate-its-birth-control-app-with-data-measured-by-apple-watch
- **Required fix:** Before any design work, pick one of two paths.
  - **(a) Drop it from v1:** remove Conceive mode, the fertile window, LH-adjusted ovulation and the "confirmed ovulation" language. Ship period prediction plus symptom tracking only.
  - **(b) Get a written qualification opinion first:** obtain an MDR/FDA regulatory qualification opinion from a specialist (not the €1k generalist review in §7) before building. Budget realistically €3–10k for the opinion alone. Keep any fertility features behind whatever wording that opinion allows.

  In either case, fix the citations (Art. 2(1), Rule 15, FDA 2026), and treat App Store text, screenshots and in-app copy as "intended purpose" evidence.

### M2. The differentiation from Apple Health is weak today and shrank further with iOS 27  (§2, §4, §11)
- **Problem:** The vision rests on "Apple Health is private but shallow". What Apple Health has as of iOS 26/27:
  - period predictions;
  - a 6-day fertile window adjusted by LH tests;
  - **retrospective ovulation estimates from wrist temperature plus heart rate**, a regulated and validated algorithm;
  - cycle deviation detection (irregular, infrequent, prolonged, persistent spotting);
  - symptom, BBT and mucus logging;
  - factors (pregnancy, lactation, contraception);
  - Watch logging and **PDF export** of cycle history;
  - E2E-encrypted iCloud sync, free.

  iOS 27 (shipped 14 Sep 2026) adds:
  - **perimenopause/menopause support** (status logging, symptoms, notifications for users 40+, post-menopausal bleeding alerts);
  - the redesigned Health app (iOS 27.3), with an Apple-Intelligence **Insights tab that explicitly covers cycle tracking data**.

  Set against that, Cyclus v1 has no Watch app, no sync and no AI. Its two headline differentiators, the Perimenopause mode and LLM insights, are both v1.5, and Apple shipped both first. The remaining v1 gaps (mood, energy, notes, custom tags, smarter UI) are thin, and the privacy-conscious user already has a zero-install answer. The "Flo is smart but leaky" framing is also dated: Clue, Stardust and others now market E2E encryption and on-device processing.
- **Why it matters:** A product with no reason to switch fails no matter how well it is engineered. "Free" is not a differentiator against a free, preinstalled incumbent.
- **Evidence:**
  - Apple Cycle Tracking features: https://support.apple.com/120356
  - Apple's US IFU: https://www.apple.com/legal/ifu/cycle-tracking/ct-ifu-en_US.pdf
  - iOS 27 press release: https://www.apple.com/newsroom/2026/09/apple-advances-health-and-fitness-capabilities-using-apple-intelligence/
  - MacRumors on iOS 27 perimenopause/menopause tracking: https://www.macrumors.com/2026/06/08/ios-27-adds-perimenopause-and-menopause-tracking/
  - 9to5Mac on the iOS 27.3 Health app: https://9to5mac.com/2026/11/03/ios-27-3-adds-all-new-apple-health-app-details-here/
- **Required fix:**
  - Write a one-page "why switch from Apple Health" with 2–3 concrete wedges. Candidates: logging speed and depth (mood, energy, pain mapping, custom tags); conditions (PCOS, endometriosis, irregular cycles) underserved by Apple; a clinician report that beats Apple's PDF; DE-first localisation of insights (Apple's AI Insights start US-English only).
  - Validate the wedges in week 1 with **current Apple Health cycle-tracking users** (not friends), and move the winning wedge into v1.
  - Run an honest competitive table against iOS 27 Health, Clue, drip, Euki and Stardust.
  - Retire the "smartest" tagline unless v1 actually contains intelligence.

### M3. The wrist-temperature design rests on a false premise, and the BBT rule is misstated  (§6.2 "Retrospective confirmation")
- **Problem:**
  - **The data is absolute, not relative.** The plan says `appleSleepingWristTemperature` "gives a deviation from baseline". Apple's documentation says the opposite: the watch records an **absolute** wrist temperature, and only the Health app displays it relative to a baseline it computes itself. No baseline or deviation is exposed to third parties. Cyclus must build its own baseline model, which brings in ambient, bedding, illness, alcohol and travel effects (Apple applies environmental correction plus HR fusion).
  - **Data presence is not guaranteed.** Samples exist only when Sleep Focus is on and the watch is worn overnight. The "≥ 5 of 6 prior nights" rule will fail often.
  - **The BBT rule is wrong.** The standard symptothermal (Sensiplan) rule requires 3 consecutive readings above the highest of the previous 6, with **only the third** ≥ 0.2 °C above that level, plus exception rules. "All three ≥ 0.2 °C" is a stricter, non-standard rule that will miss real shifts.
  - **The rule does not transfer to wrist data.** 3-over-6 is validated for oral or vaginal BBT, not for wrist skin temperature.
  - **Narrowing makes it worse.** Feeding an unvalidated "confirmed ovulation" into a ±2 d next-period prediction narrows intervals on the strength of a possibly false positive.
- **Why it matters:** This is a headline v1 feature, and as specified it computes nonsense. It also deepens M1, since "confirmed ovulation" is a diagnostic-sounding claim.
- **Evidence:**
  - "Apple Watch records the absolute wrist temperature value; however, Health displays this data as a relative value, based on a person's baseline… To enable sleeping wrist temperature measurements, ensure Sleep Focus is on." The samples are read-only. https://developer.apple.com/documentation/healthkit/hkquantitytypeidentifier/applesleepingwristtemperature
  - The forum report confirms no baseline or deflection metadata is available: https://developer.apple.com/forums/thread/725445
  - Sensiplan rule as described in Frank-Herrmann et al., Hum Reprod 2007: https://academic.oup.com/humrep/article/22/5/1310/2914938
- **Required fix:**
  - Specify a baseline model (for example, a rolling median of the prior N nights, excluding the luteal-flagged nights of the previous cycle) and a wrist-specific shift detector.
  - Label results "possible temperature shift", not "confirmed ovulation".
  - Do not narrow predictions on wrist data until the detector is back-tested on real wrist data (see M9).
  - Correct the BBT rule to the Sensiplan definition, including the exception rules, or cite whichever rule you actually use.
  - Consider cutting wrist-temperature work from v1 altogether: the owner may not even have a Series 8+ watch (§14 Q2).

### M4. File protection `.complete` contradicts Lock Screen logging, Control Center, Siri and HealthKit background work  (§4 Widgets/App Intents, §2 principle 2, §6.7)
- **Problem:** §6.7 puts the store under `.complete`, so it is unreadable and unwritable while the device is locked. Meanwhile v1 promises the following, all of which run in extension or background processes, often while the device is locked:
  - an interactive Lock Screen widget "log period" button;
  - a Control Center control;
  - Siri "Log period started" and "When is my next period?";
  - ≤ 2-tap logging from the Lock Screen (principle 2);
  - `HKObserverQuery` background delivery;
  - re-scheduling notifications after new data.

  The plan also contradicts itself: the App Group widget store holds "never logs", yet the widget button writes logs. Where does that write land? The plan does not say which process owns the main SwiftData store, or whether that store lives in the App Group at all. Further gaps:
  - SwiftData's `ModelConfiguration` has no file-protection option. Protection must be set through the entitlement or file attributes on the `.sqlite`, `-wal` and `-shm` files.
  - Multi-process SwiftData writers (app, widget, intents) need history tracking and are a known source of bugs.
  - HealthKit itself is inaccessible while locked.
  - App lock (Face ID) and "≤ 2 taps from Lock Screen" are mutually exclusive unless lock-screen writes bypass the app lock.
- **Why it matters:** Either the headline lock-screen features silently fail (data loss: the user taps "log period" and nothing is persisted), or protection gets weakened late in development without a threat-model decision.
- **Evidence:**
  - Forum threads on SQLite stores failing in widgets after restore or while locked, and on HealthKit being locked when the device is locked: https://developer.apple.com/forums/thread/787418 ; https://developer.apple.com/forums/thread/756794
  - The rest is reasoning from Data Protection class semantics.
- **Required fix:**
  - Decide explicitly, per surface, between two options. **(a) Require authentication:** App Intents set `authenticationPolicy = .requiresAuthentication`, so writes happen only after unlock. **(b) Write-ahead queue:** a tiny append-only queue file under `.completeUntilFirstUserAuthentication`, holding only "event type + date", which the app drains into the `.complete` store on next unlock. Document the leakage this causes.
  - Rewrite principle 2 to match whichever you choose.
  - Specify the single-writer process, the App Group layout, how protection is applied to SQLite sidecar files, and failure UX ("Couldn't save while locked — unlock to log").

### M5. No threat model; lock screen, Siri, HealthKit mirror and Health Sharing leak what the app lock "protects"  (§4 Privacy, §6.4, §6.7, §10)
- **Problem:** The plan treats Face ID plus discreet notification copy as the privacy story. Realistic adversaries are: an abusive partner who knows the device passcode, a shared family device, a parent of a 13-year-old, border or device seizure (BFU/AFU forensic extraction), and US legal process against Apple for iCloud data after Dobbs. Concrete leaks:
  - **Lock Screen surfaces.** Lock Screen and StandBy widgets display cycle day and next period to anyone who glances at the phone. StandBy is designed to be read across a room.
  - **Siri while locked.** Siri answers "When is my next period?" aloud on a locked phone, unless intents require authentication.
  - **Intent donations.** These surface "Log period started" in Spotlight and Siri Suggestions.
  - **Device passcode fallback.** App lock using the device passcode protects nothing against someone who knows that passcode. An app-specific PIN is not planned.
  - **The HealthKit mirror.** It writes flow, **sexual activity with protection status**, ovulation tests, contraception and, later, pregnancy tests into Apple Health, which has no separate lock. The app lock is theatre for everything mirrored.
  - **Health Sharing.** If the user has set up Health Sharing with a partner or family member (up to 5 people, with change notifications), Cyclus writes may be forwarded to them.
  - **Alternate icons.** They do not change the app's name under the icon, they trigger a system alert, and the app still appears in Settings › Health › Data Access and in Screen Time.
  - **"Zero privacy incidents (structurally guaranteed)"** (§10) is false. The incidents that matter in this category are on-device and coercive.
- **Why it matters:** For reproductive data, these are the actual harms. Shipping a "private by architecture" brand that leaks through Apple Health is a reputational and safety failure.
- **Evidence:**
  - Health Sharing: https://support.apple.com/HT212629
  - iCloud standard vs Advanced Data Protection: https://support.apple.com/102651
  - Guideline 5.1.3: https://developer.apple.com/app-store/review/guidelines/#health-and-health-research
- **Required fix:**
  - Write a threat model section listing each adversary, its capability and its mitigation.
  - Make HealthKit write **opt-in per category**, with sexual activity, contraception and pregnancy defaulting **off**, and an explainer that Apple Health has no separate lock and may be shared.
  - Make all read intents require authentication.
  - Add widget redaction (`.privacySensitive()`) and a "hide on Lock Screen" default.
  - Disable intent donations by default.
  - Add an optional app-specific PIN, independent of the device passcode.
  - Make discreet notification copy the default, not an option.
  - Delete the "structurally guaranteed" claim.

### M6. The backup and device-migration story is not honest  (§6.5, §4 Export)
- **Problem:**
  - **"HealthKit as backup" covers only part of the data.** Not in HealthKit: mood, energy, sleep quality, notes, custom tags, period-start overrides, condition flags and settings. Mood could map to `HKStateOfMind` (iOS 18+), but the plan does not mention it.
  - **The re-import window is short.** Re-import is specified as "last 24 months" (§6.4), so older history is gone even for HealthKit types.
  - **Health iCloud sync is not guaranteed.** It requires the user to have Health enabled in iCloud settings.
  - **iCloud Backup changes the privacy claim.** "Encrypted local backup included in standard (encrypted) iPhone backup" ignores that most users use **iCloud Backup**. iCloud Backup includes the app container and is **not** end-to-end encrypted unless Advanced Data Protection is on; Apple holds the keys. So the Cyclus store sits in Apple-key-held iCloud storage, less protected than Apple Health's own E2E data. That directly contradicts §1 "data never leaves the user's devices / Apple's E2E Health sync". It is also ironic next to the 5.1.3(ii) caution about CloudKit.
  - **Exports leak.** Manual JSON/CSV export is plaintext and will typically land in iCloud Drive (also not E2E by default), Mail or Messages, which makes it the largest leakage vector in the product.
- **Why it matters:** This produces data loss for users who trust the "survives via Health" message, plus a false privacy claim on the App Store page and in the privacy policy.
- **Evidence:**
  - iCloud data security (Health E2E by default; iCloud Backup only with ADP): https://support.apple.com/102651
  - What iCloud Backup includes: https://support.apple.com/108770
  - HKStateOfMind (iOS 18): https://developer.apple.com/documentation/healthkit/hkstateofmind
- **Required fix:**
  - Make an explicit choice between two options and state it in the UI. **(a)** Exclude the store from backup (`isExcludedFromBackup`); data then survives only via export or direct device-to-device transfer. **(b)** Include it, and disclose that it sits in iCloud Backup without ADP.
  - Add an **encrypted export** (CryptoKit AES-GCM with a passphrase-derived key, no third-party dependencies), with plaintext CSV available as an explicit secondary option.
  - Re-import all available HealthKit history, not 24 months.
  - Map mood to `HKStateOfMind` if mirroring is opted in.
  - Add a "last backup: X days ago" indicator and a reminder.
  - Rewrite §1 and the privacy policy wording accordingly.

### M7. The HealthKit two-way sync and conflict model is under-specified and scheduled into one week  (§6.4, §9 week 8)
- **Problem:** "Local store is truth, HK mirrored, user edit wins" does not answer the real cases:
  - **Mutation.** HealthKit samples are immutable, so an edit means delete plus re-save. You need `HKMetadataKeySyncIdentifier` and `SyncVersion` and a local↔HK UUID map, and the plan mentions none of these.
  - **Deletion.** A user deletes a Cyclus-authored flow sample in Apple Health. Does Cyclus delete locally, re-write it, or prompt?
  - **Duplication.** The user logs the same period on the Apple Watch (Apple Cycle Tracking) and in Cyclus. Either both count (double flow days) or dedup rules are needed.
  - **Required metadata.** `HKMetadataKeyMenstrualCycleStart` is **required** on every `menstrualFlow` sample, and Cyclus's own 7-day cycle-start rule (§6.2) can disagree with other apps' flags. Which wins?
  - **Reinstall and migration.** Cyclus-authored samples come back with the same source after a reinstall. Are they "own" (editable) or "other app" (read-only)? What about after a bundle-ID change?
  - **Authorization opacity.** HealthKit does not reveal read denial, so "import found nothing" is ambiguous.
  - **Days and time zones.** Flow is per-day, and an `HKCategorySample` start/end crossing time zones yields off-by-one days.
  - **Permission sheet size.** One authorization sheet with ~35 types conflicts with the ≤ 60 s onboarding.
  - **Contradictory claims.** "Source of truth is local" contradicts "core data survives via Health" (M6).
- **Why it matters:** This is the most bug-prone subsystem in the app and the main source of data corruption, duplicate periods and broken predictions. One week is fantasy.
- **Evidence:**
  - `HKMetadataKeyMenstrualCycleStart` "is required for menstrualFlow category samples": https://developer.apple.com/documentation/healthkit/hkmetadatakeymenstrualcyclestart
  - The rest is reasoning from HealthKit's sample model.
- **Required fix:**
  - Write a sync state machine in the design spec covering: ownership, sync identifiers, edit, delete in either direction, dedup by (day, type, source priority), the cycle-start flag policy, reinstall, and the denied-permission UX.
  - Consider v1 = **import plus write-own only**, no "two-way" for other apps' samples beyond read-only display.
  - Budget 2–3 weeks, with integration tests on a real device.

### M8. The prediction engine produces fake precision, which violates Principle 1  (§6.2)
- **Problem:**
  - **The interval ignores parameter uncertainty.** `μ ± z·σ` leaves out uncertainty in μ, which dominates at n = 1–4. Use a predictive interval, e.g. a t-distribution or σ√(1 + 1/n_eff). Cycle lengths are also right-skewed, so a symmetric normal interval mis-covers.
  - **MAD collapses at small n.** With 3 cycles, MAD can be ~0, so ">3 MAD" excludes legitimate cycles. Excluding outliers then reporting calibration on the remainder is self-flattering, and the excluded cycles are precisely what users care about.
  - **Missed logs are not handled.** A forgotten period produces a ~2× cycle that should be detected as "probably missed log", not silently treated as an outlier or as a real cycle.
  - **The prior is biased.** Bull et al. data come from Natural Cycles users: ovulatory cycles only, ages 18–45, not on hormonal contraception, many using BBT. That is the wrong prior for the stated audience of 13–55, including teens (cycles of 21–45 d are normal for years after menarche) and perimenopause. Luteal SD is 2.4, not ~2.
  - **The fertile window is calendar arithmetic.** It is a fixed 7-day window (ovulation −5…+1) from a calendar estimate, with no propagation of cycle-length and luteal uncertainty. Only about 30% of women have their fertile window entirely within days 10–17 (Wilcox 2000). Showing a crisp 7-day window based on the calendar alone is exactly the "fake precision" the plan says it forbids, and it worsens M1.
  - **No hormonal-contraception switch in v1.** Birth-control mode is v1.5, but pill users will install v1 and get fertile windows and ovulation estimates that are meaningless.
- **Why it matters:** Miscalibrated predictions are the primary product failure mode. They are also a safety issue: a fertile window misread as safe days.
- **Evidence:**
  - Bull et al. 2019, Table 1: cycle 29.3 ± 5.2 d, luteal 12.4 ± 2.4 d; NC users, ovulatory cycles. https://pmc.ncbi.nlm.nih.gov/articles/PMC6710244/
  - Wilcox, Dunson & Baird, BMJ 2000;321:1259: https://www.bmj.com/content/321/7271/1259
- **Required fix:**
  - Use a hierarchical or Bayesian predictive model (e.g. normal-inverse-gamma or log-normal), with posterior-predictive intervals and a robust likelihood (t or mixture) instead of hard exclusion.
  - Add explicit missed-period detection.
  - Use age-band priors (teen, adult, 40+).
  - Derive the fertile window from the predictive distribution of ovulation, and show it as a widened band with "low confidence" until temperature or LH data exist.
  - Add a v1 "hormonal contraception: on/off" factor that suppresses fertility output, mapped to HealthKit `.contraceptive`.

### M9. Validation is circular, and the beta "calibration via email" breaks the legal premise  (§6.2 Quality, §8, §10)
- **Problem:**
  - **No usable dataset.** "Back-test against public datasets (if licences allow)": essentially none allow it. mcPHASES (the main open multimodal set, n = 42) is under PhysioNet's **Restricted Health Data Use Agreement**: research use only, no sharing, and code release required for publications. You cannot put it in a commercial CI pipeline. Other datasets (e.g. Kaggle copies of the Marquette FAM data) have unclear provenance and licence.
  - **Synthetic data proves nothing.** Generators encode the model's own assumptions, so a calibration gate on synthetic data only proves the code matches its spec.
  - **A beta cannot measure calibration.** A 1-week beta (§9 week 12) covers less than one cycle.
  - **The calibration study breaks the legal premise.** Collecting testers' exported health data by email (§10) makes the developer a GDPR controller of **Art. 9 special-category data**. That contradicts §7's "the developer processes no data", and needs explicit consent, a privacy notice, retention rules and probably a DPIA. A structured accuracy study with participants may also fall under guideline 5.1.3(iii)/(iv) (consent plus an ethics board) if framed as research.
- **Why it matters:** The "calibration 75–85%" claim, and any accuracy claim on the App Store (guideline 1.4.1 requires disclosed methodology), will be unsupported. Legal exposure follows from the email collection.
- **Evidence:**
  - mcPHASES: https://physionet.org/content/mcphases/ ; its DUA: https://www.physionet.org/content/mcphases/view-dua/1.0.0/
  - Guidelines 1.4.1 and 5.1.3(iii)/(iv): https://developer.apple.com/app-store/review/guidelines/
- **Required fix:**
  - Validate on the owner's and consenting testers' **retrospective** histories (years of Apple Health or Clue exports), processed **on the tester's own device** by a debug build that outputs only aggregate metrics. Never collect raw data.
  - Make no public accuracy claims at launch.
  - Keep the synthetic tests as regression tests only.
  - If raw data is ever collected, write a consent form, a DPIA and a retention policy first.

### M10. The timeline is roughly 3× too short, and the platform baseline is a year out of date  (§1, §9)
- **Problem:**
  - **The hours do not add up.** 12 weeks × 15–20 h = 180–240 hours. That has to cover: prototype plus validation with 5 users (recruiting alone takes a week); design spec; engine plus property tests; SwiftData store with migrations; four core screens; HealthKit two-way sync (M7, in **one** week); onboarding, notifications, app lock, settings and export/import (one week); widgets, App Intents, Control Center **and** the PDF report (one week); localisation, accessibility, performance and snapshot tests; and beta, fixes and submission (one week).
  - **Work items are missing entirely:**
    - clinician review lead time (weeks of external dependency);
    - legal review;
    - privacy policy, support URL and a hosted website (still needed with "servers €0");
    - App Store screenshots and descriptions in EN and DE;
    - the new age-rating questionnaire;
    - App Privacy details;
    - DSA trader declaration;
    - Beta App Review before external TestFlight;
    - demo video for App Review.
  - **The platform baseline is stale.** The plan, dated 22 Nov 2026, still says "Deployment target iOS 26.0 … re-evaluate iOS 27 APIs before launch". iOS 27 shipped on 14 Sep 2026, and Apple's Health redesign lands in 27.3. A spring-2027 launch will be built against the iOS 27 SDK, against a Health app that competes directly.
- **Why it matters:** A guaranteed slip far beyond "~14 weeks", or a cut-corner v1 shipped with data-integrity bugs. "Buffer: none" with a solo developer is not a plan.
- **Evidence:**
  - iOS 27 release, Health redesign timing: https://www.macrumors.com/guide/ios-27-health-app/ ; https://9to5mac.com/2026/11/03/ios-27-3-adds-all-new-apple-health-app-details-here/
  - The rest is arithmetic.
- **Required fix:**
  - Re-estimate bottom-up in hours, with 30% buffer. Either accept 6–8 months, or cut v1 to: period logging, symptoms, predictions, calendar, HealthKit import + write-own, export, basic widget.
  - Defer to v1.1: Control Center control, Siri, the PDF report and the wrist-temperature work.
  - Add the missing work items as explicit timeline rows.
  - Re-baseline on iOS 27 (decide whether the minimum is 26 or 27) and review the 27.x Health changes before the design spec.

### M11. Individual developer account, personal liability and an under-budgeted legal setup  (§1 Team, §7, §11, §12)
- **Problem:**
  - **Rejection risk.** Guideline 5.1.1(ix) says apps in highly regulated fields **including healthcare**, "or that require sensitive user information", "should be submitted by a legal entity… and not by an individual developer". The plan never mentions this, and it is a concrete rejection risk for a cycle tracker submitted from a personal account.
  - **Personal liability.** With no entity, any liability claim (e.g. "the app said I wasn't fertile") lands on the developer personally. A disclaimer is not a liability shield in consumer law.
  - **Public home address.** In the EU, a tip jar or any monetisation tends toward "trader" status under the DSA, which **publishes the developer's address and phone number** on the App Store page. That is a real doxxing risk for someone publishing a reproductive-health app in the post-Dobbs climate.
  - **The legal budget is unrealistic.** €500–1,500 covers maybe a privacy-policy review. It does not cover an MDR/FDA qualification opinion (M1), plus GDPR, plus MHMDA, plus disclaimer wording.
- **Why it matters:** Rejection at submission, personal financial exposure, and personal safety.
- **Evidence:**
  - Guideline 5.1.1(ix): https://developer.apple.com/app-store/review/guidelines/#data-collection-and-storage
  - DSA trader requirements: https://developer.apple.com/help/app-store-connect/manage-compliance-information/manage-european-union-digital-services-act-trader-requirements ; https://www.macrumors.com/2024/10/17/developers-eu-app-store-trader-requirements/
- **Required fix:**
  - Decide now whether to form an entity (UG/GmbH, LLC, or a non-profit e.V. like drip's collective model) and enrol as an Organization; this needs a D-U-N-S number and adds weeks.
  - Get liability insurance quotes.
  - Settle the tip-jar question against its DSA consequences.
  - Re-budget legal at €3–10k and schedule it.

### M12. The pattern engine statistics are incoherent and will produce false or zero insights  (§6.3, v1.5 headline feature)
- **Problem:**
  - **Mixed paradigms.** Benjamini–Hochberg is a frequentist FDR procedure on p-values. Applying it "across all S×P tests" on top of a Bayesian rule (P(lift > 1.5) ≥ 0.9) is not a defined procedure.
  - **Non-independent days.** Treating days as independent Bernoulli trials inflates certainty massively, because symptoms are autocorrelated within cycles.
  - **Missing logs.** The biggest problem: **an absent log is not an absent symptom**. Users log when symptomatic (missing not at random), so "rate outside P" is badly biased unless days are explicitly marked "logged, nothing to report".
  - **Phase boundaries.** Phases have very different lengths (late luteal fixed at 5 d versus a variable follicular phase). The "ovulatory" phase depends on an ovulation estimate that is calendar-derived for most users (M8), which is circular.
  - **Too little data.** "≥ 3 distinct cycles" with ~20–40 S×P tests means either nothing passes (no insights for months, contradicting "useful on day 1") or spurious hits pass.
  - **Trend estimates.** Theil–Sen on 6 points has huge variance.
- **Why it matters:** This is the "intelligent" half of the vision. As designed, it will either be silent or wrong, and both outcomes undermine Principle 4 and the product rationale (M2).
- **Evidence:** Statistical reasoning. The MNAR logging problem is well documented in self-tracking literature, e.g. Pierson et al., Nat Hum Behav 2021 on Clue data: https://arxiv.org/abs/1909.11211
- **Required fix:**
  - Use one paradigm: a hierarchical Bayesian model per symptom with a cycle-level random effect, using only "active logging days" as the denominator. Add a "nothing else today" one-tap affordance.
  - Report effect sizes with credible intervals, ranked rather than thresholded, with copy like "early signal" versus "consistent pattern".
  - Pre-specify the minimum data per insight type.
  - Simulate the false-positive rate under the null with realistic logging sparsity before shipping.

---

## MINOR issues

### m1. Foundation Models: acceptable-use, guardrails and availability (§6.6)
Apple's AUP prohibits "inaccurate or dangerous outputs… in high-risk domains, including… medical". Document why template-grounded phrasing complies, keep the template path primary, and never let model output state numbers it was not given. Guardrails have well-documented false positives and regressions (reproductive and sexual content is a likely trigger), so handle `guardrailViolation` gracefully. The plan says to read `contextSize` at runtime, but it only exists from iOS 26.4, so it needs an availability check if the target is 26.0. Also relevant: device eligibility (Apple Intelligence devices only), the device language and Siri language needing to match, and the model download state. The keyword filter must cover DE too. Sources: https://developer.apple.com/support/terms/acceptable-use-requirements-for-the-foundation-models-framework/ ; https://developer.apple.com/forums/thread/787736 ; https://developer.apple.com/forums/thread/835777 ; https://www.infoq.com/news/2026/03/apple-foundation-models-context/ ; https://developer.apple.com/documentation/technotes/tn3193-managing-the-on-device-foundation-model-s-context-window

### m2. The age rating is out of date; the teen strategy is thin (§7)
"13+ (12+ in some regions)": 12+ no longer exists. Apple moved to 4+/9+/13+/16+/18+ in July 2025, and the questionnaire now asks about medical/wellness topics. Teens need teen-specific priors (M8), copy reviewed for minors, and a stance on parents with device access. Check the US state app-store age-assurance laws and Apple's Declared Age Range API. Source: https://developer.apple.com/news/?id=ks775ehf

### m3. The "one engine, mode changes copy" model is naive (§3)
Pregnancy, postpartum amenorrhoea, lactation, hormonal contraception and perimenopause change the **state machine**: cycles paused, withdrawal bleeds not counted as cycles, gaps expected. Model time-bounded "life-stage episodes/factors" (mirroring HealthKit `.contraceptive`, `.pregnancy`, `.lactation` with start/end) in the v1 data model, even if the UI comes later, to avoid a painful migration.

### m4. Cycle detection rules are fragile (§6.2)
- Apple Health imports often use flow value `unspecified`, which "≥ light" ignores.
- Onboarding provides a date with no flow.
- Other apps' `HKMetadataKeyMenstrualCycleStart` flags are ignored.
- A 7-day gap can split long or irregular bleeding (PCOS, perimenopause, postpartum) or merge breakthrough bleeding into a period.

Define precedence: user override > cycle-start flag > heuristic.

### m5. LH timing is misstated (§6.2)
Ovulation typically follows the **first positive** urinary LH test by about 24–48 h, not "12–36 h". Specify what "positive" means (first positive versus peak), and how multiple positives and "estrogen surge" results (a HealthKit `ovulationTestResult` value) are handled.

### m6. HealthKit mapping loses semantics (§6.4)
- Symptom types use `HKCategoryValueSeverity` (with `appetiteChanges` using its own enum), while `moodChanges` and `sleepChanges` use presence only. Cyclus's mood, energy and sleep-quality scales do not map to these.
- Cervical mucus has 5 HealthKit values, so richer "discharge" options cannot be written.
- `.intermenstrualBleeding` versus "spotting" as a flow level needs a rule.
- Check iOS 27 for new perimenopause/menopause symptom types.

Specify a lossless mapping table plus "not mirrored" fields.

### m7. "Zero network" verification is meaningless (§6.7)
Grepping for `URLSession` misses `WKWebView`, `Link`/`openURL`, `mailto:`, `AsyncImage` and system frameworks. `NSAllowsArbitraryLoads` relates to ATS and says nothing about whether the app uses the network. Instead, run a UI-test pass with the App Privacy Report or a proxy on device, and forbid `Network`/`WebKit` imports with a lint.

### m8. Privacy manifest details (§6.7)
App Group `UserDefaults` needs reason `1C8F.1` (not just `CA92.1`). Audit disk-space and boot-time APIs too, because SQLite and SwiftData may trigger them. Declare `NSPrivacyTracking=false` and empty collected types. Re-check the "Data Not Collected" label against mailto feedback (OK if optional and user-initiated) and against beta data collection (M9).

### m9. Notifications (§4, §6.7)
Discreet copy must be the **default**. "Late period" notifications can be harmful (pregnancy loss, infertility, coercive partners) and should be opt-in. Notification content mirrors to the paired Watch. Pending notification requests carry predicted dates outside the protected store.

### m10. "Erase everything" is incomplete (§6.7)
It cannot reach iCloud Backup snapshots, exported files, PDFs already shared, Health data on other devices if HealthKit deletion is unchecked, or Spotlight/intent donations. Say so in the confirmation UI, and make deleting Cyclus-authored HealthKit samples the default.

### m11. The clinician PDF is a leakage vector (§4)
Mark it "Confidential health information". Let the user exclude sexual activity, contraception and notes. Strip PDF metadata. Avoid writing temp files outside the protected container. A PDF shared through Mail lives forever in someone's inbox.

### m12. Smart ordering of the log sheet hurts speed and accessibility (§4)
Reordering items by cycle day breaks muscle memory and VoiceOver/Voice Control predictability, which works against the 3-second goal. Keep a stable order, user-pinnable, with a separate "suggested" row.

### m13. The test plan contradicts "no third-party dependencies" and Xcode Cloud quotas (§6.1, §8, §9)
Snapshot testing realistically means `swift-snapshot-testing`, a third-party dependency. The snapshot matrix (screens × light/dark × 3 Dynamic Type sizes × 2 languages) plus UI tests plus back-tests on every push will exhaust 25 Xcode Cloud hours a month. Run the heavy suites locally or nightly. Source: https://developer.apple.com/news?id=ik9z4ll6

### m14. The coverage target misses where the risk is (§8)
90% coverage on pure packages is cheap. The risky code (CycleHealth sync, CycleStore migrations, multi-process access, locked-device behaviour) has no test strategy. Add HealthKit integration tests on a device, migration tests across schema versions, and a locked-device test checklist.

### m15. Performance targets lack context (§8)
"Cold start < 400 ms to interactive Today" on which device? Face ID gating adds about 0.5–1 s and dominates. Define the device class (e.g. iPhone 12) and measure up to "first frame after auth". The 10-year perf fixture is fine; add a 10-year HealthKit import time budget.

### m16. The onboarding "≤ 60 s, every step skippable" and "useful on day 1" are in tension (§2, §4)
A user who skips everything gets prior-only predictions that are wrong for most people. Show "no prediction yet" rather than a 29-day guess, or show the very wide prior interval honestly. The HealthKit sheet with ~35 toggles alone takes 30+ s.

### m17. Success metrics are not measurable in any useful timeframe (§10)
100 ratings, with no prompt before 3 cycles, for a niche free app, could take a year. App Analytics retention covers only opt-in users, so the sample is small. "5/5 testers" is n = 5. Calibration by email is a legal problem (M9). Define launch +90-day targets that can actually be observed (e.g. TestFlight session counts, qualitative interviews, crash-free rate).

### m18. The clinician review is unscheduled and its scope is undefined (§7, §9)
Educational copy, insight templates, the anomaly-nudge thresholds ("3 cycles < 21 d"), the PDF layout and the disclaimers all need review, and all are absent from the timeline. Get a named reviewer and turnaround agreed before week 3.

### m19. Localisation and German-market specifics (§1, §9)
DE medical terminology review (Periode/Regelblutung/Menstruation, Zervixschleim), DE App Store assets, and date-range formatting all need handling. Check whether an Impressum (DDG §5) is needed for the support site. "Localisation DE" as half of week 11 is not enough.

### m20. Inclusivity gaps (§3, §5)
"Anyone who menstruates", yet the modes are framed by age bands. There is no factor for testosterone or gender-affirming hormones (breakthrough bleeding, suppressed cycles), and "Conceive" assumes a partner model. Gender-neutral "you" is good, but audit the HealthKit-derived and medical copy for gendered terms.

### m21. Accessibility of the phase palette and ring (§5)
Muted rose, sage, amber and lavender on a warm neutral base will likely fail WCAG contrast, especially in dark mode and for colour-blind users (sage versus amber). A gradient arc for ranges is hard to convey via VoiceOver. Specify contrast ratios and the audio-graph and rotor behaviour in the design spec.

### m22. MHMDA / US state law analysis is too confident (§7)
The WA Act defines "collect" to include "access, retain… or otherwise process… in any manner". Whether shipping on-device processing software makes the developer a "regulated entity" is unresolved, not "largely not triggered". Nevada SB 370 and the Connecticut amendments are similar. Have counsel confirm, and publish the Consumer Health Data Privacy Policy anyway. Source: https://lawfilesext.leg.wa.gov/Law/RCWPDF/RCW%20%2019%20%20TITLE/RCW%20%2019%20.373%20%20CHAPTER/RCW%20%2019%20.373%20%20COMBINEDCHAPTER.pdf

### m23. GDPR "minimal obligations" overlooks the real data flows (§7)
Mailto feedback (users will email health details), TestFlight tester identities, opt-in crash logs and the beta data (M9) all make the developer a controller. The privacy policy must cover these, including Art. 9 handling of unsolicited health data in emails.

### m24. The sustainability plan is a question mark (§11, §14)
"Fully free" with no entity, no revenue and one developer means users are trusting sensitive long-term health data to an app with a high abandonment risk. Commit publicly to an export format spec (versioned JSON schema) and an end-of-life plan. If tips are wanted, they must use IAP, and they trigger DSA trader status (M11).

---

## Unverified claims

- **CloudKit `encryptedValues` vs guideline 5.1.3(ii).** The plan correctly notes that Apple has not defined this, and I found no authoritative ruling either. Also unclear: whether Apple treats a store included in iCloud Backup, or user-initiated exports to iCloud Drive, as "storing personal health information in iCloud". Ask App Review in writing and keep the reply.
- **Apple Cycle Tracking FDA status.** The IFU clearly frames it as a regulated device in the US and EU, but I did not find the FDA submission number or product code. Search the FDA 510(k)/De Novo databases for "Apple Cycle Tracking" to identify the predicate or classification regulation. That tells you which claims trigger device status in the US.
- **Health Sharing scope.** Whether Health Sharing includes Cycle Tracking categories, including sexual activity, and whether recipients get notifications for them. Verify on device (Health › Sharing › Share with someone › choose topics).
- **New HealthKit types in iOS 27.** Whether iOS 27 introduces perimenopause/menopause status or symptom identifiers. Check the iOS 27 HealthKit "What's New" and API diffs.
- **Watch models with temperature sensing.** The plan says "Series 8+". Apple's documentation names Series 8 and Ultra; whether SE 3 and newer models also produce `appleSleepingWristTemperature` was not verified.
- **Foundation Models on reproductive content.** How the guardrails behave on menstrual, sexual-activity and pregnancy content specifically. No public reports were found; prototype it with real Insight payloads in EN and DE.
- **App Analytics.** Whether App Store Connect App Analytics still reports 30-day retention for opt-in users at the granularity assumed, and the typical opt-in rate.
- **Sensiplan rule details.** The rule text (third reading ≥ 0.2 °C, exception rules) is from the cited literature and memory. Verify against the official Sensiplan handbook (Arbeitsgruppe NFP).
- **US app-store age laws.** The status of state app-store age-verification laws (Texas SB 2420, Utah, Louisiana) and whether they place obligations on the developer of a 13+ health app.
- **Impressum.** Whether German Impressum (DDG §5) duties apply to a free app and support site run by a private individual.

---

## Top 5 changes before writing the design spec

1. **Settle the regulatory scope (M1).** Either cut the fertile window, Conceive mode and "confirmed ovulation" from v1, or obtain a written MDR/FDA qualification opinion first. Fix the citations (MDR Art. 2(1) "support of conception", Rule 15, FDA Jan 2026 guidance), and decide on a legal entity and Organization account (M11).
2. **Rewrite the positioning against iOS 27 Apple Health (M2).** Pick 1–2 wedges, validate them with current Apple Health cycle-tracking users in week 1, and rebuild the v1 scope around the winning wedge. Cut, in favour of that wedge: Control Center, Siri, the PDF report and wrist temperature.
3. **Write a threat model and a data-flow and protection-class map (M4, M5, M6).** For each surface (lock screen, widget, intents, HealthKit mirror, Health Sharing, iCloud Backup, exports, notifications), record the data class, protection class, adversary and mitigation. This yields: opt-in per-category HealthKit writes, authenticated intents, encrypted export, an explicit backup decision, and honest privacy copy.
4. **Specify the engine properly (M3, M7, M8, M12).**
   - Posterior-predictive intervals, robust outlier handling, missed-period detection and age-band priors.
   - A fertile window derived from ovulation uncertainty.
   - A wrist-temperature baseline model on absolute values.
   - A correct BBT rule.
   - The HealthKit sync state machine.
   - A pattern-engine model that treats "not logged" as missing.
   - Validation on on-device retrospective histories, with no raw-data collection (M9).
5. **Re-plan delivery honestly (M10).** Do a bottom-up hours estimate with buffer, against an iOS 27 baseline. Add the missing work items: legal, clinician, entity setup, store assets, website, Beta App Review, age rating, DSA. Expect 6–8 months for the current scope, or cut to a v1 that fits 12 weeks.

# Cyclus parity matrix

One row per user-visible feature. Rules: ANDROID_PLAN §1. A release ships only when every row is **done** on both platforms, or flagged off on both. Every PR that touches UI updates its row.

Status values: `—` not started · `wip` · `done` · `off` (feature-flagged off) · `n/a` (allowed platform difference, ANDROID_PLAN §1.1).

| ID | Feature | Spec § | Prototype screen | Android | iOS | Screenshot pair | Notes |
|---|---|---|---|---|---|---|---|
| E-001 | Cycle detection + prediction (`predict`) | 11.5 | engine | done | — | n/a | Shared Kotlin, golden vectors |
| E-002 | Fertility engine (`fertility`, `trail`) | 11.11 | engine | done | — | n/a | Shared Kotlin, golden vectors |
| E-003 | Learning from history (`learnFromHistory`) | 11.11.5 | engine | done | — | n/a | Shared Kotlin, golden vectors |
| E-004 | Pattern watch („Deine Muster“) | 11.12 | engine | done | — | n/a | Shared Kotlin, golden vectors |
| E-005 | Outlook („Ausblick“) | 11.13 | engine | done | — | n/a | Shared Kotlin, golden vectors |
| S-001 | Encrypted local store | 11.2–11.3 | — | done | — | n/a | Android: SQLCipher + Keystore. iOS: Data Protection `.complete` |
| S-002 | Backup / encrypted export | 11.9 | Privacy | — | — | | ANDROID_PLAN §6.4 undecided; backup disabled until then |
| F-001 | Today ring | 7.2 | Heute | done | — | | |
| F-002 | Today states (empty, between, pre, expected, later, in period, paused) | 7.2 | Heute | done | — | | Episodes UI (start/stop a pause) in F-018 |
| F-003 | „Periode hat heute begonnen“ + undo | 7.2 | Heute | done | — | | |
| F-004 | Ausblick card | 11.13 | Heute | done | — | | |
| F-005 | Log sheet | 7.3 | Eintragen | done | — | | „Bearbeiten“ (pinned) and „+ Neues Stichwort“ shown disabled |
| F-006 | Pain body map | 7.3 | Eintragen | done | — | | |
| F-007 | Calendar + Day sheet | 7.4 | Kalender | done | — | | Long-press menu + TalkBack custom action „Periodenbeginn“ (past/today only) |
| F-008 | Insights (stats, charts, heatmap, history) | 7.5 | Einblicke | done | — | | History rows not tappable until Cycle detail (7.6) exists |
| F-009 | „Deine Muster“ cards | 11.12 | Einblicke | done | — | | Muted patterns are persisted (prototype: session only). „Im Arztbericht enthalten“ hidden until F-017 |
| F-010 | Why this window? / Method sheets | 7.14 | Sheets | done | — | | Method copy: Android override (no iPhone, no outlier-question claim) |
| F-011 | Conceive mode UI | 7.14 | Heute (Kinderwunsch) | done | — | | Regulatory feature flag per region still to add before release |
| F-012 | Onboarding | 7.1 | Onboarding | — | — | | |
| F-013 | Privacy Center | 7.8 | Datenschutz | — | — | | |
| F-014 | Settings + in-app language | 4 | Einstellungen | wip | — | | Language (Android 13+), goal, show fertile window, life stage done; notifications row → A4 |
| F-015 | App lock (biometric + PIN) | 7.12 | Sperre | — | — | | |
| F-016 | Erase everything | 7.9 | Sheet | — | — | | |
| F-017 | Doctor Visit Pack (PDF) | 7.7 | Arztbericht | — | — | | |
| F-018 | Episodes (pill, pregnancy, …) | 2.2 | Einstellungen | wip | — | | Pill / pregnant / postpartum pause predictions (undoable). Conditions (peri, PCOS, endo, T) hidden until the engine widens ranges for them |
| P-001 | Health store sync | 11.4 | — | — | — | n/a | Health Connect vs HealthKit |
| P-002 | Widgets | 8.1 | — | — | — | | Glance vs WidgetKit |
| P-003 | Quick action (QS tile / Control) | 8.2 | — | — | — | n/a | v1.1 |
| P-004 | Notifications | 8.3 | — | — | — | | |
| X-001 | Shared string source → strings.xml + xcstrings | ANDROID_PLAN 2.2 | — | wip | — | n/a | Interim: `tools/strings/export.mjs` generates Android resources from the prototype's `TX` table |

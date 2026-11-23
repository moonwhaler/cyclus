# Reference screenshots

Screens of the **Android app** (the reference implementation), German, light theme unless noted, demo data from the prototype scenarios. Use them next to `prototype/index.html` when building or reviewing another platform (IOS_PLAN §1.2) and for visual parity checks (`PARITY.md`).

Regenerate after UI changes (emulator only — it erases the debug app's data):

```sh
tools/deploy.sh                       # debug build on the emulator
python3 tools/screens/capture.py      # German set → docs/screens/android/
python3 tools/screens/capture.py --lang en   # English set → docs/screens/android-en/
python3 tools/screens/capture.py --only today # one area
```

Dates shown are relative to the capture day (2026-11-23). Images are 640 px wide (50 %), 256-colour PNG.

## Onboarding

<table><tr><td valign="top"><a href="android/01-onboarding-1-welcome.png"><img src="android/01-onboarding-1-welcome.png" width="200" alt="Welcome"></a><br><sub>Welcome</sub></td><td valign="top"><a href="android/01-onboarding-2-privacy.png"><img src="android/01-onboarding-2-privacy.png" width="200" alt="Privacy promises"></a><br><sub>Privacy promises</sub></td><td valign="top"><a href="android/01-onboarding-3-notice.png"><img src="android/01-onboarding-3-notice.png" width="200" alt="„Bevor du startest“ (required consent)"></a><br><sub>„Bevor du startest“ (required consent)</sub></td><td valign="top"><a href="android/01-onboarding-4-import.png"><img src="android/01-onboarding-4-import.png" width="200" alt="Import (.cyclus or health store)"></a><br><sub>Import (.cyclus or health store)</sub></td></tr><tr><td valign="top"><a href="android/01-onboarding-5-last-period.png"><img src="android/01-onboarding-5-last-period.png" width="200" alt="Last period"></a><br><sub>Last period</sub></td><td valign="top"><a href="android/01-onboarding-6-life-stage.png"><img src="android/01-onboarding-6-life-stage.png" width="200" alt="Life stage / Kinderwunsch"></a><br><sub>Life stage / Kinderwunsch</sub></td><td valign="top"><a href="android/01-onboarding-7-app-lock.png"><img src="android/01-onboarding-7-app-lock.png" width="200" alt="App lock offer"></a><br><sub>App lock offer</sub></td></tr></table>

## Today

<table><tr><td valign="top"><a href="android/02-today-empty.png"><img src="android/02-today-empty.png" width="200" alt="Today, no data"></a><br><sub>Today, no data</sub></td><td valign="top"><a href="android/02-today-ring-explainer.png"><img src="android/02-today-ring-explainer.png" width="200" alt="„So liest du den Ring“"></a><br><sub>„So liest du den Ring“</sub></td><td valign="top"><a href="android/02-today-track-1.png"><img src="android/02-today-track-1.png" width="200" alt="Today (track), top"></a><br><sub>Today (track), top</sub></td><td valign="top"><a href="android/02-today-track-2.png"><img src="android/02-today-track-2.png" width="200" alt="Today, cards"></a><br><sub>Today, cards</sub></td></tr><tr><td valign="top"><a href="android/02-today-track-3.png"><img src="android/02-today-track-3.png" width="200" alt="Today, bottom (Demnächst, patterns, Ansicht anpassen)"></a><br><sub>Today, bottom (Demnächst, patterns, Ansicht anpassen)</sub></td></tr></table>

## Customizable Today

<table><tr><td valign="top"><a href="android/03-edit-1.png"><img src="android/03-edit-1.png" width="200" alt="Edit mode: ring size, cards"></a><br><sub>Edit mode: ring size, cards</sub></td><td valign="top"><a href="android/03-edit-2.png"><img src="android/03-edit-2.png" width="200" alt="Edit mode: more cards"></a><br><sub>Edit mode: more cards</sub></td><td valign="top"><a href="android/03-edit-3-library.png"><img src="android/03-edit-3-library.png" width="200" alt="Edit mode: library + reset"></a><br><sub>Edit mode: library + reset</sub></td><td valign="top"><a href="android/03-edit-compact-ring.png"><img src="android/03-edit-compact-ring.png" width="200" alt="Edit mode, compact ring"></a><br><sub>Edit mode, compact ring</sub></td></tr><tr><td valign="top"><a href="android/03-edit-quick-picks.png"><img src="android/03-edit-quick-picks.png" width="200" alt="Deine Schnellauswahl"></a><br><sub>Deine Schnellauswahl</sub></td><td valign="top"><a href="android/03-today-compact-1.png"><img src="android/03-today-compact-1.png" width="200" alt="Today, compact ring"></a><br><sub>Today, compact ring</sub></td><td valign="top"><a href="android/03-today-compact-2-week.png"><img src="android/03-today-compact-2-week.png" width="200" alt="Today: Woche card"></a><br><sub>Today: Woche card</sub></td></tr></table>

## Log sheet

<table><tr><td valign="top"><a href="android/04-log-1.png"><img src="android/04-log-1.png" width="200" alt="Log sheet: flow, quick picks, mood"></a><br><sub>Log sheet: flow, quick picks, mood</sub></td><td valign="top"><a href="android/04-log-2.png"><img src="android/04-log-2.png" width="200" alt="Log sheet: pain map, feelings, scales"></a><br><sub>Log sheet: pain map, feelings, scales</sub></td><td valign="top"><a href="android/04-log-3.png"><img src="android/04-log-3.png" width="200" alt="Log sheet: tags, „Sonst nichts heute“"></a><br><sub>Log sheet: tags, „Sonst nichts heute“</sub></td><td valign="top"><a href="android/04-log-4-more.png"><img src="android/04-log-4-more.png" width="200" alt="Log sheet: „Weitere Angaben“ open"></a><br><sub>Log sheet: „Weitere Angaben“ open</sub></td></tr></table>

## Calendar

<table><tr><td valign="top"><a href="android/05-calendar-1.png"><img src="android/05-calendar-1.png" width="200" alt="Calendar"></a><br><sub>Calendar</sub></td><td valign="top"><a href="android/05-calendar-2-day.png"><img src="android/05-calendar-2-day.png" width="200" alt="Calendar: inline day detail"></a><br><sub>Calendar: inline day detail</sub></td><td valign="top"><a href="android/05-calendar-3-legend.png"><img src="android/05-calendar-3-legend.png" width="200" alt="Calendar: legend"></a><br><sub>Calendar: legend</sub></td></tr></table>

## Insights & patterns

<table><tr><td valign="top"><a href="android/06-insights-1.png"><img src="android/06-insights-1.png" width="200" alt="Insights, top"></a><br><sub>Insights, top</sub></td><td valign="top"><a href="android/06-insights-2.png"><img src="android/06-insights-2.png" width="200" alt="Insights: charts"></a><br><sub>Insights: charts</sub></td><td valign="top"><a href="android/06-insights-3.png"><img src="android/06-insights-3.png" width="200" alt="Insights: heatmap"></a><br><sub>Insights: heatmap</sub></td><td valign="top"><a href="android/06-insights-4.png"><img src="android/06-insights-4.png" width="200" alt="Insights: history"></a><br><sub>Insights: history</sub></td></tr><tr><td valign="top"><a href="android/06-method-sheet.png"><img src="android/06-method-sheet.png" width="200" alt="„So funktionieren Prognosen“"></a><br><sub>„So funktionieren Prognosen“</sub></td><td valign="top"><a href="android/06-patterns-1.png"><img src="android/06-patterns-1.png" width="200" alt="Muster screen"></a><br><sub>Muster screen</sub></td><td valign="top"><a href="android/06-patterns-2.png"><img src="android/06-patterns-2.png" width="200" alt="Muster: cards"></a><br><sub>Muster: cards</sub></td></tr></table>

## Doctor Visit Pack (Arztbericht)

<table><tr><td valign="top"><a href="android/07-report-1.png"><img src="android/07-report-1.png" width="200" alt="Arztbericht builder"></a><br><sub>Arztbericht builder</sub></td><td valign="top"><a href="android/07-report-2.png"><img src="android/07-report-2.png" width="200" alt="Arztbericht: sections, questions"></a><br><sub>Arztbericht: sections, questions</sub></td><td valign="top"><a href="android/07-report-3-pdf.png"><img src="android/07-report-3-pdf.png" width="200" alt="Arztbericht: PDF preview"></a><br><sub>Arztbericht: PDF preview</sub></td><td valign="top"><a href="android/07-report-4-pdf.png"><img src="android/07-report-4-pdf.png" width="200" alt="Arztbericht: PDF page"></a><br><sub>Arztbericht: PDF page</sub></td></tr></table>

## Privacy Center

<table><tr><td valign="top"><a href="android/08-privacy-1.png"><img src="android/08-privacy-1.png" width="200" alt="Datenschutz: where your data is"></a><br><sub>Datenschutz: where your data is</sub></td><td valign="top"><a href="android/08-privacy-2.png"><img src="android/08-privacy-2.png" width="200" alt="Datenschutz: protection"></a><br><sub>Datenschutz: protection</sub></td><td valign="top"><a href="android/08-privacy-3.png"><img src="android/08-privacy-3.png" width="200" alt="Datenschutz: visibility, Health Connect"></a><br><sub>Datenschutz: visibility, Health Connect</sub></td><td valign="top"><a href="android/08-privacy-4.png"><img src="android/08-privacy-4.png" width="200" alt="Datenschutz: backups, legal, erase"></a><br><sub>Datenschutz: backups, legal, erase</sub></td></tr><tr><td valign="top"><a href="android/08-privacy-erase.png"><img src="android/08-privacy-erase.png" width="200" alt="Alles löschen"></a><br><sub>Alles löschen</sub></td><td valign="top"><a href="android/08-privacy-export.png"><img src="android/08-privacy-export.png" width="200" alt="Encrypted backup (export)"></a><br><sub>Encrypted backup (export)</sub></td><td valign="top"><a href="android/08-privacy-notice-history.png"><img src="android/08-privacy-notice-history.png" width="200" alt="Wichtiger Hinweis + confirmations"></a><br><sub>Wichtiger Hinweis + confirmations</sub></td></tr></table>

## Settings & notifications

<table><tr><td valign="top"><a href="android/09-notifications.png"><img src="android/09-notifications.png" width="200" alt="Mitteilungen"></a><br><sub>Mitteilungen</sub></td><td valign="top"><a href="android/09-settings-1.png"><img src="android/09-settings-1.png" width="200" alt="Settings: appearance"></a><br><sub>Settings: appearance</sub></td><td valign="top"><a href="android/09-settings-2.png"><img src="android/09-settings-2.png" width="200" alt="Settings: language, goal, fertile window"></a><br><sub>Settings: language, goal, fertile window</sub></td><td valign="top"><a href="android/09-settings-3.png"><img src="android/09-settings-3.png" width="200" alt="Settings: life stage, more"></a><br><sub>Settings: life stage, more</sub></td></tr></table>

## App lock

<table><tr><td valign="top"><a href="android/10-lock-pin-setup.png"><img src="android/10-lock-pin-setup.png" width="200" alt="Cyclus-PIN setup"></a><br><sub>Cyclus-PIN setup</sub></td><td valign="top"><a href="android/10-lock-screen.png"><img src="android/10-lock-screen.png" width="200" alt="App lock screen"></a><br><sub>App lock screen</sub></td></tr></table>

## Today: cycle states

<table><tr><td valign="top"><a href="android/11-today-expected.png"><img src="android/11-today-expected.png" width="200" alt="Today: period expected"></a><br><sub>Today: period expected</sub></td><td valign="top"><a href="android/11-today-in-period.png"><img src="android/11-today-in-period.png" width="200" alt="Today: in period"></a><br><sub>Today: in period</sub></td><td valign="top"><a href="android/11-today-paused.png"><img src="android/11-today-paused.png" width="200" alt="Today: paused (pill)"></a><br><sub>Today: paused (pill)</sub></td></tr></table>

## Kinderwunsch (conceive mode)

<table><tr><td valign="top"><a href="android/12-conceive-1.png"><img src="android/12-conceive-1.png" width="200" alt="Kinderwunsch: Today"></a><br><sub>Kinderwunsch: Today</sub></td><td valign="top"><a href="android/12-conceive-2.png"><img src="android/12-conceive-2.png" width="200" alt="Kinderwunsch: cards"></a><br><sub>Kinderwunsch: cards</sub></td><td valign="top"><a href="android/12-conceive-2ww.png"><img src="android/12-conceive-2ww.png" width="200" alt="Kinderwunsch: after ovulation"></a><br><sub>Kinderwunsch: after ovulation</sub></td><td valign="top"><a href="android/12-conceive-calendar.png"><img src="android/12-conceive-calendar.png" width="200" alt="Kinderwunsch: calendar"></a><br><sub>Kinderwunsch: calendar</sub></td></tr><tr><td valign="top"><a href="android/12-conceive-why-sheet.png"><img src="android/12-conceive-why-sheet.png" width="200" alt="„Warum dieses Fenster?“"></a><br><sub>„Warum dieses Fenster?“</sub></td></tr></table>

## Dark theme

<table><tr><td valign="top"><a href="android/13-dark-calendar.png"><img src="android/13-dark-calendar.png" width="200" alt="Dark: Calendar"></a><br><sub>Dark: Calendar</sub></td><td valign="top"><a href="android/13-dark-insights.png"><img src="android/13-dark-insights.png" width="200" alt="Dark: Insights"></a><br><sub>Dark: Insights</sub></td><td valign="top"><a href="android/13-dark-log.png"><img src="android/13-dark-log.png" width="200" alt="Dark: Log sheet"></a><br><sub>Dark: Log sheet</sub></td><td valign="top"><a href="android/13-dark-today.png"><img src="android/13-dark-today.png" width="200" alt="Dark: Today"></a><br><sub>Dark: Today</sub></td></tr></table>

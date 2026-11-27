# Cyclus privacy policy

Effective date: 27 November 2026 · [Deutsche Fassung](PRIVACY.de.md)

This policy covers the Cyclus app for Android, the Cyclus website (moonwhaler.github.io/cyclus) and what happens when
you contact the developer. An iOS version is planned. This policy will be updated before it is released.

## The short version

- **We never receive your data. There are no Cyclus servers.** The app has no internet permission. The build checks
  this automatically, and anyone can verify it in the public source code.
- No account, no ads, no analytics, no tracking, no crash-reporting service, and no third-party code that sends data.
- What you log is stored encrypted on your phone. Copies leave the phone only if you make them: Health Connect, device
  backup, an export file, or a PDF you share.
- We hold no data about you. That means we cannot sell it, share it, or hand it to anyone, including in response to
  court orders or law-enforcement requests.

## 1. Who is responsible

Responsible for this website and for answering your messages (controller under the GDPR):

Christian Petersen
Email: [me@nordinary.com](mailto:me@nordinary.com)

Cyclus is a free, open-source app (GPL-3.0) by a single developer. We are not legally required to appoint a data
protection officer.

## 2. Data in the app: processed only on your device

**What the app stores.** Only what you enter or import, for example:
- period and bleeding days, spotting, symptoms, moods and notes;
- sexual activity, contraception, basal body temperature, cervical mucus, ovulation and pregnancy tests;
- life stages you choose, such as pregnancy, postpartum or perimenopause, and conditions such as PCOS;
- your settings (goal, reminders, app lock).

Predictions and patterns are calculated on the phone from this data.

**Where it is stored.** In an encrypted database (SQLCipher, AES-256) in the app's private storage on your phone. The
key is protected by the phone's secure hardware (Android Keystore). An optional app lock (biometrics or a Cyclus PIN)
protects the app itself.

**Who can access it.** Only you, through the app on your phone. The developer has no access: no copy is ever sent to
us. Storing this data on your device is strictly necessary to provide the app you asked for
(§ 25(2) no. 2 TDDDG, the German law on device storage). No consent banner is needed, and there are no cookies.

**Deleting it.** "Privacy › Erase everything" deletes all Cyclus data on the phone. It can also delete what Cyclus wrote
to Health Connect. Uninstalling the app also deletes its data. We cannot delete copies you exported or shared, or
backups that were made earlier.

## 3. When data leaves your phone (only if you choose)

### 3.1 Health Connect
Health Connect is Android's health data store on your phone, provided by Google. Using it with Cyclus is optional.
- **Reading:** if you allow it, Cyclus reads menstruation, intermenstrual bleeding, basal body temperature, cervical
  mucus, ovulation tests and sexual activity. This includes entries older than 30 days if you allow access to past data.
  These come from other apps and watches, so your history and predictions are complete. Cyclus reads only while it is
  open.
- **Writing:** only for the categories you turn on in Cyclus. All are off by default. Other apps you allow in Health
  Connect can read what Cyclus writes.
- You can remove access at any time in Health Connect or in Cyclus under Privacy › Health Connect.

Data from Health Connect stays in the app on your phone. It is never sent to us or to anyone else. It is not used for
advertising, it is not sold, it is not used to decide on credit or insurance, and it is not used to train AI models.
Cyclus's use of information received from Health Connect adheres to the
[Health Connect Permissions policy](https://support.google.com/googleplay/android-developer/answer/12991134),
including the Limited Use requirements.

### 3.2 Android device backup
If your phone's backup is switched on, Cyclus adds an encrypted snapshot of your data to it. The database itself is
never backed up. The snapshot is sent only in two cases:
- the backup is end-to-end encrypted with your screen lock, so Google cannot read it;
- you are moving directly from one phone to another.

Otherwise nothing is added. You can turn this off with "Device only" under Privacy › Backups. The backup is stored by
Google as part of your Google account, and [Google's privacy policy](https://policies.google.com/privacy) applies to
it. We have no access to it.

### 3.3 Exports and the Doctor Visit Pack
- **Backup file (`.cyclus`):** encrypted with a passphrase you choose and saved wherever you pick. If you save it to
  a cloud folder, that provider's terms apply.
- **Doctor Visit Pack (PDF):** you choose which sections are included. Sexual activity, contraception and notes are left
  out by default. You share it through Android's share menu with the app or person you pick. From then on, the
  recipient's privacy rules apply.

### 3.4 Notifications and widgets
Reminders are scheduled on the phone, and no server is involved. By default their text is discreet (for example
"Cyclus · Check-in") and never shows dates or symptoms. Home-screen widgets show cycle days only if you turn on
"Details in widget". Notifications and widgets can be seen by anyone who sees your screen.

## 4. Google Play and GitHub (where you get the app)

- **Google Play:** Google processes data to run the store, the download and updates. See
  [Google's privacy policy](https://policies.google.com/privacy). If you allowed your phone to share usage and
  diagnostics data, Google gives us crash reports and statistics, such as installs by country, device and Android
  version. These never contain what you logged in Cyclus. We use them only to find and fix errors and to see which
  devices to support. Legal basis: our legitimate interest in a working app (Art. 6(1)(f) GDPR).
- **GitHub releases:** if you download Cyclus from GitHub, GitHub processes the download (see the
  [GitHub Privacy Statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement)).

## 5. This website

This website is hosted on GitHub Pages by GitHub, Inc., 88 Colin P. Kelly Jr. Street, San Francisco, CA 94107, USA.
When you visit, GitHub records your IP address and technical request data in server logs to keep the service secure
(see the GitHub Privacy Statement above). We have no access to these logs. The site uses no cookies, no analytics, no
tracking and no externally loaded fonts or scripts. Legal basis: our legitimate interest in reliable, secure hosting
(Art. 6(1)(f) GDPR). GitHub may process this data in the USA. For such transfers it relies on the EU-U.S. Data Privacy
Framework and the EU Standard Contractual Clauses (Art. 45 and 46 GDPR).

## 6. Contacting us

If you email us, we process your email address and your message only to answer you. Legal basis: Art. 6(1)(b) GDPR for
questions about the app, otherwise Art. 6(1)(f) GDPR. We delete the messages no later than 90 days after your request
is closed, unless the law requires us to keep them longer. Please don't send health information you don't need to.
Issues and discussions on GitHub are public and are governed by GitHub's terms.

## 7. Your rights (EU and UK)

You have the right to access, rectification, erasure, restriction of processing, data portability and to object to
processing based on legitimate interest (Art. 15–21 GDPR). You can also lodge a complaint with a data protection
authority (Art. 77 GDPR), in particular in the EU country where you live. Email us to use your rights. For the data in
the app, you exercise them directly: you can see everything in the app, export it, and erase it (section 2). We hold
none of it.

Cyclus makes no automated decisions about you that have legal or similarly significant effects (Art. 22 GDPR).
Predictions are estimates shown only to you.

## 8. Your rights in the United States

- **No sale or sharing:** we do not sell or share personal information, we do not use it for targeted advertising, and
  we do not build profiles. This applies under the California Consumer Privacy Act (CCPA/CPRA) and the privacy laws of
  other states. Because nothing is tracked, there is nothing for Global Privacy Control or "Do Not Track" signals to
  stop.
- **Consumer health data:** this section is our consumer health data privacy policy under the Washington My Health My
  Data Act, Nevada SB 370 and similar state laws. Cyclus does not collect, share or sell consumer health data. The
  health information you log stays on your device under your control (sections 2 and 3). The only health-related
  information we could ever receive is what you choose to write in an email to us. We use it only to answer you and
  never share it. You can ask us to delete it at any time. These rights also cover anyone acting on your behalf.
- **Legal requests:** we have no app data, so we cannot hand any over.
- **Children:** Cyclus is not directed at children under 13, and we do not knowingly collect personal information from
  children.

## 9. Security

All data on the phone is encrypted, and the key is held by secure hardware. The app has no network access. Exports are
encrypted by default (AES-256-GCM). The [source code](https://github.com/moonwhaler/cyclus) is public, so anyone can
check these claims.

## 10. Changes

If the app or this website changes how data is handled, we update this policy and the date at the top before the change
takes effect. Earlier versions are in the
[history of this file](https://github.com/moonwhaler/cyclus/commits/main/PRIVACY.md).

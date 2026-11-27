# Cyclus – Datenschutzerklärung

Stand: 27. November 2026 · [English version](PRIVACY.md)

Diese Erklärung gilt für die Cyclus-App für Android, die Cyclus-Website (moonwhaler.github.io/cyclus) und für den
Fall, dass du den Entwickler kontaktierst. Eine iOS-Version ist geplant. Vor ihrem Erscheinen wird diese Erklärung
aktualisiert.

## Kurz gesagt

- **Wir erhalten deine Daten nie. Es gibt keine Cyclus-Server.** Die App hat keine Internet-Berechtigung. Der Build
  prüft das automatisch, und im öffentlichen Quellcode kann es jeder nachprüfen.
- Kein Konto, keine Werbung, keine Analyse, kein Tracking, kein Absturzberichte-Dienst und kein Fremdcode, der Daten
  sendet.
- Was du einträgst, wird verschlüsselt auf deinem Handy gespeichert. Kopien verlassen das Handy nur, wenn du sie
  anlegst: Health Connect, Gerätesicherung, eine Exportdatei oder ein PDF, das du teilst.
- Wir haben keine Daten über dich. Deshalb können wir sie weder verkaufen noch weitergeben noch herausgeben, auch nicht
  auf gerichtliche oder behördliche Anordnung.

## 1. Verantwortlicher

Verantwortlich für diese Website und für die Beantwortung deiner Nachrichten (Verantwortlicher im Sinne der DSGVO):

Christian Petersen
E-Mail: [me@nordinary.com](mailto:me@nordinary.com)

Cyclus ist eine kostenlose Open-Source-App (GPL-3.0) eines einzelnen Entwicklers. Ein Datenschutzbeauftragter ist
gesetzlich nicht vorgeschrieben.

## 2. Daten in der App: nur auf deinem Gerät verarbeitet

**Was die App speichert.** Nur, was du einträgst oder importierst, zum Beispiel:
- Perioden- und Blutungstage, Schmierblutungen, Symptome, Stimmungen und Notizen;
- sexuelle Aktivität, Verhütung, Basaltemperatur, Zervixschleim, Ovulations- und Schwangerschaftstests;
- Lebensphasen, die du auswählst, etwa Schwangerschaft, Wochenbett oder Perimenopause, und Erkrankungen wie PCOS;
- deine Einstellungen (Ziel, Erinnerungen, App-Sperre).

Vorhersagen und Muster berechnet das Handy selbst aus diesen Daten.

**Wo sie gespeichert werden.** In einer verschlüsselten Datenbank (SQLCipher, AES-256) im privaten Speicher der App
auf deinem Handy. Den Schlüssel schützt die Sicherheitshardware des Handys (Android Keystore). Eine optionale
App-Sperre (Biometrie oder Cyclus-PIN) schützt die App selbst.

**Wer darauf zugreifen kann.** Nur du, über die App auf deinem Handy. Der Entwickler hat keinen Zugriff: Es wird nie
eine Kopie an uns gesendet. Das Speichern auf deinem Gerät ist unbedingt erforderlich, damit die App funktioniert, die
du nutzen willst (§ 25 Abs. 2 Nr. 2 TDDDG). Ein Einwilligungsbanner ist daher nicht nötig, und es gibt keine Cookies.

**Löschen.** „Datenschutz › Alles löschen“ entfernt alle Cyclus-Daten auf dem Handy und auf Wunsch auch, was Cyclus
in Health Connect geschrieben hat. Auch beim Deinstallieren werden die Daten gelöscht. Kopien, die du exportiert oder
geteilt hast, und früher angelegte Sicherungen können wir nicht löschen.

## 3. Wann Daten dein Handy verlassen (nur, wenn du es willst)

### 3.1 Health Connect
Health Connect ist der Gesundheitsdatenspeicher von Android auf deinem Handy, bereitgestellt von Google. Die Nutzung mit
Cyclus ist freiwillig.
- **Lesen:** Wenn du es erlaubst, liest Cyclus Menstruation, Zwischenblutungen, Basaltemperatur, Zervixschleim,
  Ovulationstests und sexuelle Aktivität. Wenn du den Zugriff auf frühere Daten erlaubst, auch Einträge, die älter als
  30 Tage sind. Diese Daten stammen aus anderen Apps und von Uhren, damit Verlauf und Vorhersagen vollständig sind.
  Cyclus liest nur, solange die App geöffnet ist.
- **Schreiben:** nur für die Kategorien, die du in Cyclus einschaltest. Ab Werk sind alle aus. Andere Apps, die du in
  Health Connect zulässt, können lesen, was Cyclus schreibt.
- Den Zugriff kannst du jederzeit in Health Connect oder in Cyclus unter Datenschutz › Health Connect entziehen.

Daten aus Health Connect bleiben in der App auf deinem Handy. Sie werden nie an uns oder an Dritte gesendet. Sie werden
nicht für Werbung verwendet, nicht verkauft, nicht für Kredit- oder Versicherungsentscheidungen genutzt und nicht zum
Training von KI-Modellen verwendet. Die Nutzung von Informationen aus Health Connect durch Cyclus entspricht der
[Health-Connect-Richtlinie von Google](https://support.google.com/googleplay/android-developer/answer/12991134),
einschließlich der Anforderungen zur eingeschränkten Nutzung („Limited Use“).

### 3.2 Android-Gerätesicherung
Wenn die Sicherung deines Handys eingeschaltet ist, legt Cyclus dort einen verschlüsselten Schnappschuss deiner Daten
ab. Die Datenbank selbst wird nie gesichert. Der Schnappschuss wird nur in zwei Fällen übertragen:
- die Sicherung ist mit deiner Displaysperre Ende-zu-Ende-verschlüsselt, Google kann sie also nicht lesen;
- du ziehst direkt von einem Handy auf ein anderes um.

Sonst wird nichts gesichert. Unter Datenschutz › Backups kannst du das mit „Nur auf dem Gerät“ abschalten. Die
Sicherung speichert Google in deinem Google-Konto, dafür gilt die
[Datenschutzerklärung von Google](https://policies.google.com/privacy?hl=de). Wir haben darauf keinen Zugriff.

### 3.3 Exporte und Arztbericht
- **Sicherungsdatei (`.cyclus`):** mit einem Passwort deiner Wahl verschlüsselt und dort gespeichert, wo du willst.
  Speicherst du sie in einem Cloud-Ordner, gelten die Bedingungen dieses Anbieters.
- **Arztbericht (PDF):** Du wählst, welche Abschnitte enthalten sind. Sexuelle Aktivität, Verhütung und Notizen
  sind ab Werk ausgeschlossen. Du teilst es über das Teilen-Menü von Android mit der App oder Person deiner Wahl. Ab
  dann gelten die Datenschutzregeln der Empfängerin oder des Empfängers.

### 3.4 Benachrichtigungen und Widgets
Erinnerungen plant das Handy selbst, ohne Server. Ab Werk ist ihr Text unauffällig (etwa „Cyclus · Check-in“) und
zeigt nie Daten oder Symptome. Widgets auf dem Startbildschirm zeigen Zyklustage nur, wenn du „Details im Widget“
einschaltest. Benachrichtigungen und Widgets kann jeder sehen, der deinen Bildschirm sieht.

## 4. Google Play und GitHub (woher du die App hast)

- **Google Play:** Google verarbeitet Daten, um den Store, den Download und Updates bereitzustellen; siehe die
  [Datenschutzerklärung von Google](https://policies.google.com/privacy?hl=de). Hast du deinem Handy erlaubt, Nutzungs-
  und Diagnosedaten zu teilen, stellt Google uns Absturzberichte und Statistiken bereit, etwa Installationen nach Land,
  Gerät und Android-Version. Diese enthalten nie, was du in Cyclus eingetragen hast. Wir nutzen sie nur, um Fehler zu
  finden und zu beheben und um zu sehen, welche Geräte wir unterstützen sollten. Rechtsgrundlage: unser berechtigtes
  Interesse an einer funktionierenden App (Art. 6 Abs. 1 lit. f DSGVO).
- **GitHub-Releases:** Lädst du Cyclus über GitHub herunter, verarbeitet GitHub den Download (siehe das
  [GitHub Privacy Statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement)).

## 5. Diese Website

Diese Website wird über GitHub Pages bereitgestellt von GitHub, Inc., 88 Colin P. Kelly Jr. Street, San Francisco,
CA 94107, USA. Bei einem Besuch speichert GitHub deine IP-Adresse und technische Anfragedaten in Server-Logs, um den
Dienst sicher zu betreiben (siehe GitHub Privacy Statement oben). Wir haben keinen Zugriff auf diese Logs. Die Seite
verwendet keine Cookies, keine Analyse, kein Tracking und keine von außen geladenen Schriften oder Skripte.
Rechtsgrundlage: unser berechtigtes Interesse an einem zuverlässigen und sicheren Hosting (Art. 6 Abs. 1 lit. f DSGVO).
GitHub kann diese Daten in den USA verarbeiten. Für solche Übermittlungen stützt sich GitHub auf das EU-US Data Privacy
Framework und die EU-Standardvertragsklauseln (Art. 45 und 46 DSGVO).

## 6. Kontakt mit uns

Schreibst du uns eine E-Mail, verarbeiten wir deine Adresse und deine Nachricht nur, um dir zu antworten.
Rechtsgrundlage: Art. 6 Abs. 1 lit. b DSGVO bei Fragen zur App, sonst Art. 6 Abs. 1 lit. f DSGVO. Wir löschen die
Nachrichten spätestens 90 Tage, nachdem dein Anliegen erledigt ist, sofern das Gesetz keine längere Aufbewahrung
verlangt. Bitte schick uns keine Gesundheitsdaten, die für deine Frage nicht nötig sind. Issues und Diskussionen auf
GitHub sind öffentlich, dafür gelten die Bedingungen von GitHub.

## 7. Deine Rechte (EU und Vereinigtes Königreich)

Du hast das Recht auf Auskunft, Berichtigung, Löschung, Einschränkung der Verarbeitung und Datenübertragbarkeit sowie
das Recht, einer Verarbeitung auf Grundlage berechtigter Interessen zu widersprechen (Art. 15–21 DSGVO). Außerdem
kannst du dich bei einer Datenschutz-Aufsichtsbehörde beschweren (Art. 77 DSGVO), insbesondere in dem EU-Land, in dem
du lebst. Um deine Rechte zu nutzen, schreib uns eine E-Mail. Für die Daten in der App nutzt du sie direkt: Du siehst
alles in der App, kannst es exportieren und löschen (Abschnitt 2). Wir haben davon nichts.

Cyclus trifft keine automatisierten Entscheidungen über dich, die rechtliche oder ähnlich erhebliche Wirkung haben
(Art. 22 DSGVO). Vorhersagen sind Schätzungen, die nur dir angezeigt werden.

## 8. Deine Rechte in den USA

- **Kein Verkauf, keine Weitergabe:** Wir verkaufen keine personenbezogenen Daten und geben sie nicht weiter, wir
  nutzen sie nicht für personalisierte Werbung und erstellen keine Profile. Das gilt nach dem California Consumer
  Privacy Act (CCPA/CPRA) und den Datenschutzgesetzen anderer Bundesstaaten. Da nichts getrackt wird, gibt es nichts,
  was Signale wie Global Privacy Control oder „Do Not Track“ stoppen müssten.
- **Gesundheitsdaten von Verbrauchern:** Dieser Abschnitt ist unsere Datenschutzerklärung für Gesundheitsdaten nach dem
  Washington My Health My Data Act, Nevada SB 370 und ähnlichen Gesetzen. Cyclus erhebt keine Gesundheitsdaten von
  Verbrauchern, gibt keine weiter und verkauft keine. Was du einträgst, bleibt auf deinem Gerät und unter deiner
  Kontrolle (Abschnitte 2 und 3). Die einzigen gesundheitsbezogenen Angaben, die wir je erhalten könnten, sind die, die
  du selbst in eine E-Mail an uns schreibst. Wir nutzen sie nur für die Antwort und geben sie nie weiter. Du kannst
  jederzeit ihre Löschung verlangen. Diese Rechte gelten auch für Personen, die in deinem Auftrag handeln.
- **Behördliche Anfragen:** Wir haben keine App-Daten und können daher keine herausgeben.
- **Kinder:** Cyclus richtet sich nicht an Kinder unter 13 Jahren, und wir erheben wissentlich keine
  personenbezogenen Daten von Kindern.

## 9. Sicherheit

Alle Daten auf dem Handy sind verschlüsselt, den Schlüssel hält die Sicherheitshardware. Die App hat keinen
Netzwerkzugriff. Exporte sind standardmäßig verschlüsselt (AES-256-GCM). Der
[Quellcode](https://github.com/moonwhaler/cyclus) ist öffentlich, so kann jeder diese Angaben überprüfen.

## 10. Änderungen

Ändert sich durch die App oder diese Website, wie mit Daten umgegangen wird, aktualisieren wir diese Erklärung und das
Datum oben, bevor die Änderung wirksam wird. Frühere Fassungen findest du im
[Verlauf dieser Datei](https://github.com/moonwhaler/cyclus/commits/main/PRIVACY.de.md).

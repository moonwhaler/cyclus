// Generates Android string resources from the prototype's translation table (prototype/index.html `TX` + item
// labels). Until the shared string source exists (PARITY X-001), the prototype is the single copy source, so the
// apps say exactly what the prototype says. German is the default locale (values/), English is values-en/.
//
//   node tools/strings/export.mjs
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const html = readFileSync(join(ROOT, 'prototype', 'index.html'), 'utf8');
const slice = (from, to) => {
  const s = html.indexOf(from), e = html.indexOf(to, s);
  if (s < 0 || e < 0) throw new Error(`marker not found: ${from}`);
  return html.slice(s, e);
};
const TX = new Function(slice('const TX = {', '\nconst tl = ') + '; return TX;')();
const tables = new Function(
  slice('const SYM = {', '\nconst symL') + slice('const EMO = {', '\nconst PINNED') + slice('const FLOWL = {', '\nconst FLOWS') +
  slice('const MUCL = {', '\nconst MUCUS') + slice('const TAGL = {', '\nconst TAGS') + slice('const REGIONS = [', '\nconst regL') +
  '; return {SYM, EMO, FLOWL, MUCL, TAGL, REGIONS};')();

// prototype-only (interview control panel) keys
const SKIP = /^(p[A-Z]|sc_|sim|th[A-Z]|tasks$)/;
// hand-written plurals: key -> {de: {one, other}, en: {one, other}} (argument %1$d)
const PLURALS = {
  windowOpens: { de: { one: 'Fenster öffnet in etwa %1$d Tag', other: 'Fenster öffnet in etwa %1$d Tagen' }, en: { one: 'Window opens in about %1$d day', other: 'Window opens in about %1$d days' } },
  flhT: { de: { one: 'Eisprung meist %1$d Tag nach dem ersten positiven LH-Test', other: 'Eisprung meist %1$d Tage nach dem ersten positiven LH-Test' }, en: { one: 'Ovulation usually %1$d day after your first positive LH test', other: 'Ovulation usually %1$d days after your first positive LH test' } },
  nPatterns: { de: { one: '%1$d Muster gefunden', other: '%1$d Muster gefunden' }, en: { one: '%1$d pattern found', other: '%1$d patterns found' } },
};
// Android overrides: prototype copy that is iOS-specific or not (yet) true on Android. Trust rule: never claim
// something the app doesn't do. Extend when a screen with such copy is built (Privacy, onboarding: A3).
const ANDROID_OVERRIDES = {
  calTip: {
    de: 'Tippe auf einen Tag für Details. Lange drücken, um einen Periodenbeginn zu markieren.',
    en: 'Tap a day for details. Long-press to mark a period start.',
  },
  // Privacy / erase / lock (slice 3): device, screen lock, biometrics and Google backup instead of iPhone, Face ID, iCloud
  onPhone: { de: 'Auf diesem Gerät', en: 'On this device' },
  onPhoneS: {
    de: 'Verschlüsselt. Der Schlüssel liegt im Hardware-Schlüsselspeicher und funktioniert nur bei entsperrtem Gerät.',
    en: 'Encrypted. The key lives in the hardware keystore and only works while your phone is unlocked.',
  },
  faceLock: { de: 'App-Sperre', en: 'App lock' },
  pinS: { de: 'Ein Code nur für diese App, getrennt von deiner Displaysperre', en: 'A code only for this app, separate from your screen lock' },
  pinOnlyT: { de: 'Nur PIN, keine Biometrie', en: 'PIN only, no biometrics' },
  pinOnlyS: { de: 'Hilfreich, wenn jemand anderes deine Displaysperre kennt', en: 'Useful if someone else knows your screen lock' },
  devOnlyS: {
    de: 'Cyclus aus Google-Backups ausschließen. Geht dein Gerät verloren, sind alle Daten weg, die du nicht exportiert hast.',
    en: 'Exclude Cyclus from Google backups. If you lose this phone, anything you haven’t exported is gone.',
  },
  er1: { de: '✓ Alle Cyclus-Daten auf diesem Gerät, samt Schlüsseln', en: '✓ All Cyclus data on this device, including its keys' },
  er3s: {
    de: 'Ältere Google-Backups bis zur nächsten Sicherung, geteilte Exporte und PDFs',
    en: 'Older Google backups until the next backup runs, exports and PDFs you shared',
  },
  unlockBtn: { de: 'Entsperren', en: 'Unlock' },
  // Onboarding (slice 4): no iPhone / Apple Health / Face ID on Android; import step is a .cyclus backup (Health Connect: A4)
  onb0B: { de: 'Ein privates Zyklus- und Symptomtagebuch.', en: 'A private cycle and symptom diary.' },
  pr1s: { de: 'Deine Daten bleiben auf deinem Gerät.', en: 'Your data stays on your device.' },
  pr3: { de: 'Du bestimmst, was du teilst.', en: 'You decide what you share.' },
  pr3s: { de: 'Exporte und Arztberichte gibt es nur, wenn du sie selbst erstellst.', en: 'Exports and doctor reports exist only when you create them.' },
  onb1N: {
    de: 'In Google-Backups landet Cyclus nur Ende-zu-Ende-verschlüsselt. Das kannst du unter Datenschutz ändern.',
    en: 'Cyclus goes into Google backups only end-to-end encrypted. You can change that in Privacy.',
  },
  onb2T: { de: 'Hast du eine Cyclus-Sicherung?', en: 'Do you have a Cyclus backup?' },
  onb2B: { de: 'Importiere eine .cyclus-Datei von einem anderen Gerät, damit Prognosen ab dem ersten Tag funktionieren.', en: 'Import a .cyclus file from another device so predictions work from day one.' },
  importBtn: { de: 'Sicherung importieren', en: 'Import backup' },
  onb5T: { de: 'Cyclus sperren?', en: 'Lock Cyclus?' },
  onb5B: {
    de: 'Wer dein Gerät benutzt, braucht dann deinen Fingerabdruck, dein Gesicht, deine Displaysperre oder eine Cyclus-PIN.',
    en: 'Anyone using your phone would need your fingerprint, face, screen lock or a Cyclus PIN to open it.',
  },
  method: {
    de: [
      TX.method.de[0],
      'Mit wenigen Zyklen ist der Bereich absichtlich breit. Er wird enger, je mehr du einträgst.',
      'Alles wird auf deinem Gerät berechnet. Cyclus erhält deine Daten nie.',
    ],
    en: [
      TX.method.en[0],
      'With few cycles the range is wide on purpose. It narrows as you log more.',
      'Everything is calculated on your device. Cyclus never receives your data.',
    ],
  },
};
for (const [k, v] of Object.entries(ANDROID_OVERRIDES)) TX[k] = v;

// inline bold (<b …>x</b>) → {b}x{/b} markers, rendered bold by Fmt.rich()
const boldMarkers = (s) => s.replace(/<b[^>]*>/g, '{b}').replace(/<\/b>/g, '{/b}');

// arguments the caller passes already lowercased
const LOWERCASED_ARG = new Set(['flowWord', 'lhWord']);

const esc = (s) => s
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  .replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '\\"').replace(/\n/g, '\\n')
  .replace(/^([@?])/, '\\$1');
const resName = (prefix, key) => prefix + key.replace(/[^A-Za-z0-9]/g, '_');

function fromFunction(key, fn) {
  const src = fn.toString();
  const arrow = src.indexOf('=>');
  const params = src.slice(0, arrow).replace(/[()\s]/g, '').split(',').filter(Boolean);
  let body = src.slice(arrow + 2).trim();
  if (!/^`[^`]*`$/.test(body)) throw new Error(`unsupported template for ${key}: ${src}`);
  body = boldMarkers(body.slice(1, -1));
  let out = '', last = 0;
  for (const m of body.matchAll(/\$\{([^}]*)\}/g)) {
    out += esc(body.slice(last, m.index)).replace(/%/g, '%%');
    const expr = m[1].trim().replace(/\.toLowerCase\(\)$/, '');
    const i = params.indexOf(expr);
    if (i < 0) throw new Error(`unsupported expression in ${key}: ${m[1]}`);
    out += `%${i + 1}$s`;
    last = m.index + m[0].length;
  }
  return out + esc(body.slice(last)).replace(/%/g, '%%');
}

const out = { de: [], en: [] };
const arrays = { de: [], en: [] };
const plurals = { de: [], en: [] };
const keys = [];
for (const [key, v] of Object.entries(TX)) {
  if (SKIP.test(key)) continue;
  if (PLURALS[key]) {
    for (const L of ['de', 'en']) plurals[L].push(`    <plurals name="${resName('tx_', key)}">\n        <item quantity="one">${esc(PLURALS[key][L].one)}</item>\n        <item quantity="other">${esc(PLURALS[key][L].other)}</item>\n    </plurals>`);
    continue;
  }
  if (Array.isArray(v.de)) {
    for (const L of ['de', 'en']) arrays[L].push(`    <string-array name="${resName('tx_', key)}">\n${v[L].map((x) => `        <item>${esc(String(x))}</item>`).join('\n')}\n    </string-array>`);
    continue;
  }
  for (const L of ['de', 'en']) {
    const x = v[L];
    const text = typeof x === 'function' ? fromFunction(key, x) : esc(boldMarkers(String(x)));
    const noFormat = typeof x !== 'function' && text.includes('%') ? ' formatted="false"' : '';
    out[L].push(`    <string name="${resName('tx_', key)}"${noFormat}>${text}</string>`);
  }
  keys.push(key);
}
// item labels (engine keys → text)
const items = [
  ['sym_', Object.entries(tables.SYM).map(([k, v]) => [k, v])],
  ['emo_', Object.entries(tables.EMO)],
  ['flow_', Object.entries(tables.FLOWL)],
  ['mucus_', Object.entries(tables.MUCL)],
  ['tag_', Object.entries(tables.TAGL)],
  ['region_', tables.REGIONS.map((r) => [r.id, r.l])],
];
const itemKeys = {};
for (const [prefix, entries] of items) {
  itemKeys[prefix] = entries.map(([k]) => k);
  for (const [k, [de, en]] of entries) {
    out.de.push(`    <string name="${resName(prefix, k)}">${esc(de)}</string>`);
    out.en.push(`    <string name="${resName(prefix, k)}">${esc(en)}</string>`);
  }
}

const header = '<?xml version="1.0" encoding="utf-8"?>\n<!-- GENERATED by tools/strings/export.mjs from prototype/index.html. Do not edit by hand. -->\n<resources>\n';
for (const [L, dir] of [['de', 'values'], ['en', 'values-en']]) {
  const d = join(ROOT, 'androidApp', 'src', 'main', 'res', dir);
  mkdirSync(d, { recursive: true });
  writeFileSync(join(d, 'strings_tx.xml'), header + [...out[L], ...plurals[L], ...arrays[L]].join('\n') + '\n</resources>\n');
}

// Kotlin lookup tables (no reflection / getIdentifier)
const kt = [
  '// GENERATED by tools/strings/export.mjs from prototype/index.html. Do not edit by hand.',
  'package com.nordinary.cyclus.i18n',
  '',
  'import com.nordinary.cyclus.R',
  '',
  'internal object TxRes {',
  '    val strings: Map<String, Int> = mapOf(',
  ...keys.map((k) => `        "${k}" to R.string.${resName('tx_', k)},`),
  '    )',
  '    val plurals: Map<String, Int> = mapOf(',
  ...Object.keys(PLURALS).map((k) => `        "${k}" to R.plurals.${resName('tx_', k)},`),
  '    )',
  ...items.map(([prefix]) => [
    `    val ${prefix.slice(0, -1)}: Map<String, Int> = mapOf(`,
    ...itemKeys[prefix].map((k) => `        "${k}" to R.string.${resName(prefix, k)},`),
    '    )',
  ].join('\n')),
  `    /** Keys whose argument the caller lowercases (prototype calls toLowerCase on it). */`,
  `    val lowercasedArg: Set<String> = setOf(${[...LOWERCASED_ARG].map((k) => `"${k}"`).join(', ')})`,
  '}',
  '',
];
const ktDir = join(ROOT, 'androidApp', 'src', 'main', 'kotlin', 'com', 'nordinary', 'cyclus', 'i18n');
mkdirSync(ktDir, { recursive: true });
writeFileSync(join(ktDir, 'TxRes.kt'), kt.join('\n'));
console.log(`strings: ${keys.length}, plurals: ${Object.keys(PLURALS).length}, arrays: ${arrays.de.length}, items: ${Object.values(itemKeys).flat().length}`);

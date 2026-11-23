// Generates Android string resources from the prototype's translation table (prototype/index.html `TX` + item
// labels). Until the shared string source exists (PARITY X-001), the prototype is the copy source for German and
// English, so the apps say exactly what the prototype says. English is the base locale (values/), German values-de/.
// French, Spanish (es-ES), Portuguese (pt-PT) and Italian come from tools/strings/i18n/<lang>.json, translated from
// the generated English source (tools/strings/i18n/en.json, plain text). Every locale is checked against English:
// same keys, same placeholders (%1$s …) and bold markers ({b}…{/b}), no banned wording (spec §9.3).
//
//   node tools/strings/export.mjs
import { readFileSync, writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
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
  usPeriodLen: { de: { one: 'Periode: %1$d Tag', other: 'Periode: %1$d Tage' }, en: { one: 'Period: %1$d day', other: 'Period: %1$d days' } },
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
const PH = /%(\d+)\$([sd])/g;

/** Prototype template function → plain text with Android placeholders (%1$s …); literal text untouched. */
function fromFunction(key, fn) {
  const src = fn.toString();
  const arrow = src.indexOf('=>');
  const params = src.slice(0, arrow).replace(/[()\s]/g, '').split(',').filter(Boolean);
  let body = src.slice(arrow + 2).trim();
  if (!/^`[^`]*`$/.test(body)) throw new Error(`unsupported template for ${key}: ${src}`);
  body = boldMarkers(body.slice(1, -1));
  return body.replace(/\$\{([^}]*)\}/g, (_, e) => {
    const i = params.indexOf(e.trim().replace(/\.toLowerCase\(\)$/, ''));
    if (i < 0) throw new Error(`unsupported expression in ${key}: ${e}`);
    return `%${i + 1}$s`;
  });
}

// ---- plain-text source per language: { strings: {key: {text, fmt}}, plurals: {key: {one, other}}, arrays, items }
const src = { de: { strings: {}, plurals: {}, arrays: {}, items: {} }, en: { strings: {}, plurals: {}, arrays: {}, items: {} } };
const keys = [];
for (const [key, v] of Object.entries(TX)) {
  if (SKIP.test(key)) continue;
  if (PLURALS[key]) { for (const L of ['de', 'en']) src[L].plurals[key] = PLURALS[key][L]; continue; }
  if (Array.isArray(v.de)) { for (const L of ['de', 'en']) src[L].arrays[key] = v[L].map(String); continue; }
  for (const L of ['de', 'en']) {
    const x = v[L];
    src[L].strings[key] = typeof x === 'function' ? { text: fromFunction(key, x), fmt: true } : { text: boldMarkers(String(x)), fmt: false };
  }
  keys.push(key);
}
// item labels (engine keys → text)
const itemTables = [
  ['sym_', Object.entries(tables.SYM)],
  ['emo_', Object.entries(tables.EMO)],
  ['flow_', Object.entries(tables.FLOWL)],
  ['mucus_', Object.entries(tables.MUCL)],
  ['tag_', Object.entries(tables.TAGL)],
  ['region_', tables.REGIONS.map((r) => [r.id, r.l])],
];
const itemKeys = {};
for (const [prefix, entries] of itemTables) {
  itemKeys[prefix] = entries.map(([k]) => k);
  for (const [k, [de, en]] of entries) { src.de.items[prefix + k] = de; src.en.items[prefix + k] = en; }
}

// ---- translations: same shape as en.json (plain text); "fmt" follows English
const I18N = join(ROOT, 'tools', 'strings', 'i18n');
mkdirSync(I18N, { recursive: true });
const plainOf = (L) => ({
  strings: Object.fromEntries(Object.entries(src[L].strings).map(([k, v]) => [k, v.text])),
  plurals: src[L].plurals, arrays: src[L].arrays, items: src[L].items,
});
writeFileSync(join(I18N, 'en.json'), JSON.stringify({ _note: 'GENERATED source for translations (English). Do not edit; edit the prototype.', ...plainOf('en') }, null, 1) + '\n');

// banned wording per language (spec §9.3), case-insensitive substrings
const BANNED = {
  en: ['safe days', 'infertile days', 'unsafe days', "can't get pregnant", 'cannot get pregnant', 'confirmed ovulation', 'you ovulated', 'diagnos'],
  de: ['sichere tage', 'unfruchtbare tage', 'bestätigter eisprung', 'diagnostizier'],
  fr: ['jours sûrs', 'jours sans risque', 'jours infertiles', 'jours non fertiles', 'ovulation confirmée', 'diagnostiqu'],
  es: ['días seguros', 'días infértiles', 'días no fértiles', 'ovulación confirmada', 'diagnóstic', 'diagnostic'],
  pt: ['dias seguros', 'dias inférteis', 'dias não férteis', 'ovulação confirmada', 'diagnóstic', 'diagnostic'],
  it: ['giorni sicuri', 'giorni infertili', 'giorni non fertili', 'ovulazione confermata', 'diagnostic', 'diagnosi'],
};
// "diagnosis" itself is allowed only in the disclaimers ("keine Diagnose", "not a diagnosis")
const DISCLAIMER_OK = /(keine diagnose|stellt keine diagnosen|not a diagnosis|does not diagnose|pas un diagnostic|ne pose aucun diagnostic|no es un diagnóstico|no hace diagnósticos|não é um diagnóstico|não faz diagnósticos|non è una diagnosi|non fa diagnosi|nessuna diagnosi)/i;

const errors = [];
const phSet = (t) => [...String(t).matchAll(PH)].map((m) => m[0]).sort().join(',');
const boldCount = (t) => (String(t).match(/\{b\}/g) || []).length + '/' + (String(t).match(/\{\/b\}/g) || []).length;
function check(L, data) {
  const en = plainOf('en');
  for (const part of ['strings', 'plurals', 'arrays', 'items']) {
    const a = Object.keys(en[part]), b = Object.keys(data[part] || {});
    for (const k of a) if (!(k in (data[part] || {}))) errors.push(`${L}: missing ${part}.${k}`);
    for (const k of b) if (!(k in en[part])) errors.push(`${L}: extra ${part}.${k}`);
  }
  for (const [k, t] of Object.entries(data.strings || {})) if (k in en.strings) {
    if (phSet(t) !== phSet(en.strings[k])) errors.push(`${L}: placeholders differ in ${k}: "${t}"`);
    if (boldCount(t) !== boldCount(en.strings[k])) errors.push(`${L}: bold markers differ in ${k}`);
  }
  for (const [k, f] of Object.entries(data.plurals || {})) if (k in en.plurals) {
    for (const q of ['one', 'other']) if (!f[q]) errors.push(`${L}: plural ${k} lacks ${q}`);
    for (const q of Object.keys(f)) if (phSet(f[q]) !== phSet(en.plurals[k].other)) errors.push(`${L}: placeholders differ in plural ${k}.${q}`);
  }
  for (const [k, arr] of Object.entries(data.arrays || {})) if (k in en.arrays && arr.length !== en.arrays[k].length) errors.push(`${L}: array ${k} length`);
  const all = [...Object.values(data.strings || {}), ...Object.values(data.items || {}), ...Object.values(data.arrays || {}).flat(),
    ...Object.values(data.plurals || {}).flatMap((f) => Object.values(f))];
  for (const t of all) for (const w of BANNED[L] || []) {
    const low = String(t).toLowerCase();
    if (low.includes(w) && !(w.startsWith('diagnos') || w.startsWith('diagnós') ? DISCLAIMER_OK.test(t) : false)) errors.push(`${L}: banned wording "${w}" in "${t}"`);
  }
}
const LOCALES = { de: 'values-de', en: 'values' };
const data = { de: plainOf('de'), en: plainOf('en') };
for (const L of ['fr', 'es', 'pt', 'it']) {
  const f = join(I18N, `${L}.json`);
  if (!existsSync(f)) { console.warn(`strings: ${L}.json missing, skipped`); continue; }
  data[L] = JSON.parse(readFileSync(f, 'utf8'));
  LOCALES[L] = `values-${L}`;
}
// French typography: no line break before : ; ! ? » % or after « (non-breaking spaces, written at export time)
const frSpace = (t) => typeof t !== 'string' ? t : t.replace(/ ([:;!?»])/g, '\u00a0$1').replace(/ %(?!\d+\$)/g, '\u00a0%').replace(/« /g, '«\u00a0');
const mapText = (d, f) => ({
  ...d,
  strings: Object.fromEntries(Object.entries(d.strings).map(([k, v]) => [k, f(v)])),
  plurals: Object.fromEntries(Object.entries(d.plurals).map(([k, v]) => [k, Object.fromEntries(Object.entries(v).map(([q, t]) => [q, f(t)]))])),
  arrays: Object.fromEntries(Object.entries(d.arrays).map(([k, v]) => [k, v.map(f)])),
  items: Object.fromEntries(Object.entries(d.items).map(([k, v]) => [k, f(v)])),
});
if (data.fr) data.fr = mapText(data.fr, frSpace);
// every language: a number placeholder stays on the line of the word after it ("8 cycles", "29 jours")
const keepUnit = (t) => typeof t !== 'string' ? t : t.replace(/(%\d+\$[sd]) (?=\p{Ll})/gu, '$1\u00a0');
for (const L of Object.keys(data)) data[L] = mapText(data[L], keepUnit);
for (const L of Object.keys(data)) check(L, data[L]);
// hand-written Android strings (strings.xml): same keys and placeholders as English, no banned wording
const RES = join(ROOT, 'androidApp', 'src', 'main', 'res');
const handStrings = (dir) => {
  const f = join(RES, dir, 'strings.xml');
  if (!existsSync(f)) return null;
  const x = readFileSync(f, 'utf8');
  const out = {};
  for (const m of x.matchAll(/<string name="([^"]+)"([^>]*)>([\s\S]*?)<\/string>/g)) if (!/translatable="false"/.test(m[2])) out[m[1]] = m[3];
  for (const m of x.matchAll(/<plurals name="([^"]+)">([\s\S]*?)<\/plurals>/g))
    for (const q of m[2].matchAll(/<item quantity="([a-z]+)">([\s\S]*?)<\/item>/g)) out[`${m[1]}#${q[1]}`] = q[2];
  return out;
};
const handEn = handStrings('values');
const phAny = (t) => [...String(t).matchAll(/%(\d+\$)?[sd]/g)].map((m) => m[0]).sort().join(',');
for (const [L, dir] of Object.entries(LOCALES)) {
  if (L === 'en') continue;
  const h = handStrings(dir);
  if (!h) { errors.push(`${L}: ${dir}/strings.xml missing`); continue; }
  for (const k of Object.keys(handEn)) {
    const base = k.split('#')[0];
    const has = k in h || (k.includes('#') && Object.keys(h).some((x) => x.startsWith(base + '#')));
    if (!has) errors.push(`${L}: strings.xml missing ${k}`);
  }
  for (const [k, t] of Object.entries(h)) {
    const enK = k in handEn ? k : (k.includes('#') ? `${k.split('#')[0]}#other` : null);
    if (!enK || !(enK in handEn)) { errors.push(`${L}: strings.xml extra ${k}`); continue; }
    if (phAny(t) !== phAny(handEn[enK])) errors.push(`${L}: strings.xml placeholders differ in ${k}: "${t}"`);
    for (const w of BANNED[L] || []) if (t.toLowerCase().includes(w) && !DISCLAIMER_OK.test(t)) errors.push(`${L}: strings.xml banned "${w}" in ${k}`);
    if (L === 'fr' && / [:;!?»]|« /.test(t)) errors.push(`fr: strings.xml needs a non-breaking space in ${k}: "${t}"`);
  }
}
if (errors.length) { console.error(errors.join('\n')); console.error(`strings: ${errors.length} problem(s), nothing written`); process.exit(1); }

// ---- emit XML
const isFmt = (k) => src.en.strings[k]?.fmt;
const emitText = (t, fmt) => {
  if (!fmt) return esc(t);
  // literal % doubled, placeholders kept
  return esc(t).replace(/%(?!\d+\$[sd])/g, '%%');
};
const header = '<?xml version="1.0" encoding="utf-8"?>\n<!-- GENERATED by tools/strings/export.mjs. Do not edit by hand. -->\n<resources>\n';
// many: Romance languages use it for large numbers (CLDR); same text as other
const MANY = new Set(['fr', 'es', 'pt', 'it']);
for (const [L, dir] of Object.entries(LOCALES)) {
  const d = data[L];
  const lines = [];
  for (const k of keys) {
    const fmt = isFmt(k);
    const text = emitText(d.strings[k], fmt);
    const noFormat = !fmt && text.includes('%') ? ' formatted="false"' : '';
    lines.push(`    <string name="${resName('tx_', k)}"${noFormat}>${text}</string>`);
  }
  for (const [prefix] of itemTables) for (const k of itemKeys[prefix]) lines.push(`    <string name="${resName(prefix, k)}">${esc(d.items[prefix + k])}</string>`);
  for (const [k, f] of Object.entries(d.plurals)) {
    const q = [['one', f.one], ...(MANY.has(L) ? [['many', f.many || f.other]] : []), ['other', f.other]];
    lines.push(`    <plurals name="${resName('tx_', k)}">\n${q.map(([n, t]) => `        <item quantity="${n}">${esc(t)}</item>`).join('\n')}\n    </plurals>`);
  }
  for (const [k, arr] of Object.entries(d.arrays)) lines.push(`    <string-array name="${resName('tx_', k)}">\n${arr.map((x) => `        <item>${esc(String(x))}</item>`).join('\n')}\n    </string-array>`);
  const out = join(ROOT, 'androidApp', 'src', 'main', 'res', dir);
  mkdirSync(out, { recursive: true });
  writeFileSync(join(out, 'strings_tx.xml'), header + lines.join('\n') + '\n</resources>\n');
}
// English moved to values/ (2026-11-23): an old values-en/ copy would shadow it
rmSync(join(ROOT, 'androidApp', 'src', 'main', 'res', 'values-en', 'strings_tx.xml'), { force: true });

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
  ...itemTables.map(([prefix]) => [
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
console.log(`strings: ${keys.length}, plurals: ${Object.keys(PLURALS).length}, arrays: ${Object.keys(src.en.arrays).length}, items: ${Object.values(itemKeys).flat().length}, locales: ${Object.keys(LOCALES).join(' ')}`);

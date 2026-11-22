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

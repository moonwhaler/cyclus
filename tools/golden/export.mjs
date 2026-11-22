// Golden-vector exporter: runs the prototype's engines (prototype/index.html) in Node and writes
// input/output pairs that the Kotlin port in shared/ must reproduce exactly.
//
//   node tools/golden/export.mjs            # writes shared/golden/*.json
//
// The prototype is the behavioural source of truth (ANDROID_PLAN §1, §9). Nothing here changes its logic:
// we only slice the engine part out of the page and patch a few presentation helpers, so that text comes
// out as {k, a} keys + args instead of localized strings.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const OUT = join(ROOT, 'shared', 'golden');
const html = readFileSync(join(ROOT, 'prototype', 'index.html'), 'utf8');

// ---- slice the engine part of the script ----
const start = html.indexOf('const S = {');
const end = html.indexOf('/* ============ icons');
if (start < 0 || end < 0) throw new Error('prototype layout changed: engine markers not found');
let src = html.slice(start, end);

const patch = (from, to) => {
  if (!src.includes(from)) throw new Error(`patch target not found: ${from.slice(0, 60)}`);
  src = src.replace(from, to);
};
// time is an input
patch('const TODAY = D(2026,11,22);', 'let TODAY = D(2026,11,22);');
// text → keys + args (toLowerCase is the one string method the engines call on a translation)
patch('const tl = (lang,k,...a) => { const e = TX[k]; if (!e) return k; const v = e[lang]; return typeof v===\'function\' ? v(...a) : v; };',
  'const tl = (lang,k,...a) => { const o = {k, a}; Object.defineProperty(o, \'toLowerCase\', {value(){ return {k, a, lower:true}; }}); return o; };');
// item / tag names → stable keys
patch('const symL = (k,lang) => SYM[k] ? SYM[k][lang===\'de\'?0:1] : k;', 'const symL = (k,lang) => k;');
patch('const tagLL = (k,lang) => TAGL[k][lang===\'de\'?0:1];', 'const tagLL = (k,lang) => k;');
patch('const itemL = (k,lang) => SYM[k] ? symL(k,lang) : EMO[k] ? EMO[k][lang===\'de\'?0:1] : k;', 'const itemL = (k,lang) => k;');
// formatted numbers → raw value + decimals (the UI formats them)
patch('const nf = (x,d,lang) => Number(x).toLocaleString(lang===\'de\'?\'de-DE\':\'en-US\',{minimumFractionDigits:d,maximumFractionDigits:d});',
  'const nf = (x,d,lang) => ({num:Number(x), d});');

// date formatters are only used for labels; stub them (setLocale touches the DOM)
const api = `
F = new Proxy({}, {get: () => ({format: () => ''})});
return {
  S, D, rng, genScenario, cycleStarts, periodDays, predict, fertility, trail, learnFromHistory, patternWatch, forecast, vm,
  getToday: () => TODAY, setToday: (d) => { TODAY = d; },
  resetCaches: () => { S.rev++; PW.clear(); FC.clear(); LEARN = null; LEARN_KEY = null; },
  getLearn: () => LEARN,
  SYMPTOMS, EMOS, TAGS, MUCUS, FLOWS, REGIONS: REGIONS.map(r => r.id),
};`;
const E = new Function(`"use strict";\n${src}\n${api}`)();

// ---- serialisation helpers ----
const num = (x) => (Number.isNaN(x) ? 'NaN' : x === Infinity ? 'Inf' : x === -Infinity ? '-Inf' : x);
const json = (o) => JSON.stringify(o, (k, v) => (typeof v === 'number' ? num(v) : v));
const logsOut = () => [...E.S.logs.entries()].sort((a, b) => a[0] - b[0]).map(([d, l]) => [d, l]);
const overridesOut = () => [...E.S.overrides.entries()].sort((a, b) => a[0] - b[0]);
const pickPred = (p) => p && {
  starts: p.starts, lens: p.lens, used: p.used, last: p.last, lo: p.lo, hi: p.hi, neff: p.neff, mun: p.mun,
  scale: p.scale, df: p.df, loL: p.loL, hiL: p.hiL, tier: p.tier, typLo: p.typLo, typHi: p.typHi,
};
const pickFert = (f) => f && {
  cs: f.cs, todayCd: f.todayCd, Dmax: f.Dmax,
  post: Object.keys(f.post).map(Number).sort((a, b) => a - b).map((d) => [d, f.post[d]]),
  pA: f.pA, days: f.days, band: f.band, mapD: f.mapD, hpd: f.hpd, status: f.status, shift: f.shift,
  ev: f.ev, bbt: f.bbt, planStart: f.planStart, chip: f.chip,
};

function evaluate(name) {
  E.resetCaches();
  const v = E.vm();
  const out = {
    cycleStarts: E.cycleStarts(),
    predict: pickPred(v.pred),
    vm: { paused: v.paused, cd: v.cd ?? null, inPeriod: v.pred ? !!v.inPeriod : null, periodDay: v.periodDay ?? null, status: v.status ?? null, fertOn: v.fertOn ?? null },
  };
  if (v.pred) {
    out.learn = (() => { const L = E.getLearn(); return { cycles: L.cycles, good: L.good.map((c) => c.cs), lut: L.lut }; })();
    out.periodDaysLast = E.periodDays(v.pred.last);
    out.fertility = pickFert(E.fertility(v.pred));
    const full = E.fertility(v.pred);
    const cds = [0, ...new Set(full.ev.map((e) => e.cd))].sort((a, b) => a - b);
    out.trail = E.trail(v.pred).map((x, i) => ({ cd: cds[i], band: x.band, changed: x.changed ?? null, events: i ? [...new Set(full.ev.filter((e) => e.cd === cds[i]).map((e) => e.type))] : [] }));
    out.patterns = E.patternWatch(v, 'xx');
    out.forecast = E.forecast(v);
  }
  return {
    name, today: E.getToday(), mode: E.S.mode, showFertTrack: E.S.showFertTrack, episode: E.S.episode,
    logs: logsOut(), overrides: overridesOut(), out,
  };
}

// ---- cases ----
const BASE = E.D(2026, 11, 22);
const named = [];
const sweeps = [];
for (const key of ['empty', 'day12', 'expected', 'period', 'hbc', 'conceive', 'conceive2ww']) {
  E.setToday(BASE);
  E.genScenario(key);
  named.push(evaluate(key));
}
// time passes without new logs: walks through between → pre → expected → later
for (const key of ['day12', 'conceive']) {
  E.setToday(BASE);
  E.genScenario(key);
  for (let k = 1; k <= 35; k++) { E.setToday(BASE + k); sweeps.push(evaluate(`${key}+${k}`)); }
}
// fertility-on in track mode
E.setToday(BASE); E.genScenario('day12'); E.S.showFertTrack = true; named.push(evaluate('day12-fertTrack'));

// fuzz: irregular cycles, gaps, overrides, all log fields
const fuzz = [];
for (let seed = 1; seed <= 60; seed++) {
  const r = E.rng(1000 + seed);
  const pick = (a) => a[Math.floor(r() * a.length)];
  const S = E.S;
  E.setToday(BASE);
  S.logs = new Map(); S.overrides = new Map(); S.episode = null; S.showFertTrack = false;
  S.mode = r() < 0.3 ? 'conceive' : 'track';
  if (S.mode === 'track' && r() < 0.3) S.showFertTrack = true;
  const nCycles = 1 + Math.floor(r() * 12);
  const logRate = 0.2 + r() * 0.75;
  // current cycle started 0–34 days ago; earlier cycles go backwards from there
  let day = BASE - Math.floor(r() * 35);
  const starts = [day];
  for (let i = 1; i < nCycles; i++) {
    const L = r() < 0.08 ? pick([16, 17, 48, 55, 65, 95]) : 22 + Math.floor(r() * 18);
    day -= L; starts.unshift(day);
  }
  const lut = 9 + Math.floor(r() * 7);
  starts.forEach((st, ci) => {
    const end = ci + 1 < starts.length ? starts[ci + 1] : BASE + 1;
    const L = ci + 1 < starts.length ? end - st : 26 + Math.floor(r() * 6), pl = 2 + Math.floor(r() * 7), O = L - lut;
    for (let d = st; d < Math.min(end, BASE + 1); d++) {
      const k = d - st;
      if (k >= pl && r() > logRate) continue;
      const l = { symptoms: [], pain: {}, tags: [], emo: [] };
      if (k < pl) l.flow = r() < 0.1 ? 'spotting' : pick(['light', 'medium', 'heavy', 'medium']);
      else if (r() < 0.05) l.flow = pick(['spotting', 'none', 'light']);
      if (r() < 0.6) {
        E.SYMPTOMS.forEach((s) => { if (r() < (k < 2 || L - k <= 3 ? 0.25 : 0.06)) l.symptoms.push(s); });
        E.EMOS.forEach((s) => { if (r() < 0.1) l.emo.push(s); });
        E.TAGS.forEach((s) => { if (r() < 0.08) l.tags.push(s); });
        if (r() < 0.2) l.pain[pick(E.REGIONS)] = Math.floor(r() * 11);
        for (const sc of ['mood', 'energy', 'sleep', 'libido']) if (r() < 0.5) l[sc] = 1 + Math.floor(r() * 5);
        if (r() < 0.2) l.nothingElse = true;
      }
      if (S.mode === 'conceive' || r() < 0.15) {
        if (k >= 2 && r() < 0.7) l.bbt = Math.round(((k > O ? 36.65 : 36.3) + r() * 0.2) * 100) / 100;
        if (k >= O - 5 && k <= O + 1 && r() < 0.7) l.lh = k === O - 1 ? 'pos' : r() < 0.1 ? 'surge' : 'neg';
        if (k >= O - 6 && k <= O + 3 && r() < 0.6) l.mucus = k <= O - 4 ? 'creamy' : k <= O ? pick(['eggwhite', 'watery']) : pick(['sticky', 'dry']);
        if (r() < 0.1) l.sex = true;
      }
      S.logs.set(d, l);
    }
  });
  if (starts.length > 2 && r() < 0.25) S.overrides.set(starts[1], 'not');
  if (r() < 0.25) S.overrides.set(starts[0] + 10 + Math.floor(r() * 5), 'start');
  if (r() < 0.1) S.episode = { kind: 'hbc', start: BASE - 30 };
  E.setToday(BASE + Math.floor(r() * 4));
  fuzz.push(evaluate(`fuzz-${seed}`));
}

mkdirSync(OUT, { recursive: true });
const meta = { generator: 'tools/golden/export.mjs', source: 'prototype/index.html', node: process.version };
for (const [file, cases] of [['named', named], ['sweep', sweeps], ['fuzz', fuzz]]) {
  writeFileSync(join(OUT, `${file}.json`), json({ meta, cases }) + '\n');
  console.log(`${file}.json: ${cases.length} cases`);
}

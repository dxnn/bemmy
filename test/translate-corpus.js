'use strict';

// Pre-translates the SICM corpus into an EDN file so the Clojure
// equivalence test doesn't need to shell into node per snippet.

const fs = require('node:fs');
const path = require('node:path');

require('../public/sicm2emmy.js');
const { translate } = globalThis.SicmToEmmy;

const inPath  = path.join(__dirname, 'fixtures', 'sicm-snippets.json');
const outPath = path.join(__dirname, 'fixtures', 'sicm-snippets.translated.edn');

function ednStr(s) {
  return '"' + s
    .replace(/\\/g, '\\\\')
    .replace(/"/g,  '\\"')
    .replace(/\n/g, '\\n')
    .replace(/\r/g, '\\r')
    .replace(/\t/g, '\\t') + '"';
}

const corpus = JSON.parse(fs.readFileSync(inPath, 'utf8'));

// Hand-fixes for HTML-scrape corruption + Clojure-incompatible symbols
// that the upstream tgvaughan.github.io/sicm corpus carries. Each
// patch matches by a unique distinguishing substring rather than by
// idx, so reorderings in the source corpus don't silently no-op.

function patchCode(code) {
  // §3.5 / §3.6.2 / §3.6.4 / §3.9 prereq: the original scrape's
  // `(let ((...) (omega (* 2 (sqrt 9.8))) ((evolve H-pend-sysder ...) …))`
  // lost the `)` that should close the binding list before the body,
  // so the function-call body got pulled into the bindings vector and
  // sicm2emmy emitted an invalid `(let [... (evolve …) (up …)])`.
  // Re-insert the missing `)` so the body sits outside.
  if (/\(omega \(\* 2 \(sqrt 9\.8\)\)\)\s*\n\s*\(\(evolve H-pend-sysder/.test(code)) {
    code = code.replace(
      /(\(omega \(\* 2 \(sqrt 9\.8\)\)\))\s*\n(\s*)\(\(evolve H-pend-sysder/,
      '$1)\n$2((evolve H-pend-sysder');
  }
  return code;
}

function patchTranslated(translated) {
  // SICM book uses `1st-order-map` as a free symbol in §6.7; Clojure
  // can't read a token that starts with a digit followed by alpha. The
  // plugin defines a stub `first-order-map` (no-op state advancer) so
  // the page evaluates instead of failing the reader on
  // "Invalid number: 1st-order-map".
  translated = translated.replace(/\b1st-order-map\b/g, 'first-order-map');
  return translated;
}

// Type-inference env, reset per chapter so cross-chapter aliases don't leak.
let envChapter = null;
let env = null;

const lines = ['['];
corpus.forEach((entry, idx) => {
  if (entry.chapter !== envChapter) {
    envChapter = entry.chapter;
    env = Object.create(null);
  }
  const code = patchCode(entry.code);
  const translated = patchTranslated(translate(code, env));
  const fields = [
    `:chapter ${ednStr(entry.chapter)}`,
    `:chapter-title ${ednStr(entry.chapter_title ?? '')}`,
    `:section ${ednStr(entry.section ?? '')}`,
    `:section-title ${ednStr(entry.section_title ?? '')}`,
    `:page ${entry.page != null ? entry.page : 'nil'}`,
    `:idx ${idx}`,
    `:code ${ednStr(code)}`,
    `:translated ${ednStr(translated)}`,
  ];
  if (entry.subheading !== undefined) {
    fields.push(`:subheading ${ednStr(entry.subheading)}`);
  }
  if (entry.source !== undefined) {
    fields.push(`:source ${ednStr(entry.source)}`);
  }
  if (entry.expected !== undefined) {
    // Mirror the translator's `^` → `↑` mangling on the SICM-book
    // printed output so the equivalence-test comparator sees the same
    // symbol encoding as the eval result.
    fields.push(`:expected ${ednStr(entry.expected.replace(/\^/g, '↑'))}`);
  }
  lines.push(`  {${fields.join(' ')}}`);
});
lines.push(']');

fs.writeFileSync(outPath, lines.join('\n'), 'utf8');
console.log(`Wrote ${corpus.length} entries (${corpus.filter(e => e.expected !== undefined).length} with expected) to ${outPath}`);

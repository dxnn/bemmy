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

const lines = ['['];
corpus.forEach((entry, idx) => {
  const translated = translate(entry.code);
  const fields = [
    `:chapter ${ednStr(entry.chapter)}`,
    `:section ${ednStr(entry.section ?? '')}`,
    `:page ${entry.page != null ? entry.page : 'nil'}`,
    `:idx ${idx}`,
    `:code ${ednStr(entry.code)}`,
    `:translated ${ednStr(translated)}`,
  ];
  if (entry.expected !== undefined) {
    fields.push(`:expected ${ednStr(entry.expected)}`);
  }
  lines.push(`  {${fields.join(' ')}}`);
});
lines.push(']');

fs.writeFileSync(outPath, lines.join('\n'), 'utf8');
console.log(`Wrote ${corpus.length} entries (${corpus.filter(e => e.expected !== undefined).length} with expected) to ${outPath}`);

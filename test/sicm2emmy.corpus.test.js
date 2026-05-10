'use strict';

const { describe, test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');

require('../public/sicm2emmy.js');
const { translate } = globalThis.SicmToEmmy;

const corpus = JSON.parse(
  fs.readFileSync(path.join(__dirname, 'fixtures', 'sicm-snippets.json'), 'utf8')
);

// Track () [] {} depth while skipping ; line comments, "strings", and \char
// literals. Good enough for both Scheme input and Clojure output — neither
// uses #|...|# block comments in the corpus.
function checkBalanced(src) {
  const stack = [];
  const pairs = { ')': '(', ']': '[', '}': '{' };
  let i = 0;
  while (i < src.length) {
    const c = src[i];
    if (c === ';') {
      while (i < src.length && src[i] !== '\n') i++;
      continue;
    }
    if (c === '"') {
      i++;
      while (i < src.length && src[i] !== '"') {
        if (src[i] === '\\') i++;
        i++;
      }
      i++;
      continue;
    }
    if (c === '\\') { i += 2; continue; }
    if (c === '(' || c === '[' || c === '{') {
      stack.push(c);
    } else if (c === ')' || c === ']' || c === '}') {
      const got = stack.pop();
      if (got !== pairs[c]) {
        return `mismatched ${c} (top of stack: ${got ?? 'empty'})`;
      }
    }
    i++;
  }
  return stack.length ? `unclosed ${stack.join('')}` : null;
}

const byChapter = corpus.reduce((acc, entry, idx) => {
  (acc[entry.chapter] ??= []).push({ ...entry, idx });
  return acc;
}, {});

for (const ch of Object.keys(byChapter).sort()) {
  describe(`SICM ch${ch} corpus`, () => {
    for (const entry of byChapter[ch]) {
      const label = `§${entry.section}`
        + (entry.page ? ` p${entry.page}` : '')
        + ` #${entry.idx}`;
      test(label, () => {
        let out;
        assert.doesNotThrow(() => { out = translate(entry.code); });
        assert.ok(typeof out === 'string' && out.length > 0, 'empty output');
        const err = checkBalanced(out);
        assert.equal(err, null, `unbalanced output: ${err}`);
      });
    }
  });
}

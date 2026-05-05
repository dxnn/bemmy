// vendor-cm.mjs: walk esm.sh module graph for CM6, download and vendor locally.
// Run from the project root via bin/vendor.sh (requires Node 18+).
//
// The localPath() function maps esm.sh URL paths to local filenames.
// NOTE: version strings like "6.x.x" match the /\.[a-z]+$/i extension check
// and are saved without a .js suffix — if re-running from scratch, those files
// must be renamed manually (see the 6.x.x handling in bin/vendor.sh comments).
import { mkdir, writeFile } from 'node:fs/promises';
import { join, dirname, relative } from 'node:path';

const ESM = 'https://esm.sh';
const OUT_DIR = 'public/vendor/codemirror';

const ENTRIES = [
  '@codemirror/view@6',
  '@codemirror/state@6',
  '@codemirror/commands@6',
  '@codemirror/language@6',
  '@nextjournal/clojure-mode',
  '@replit/codemirror-vim',
  '@codemirror/theme-one-dark',
];

// canonical key (no leading /, no query) → local path relative to OUT_DIR
const keyToLocal = new Map();
// canonical key → Promise<localPath>
const inFlight = new Map();

function keyFor(esmPath) {
  const p = esmPath.startsWith('/') ? esmPath.slice(1) : esmPath;
  return p.split('?')[0];
}

function localPath(key) {
  // Files with a recognised extension keep their name as-is.
  // Version-only entries (e.g. @codemirror/state@6) get .js appended.
  // Exception: "6.x.x" strings end with ".x" which matches the regex —
  // those get saved as "pkg@6.x.x" (no .js) and are fixed up post-hoc
  // by the sed call in vendor.sh.
  return /\.[a-z]+$/i.test(key) ? key : key + '.js';
}

async function fetchText(url) {
  const r = await fetch(url);
  if (!r.ok) throw new Error(`HTTP ${r.status}: ${url}`);
  return r.text();
}

function extractImports(text) {
  const re = /\b(?:from|import)\s*(["'])([^"'\n]+)\1/g;
  const out = [];
  let m;
  while ((m = re.exec(text)) !== null) {
    const p = m[2];
    if (p.startsWith('/') || p.startsWith('./') || p.startsWith('../')) {
      out.push({ path: p, isRelative: !p.startsWith('/') });
    }
  }
  return out;
}

function resolveRelative(basePath, relPath) {
  const base = new URL(ESM + (basePath.startsWith('/') ? basePath : '/' + basePath));
  return new URL(relPath, base).pathname;
}

async function vendor(esmPath) {
  const key = keyFor(esmPath);
  if (inFlight.has(key)) return inFlight.get(key);

  const lp = localPath(key);
  keyToLocal.set(key, lp);

  const fetchUrl = ESM + (esmPath.startsWith('/') ? esmPath : '/' + esmPath);

  const promise = (async () => {
    const text = await fetchText(fetchUrl);
    const imports = extractImports(text);

    await Promise.all(imports.map(({ path, isRelative }) => {
      const resolved = isRelative ? resolveRelative('/' + key, path) : path;
      return vendor(resolved);
    }));

    const fullLocal = join(OUT_DIR, lp);
    const rewritten = text
      .replace(/\/\/# sourceMappingURL=\S+/g, '')
      .replace(
        /(\b(?:from|import)\s*)(["'])([^"'\n]+)\2/g,
        (match, kw, q, imp) => {
          if (!imp.startsWith('/')) return match;
          const impKey = keyFor(imp);
          const target = keyToLocal.get(impKey);
          if (!target) return match;
          const rel = relative(dirname(fullLocal), join(OUT_DIR, target));
          return kw + q + (rel.startsWith('.') ? rel : './' + rel) + q;
        }
      );

    await mkdir(dirname(fullLocal), { recursive: true });
    await writeFile(fullLocal, rewritten, 'utf8');
    process.stderr.write(`  ✓ ${lp}\n`);
    return lp;
  })();

  inFlight.set(key, promise);
  return promise;
}

process.stderr.write(`Vendoring CodeMirror from esm.sh → ${OUT_DIR}/\n\n`);
await mkdir(OUT_DIR, { recursive: true });

const results = {};
for (const entry of ENTRIES) {
  results[entry] = await vendor('/' + entry);
}

process.stderr.write('\nEntry file paths (relative to OUT_DIR):\n');
for (const [pkg, lp] of Object.entries(results)) {
  process.stderr.write(`  ${pkg}: ${lp}\n`);
}

// Fix up the 6.x.x filenames that were saved without .js
const { execSync } = await import('node:child_process');
const fixup = [
  'commands@6.x.x', 'language@6.x.x', 'search@6.x.x',
  'state@6.x.x', 'view@6.x.x'
];
for (const f of fixup) {
  const src = join(OUT_DIR, '@codemirror', f);
  const dst = src + '.js';
  try {
    const { rename } = await import('node:fs/promises');
    await rename(src, dst);
    process.stderr.write(`  renamed ${f} → ${f}.js\n`);
  } catch { /* already renamed or doesn't exist */ }
}

// Fix imports referencing 6.x.x without .js
execSync(`grep -rl "@6\\.x\\.x\\"" "${OUT_DIR}" 2>/dev/null | xargs -r sed -i '' 's/@6\\.x\\.x"/@6.x.x.js"/g'`, { stdio: 'inherit', shell: '/bin/sh' });

process.stderr.write('\nDone.\n');

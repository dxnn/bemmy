#!/bin/sh
# Download the static CDN deps into public/vendor/ so the page is
# self-hostable with no third-party CDN at runtime (CodeMirror is
# still ESM-imported from esm.sh; that's a separate refactor).
#
# Idempotent — re-run after bumping any version below.
set -e

KATEX_VER="0.16.9"
MAFS_VER="0.21.0"
HLJS_VER="11.9.0"
REACT_VER="18"

cd "$(dirname "$0")/.." || exit 1

mkdir -p public/vendor/katex/fonts
mkdir -p public/vendor/mafs
mkdir -p public/vendor/hljs
mkdir -p public/vendor/react

KATEX_CDN="https://cdn.jsdelivr.net/npm/katex@${KATEX_VER}/dist"
MAFS_CDN="https://cdn.jsdelivr.net/npm/mafs@${MAFS_VER}"
HLJS_CDN="https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@${HLJS_VER}/build"
REACT_CDN="https://unpkg.com/react@${REACT_VER}/umd"
REACT_DOM_CDN="https://unpkg.com/react-dom@${REACT_VER}/umd"

echo "==> KaTeX"
curl -sSL "${KATEX_CDN}/katex.min.css" -o public/vendor/katex/katex.min.css
curl -sSL "${KATEX_CDN}/katex.min.js"  -o public/vendor/katex/katex.min.js

# Fonts referenced by katex.min.css via ./fonts/<name>.woff2
KATEX_FONTS="KaTeX_AMS-Regular \
KaTeX_Caligraphic-Bold KaTeX_Caligraphic-Regular \
KaTeX_Fraktur-Bold KaTeX_Fraktur-Regular \
KaTeX_Main-Bold KaTeX_Main-BoldItalic KaTeX_Main-Italic KaTeX_Main-Regular \
KaTeX_Math-BoldItalic KaTeX_Math-Italic \
KaTeX_SansSerif-Bold KaTeX_SansSerif-Italic KaTeX_SansSerif-Regular \
KaTeX_Script-Regular \
KaTeX_Size1-Regular KaTeX_Size2-Regular KaTeX_Size3-Regular KaTeX_Size4-Regular \
KaTeX_Typewriter-Regular"
for f in $KATEX_FONTS; do
  curl -sSL "${KATEX_CDN}/fonts/${f}.woff2" -o "public/vendor/katex/fonts/${f}.woff2"
done

echo "==> Mafs"
curl -sSL "${MAFS_CDN}/core.css" -o public/vendor/mafs/core.css

echo "==> highlight.js"
curl -sSL "${HLJS_CDN}/styles/github.min.css"        -o public/vendor/hljs/github.min.css
curl -sSL "${HLJS_CDN}/styles/github-dark.min.css"   -o public/vendor/hljs/github-dark.min.css
curl -sSL "${HLJS_CDN}/highlight.min.js"             -o public/vendor/hljs/highlight.min.js
curl -sSL "${HLJS_CDN}/languages/clojure.min.js"     -o public/vendor/hljs/clojure.min.js

echo "==> React UMD"
curl -sSL "${REACT_CDN}/react.production.min.js"           -o public/vendor/react/react.production.min.js
curl -sSL "${REACT_DOM_CDN}/react-dom.production.min.js"   -o public/vendor/react/react-dom.production.min.js

echo "==> Done. Vendored into public/vendor/."

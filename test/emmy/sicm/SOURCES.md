# Vendored Emmy SICM Tests

The `chN_test.cljc` files in this directory are verbatim copies from
the Mentat Collective's Emmy library:

- Origin: <https://github.com/mentat-collective/emmy/tree/main/test/emmy/sicm>
- Raw paths:
  - <https://raw.githubusercontent.com/mentat-collective/emmy/main/test/emmy/sicm/ch1_test.cljc>
  - <https://raw.githubusercontent.com/mentat-collective/emmy/main/test/emmy/sicm/ch2_test.cljc>
  - <https://raw.githubusercontent.com/mentat-collective/emmy/main/test/emmy/sicm/ch3_test.cljc>
  - <https://raw.githubusercontent.com/mentat-collective/emmy/main/test/emmy/sicm/ch5_test.cljc>
  - <https://raw.githubusercontent.com/mentat-collective/emmy/main/test/emmy/sicm/ch6_test.cljc>
  - <https://raw.githubusercontent.com/mentat-collective/emmy/main/test/emmy/sicm/ch7_test.cljc>
- License: GPL-3.0 (per each file's `SPDX-License-Identifier` header)
- Vendored on: 2026-05-10

These files are chapter-by-chapter Clojure/Emmy ports of examples from
*Structure and Interpretation of Classical Mechanics* (Sussman &
Wisdom). `bin/build-emmy-sicm-pages.bb` transforms each `(deftest
section-X-Y …)` into a BEmmy playground page; the generated page text
in `public/app.cljs` carries an attribution header pointing back here.

The companion `test/emmy/examples/*.cljc` files (vendored from
`mentat-collective/emmy/test/emmy/examples/`) are required by some of
the chapter tests (e.g. `driven-pendulum`, `pendulum`). Same upstream
license.

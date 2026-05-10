'use strict';

const { describe, test } = require('node:test');
const assert = require('node:assert/strict');

// The IIFE assigns SicmToEmmy to globalThis when loaded as CJS.
require('../public/sicm2emmy.js');
const { translate } = globalThis.SicmToEmmy;

function tr(src) {
  return translate(src).trim();
}

// ---- Symbol mapping -------------------------------------------------------

describe('symbol mapping', () => {
  test('list primitives', () => {
    assert.equal(tr('(car x)'),   '(first x)');
    assert.equal(tr('(cdr x)'),   '(rest x)');
    assert.equal(tr('(cadr x)'),  '(second x)');
    assert.equal(tr('(caddr x)'), '(nth x 2)');
    assert.equal(tr('(cadddr x)'), '(nth x 3)');
  });

  test('ca*r chains via loop', () => {
    // Regex requires pattern ^ca[ad]+r$; only caXXr forms are handled.
    // caar: chain='a', reversed [a] → first(first(x))
    assert.equal(tr('(caar x)'), '(first (first x))');
    // caaar: chain='aa', reversed [a,a,a] → first(first(first(x)))
    assert.equal(tr('(caaar x)'), '(first (first (first x)))');
    // cadar: chain='ad', reversed [d,a] → rest(first(x)) then first
    assert.equal(tr('(cadar x)'), '(first (rest (first x)))');
  });

  test('predicates', () => {
    assert.equal(tr('(null? x)'),   '(nil? x)');
    assert.equal(tr('(pair? x)'),   '(seq? x)');
    assert.equal(tr('(integer? x)'), '(integer? x)');
    assert.equal(tr('(zero? x)'),   '(zero? x)');
  });

  test('arithmetic rename', () => {
    assert.equal(tr('(remainder x y)'), '(rem x y)');
    assert.equal(tr('(modulo x y)'),    '(mod x y)');
    assert.equal(tr('(quotient x y)'),  '(quot x y)');
    assert.equal(tr('(1+ x)'),  '(inc x)');
    assert.equal(tr('(-1+ x)'), '(dec x)');
    assert.equal(tr('(1- x)'),  '(dec x)');
  });

  test('collection ops', () => {
    assert.equal(tr('(length xs)'),             '(count xs)');
    assert.equal(tr('(append xs ys)'),          '(concat xs ys)');
    assert.equal(tr('(fold-left f init xs)'),   '(reduce f init xs)');
    assert.equal(tr('(delete-duplicates xs)'),  '(distinct xs)');
    assert.equal(tr('(for-each f xs)'),         '(run! f xs)');
  });

  test('i/o', () => {
    assert.equal(tr('(display x)'),  '(print x)');
    assert.equal(tr('(newline)'),    '(println)');
    assert.equal(tr('(print x)'),    '(println x)');
  });

  test('scmutils-specific → simplify', () => {
    assert.equal(tr('(show-expression x)'),    '(simplify x)');
    assert.equal(tr('(print-expression x)'),   '(simplify x)');
    assert.equal(tr('(simplify-expression x)'), '(simplify x)');
  });

  test('Inf/NaN literals', () => {
    assert.equal(tr('+inf.0'), '##Inf');
    assert.equal(tr('-inf.0'), '##-Inf');
    assert.equal(tr('+nan.0'), '##NaN');
  });
});

// ---- SICM pi constants ----------------------------------------------------

describe('SICM constants', () => {
  test(':pi', ()     => assert.equal(tr(':pi'),    'Math/PI'));
  test(':2pi', ()    => assert.equal(tr(':2pi'),   '(* 2 Math/PI)'));
  test(':pi/2', ()   => assert.equal(tr(':pi/2'),  '(/ Math/PI 2)'));
  test(':pi/3', ()   => assert.equal(tr(':pi/3'),  '(/ Math/PI 3)'));
  test(':pi/4', ()   => assert.equal(tr(':pi/4'),  '(/ Math/PI 4)'));
  test(':-pi', ()    => assert.equal(tr(':-pi'),   '(- Math/PI)'));
  test(':-pi/2', ()  => assert.equal(tr(':-pi/2'), '(- (/ Math/PI 2))'));
});

// ---- define ---------------------------------------------------------------

describe('define', () => {
  test('function form', () => {
    assert.equal(tr('(define (square x) (* x x))'),
                 '(defn square [x] (* x x))');
  });

  test('value form', () => {
    assert.equal(tr('(define x 5)'), '(def x 5)');
  });

  test('define + lambda → defn', () => {
    assert.equal(tr('(define square (lambda (x) (* x x)))'),
                 '(defn square [x] (* x x))');
  });

  test('variadic with dot', () => {
    assert.equal(tr('(define (foo . args) args)'),
                 '(defn foo [& args] args)');
  });

  test('fixed + variadic', () => {
    assert.equal(tr('(define (foo x . rest) rest)'),
                 '(defn foo [x & rest] rest)');
  });

  test('curried define', () => {
    // Fits on one line at col 0 (34 chars ≤ 72), so no wrapping
    assert.equal(tr('(define ((adder x) y) (+ x y))'),
                 '(defn adder [x] (fn [y] (+ x y)))');
  });
});

// ---- lambda ---------------------------------------------------------------

describe('lambda', () => {
  test('basic', () => {
    assert.equal(tr('(lambda (x) (* x x))'), '(fn [x] (* x x))');
  });

  test('multi-arg', () => {
    assert.equal(tr('(lambda (x y) (+ x y))'), '(fn [x y] (+ x y))');
  });

  test('variadic (rest-args)', () => {
    assert.equal(tr('(lambda args (length args))'), '(fn [& args] (count args))');
  });
});

// ---- let forms ------------------------------------------------------------

describe('let forms', () => {
  test('let', () => {
    assert.equal(tr('(let ((x 1) (y 2)) (+ x y))'),
                 '(let [x 1 y 2] (+ x y))');
  });

  test('let*', () => {
    assert.equal(tr('(let* ((x 1) (y x)) y)'),
                 '(let [x 1 y x] y)');
  });

  test('named let → letfn + initial call', () => {
    // Named let binds a labeled function so the body can recurse on it.
    // Translate to letfn rather than Clojure `loop`, which would require
    // rewriting recursive calls to `recur`.
    assert.equal(tr('(let loop ((n 0)) n)'),
                 '(letfn [(loop [n] n)] (loop 0))');
    assert.equal(tr('(let f ((c 1)) (if (> c 5) c (f (+ c 1))))'),
                 '(letfn [(f [c] (if (> c 5) c (f (+ c 1))))] (f 1))');
  });

  test('letrec (all fns) → letfn', () => {
    assert.equal(tr('(letrec ((f (lambda (x) x))) (f 1))'),
                 '(letfn [(f [x] x)] (f 1))');
  });
});

// ---- internal defines → let/letfn ----------------------------------------

describe('internal defines', () => {
  test('val def → let', () => {
    // Flat form fits in 72 chars, so emitter returns single line
    assert.equal(tr('(define (f x) (define a 1) (+ x a))'),
                 '(defn f [x] (let [a 1] (+ x a)))');
  });

  test('fn def → letfn', () => {
    assert.equal(tr('(define (f x) (define (g y) y) (g x))'),
                 '(defn f [x] (letfn [(g [y] y)] (g x)))');
  });
});

// ---- conditionals ---------------------------------------------------------

describe('conditionals', () => {
  test('if with else', () => {
    assert.equal(tr('(if (> x 0) x (- x))'), '(if (> x 0) x (- x))');
  });

  test('if without else → when', () => {
    assert.equal(tr('(if (> x 0) x)'), '(when (> x 0) x)');
  });

  test('unless', () => {
    assert.equal(tr('(unless (> x 0) x)'), '(when-not (> x 0) x)');
  });

  test('cond with else', () => {
    assert.equal(tr('(cond ((= x 1) "one") (else "other"))'),
                 '(cond (= x 1) "one" :else "other")');
  });

  test('case', () => {
    assert.equal(tr('(case x ((1) "one") (else "other"))'),
                 '(case x 1 "one" :else "other")');
  });
});

// ---- begin ----------------------------------------------------------------

describe('begin', () => {
  test('single expr unwraps', () => {
    assert.equal(tr('(begin x)'), 'x');
  });

  test('multiple → do', () => {
    assert.equal(tr('(begin a b c)'), '(do a b c)');
  });
});

// ---- iota -----------------------------------------------------------------

describe('iota', () => {
  test('count only → range n', () => {
    assert.equal(tr('(iota 5)'), '(range 5)');
  });

  test('count + start → range start (+ start n)', () => {
    assert.equal(tr('(iota 3 1)'), '(range 1 (+ 1 3))');
  });

  test('count + start + step → range start (+ start (* n step)) step', () => {
    assert.equal(tr('(iota 3 0 2)'), '(range 0 (+ 0 (* 3 2)) 2)');
  });
});

// ---- sort (argument order flip) ------------------------------------------

describe('sort', () => {
  test('sort flips list and comparator', () => {
    assert.equal(tr('(sort xs <)'), '(sort < xs)');
  });

  test('sort! flips too', () => {
    assert.equal(tr('(sort! xs <)'), '(sort < xs)');
  });
});

// ---- hash tables ----------------------------------------------------------

describe('hash tables', () => {
  test('make-hash-table → {}', () => {
    assert.equal(tr('(make-hash-table)'), '{}');
  });

  test('hash-table-ref', () => {
    assert.equal(tr('(hash-table-ref m k)'), '(get m k)');
  });

  test('hash-table/get with default', () => {
    assert.equal(tr('(hash-table/get m k d)'), '(get m k d)');
  });

  test('hash-table-set!', () => {
    assert.equal(tr('(hash-table-set! m k v)'), '(swap! m assoc k v)');
  });

  test('hash-table-delete!', () => {
    assert.equal(tr('(hash-table-delete! m k)'), '(swap! m dissoc k)');
  });

  test('hash-table-keys', () => {
    assert.equal(tr('(hash-table-keys m)'), '(keys m)');
  });
});

// ---- error ----------------------------------------------------------------

describe('error', () => {
  test('error → throw ex-info', () => {
    assert.equal(tr('(error "msg")'), '(throw (ex-info "msg" {}))');
  });
});

// ---- string ops -----------------------------------------------------------

describe('string ops', () => {
  test('string-append → str', () => {
    assert.equal(tr('(string-append a b)'), '(str a b)');
  });

  test('string-prefix?', () => {
    assert.equal(tr('(string-prefix? "x" s)'),
                 '(clojure.string/starts-with? s "x")');
  });

  test('substring → subs', () => {
    assert.equal(tr('(substring s 1 3)'), '(subs s 1 3)');
  });
});

// ---- values ---------------------------------------------------------------

describe('values', () => {
  test('single value unwraps', () => {
    assert.equal(tr('(values x)'), 'x');
  });

  test('multiple values → vector', () => {
    assert.equal(tr('(values x y)'), '[x y]');
  });
});

// ---- records --------------------------------------------------------------

describe('define-record-type', () => {
  test('basic record', () => {
    // (define-record-type <point> (make-point x y) point? (x px) (y py))
    const out = tr('(define-record-type <point> (make-point x y) point? (x px) (y py))');
    assert.equal(out, '(defrecord point [x y])');
  });
});

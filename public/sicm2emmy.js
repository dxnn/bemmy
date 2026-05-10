// sicm2emmy.js — Scheme/SICM → Clojure/Emmy translator.
// Logic lifted from convert2.js's <script> block, minus the DOM glue and
// example library. Wrapped in an IIFE that exposes window.SicmToEmmy.
(function (global) {
  'use strict';

  // ---- TOKENIZER -----------------------------------------------------------

  function tokenize(src) {
    const toks = [];
    let i = 0;
    while (i < src.length) {
      const ch = src[i];
      if (ch === ' ' || ch === '\t' || ch === '\r') { i++; continue; }
      if (ch === '\n') { toks.push({t:'nl'}); i++; continue; }
      if (ch === ';') {
        let j = i;
        while (j < src.length && src[j] !== '\n') j++;
        toks.push({t:'comment', v:src.slice(i,j)}); i = j; continue;
      }
      if (ch === '"') {
        let j = i+1;
        while (j < src.length && src[j] !== '"') { if (src[j]==='\\') j++; j++; }
        toks.push({t:'atom', v:src.slice(i,j+1)}); i = j+1; continue;
      }
      if (ch==='(') { toks.push({t:'('}); i++; continue; }
      if (ch===')') { toks.push({t:')'}); i++; continue; }
      if (ch==='[') { toks.push({t:'['}); i++; continue; }
      if (ch===']') { toks.push({t:']'}); i++; continue; }
      if (ch==="'") { toks.push({t:'pfx',v:"'"}); i++; continue; }
      if (ch==='`') { toks.push({t:'pfx',v:"`"}); i++; continue; }
      if (ch===',') {
        if (src[i+1]==='@') { toks.push({t:'pfx',v:",@"}); i+=2; }
        else { toks.push({t:'pfx',v:","}); i++; }
        continue;
      }
      if (ch==='#') {
        const nx = src[i+1];
        if (nx==='t' && (i+2>=src.length || /[\s()\[\].]/.test(src[i+2]))) {
          toks.push({t:'atom',v:'true'}); i+=2; continue;
        }
        if (nx==='f' && (i+2>=src.length || /[\s()\[\].]/.test(src[i+2]))) {
          toks.push({t:'atom',v:'false'}); i+=2; continue;
        }
        if (nx==='\\') {
          let j = i+2;
          while (j<src.length && /\S/.test(src[j]) && !/[()[\]]/.test(src[j])) j++;
          const name = src.slice(i+2,j);
          const cm = {space:'\\space',newline:'\\newline',tab:'\\tab',return:'\\return'};
          toks.push({t:'atom', v: cm[name] || (name.length===1 ? '\\'+name : '\\'+name)});
          i = j; continue;
        }
        if (nx==='(') { toks.push({t:'vec-start'}); i+=2; continue; }
      }
      let j = i;
      while (j<src.length && !/[\s()\[\]{};"'`,]/.test(src[j])) j++;
      if (j>i) { toks.push({t:'atom', v:src.slice(i,j)}); i=j; }
      else { i++; }
    }
    return toks;
  }

  // ---- PARSER --------------------------------------------------------------

  function parseAll(src) {
    const tokens = tokenize(src);
    let pos = 0;
    const peek = () => pos < tokens.length ? tokens[pos] : null;

    function parseChildren(closer) {
      const ch = [];
      while (peek() && peek().t !== closer) {
        if (peek().t === 'nl') { pos++; continue; }
        if (peek().t === 'comment') { pos++; continue; }
        const child = parseExpr();
        if (child) ch.push(child);
      }
      if (peek()) pos++;
      return ch;
    }

    function parseExpr() {
      while (peek() && peek().t === 'nl') pos++;
      if (!peek()) return null;
      const tok = tokens[pos];
      if (tok.t === 'comment') { pos++; return {t:'comment',v:tok.v}; }
      if (tok.t === 'atom') { pos++; return {t:'atom',v:tok.v}; }
      if (tok.t === 'pfx') { pos++; return {t:'pfx',v:tok.v,c:parseExpr()}; }
      if (tok.t === '(' || tok.t === 'vec-start') {
        const isVec = tok.t === 'vec-start';
        pos++;
        const ch = parseChildren(')');
        return isVec ? {t:'vec',c:ch} : {t:'list',c:ch};
      }
      if (tok.t === '[') {
        pos++;
        return {t:'vec', c:parseChildren(']')};
      }
      pos++; return null;
    }

    const top = [];
    while (pos < tokens.length) {
      const t = peek();
      if (!t) break;
      if (t.t === 'nl') { pos++; top.push({t:'nl'}); continue; }
      if (t.t === 'comment') { pos++; top.push({t:'comment',v:t.v}); continue; }
      const expr = parseExpr();
      if (expr) top.push(expr);
    }
    return top;
  }

  // ---- TRANSFORMER ---------------------------------------------------------

  const A = v => ({t:'atom',v});
  const L = (...args) => ({t:'list', c: args.length===1&&Array.isArray(args[0]) ? args[0] : args});
  const V = (...args) => ({t:'vec',  c: args.length===1&&Array.isArray(args[0]) ? args[0] : args});
  const B = items => ({t:'bindings', c: items});
  const M = () => ({t:'map', c:[]});

  const SYM_MAP = {
    'car':'first',  'cdr':'rest',  'cadr':'second',
    'null?':'nil?', 'pair?':'seq?', 'list?':'seq?',
    'length':'count', 'append':'concat', 'last-pair':'last',
    'list-ref':'nth', 'vector-ref':'nth',
    // scmutils matrix accessors → Emmy (live in emmy.matrix as row/column).
    'm:nth-row':'row', 'm:nth-col':'column',
    // scmutils plural; Emmy exposes the singular state-tuple selector.
    'coordinates':'coordinate',
    'delete-duplicates':'distinct', 'remove-duplicates':'distinct',
    'every':'every?', 'any':'some',
    'fold-left':'reduce',
    'positive?':'pos?', 'negative?':'neg?', 'zero?':'zero?',
    'integer?':'integer?', 'number?':'number?',
    'string?':'string?', 'symbol?':'symbol?',
    'boolean?':'boolean?', 'procedure?':'ifn?', 'char?':'char?',
    'eq?':'identical?', 'eqv?':'=', 'equal?':'=',
    'char=?':'=', 'char<?':'<', 'char<=?':'<=', 'char>?':'>', 'char>=?':'>=',
    'string=?':'=', 'string<?':'<', 'string<=?':'<=', 'string>?':'>', 'string>=?':'>=',
    'boolean=?':'=',
    '1+':'inc', '-1+':'dec', '1-':'dec',
    'remainder':'rem', 'modulo':'mod', 'quotient':'quot',
    'ceiling':'ceil', 'floor':'floor', 'round':'round', 'truncate':'truncate',
    'abs':'abs', 'max':'max', 'min':'min', 'gcd':'gcd', 'lcm':'lcm',
    'expt':'expt', 'sqrt':'sqrt', 'exp':'exp', 'log':'log',
    'sin':'sin', 'cos':'cos', 'tan':'tan',
    'asin':'asin', 'acos':'acos', 'atan':'atan',
    'exact->inexact':'double', 'inexact->exact':'rationalize',
    'char->integer':'int', 'integer->char':'char',
    'string->symbol':'symbol', 'symbol->string':'name',
    'number->string':'str', 'string->number':'parse-long',
    'string-append':'str', 'string-length':'count', 'string-ref':'nth',
    'string-upcase':'clojure.string/upper-case',
    'string-downcase':'clojure.string/lower-case',
    'string-contains':'clojure.string/includes?',
    'string-copy':'identity',
    'display':'print', 'write':'pr', 'pp':'pprint',
    'for-each':'run!', 'void':'nil',
    'error-message':'ex-message',
    // scmutils niceties — the playground TeX-renders results, so
    // (show-expression x) is just (simplify x) under Emmy.
    'show-expression':'simplify', 'simplify-expression':'simplify',
    'print-expression':'simplify',
  };

  // scmutils binds symbols like :pi/2 to numeric constants; in Clojure
  // those parse as keywords, so we expand to numeric expressions instead.
  // Returns AST nodes, not strings.
  const PI = () => A('Math/PI');
  const SCMUTILS_CONSTS = {
    ':pi':    () => PI(),
    ':2pi':   () => L(A('*'), A('2'),  PI()),
    ':pi/2':  () => L(A('/'), PI(), A('2')),
    ':pi/3':  () => L(A('/'), PI(), A('3')),
    ':pi/4':  () => L(A('/'), PI(), A('4')),
    ':pi/6':  () => L(A('/'), PI(), A('6')),
    ':-pi':   () => L(A('-'), PI()),
    ':-pi/2': () => L(A('-'), L(A('/'), PI(), A('2'))),
    ':-pi/3': () => L(A('-'), L(A('/'), PI(), A('3'))),
    ':-pi/4': () => L(A('-'), L(A('/'), PI(), A('4'))),
    ':-pi/6': () => L(A('-'), L(A('/'), PI(), A('6'))),
  };

  function txAtom(v) {
    if (v === '+inf.0') return '##Inf';
    if (v === '-inf.0') return '##-Inf';
    if (v === '+nan.0' || v === '-nan.0') return '##NaN';
    return SYM_MAP[v] != null ? SYM_MAP[v] : v;
  }

  // Names bound by a list-form's binding spec (let/lambda/fn). Used to
  // skip recursing into inner scopes that shadow a target name.
  function getShadowedNames(node) {
    if (!node || node.t !== 'list' || node.c.length < 2) return new Set();
    const head = node.c[0] && node.c[0].v;
    if (head === 'lambda' || head === 'fn') {
      const args = node.c[1];
      if (!args) return new Set();
      if (args.t === 'atom') return new Set([args.v]);
      if (args.t === 'list' || args.t === 'vec') {
        return new Set((args.c || []).filter(n => n && n.t === 'atom').map(n => n.v));
      }
    }
    if (head === 'let' || head === 'let*' || head === 'letrec') {
      const bindings = node.c[1];
      if (bindings && bindings.t === 'list') {
        return new Set(
          bindings.c.filter(p => p && p.t === 'list' && p.c[0]).map(p => p.c[0].v).filter(Boolean)
        );
      }
    }
    return new Set();
  }

  // Walk an AST collecting names mutated via (set! name ...). Skips inner
  // binding forms that shadow the name.
  function collectMutatedNames(node, into, excluded) {
    excluded = excluded || new Set();
    if (!node) return;
    if (node.t === 'list' && node.c.length >= 2) {
      const head = node.c[0] && node.c[0].v;
      if (head === 'set!' && node.c[1] && node.c[1].t === 'atom' && !excluded.has(node.c[1].v)) {
        into.add(node.c[1].v);
      }
      const shadowed = getShadowedNames(node);
      if (shadowed.size > 0) {
        const newExcluded = new Set(excluded);
        for (const s of shadowed) newExcluded.add(s);
        for (const child of node.c) collectMutatedNames(child, into, newExcluded);
        return;
      }
    }
    if (Array.isArray(node.c)) {
      for (const child of node.c) collectMutatedNames(child, into, excluded);
    } else if (node.c) {
      collectMutatedNames(node.c, into, excluded);
    }
  }

  // Rewrite atom references and (set! …) inside `node` for any name in
  // `names`: atom → (deref name), (set! name e) → (vreset! name e).
  // Skips inner scopes that shadow a name.
  function rewriteRefsAndSets(node, names) {
    if (!node) return node;
    if (node.t === 'atom' && names.has(node.v)) {
      return L(A('deref'), A(node.v));
    }
    if (node.t === 'list' && node.c[0] && node.c[0].v === 'set!' &&
        node.c[1] && node.c[1].t === 'atom' && names.has(node.c[1].v)) {
      return L(A('vreset!'), A(node.c[1].v),
               rewriteRefsAndSets(node.c[2], names));
    }
    if (node.t === 'list') {
      const shadowed = getShadowedNames(node);
      if (shadowed.size > 0) {
        const remaining = new Set();
        for (const n of names) if (!shadowed.has(n)) remaining.add(n);
        if (remaining.size === 0) return node;
        return Object.assign({}, node, {c: node.c.map(child => rewriteRefsAndSets(child, remaining))});
      }
    }
    if (Array.isArray(node.c)) {
      return Object.assign({}, node, {c: node.c.map(child => rewriteRefsAndSets(child, names))});
    } else if (node.c) {
      return Object.assign({}, node, {c: rewriteRefsAndSets(node.c, names)});
    }
    return node;
  }

  function liftInternalDefs(body) {
    if (body.length === 0) return body;
    const defs = [];
    let i = 0;
    while (i < body.length) {
      const n = body[i];
      const h = n && n.t==='list' ? (n.c[0] && n.c[0].v) : null;
      if (h === 'def' || h === 'defn') { defs.push(n); i++; } else break;
    }
    if (defs.length === 0) return body;
    const rest = body.slice(i);
    if (rest.length === 0) return body;

    const fnDefs  = defs.filter(d => d.c[0] && d.c[0].v === 'defn');
    const valDefs = defs.filter(d => d.c[0] && d.c[0].v === 'def');

    const mkFnSpec = d => {
      const [, name, args, ...fb] = d.c;
      return L(name, args, ...fb);
    };

    if (fnDefs.length > 0 && valDefs.length === 0) {
      return [L(A('letfn'), V(fnDefs.map(mkFnSpec)), ...rest)];
    }
    if (fnDefs.length > 0) {
      const pairs = valDefs.flatMap(d => [d.c[1], d.c[2] || A('nil')]);
      return [L(A('letfn'), V(fnDefs.map(mkFnSpec)), L(A('let'), B(pairs), ...rest))];
    }
    const pairs = defs.flatMap(d => [d.c[1], d.c[2] || A('nil')]);
    return [L(A('let'), B(pairs), ...rest)];
  }

  function tx(node) {
    if (!node) return node;
    if (node.t === 'atom') {
      if (SCMUTILS_CONSTS[node.v]) return SCMUTILS_CONSTS[node.v]();
      return A(txAtom(node.v));
    }
    if (node.t === 'nl' || node.t === 'comment') return node;
    if (node.t === 'pfx')  return {t:'pfx', v:node.v, c:tx(node.c)};
    if (node.t === 'vec')  return V(node.c.map(tx));
    if (node.t !== 'list') return node;

    const c = node.c;
    if (c.length === 0) return node;
    const hs = c[0] && c[0].t === 'atom' ? c[0].v : null;

    if (hs === 'define' && c[1] && c[1].t === 'list') {
      const sig = c[1].c;
      if (sig.length === 0) return L(A('defn'), ...c.slice(1).map(tx));
      const name = sig[0];
      // Curried define:
      //   (define ((inner...) outer-args...) body)
      // ≡ (define (inner...) (lambda (outer-args...) body))
      // Rebuild the AST and re-tx; recursion handles deeper curry too.
      if (name && name.t === 'list') {
        const outerArgs = sig.slice(1);
        const lam = {t:'list', c: [A('lambda'),
                                   {t:'list', c: outerArgs},
                                   ...c.slice(2)]};
        return tx({t:'list', c: [A('define'), name, lam]});
      }
      const dotIdx = sig.findIndex(n => n.t==='atom' && n.v==='.');
      const args = dotIdx > -1
        ? V([...sig.slice(1,dotIdx).map(tx), A('&'), ...sig.slice(dotIdx+1).map(tx)])
        : V(sig.slice(1).map(tx));
      const rawBody = c.slice(2).map(tx);
      if (rawBody.length > 1 && rawBody[0] && rawBody[0].t==='atom' && rawBody[0].v.startsWith('"')) {
        const [doc, ...rest] = rawBody;
        return L(A('defn'), name, doc, args, ...liftInternalDefs(rest));
      }
      return L(A('defn'), name, args, ...liftInternalDefs(rawBody));
    }

    if (hs === 'define' && c[1] && c[1].t === 'atom') {
      // (define name (lambda (args) body)) → (defn name [args] body).
      // SICM idiomatically uses both (define (foo ...) ...) and the
      // explicit-lambda form interchangeably; collapse to defn so the
      // output stays readable. (Borrowed from convert1.js.)
      const val = c[2];
      if (val && val.t === 'list' && val.c.length >= 2 &&
          val.c[0] && val.c[0].t === 'atom' && val.c[0].v === 'lambda') {
        const argsNode = val.c[1];
        let args;
        if (argsNode.t === 'atom') {
          args = V(A('&'), tx(argsNode));
        } else if (argsNode.t === 'list') {
          const di = argsNode.c.findIndex(n => n.t==='atom' && n.v==='.');
          args = di > -1
            ? V([...argsNode.c.slice(0,di).map(tx), A('&'),
                 ...argsNode.c.slice(di+1).map(tx)])
            : V(argsNode.c.map(tx));
        } else {
          args = V();
        }
        return L(A('defn'), c[1], args,
                 ...liftInternalDefs(val.c.slice(2).map(tx)));
      }
      return L(A('def'), c[1], c.length >= 3 ? tx(c[2]) : A('nil'));
    }

    if (hs === 'lambda' && c.length >= 3) {
      let args;
      if (c[1].t === 'atom') {
        args = V(A('&'), tx(c[1]));
      } else if (c[1].t === 'list') {
        const di = c[1].c.findIndex(n => n.t==='atom' && n.v==='.');
        args = di > -1
          ? V([...c[1].c.slice(0,di).map(tx), A('&'), ...c[1].c.slice(di+1).map(tx)])
          : V(c[1].c.map(tx));
      } else { args = V(); }
      return L(A('fn'), args, ...liftInternalDefs(c.slice(2).map(tx)));
    }

    if ((hs === 'let' || hs === 'let*') && c[1] && c[1].t === 'list') {
      const bindingPairs = c[1].c.filter(n => n.t === 'list');
      const body = c.slice(2);

      // Detect Scheme's mutable-let pattern (e.g. make-counter): a let
      // whose body uses (set! name ...) on a bound name. Rewrite that
      // binding to a `volatile!` cell, references to (deref name), and
      // (set! name v) to (vreset! name v).
      const mutated = new Set();
      for (const stmt of body) collectMutatedNames(stmt, mutated);
      const mutatedHere = new Set(
        bindingPairs.map(p => p.c[0] && p.c[0].v).filter(n => n && mutated.has(n))
      );

      if (mutatedHere.size > 0) {
        const newBindings = bindingPairs.flatMap(p => {
          const name = p.c[0];
          const init = tx(p.c[1]);
          return [tx(name),
                  mutatedHere.has(name.v) ? L(A('volatile!'), init) : init];
        });
        const newBody = body.map(stmt =>
          tx(rewriteRefsAndSets(stmt, mutatedHere)));
        return L(A('let'), B(newBindings), ...newBody);
      }

      const pairs = bindingPairs.flatMap(p => [tx(p.c[0]), tx(p.c[1])]);
      return L(A('let'), B(pairs), ...body.map(tx));
    }

    if (hs === 'let' && c[1] && c[1].t === 'atom' && c[2] && c[2].t === 'list') {
      const pairs = c[2].c.filter(n=>n.t==='list').flatMap(p => [tx(p.c[0]), tx(p.c[1])]);
      return L(A('loop'), B(pairs), ...c.slice(3).map(tx));
    }

    if (hs === 'letrec' && c[1] && c[1].t === 'list') {
      const binds = c[1].c.filter(n=>n.t==='list');
      const allFn = binds.every(b => b.c[1] && b.c[1].t==='list' && b.c[1].c[0] && b.c[1].c[0].v==='lambda');
      if (allFn) {
        const fns = binds.map(b => {
          const [fname, lam] = [b.c[0], b.c[1]];
          const ai = lam.c[1];
          const args = ai && ai.t==='list' ? V(ai.c.map(tx)) : V(A('&'), tx(ai));
          return L(fname, args, ...lam.c.slice(2).map(tx));
        });
        return L(A('letfn'), V(fns), ...c.slice(2).map(tx));
      }
      const pairs = binds.flatMap(b => [tx(b.c[0]), tx(b.c[1])]);
      return L(A('let'), B(pairs), ...c.slice(2).map(tx));
    }

    if (hs === 'begin') {
      const body = c.slice(1).map(tx);
      if (body.length === 1) return body[0];
      return L(A('do'), ...body);
    }

    if (hs === 'do' && c.length >= 3 && c[1] && c[1].t==='list' && c[2] && c[2].t==='list') {
      const specs   = c[1].c.filter(n=>n.t==='list');
      const testCl  = c[2].c;
      const cmds    = c.slice(3).map(tx);
      const binds   = specs.flatMap(s => [tx(s.c[0]), tx(s.c[1])]);
      const steps   = specs.map(s => s.c[2] ? tx(s.c[2]) : tx(s.c[0]));
      const tExpr   = tx(testCl[0]);
      const results = testCl.slice(1).map(tx);
      const rExpr   = results.length===0 ? A('nil')
                    : results.length===1 ? results[0]
                    : L(A('do'), ...results);
      const recur   = L(A('recur'), ...steps);
      const elseB   = cmds.length===0 ? recur : L(A('do'), ...cmds, recur);
      return L(A('loop'), B(binds), L(A('if'), tExpr, rExpr, elseB));
    }

    if (hs === 'cond') {
      const flat = c.slice(1).flatMap(cl => {
        const cs = cl.c || [];
        const key = (cs[0] && cs[0].t==='atom' && cs[0].v==='else') ? A(':else') : tx(cs[0]);
        const exprs = cs.slice(1).map(tx);
        const val = exprs.length>1 ? L(A('do'),...exprs) : (exprs[0] != null ? exprs[0] : A('nil'));
        return [key, val];
      });
      return L(A('cond'), ...flat);
    }

    if (hs === 'case') {
      const val = tx(c[1]);
      const cls = c.slice(2).flatMap(cl => {
        const cs = cl.c || [];
        const exprs = cs.slice(1).map(tx);
        const v = exprs.length>1 ? L(A('do'),...exprs) : (exprs[0] != null ? exprs[0] : A('nil'));
        if (cs[0] && cs[0].t==='atom' && cs[0].v==='else') return [A(':else'), v];
        if (cs[0] && cs[0].t==='list') return cs[0].c.flatMap(k => [tx(k), v]);
        return [tx(cs[0]), v];
      });
      return L(A('case'), val, ...cls);
    }

    if (hs === 'if' && c.length === 3) return L(A('when'), tx(c[1]), tx(c[2]));
    if (hs === 'if') return L(A('if'), tx(c[1]), tx(c[2]), tx(c[3]));

    if (hs === 'unless') return L(A('when-not'), tx(c[1]), ...c.slice(2).map(tx));

    if (hs === 'values') {
      if (c.length === 2) return tx(c[1]);
      return V(c.slice(1).map(tx));
    }

    if (hs === 'call-with-values' || hs === 'with-values') {
      const [prod, cons] = [c[1], c[2]];
      const isLam = n => n && n.t==='list' && n.c[0] && n.c[0].v==='lambda';
      if (isLam(prod) && isLam(cons)) {
        const pb = prod.c.slice(2).map(tx);
        const cargs = ((cons.c[1] && cons.c[1].c) || []).map(tx);
        const cb = cons.c.slice(2).map(tx);
        const produced = pb.length===1 ? pb[0] : L(A('do'), ...pb);
        const binding = cargs.length>1 ? [V(cargs), produced]
                      : cargs.length===1 ? [cargs[0], produced]
                      : [A('_'), produced];
        return L(A('let'), B(binding), ...cb);
      }
      return L(tx(cons), L(tx(prod)));
    }

    if (hs === 'receive' && c.length >= 4 && c[1] && c[1].t === 'list') {
      const args = c[1].c.map(tx);
      const produced = tx(c[2]);
      const body = c.slice(3).map(tx);
      const binding = args.length>1 ? [V(args), produced] : [args[0] || A('_'), produced];
      return L(A('let'), B(binding), ...body);
    }

    if (hs === 'iota') {
      if (c.length === 2) return L(A('range'), tx(c[1]));
      if (c.length === 3) {
        const [n, s] = [tx(c[1]), tx(c[2])];
        return L(A('range'), s, L(A('+'), s, n));
      }
      const [n, s, st] = [tx(c[1]), tx(c[2]), tx(c[3])];
      return L(A('range'), s, L(A('+'), s, L(A('*'), n, st)), st);
    }

    if ((hs === 'sort' || hs === 'sort!') && c.length === 3) {
      return L(A('sort'), tx(c[2]), tx(c[1]));
    }

    if (hs === 'list-tail' && c.length === 3) {
      return L(A('drop'), tx(c[2]), tx(c[1]));
    }

    if (hs === 'string-prefix?' && c.length === 3) {
      return L(A('clojure.string/starts-with?'), tx(c[2]), tx(c[1]));
    }
    if (hs === 'string-suffix?' && c.length === 3) {
      return L(A('clojure.string/ends-with?'), tx(c[2]), tx(c[1]));
    }
    if (hs === 'substring') {
      return L(A('subs'), ...c.slice(1).map(tx));
    }

    if ((hs === 'fluid-let' || hs === 'parameterize') && c[1] && c[1].t === 'list') {
      const pairs = c[1].c.filter(n=>n.t==='list').flatMap(p => [tx(p.c[0]), tx(p.c[1])]);
      return L(A('binding'), B(pairs), ...c.slice(2).map(tx));
    }

    if (hs === 'make-parameter') {
      return L(A('atom'), c.length>=2 ? tx(c[1]) : A('nil'));
    }
    if (hs === 'parameter-ref' && c.length === 2) {
      return {t:'pfx', v:'@', c:tx(c[1])};
    }
    if (hs === 'parameter-set!' && c.length === 3) {
      return L(A('reset!'), tx(c[1]), tx(c[2]));
    }

    if (hs === 'error') {
      const msg = c[1] ? tx(c[1]) : A('"error"');
      return L(A('throw'), L(A('ex-info'), msg, M()));
    }

    if (hs === 'guard' && c.length >= 3) {
      const varCl  = c[1];
      const eVar   = (varCl && varCl.c && varCl.c[0]) || A('e');
      const clauses = ((varCl && varCl.c) || []).slice(1);
      const body   = c.slice(2).map(tx);
      const conds  = clauses.flatMap(cl => {
        const cs = cl.c || [];
        const key = cs[0] && cs[0].v==='else' ? A(':else') : tx(cs[0]);
        const exprs = cs.slice(1).map(tx);
        return [key, exprs.length>1 ? L(A('do'),...exprs) : (exprs[0] != null ? exprs[0] : A('nil'))];
      });
      return L(A('try'), ...body,
        L(A('catch'), A('Exception'), tx(eVar), L(A('cond'), ...conds)));
    }

    if (hs === 'make-hash-table' || hs === 'make-equal-hash-table' ||
        hs === 'make-strong-hash-table') {
      return M();
    }
    if ((hs==='hash-table/get'||hs==='hash-table-ref/default') && c.length>=4) {
      return L(A('get'), tx(c[1]), tx(c[2]), tx(c[3]));
    }
    if (hs==='hash-table-ref' && c.length===3) {
      return L(A('get'), tx(c[1]), tx(c[2]));
    }
    if (hs==='hash-table/put!' || hs==='hash-table-set!') {
      return L(A('swap!'), tx(c[1]), A('assoc'), tx(c[2]), tx(c[3]));
    }
    if (hs==='hash-table/remove!' || hs==='hash-table-delete!') {
      return L(A('swap!'), tx(c[1]), A('dissoc'), tx(c[2]));
    }
    if (hs==='hash-table-walk') {
      return L(A('run!'), L(A('fn'), V(V(A('k'),A('v'))), tx(c[2])), tx(c[1]));
    }
    if (hs==='hash-table-keys')   return L(A('keys'), tx(c[1]));
    if (hs==='hash-table-values') return L(A('vals'), tx(c[1]));
    if (hs==='hash-table->alist') return L(A('seq'),  tx(c[1]));

    // scmutils series printer:
    //   (series:for-each f s n) → (run! f (take n s))
    if (hs === 'series:for-each' && c.length === 4) {
      return L(A('run!'), tx(c[1]), L(A('take'), tx(c[3]), tx(c[2])));
    }

    if (hs === 'define-record-type') {
      const raw = (c[1] && c[1].v) || 'Record';
      const name = raw.replace(/[<>]/g,'');
      const fieldSpecs = c.slice(4);
      const fields = fieldSpecs.map(fs => fs && fs.c && fs.c[0]).filter(Boolean);
      return L(A('defrecord'), A(name), V(fields.map(tx)));
    }

    if (hs && /^ca[ad]+r$/.test(hs) && c.length === 2) {
      if (hs==='cadr')   return L(A('second'), tx(c[1]));
      if (hs==='caddr')  return L(A('nth'), tx(c[1]), A('2'));
      if (hs==='cadddr') return L(A('nth'), tx(c[1]), A('3'));
      const chain = hs.slice(1,-1);
      let res = tx(c[1]);
      for (const ch of [...chain].reverse())
        res = L(A(ch==='a'?'first':'rest'), res);
      return res;
    }

    if (hs === 'newline' && c.length===1) return L(A('println'));
    if (hs === 'print')  return L(A('println'), ...c.slice(1).map(tx));
    if (hs === 'assert') return L(A('assert'),  ...c.slice(1).map(tx));

    return L(c.map(tx));
  }

  function txAll(nodes) {
    return nodes.map(n => (n.t==='comment'||n.t==='nl') ? n : tx(n));
  }

  // ---- EMITTER -------------------------------------------------------------

  const BODY_SPECIAL = new Set([
    'defn','defn-','defmacro','defmethod',
    'fn','fn*',
    'let','let*','loop','letfn',
    'when','when-not','when-let','when-some',
    'if','if-let','if-some',
    'do','doseq','dotimes','for',
    'try','catch','finally',
    'binding','with-open','with-redefs',
    'ns','def','defonce','defmulti','defrecord',
  ]);

  function emit(node, col) {
    col = col || 0;
    if (!node) return '';
    switch (node.t) {
      case 'atom':    return node.v;
      case 'comment': return node.v;
      case 'pfx':     return node.v + emit(node.c, col + node.v.length);
      case 'map':     return '{}';

      case 'bindings': {
        if (!node.c || node.c.length===0) return '[]';
        const pairs = [];
        for (let i=0; i<node.c.length; i+=2) {
          const k = emit(node.c[i], col+1);
          const v = node.c[i+1] ? emit(node.c[i+1], col+1+k.length+1) : '_';
          pairs.push(k+' '+v);
        }
        const flat = '['+pairs.join(' ')+']';
        if (col+flat.length<=72) return flat;
        const sp = ' '.repeat(col+1);
        return '['+pairs.join('\n'+sp)+']';
      }

      case 'vec': {
        if (!node.c || node.c.length===0) return '[]';
        const items = node.c.map(n => emit(n, col+1));
        const flat = '['+items.join(' ')+']';
        if (col+flat.length<=72) return flat;
        const sp = ' '.repeat(col+1);
        return '['+items.join('\n'+sp)+']';
      }

      case 'list': {
        if (!node.c || node.c.length===0) return '()';
        const hs = node.c[0] && node.c[0].t==='atom' ? node.c[0].v : null;
        const headStr = emit(node.c[0], col+1);
        const bd = ' '.repeat(col+2);
        const ic = col+1+headStr.length+1;
        const rests = node.c.slice(1).map(n => emit(n, ic));
        const flat = '('+[headStr,...rests].join(' ')+')';
        if (col+flat.length<=72) return flat;

        if (!BODY_SPECIAL.has(hs)) {
          if (rests.length===0) return '('+headStr+')';
          // Hanging indent (2 spaces from col) plus greedy fill: pack as
          // many args as fit on each continuation line. Avoids the
          // "single token per line" look you get when args are aligned
          // after a long head name.
          const indent = col + 2;
          const pad = ' '.repeat(indent);
          const lines = [];
          let line = '';
          for (const r of rests) {
            const rIsMulti = r.indexOf('\n') >= 0;
            if (line === '') { line = r; continue; }
            if (rIsMulti || indent + line.length + 1 + r.length > 72) {
              lines.push(line);
              line = r;
            } else {
              line = line + ' ' + r;
            }
          }
          if (line) lines.push(line);
          return '('+headStr+'\n'+pad+lines.join('\n'+pad)+')';
        }

        if (hs==='defn'||hs==='defn-'||hs==='defmacro'||hs==='defmethod') {
          if (rests.length<2) return '('+[headStr,...rests].join(' ')+')';
          const hasDoc = rests[1] && rests[1].startsWith('"') && rests.length>=3;
          const header = hasDoc
            ? headStr+' '+rests[0]+'\n'+bd+rests[1]+'\n'+bd+rests[2]
            : headStr+' '+rests[0]+' '+rests[1];
          const body = rests.slice(hasDoc?3:2);
          return '('+header+(body.length?'\n'+bd+body.join('\n'+bd):'')+')';
        }
        if (hs==='fn'||hs==='fn*') {
          if (rests.length<1) return '('+headStr+')';
          const body = rests.slice(1);
          return '('+headStr+' '+rests[0]+(body.length?'\n'+bd+body.join('\n'+bd):'')+')';
        }
        if (hs==='let'||hs==='let*'||hs==='loop'||hs==='letfn') {
          if (rests.length<1) return '('+headStr+')';
          const body = rests.slice(1);
          return '('+headStr+' '+rests[0]+(body.length?'\n'+bd+body.join('\n'+bd):'')+')';
        }
        if (rests.length===0) return '('+headStr+')';
        const body = rests.slice(1);
        return '('+headStr+' '+rests[0]+(body.length?'\n'+bd+body.join('\n'+bd):'')+')';
      }

      default: return '';
    }
  }

  function emitAll(nodes) {
    const out = [];
    let blanks = 0;
    for (const n of nodes) {
      if (n.t==='nl') { blanks++; if (blanks<=2) out.push(''); }
      else { blanks=0; out.push(n.t==='comment' ? n.v : emit(n,0)); }
    }
    return out.join('\n');
  }

  function translate(src) {
    return emitAll(txAll(parseAll(src)));
  }

  global.SicmToEmmy = { translate, tokenize, parseAll, txAll, emitAll };
})(typeof window !== 'undefined' ? window : globalThis);

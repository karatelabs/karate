const counter = (function () {
  let c = 0;
  return { inc: () => ++c, get: () => c };
})();
counter.inc(); counter.inc();
if (counter.get() !== 2) throw new Error('iife closure ' + counter.get());
const adder = a => b => a + b;
if (adder(2)(3) !== 5) throw new Error('curried');
const fns = [];
for (let i = 0; i < 3; i++) fns.push(() => i);
if (fns.map(f => f()).join(',') !== '0,1,2') throw new Error('let capture ' + fns.map(f=>f()).join(','));
const memo = (fn) => { const c = {}; return (n) => c[n] !== undefined ? c[n] : (c[n] = fn(n)); };
const sq = memo(x => x * x);
if (sq(4) !== 16 || sq(4) !== 16) throw new Error('memo');

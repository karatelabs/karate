function* gen() { yield 1; yield 2; yield 3; }
const g = gen();
if (g.next().value !== 1) throw new Error('n1');
if (g.next().value !== 2) throw new Error('n2');
const r = g.next();
if (r.value !== 3 || r.done !== false) throw new Error('n3');
if (g.next().done !== true) throw new Error('done');
let s = 0;
for (const v of gen()) s += v;
if (s !== 6) throw new Error('for-of ' + s);

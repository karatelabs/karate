function fmt(n) { return n.toFixed(1); }
const items = [{ n: 'a', p: 1.5 }, { n: 'b', p: 2 }];
const out = items.map(i => `${i.n}: $${fmt(i.p)}`).join('\n');
if (out !== 'a: $1.5\nb: $2.0') throw new Error(JSON.stringify(out));

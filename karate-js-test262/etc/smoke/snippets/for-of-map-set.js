const m = new Map([['a', 1], ['b', 2]]);
let out = '';
for (const [k, v] of m) out += k + v;
if (out !== 'a1b2') throw new Error(out);
const st = new Set([1, 2, 2, 3]);
let sum = 0;
for (const v of st) sum += v;
if (sum !== 6) throw new Error('set ' + sum);
if ([...st].length !== 3) throw new Error('set spread');

// Object.fromEntries takes any iterable of [k, v] pairs, not just arrays.
const m = new Map([['k', 1], ['j', 2]]);
if (Object.fromEntries(m).k !== 1) throw new Error('from Map');
if (Object.fromEntries(new Set([['k', 1]])).k !== 1) throw new Error('from Set');
const o = { a: 1, b: 2 };
const doubled = Object.fromEntries(Object.entries(o).map(([k, v]) => [k, v * 2]));
if (doubled.b !== 4) throw new Error('entries round-trip');

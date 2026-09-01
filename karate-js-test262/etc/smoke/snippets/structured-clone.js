if (typeof structuredClone !== 'function') throw new Error('typeof');
const src = {
  n: 1,
  s: 'x',
  b: true,
  nil: null,
  arr: [1, [2, 3]],
  d: new Date(1700000000000),
  m: new Map([['k', { deep: 1 }]]),
  set: new Set([1, 'two']),
  re: /ab+c/gi
};
const c = structuredClone(src);
if (c === src) throw new Error('identity');
if (c.n !== 1 || c.s !== 'x' || c.b !== true || c.nil !== null) throw new Error('primitives');
if (c.arr === src.arr || c.arr[1][0] !== 2) throw new Error('array');
if (!(c.d instanceof Date) || c.d === src.d || c.d.getTime() !== 1700000000000) throw new Error('date');
if (!(c.m instanceof Map) || c.m.get('k').deep !== 1 || c.m.get('k') === src.m.get('k')) throw new Error('map');
if (!(c.set instanceof Set) || c.set.size !== 2 || !c.set.has('two')) throw new Error('set');
if (!(c.re instanceof RegExp) || c.re.source !== 'ab+c' || c.re.flags !== 'gi') throw new Error('regexp');

const arr = [0, , 2];
arr.extra = { n: 1 };
const ac = structuredClone(arr);
if (ac.length !== 3 || (1 in ac)) throw new Error('sparse');
if (ac.extra.n !== 1 || ac.extra === arr.extra) throw new Error('array named prop');

// a user subclass keeps `instanceof Error`, not `instanceof AppError`
class AppError extends Error {}
const ec = structuredClone(new AppError('boom'));
if (!(ec instanceof Error) || ec.message !== 'boom') throw new Error('error subclass');

const cyclic = { name: 'root' };
cyclic.self = cyclic;
cyclic.kids = [cyclic];
const cc = structuredClone(cyclic);
if (cc === cyclic) throw new Error('cycle identity');
if (cc.self !== cc) throw new Error('cycle self');
if (cc.kids[0] !== cc) throw new Error('cycle array');

let name = '';
try {
  structuredClone({ fn: () => 1 });
} catch (e) {
  name = e.name;
}
if (name !== 'DataCloneError') throw new Error('function ' + name);

// the transfer option is accepted and ignored
if (structuredClone({ a: 1 }, { transfer: [] }).a !== 1) throw new Error('transfer');

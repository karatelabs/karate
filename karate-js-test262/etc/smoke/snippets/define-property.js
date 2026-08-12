const o = {};
let backing = 1;
Object.defineProperty(o, 'x', {
  get() { return backing; },
  set(v) { backing = v * 2; },
  enumerable: true,
  configurable: true
});
if (o.x !== 1) throw new Error('get');
o.x = 5;
if (o.x !== 10) throw new Error('set ' + o.x);
Object.defineProperty(o, 'ro', { value: 'v', writable: false, enumerable: false });
if (o.ro !== 'v') throw new Error('value');
if (Object.keys(o).indexOf('ro') !== -1) throw new Error('enumerable false');
const d = Object.getOwnPropertyDescriptor(o, 'ro');
if (d.writable !== false) throw new Error('descriptor');

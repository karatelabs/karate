const x = 1, y = 2;
const key = 'dyn';
const o = {
  x,
  y,
  [key]: 'val',
  ['a' + 'b']: 3,
  method() { return this.x + this.y; },
  get double() { return this.x * 2; },
  set double(v) { this.x = v / 2; }
};
if (o.x !== 1 || o.y !== 2) throw new Error('shorthand');
if (o.dyn !== 'val') throw new Error('computed');
if (o.ab !== 3) throw new Error('computed expr');
if (o.method() !== 3) throw new Error('method shorthand');
if (o.double !== 2) throw new Error('getter ' + o.double);
o.double = 10;
if (o.x !== 5) throw new Error('setter');
const parent = { greet() { return 'hi' } };
const child = { __proto__: parent, own: 1 };
if (child.greet() !== 'hi') throw new Error('__proto__ literal');
if (Object.keys(child).join(',') !== 'own') throw new Error('__proto__ became an own property');
if (Object.getPrototypeOf({ __proto__: null }) !== null) throw new Error('__proto__ null');
if (Object.keys({ ['__proto__']: 1 }).join(',') !== '__proto__') throw new Error('computed __proto__');

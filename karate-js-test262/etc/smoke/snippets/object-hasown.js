const o = { a: 1 };
if (Object.hasOwn(o, 'a') !== true) throw new Error('hasOwn true');
if (Object.hasOwn(o, 'b') !== false) throw new Error('hasOwn false');

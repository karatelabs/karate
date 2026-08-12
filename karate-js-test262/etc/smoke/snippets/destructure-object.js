const src = { a: 1, b: 2 };
const { a: alpha, c = 99 } = src;
if (alpha !== 1) throw new Error('rename');
if (c !== 99) throw new Error('default');
const { b, ...rest } = { a: 1, b: 2, d: 3 };
if (b !== 2) throw new Error('b');
if (JSON.stringify(rest) !== '{"a":1,"d":3}') throw new Error(JSON.stringify(rest));

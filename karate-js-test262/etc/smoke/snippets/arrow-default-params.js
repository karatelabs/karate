const greet = (name = 'world', punct = '!') => 'hello ' + name + punct;
if (greet() !== 'hello world!') throw new Error(greet());
if (greet('bob') !== 'hello bob!') throw new Error(greet('bob'));
if (greet('bob', '?') !== 'hello bob?') throw new Error('x');
const dep = (a, b = a * 2) => a + b;
if (dep(3) !== 9) throw new Error('dep ' + dep(3));

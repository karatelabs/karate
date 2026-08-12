function f(a, b, c) { return a + b + c; }
const args = [1, 2, 3];
if (f(...args) !== 6) throw new Error('spread call');
if (Math.max(...[3, 9, 4]) !== 9) throw new Error('max spread');
if (f(1, ...[2, 3]) !== 6) throw new Error('mixed');

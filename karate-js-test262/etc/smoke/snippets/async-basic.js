async function f() { return 42; }
const p = f();
if (typeof p.then !== 'function') throw new Error('not thenable');
let got = null;
p.then(v => { got = v; });
if (got !== 42) throw new Error('then got ' + got);

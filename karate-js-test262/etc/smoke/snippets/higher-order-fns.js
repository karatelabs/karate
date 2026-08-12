function compose(...fns) { return x => fns.reduceRight((acc, f) => f(acc), x); }
const inc = x => x + 1, dbl = x => x * 2;
if (compose(inc, dbl)(3) !== 7) throw new Error('compose ' + compose(inc, dbl)(3));
function pipe(...fns) { return x => fns.reduce((acc, f) => f(acc), x); }
if (pipe(inc, dbl)(3) !== 8) throw new Error('pipe');
const bound = function () { return this.v; }.bind({ v: 9 });
if (bound() !== 9) throw new Error('bind');
if (inc.call(null, 1) !== 2) throw new Error('call');
if (inc.apply(null, [1]) !== 2) throw new Error('apply');

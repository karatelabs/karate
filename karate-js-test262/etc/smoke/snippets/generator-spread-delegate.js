function* inner() { yield 'a'; yield 'b'; }
function* outer() { yield 1; yield* inner(); yield 2; }
const arr = [...outer()];
if (arr.join(',') !== '1,a,b,2') throw new Error(arr.join(','));
function* infinite() { let i = 0; while (true) yield i++; }
const it = infinite();
if (it.next().value !== 0 || it.next().value !== 1) throw new Error('infinite');
function* two() { const x = yield 1; yield x * 2; }
const t = two(); t.next();
if (t.next(5).value !== 10) throw new Error('send value');

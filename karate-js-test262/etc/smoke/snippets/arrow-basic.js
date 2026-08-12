const add = (a, b) => a + b;
const sq = x => x * x;
const noop = () => {};
if (add(2, 3) !== 5) throw new Error('add ' + add(2,3));
if (sq(4) !== 16) throw new Error('sq');
if (noop() !== undefined) throw new Error('noop');
const obj = () => ({ a: 1 });
if (obj().a !== 1) throw new Error('implicit obj return');

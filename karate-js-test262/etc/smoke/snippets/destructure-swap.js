let a = 1, b = 2;
[a, b] = [b, a];
if (a !== 2 || b !== 1) throw new Error(a + ',' + b);
let o = {};
({ p: o.q } = { p: 7 });
if (o.q !== 7) throw new Error('member target ' + o.q);

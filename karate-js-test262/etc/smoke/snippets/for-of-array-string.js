let s = '';
for (const x of [1, 2, 3]) s += x;
if (s !== '123') throw new Error(s);
let t = '';
for (const ch of 'abc') t += ch + '-';
if (t !== 'a-b-c-') throw new Error(t);
let idx = '';
for (const [i, v] of ['a','b'].entries()) idx += i + v;
if (idx !== '0a1b') throw new Error(idx);

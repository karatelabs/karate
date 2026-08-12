const o = { a: 1, b: 2 };
let keys = '';
for (const k in o) keys += k;
if (keys !== 'ab') throw new Error(keys);
let pairs = '';
for (const [k, v] of Object.entries(o)) pairs += k + '=' + v + ';';
if (pairs !== 'a=1;b=2;') throw new Error(pairs);

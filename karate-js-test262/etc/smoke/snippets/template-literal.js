const name = 'World';
const n = 3;
const s = `Hello ${name}, ${n + 1} times`;
if (s !== 'Hello World, 4 times') throw new Error(s);
const multi = `line1
line2`;
if (multi !== 'line1\nline2') throw new Error(JSON.stringify(multi));
const nested = `a${`b${1+1}`}c`;
if (nested !== 'ab2c') throw new Error(nested);

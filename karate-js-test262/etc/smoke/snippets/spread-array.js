const a = [1, 2];
const b = [0, ...a, 3];
if (b.join(',') !== '0,1,2,3') throw new Error(b.join(','));
const s = [...'abc'];
if (s.length !== 3 || s[2] !== 'c') throw new Error('string spread');
const copy = [...a];
if (copy === a) throw new Error('same ref');

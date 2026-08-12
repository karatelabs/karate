const s = new Set([1, 2, 3]);
s.add(3); s.add(4);
if (s.size !== 4) throw new Error('size ' + s.size);
if (!s.has(2)) throw new Error('has');
s.delete(1);
if (s.has(1)) throw new Error('delete');
if (Array.from(s).join(',') !== '2,3,4') throw new Error(Array.from(s).join(','));
const uniq = [...new Set([1,1,2,2,3])];
if (uniq.length !== 3) throw new Error('dedupe');

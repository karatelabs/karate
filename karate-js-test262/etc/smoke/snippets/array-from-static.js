const a = Array.from({ length: 5 }, (_, i) => i);
if (a.join(',') !== '0,1,2,3,4') throw new Error('Array.from ' + a.join(','));
if (Array.from('abc').length !== 3) throw new Error('from string');
if (Array.from(new Set([1,2])).length !== 2) throw new Error('from set');
if (!Array.isArray([])) throw new Error('isArray');
if (Array.isArray({})) throw new Error('isArray obj');
if (Array.of(1,2).length !== 2) throw new Error('Array.of');
if (new Array(3).fill(0).join(',') !== '0,0,0') throw new Error('fill');

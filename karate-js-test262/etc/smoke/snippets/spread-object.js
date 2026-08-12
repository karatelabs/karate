const base = { a: 1, b: 2 };
const over = { ...base, b: 3, c: 4 };
if (JSON.stringify(over) !== '{"a":1,"b":3,"c":4}') throw new Error(JSON.stringify(over));
const merged = { ...{ x: 1 }, ...{ y: 2 } };
if (merged.x !== 1 || merged.y !== 2) throw new Error('merge');

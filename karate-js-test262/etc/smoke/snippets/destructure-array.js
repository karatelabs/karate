const [a, , c, ...rest] = [1, 2, 3, 4, 5];
if (a !== 1) throw new Error('a');
if (c !== 3) throw new Error('c ' + c);
if (rest.length !== 2 || rest[0] !== 4) throw new Error('rest ' + rest);
const [x = 10, y = 20] = [undefined];
if (x !== 10 || y !== 20) throw new Error('defaults ' + x + ',' + y);

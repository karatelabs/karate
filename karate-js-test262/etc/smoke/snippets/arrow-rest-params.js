const sum = (...nums) => nums.reduce((a, b) => a + b, 0);
if (sum(1,2,3,4) !== 10) throw new Error('sum ' + sum(1,2,3,4));
const tail = (first, ...rest) => rest.length;
if (tail(1,2,3) !== 2) throw new Error('tail');
if (sum() !== 0) throw new Error('empty');

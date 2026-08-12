if ([1,[2,[3,[4]]]].flat(Infinity).join(',') !== '1,2,3,4') throw new Error('flat Infinity');
if ([1,[2,[3]]].flat().length !== 3) throw new Error('flat default');
if ([1,2].flatMap(x => [x, x * 10]).join(',') !== '1,10,2,20') throw new Error('flatMap');
const nums = [10, 9, 100, 1];
if (nums.sort((a, b) => a - b).join(',') !== '1,9,10,100') throw new Error('sort comparator');
if ([3,1,2].sort().join(',') !== '1,2,3') throw new Error('sort default');
if ([1,2,3].reverse().join(',') !== '3,2,1') throw new Error('reverse');
if ([1,2,3].slice(1).join(',') !== '2,3') throw new Error('slice');

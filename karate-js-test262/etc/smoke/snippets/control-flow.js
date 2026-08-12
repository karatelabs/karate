function sw(x) {
  let r = '';
  switch (x) {
    case 1:
    case 2: r += 'low'; break;
    case 3: r += 'three';
    case 4: r += 'four'; break;
    default: r += 'other';
  }
  return r;
}
if (sw(1) !== 'low') throw new Error('fallthrough1');
if (sw(3) !== 'threefour') throw new Error('fallthrough2 ' + sw(3));
if (sw(9) !== 'other') throw new Error('default');
let i = 0, s = '';
do { s += i; i++; } while (i < 3);
if (s !== '012') throw new Error('do-while ' + s);
let found = '';
outer:
for (let a = 0; a < 3; a++) {
  for (let b = 0; b < 3; b++) {
    if (a === 1 && b === 1) { found = a + '' + b; break outer; }
  }
}
if (found !== '11') throw new Error('labeled break ' + found);
let cnt = 0;
loop2: for (let a = 0; a < 3; a++) { for (let b = 0; b < 3; b++) { if (b === 1) continue loop2; cnt++; } }
if (cnt !== 3) throw new Error('labeled continue ' + cnt);
const g = (n) => n < 0 ? 'neg' : n === 0 ? 'zero' : 'pos';
if (g(-1) !== 'neg' || g(0) !== 'zero' || g(1) !== 'pos') throw new Error('ternary chain');

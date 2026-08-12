const range = {
  from: 1, to: 3,
  [Symbol.iterator]() {
    let cur = this.from, last = this.to;
    return { next: () => cur <= last ? { value: cur++, done: false } : { value: undefined, done: true } };
  }
};
if ([...range].join(',') !== '1,2,3') throw new Error('custom iterator');
let s = 0;
for (const v of range) s += v;
if (s !== 6) throw new Error('for-of custom');

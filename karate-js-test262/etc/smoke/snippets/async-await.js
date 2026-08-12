async function inner() { return 7; }
async function outer() {
  const v = await inner();
  return v * 2;
}
const got = await outer().then(v => v);
if (got !== 14) throw new Error('await ' + got);

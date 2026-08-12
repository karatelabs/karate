async function inner() { return 7; }
async function outer() {
  const v = await inner();
  return v * 2;
}
let got = null;
outer().then(v => { got = v; });
if (got !== 14) throw new Error('await ' + got);

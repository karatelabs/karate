async function bad() { throw new Error('nope'); }
async function run() {
  try {
    await bad();
    return 'no-throw';
  } catch (e) {
    return 'caught:' + e.message;
  }
}
let got = null;
run().then(v => { got = v; });
if (got !== 'caught:nope') throw new Error('got ' + got);

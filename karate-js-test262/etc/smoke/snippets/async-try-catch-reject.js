async function bad() { throw new Error('nope'); }
async function run() {
  try {
    await bad();
    return 'no-throw';
  } catch (e) {
    return 'caught:' + e.message;
  }
}
const got = await run();
if (got !== 'caught:nope') throw new Error('got ' + got);

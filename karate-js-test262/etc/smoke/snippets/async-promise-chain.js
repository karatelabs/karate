const got = await Promise.resolve(1)
  .then(v => v + 1)
  .then(v => v * 10)
  .catch(() => -1);
if (got !== 20) throw new Error('chain ' + got);
const err = await Promise.reject(new Error('x')).catch(e => e.message);
if (err !== 'x') throw new Error('reject ' + err);

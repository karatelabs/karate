let got = null;
Promise.resolve(1)
  .then(v => v + 1)
  .then(v => v * 10)
  .catch(() => -1)
  .then(v => { got = v; });
if (got !== 20) throw new Error('chain ' + got);
let err = null;
Promise.reject(new Error('x')).catch(e => { err = e.message; });
if (err !== 'x') throw new Error('reject ' + err);

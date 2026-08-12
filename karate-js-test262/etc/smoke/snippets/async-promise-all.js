const fns = [1, 2, 3].map(n => Promise.resolve(n * 2));
let got = null;
Promise.all(fns).then(vs => { got = vs.join(','); });
if (got !== '2,4,6') throw new Error('Promise.all ' + got);

const out = 'a1b2'.replace(/\d/g, (d) => String(Number(d) * 2));
if (out !== 'a2b4') throw new Error(out);
const out2 = 'hello world'.replace(/(\w+) (\w+)/, (m, a, b) => b + ' ' + a);
if (out2 !== 'world hello') throw new Error(out2);
const out3 = 'x-y'.replace(/-/, '_');
if (out3 !== 'x_y') throw new Error(out3);
if ('aaa'.replace(/a/g, '$&$&') !== 'aaaaaa') throw new Error('dollar-amp');

// err.stack is the string every catch block logs.
const e = new Error('boom');
if (typeof e.stack !== 'string') throw new Error('stack is not a string');
if (e.stack.indexOf('Error: boom') !== 0) throw new Error('header: ' + e.stack);
if (!e.stack.includes('\n    at ')) throw new Error('no frame: ' + e.stack);
if (typeof new TypeError('t').stack !== 'string') throw new Error('TypeError stack');
class AppError extends Error {}
if (typeof new AppError('a').stack !== 'string') throw new Error('subclass stack');
try {
  null.x;
} catch (err) {
  if (!err.stack.includes('TypeError')) throw new Error('engine error stack');
}

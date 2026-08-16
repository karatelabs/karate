// Runaway recursion is a catchable RangeError, not a Java StackOverflowError.
function f() { return f(); }
let caught = null;
try {
  f();
} catch (e) {
  caught = e;
}
if (!(caught instanceof RangeError)) throw new Error('not a RangeError: ' + caught);
if (!caught.message.includes('call stack')) throw new Error('message: ' + caught.message);
// the engine is still usable afterwards
if ([1, 2, 3].map(x => x * 2).join(',') !== '2,4,6') throw new Error('engine unusable');

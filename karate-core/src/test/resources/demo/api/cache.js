// Exercise request.cache(). Used by RequestCacheTest (K2).

var calls = 0;
function compute(tag) {
  return function () {
    calls++;
    return { tag: tag, n: calls };
  };
}

// First call with key 'foo' invokes the fn and stores the result
var a = request.cache('foo', compute('foo'));
// Second call with same key returns the cached value, fn not invoked
var b = request.cache('foo', compute('foo'));
// Different key invokes the fn again
var c = request.cache('bar', compute('bar'));
// Same first key still cached even after another key was added
var d = request.cache('foo', compute('foo'));

// A fn that returns null should still be cached as null (no re-invoke)
var nullCalls = 0;
function nullFn() { nullCalls++; return null; }
var n1 = request.cache('nul', nullFn);
var n2 = request.cache('nul', nullFn);

response.body = {
  totalCalls: calls,           // expect 2 (foo once, bar once)
  fooFirst: a.n,               // 1
  fooSecond: b.n,              // 1 (cached)
  fooFinal: d.n,               // 1 (still cached)
  bar: c.n,                    // 2
  fooSameRef: a === b && b === d,   // true
  nullCallsAfterTwo: nullCalls,// 1
  nulFirst: n1,                // null
  nulSecond: n2                // null
};

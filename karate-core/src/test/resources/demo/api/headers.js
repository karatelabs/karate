// Exercise all four header-set patterns. Used by ResponseHeadersTest (K10).

// 1. 2-arg setter form
response.header('X-Setter', 'one');

// 2. bracket-set with string value (the silent-500 path before K10)
response.headers['X-Bracket'] = 'two';

// 3. bracket-set with list value
response.headers['X-Multi'] = ['a', 'b'];

// 4. set then remove via null
response.header('X-Removed', 'will-be-gone');
response.headers['X-Removed'] = null;

// 5. read-back after set should still work
var echoed = response.header('X-Setter');

response.body = { ok: true, echoed: echoed };

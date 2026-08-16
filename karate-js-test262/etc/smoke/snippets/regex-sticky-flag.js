// The sticky flag advances lastIndex and anchors each attempt at it.
const y = /ab/y;
if (y.exec('ababXab')[0] !== 'ab') throw new Error('first match');
if (y.lastIndex !== 2) throw new Error('lastIndex advanced: ' + y.lastIndex);
if (y.exec('ababXab')[0] !== 'ab') throw new Error('second match');
if (y.exec('ababXab') !== null) throw new Error('must not skip the gap');
if (y.lastIndex !== 0) throw new Error('miss resets lastIndex');
const g = /b/g;
g.exec('aab');
if (g.lastIndex !== 3) throw new Error('/g still scans forward');

// Capture groups interleave into the result of a regex split.
const parts = 'a1b2c'.split(/(\d)/);
if (parts.join('|') !== 'a|1|b|2|c') throw new Error('split captures: ' + parts);
if ('a1b2c'.split(/(\d)/g).join('|') !== 'a|1|b|2|c') throw new Error('split captures with /g');
if ('2024-01-15'.split(/(-)/).length !== 5) throw new Error('split separator kept');
if ('a,b'.split(',', -1).join('|') !== 'a|b') throw new Error('negative limit is ToUint32');

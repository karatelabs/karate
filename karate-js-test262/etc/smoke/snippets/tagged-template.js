function tag(strings, ...vals) {
  return strings.raw.length + '|' + strings[0] + '|' + vals.join(',');
}
const r = tag`a${1}b${2}c`;
if (r !== '3|a|1,2') throw new Error(r);
function upper(s, ...v) { return s.reduce((acc, cur, i) => acc + cur + (v[i] !== undefined ? String(v[i]).toUpperCase() : ''), ''); }
if (upper`x${'y'}z` !== 'xYz') throw new Error(upper`x${'y'}z`);

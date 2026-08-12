const re = /(?<year>\d{4})-(?<month>\d{2})/;
const m = re.exec('2024-05');
if (!m) throw new Error('no match');
if (m.groups.year !== '2024') throw new Error('year ' + m.groups.year);
if (m.groups.month !== '05') throw new Error('month');
if (m[1] !== '2024') throw new Error('index group');
const out = '2024-05'.replace(re, '$<month>/$<year>');
if (out !== '05/2024') throw new Error('replace named ' + out);

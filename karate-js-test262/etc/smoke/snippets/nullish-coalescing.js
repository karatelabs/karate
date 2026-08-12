if ((null ?? 'd') !== 'd') throw new Error('null');
if ((undefined ?? 'd') !== 'd') throw new Error('undefined');
if ((0 ?? 'd') !== 0) throw new Error('zero');
if (('' ?? 'd') !== '') throw new Error('empty');
if ((false ?? 'd') !== false) throw new Error('false');

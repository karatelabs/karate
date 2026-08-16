// f.length counts params before the first default or rest element.
function f(a, b) {}
function withDefault(a, b = 1) {}
function withRest(a, ...r) {}
if (f.length !== 2) throw new Error('plain');
if (withDefault.length !== 1) throw new Error('default: ' + withDefault.length);
if (withRest.length !== 1) throw new Error('rest: ' + withRest.length);
if (((a, b, c = 1) => 0).length !== 2) throw new Error('arrow');
if (f.bind(null, 1).length !== 1) throw new Error('bind subtracts partials');

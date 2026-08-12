let order = '';
function f() {
  try {
    order += 't';
    throw new Error('e');
  } catch (e) {
    order += 'c';
    return 'ret';
  } finally {
    order += 'f';
  }
}
if (f() !== 'ret') throw new Error('return from catch');
if (order !== 'tcf') throw new Error('order ' + order);
try { throw new Error('x'); } catch { order += 'o'; }
if (order !== 'tcfo') throw new Error('optional catch binding');
try { throw 'plain string'; } catch (e) {
  if (e !== 'plain string') throw new Error('non-error throw ' + e);
}
try { throw { code: 5 }; } catch (e) { if (e.code !== 5) throw new Error('obj throw'); }
try { null.foo; throw new Error('should have thrown'); } catch (e) {
  if (!(e instanceof TypeError)) throw new Error('expected TypeError got ' + e);
}

const o = Object.freeze({ a: 1 });
if (!Object.isFrozen(o)) throw new Error('isFrozen');
try { o.a = 2; } catch (e) { /* strict mode throws */ }
if (o.a !== 1) throw new Error('mutated to ' + o.a);
try { o.b = 3; } catch (e) {}
if (o.b !== undefined) throw new Error('added b');

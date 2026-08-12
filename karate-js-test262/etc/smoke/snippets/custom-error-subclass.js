class MyErr extends Error {
  constructor(msg, code) {
    super(msg);
    this.name = 'MyErr';
    this.code = code;
  }
}
try {
  throw new MyErr('boom', 42);
} catch (e) {
  if (!(e instanceof MyErr)) throw new Error('not MyErr');
  if (!(e instanceof Error)) throw new Error('not Error');
  if (e.message !== 'boom') throw new Error('msg ' + e.message);
  if (e.code !== 42) throw new Error('code');
  if (e.name !== 'MyErr') throw new Error('name ' + e.name);
}

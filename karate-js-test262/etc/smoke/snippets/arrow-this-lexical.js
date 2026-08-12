const obj = {
  v: 42,
  get() {
    const f = () => this.v;
    return f();
  }
};
if (obj.get() !== 42) throw new Error('lexical this ' + obj.get());

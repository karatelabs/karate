class T {
  label = 'hi';
  static kind = 'T';
  bound = () => this.label + '!';
}
const t = new T();
if (t.label !== 'hi') throw new Error('field');
if (T.kind !== 'T') throw new Error('static field');
const fn = t.bound;
if (fn() !== 'hi!') throw new Error('bound arrow field');

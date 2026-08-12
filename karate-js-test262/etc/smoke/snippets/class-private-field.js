class Counter {
  #count = 0;
  static #total = 0;
  increment() { this.#count++; Counter.#total++; return this.#count; }
  get value() { return this.#count; }
  static get total() { return Counter.#total; }
}
const c = new Counter();
c.increment(); c.increment();
if (c.value !== 2) throw new Error('private ' + c.value);
if (Counter.total !== 2) throw new Error('static private ' + Counter.total);
if (c.count !== undefined) throw new Error('leaked');

const log = [];
class Registry {
  static #entries = [];
  static seq = log.push('field');
  static {
    log.push('block');
    let local = 'a';
    Registry.#entries.push(local);
    this.owner = this;
  }
  static { Registry.#entries.push('b'); }
  static list() { return Registry.#entries.join(','); }
}
if (log.join(',') !== 'field,block') throw new Error('order ' + log.join(','));
if (Registry.owner !== Registry) throw new Error('this');
if (Registry.list() !== 'a,b') throw new Error('entries ' + Registry.list());
if (typeof local !== 'undefined') throw new Error('block scope leaked');

class Base { static make() { return 'base'; } }
class Derived extends Base {
  static { this.from = super.make(); }
}
if (Derived.from !== 'base') throw new Error('super ' + Derived.from);

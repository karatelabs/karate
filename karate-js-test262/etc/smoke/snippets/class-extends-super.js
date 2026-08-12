class Animal {
  constructor(name) { this.name = name; }
  speak() { return this.name + ' makes a noise'; }
}
class Dog extends Animal {
  constructor(name) { super(name); this.legs = 4; }
  speak() { return super.speak() + ' (woof)'; }
}
const d = new Dog('rex');
if (d.name !== 'rex') throw new Error('super ctor');
if (d.legs !== 4) throw new Error('own field');
if (d.speak() !== 'rex makes a noise (woof)') throw new Error(d.speak());
if (!(d instanceof Dog) || !(d instanceof Animal)) throw new Error('instanceof chain');

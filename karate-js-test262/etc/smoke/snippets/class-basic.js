class Point {
  constructor(x, y) { this.x = x; this.y = y; }
  get mag() { return Math.sqrt(this.x * this.x + this.y * this.y); }
  set mag(v) { this.x = v; }
  dist() { return this.x + this.y; }
  static origin() { return new Point(0, 0); }
}
const p = new Point(3, 4);
if (p.mag !== 5) throw new Error('getter ' + p.mag);
p.mag = 10;
if (p.x !== 10) throw new Error('setter');
if (p.dist() !== 14) throw new Error('method');
if (Point.origin().x !== 0) throw new Error('static');
if (!(p instanceof Point)) throw new Error('instanceof');

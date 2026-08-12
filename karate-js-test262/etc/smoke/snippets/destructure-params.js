function fmt({ name, age = 0 }, [first, second] = []) {
  return name + ':' + age + ':' + first + ':' + second;
}
const r = fmt({ name: 'a' }, [1, 2]);
if (r !== 'a:0:1:2') throw new Error(r);
const g = ({ x }) => x * 2;
if (g({ x: 5 }) !== 10) throw new Error('arrow destructure');

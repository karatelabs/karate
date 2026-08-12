const data = { user: { name: 'ann', addr: { city: 'NY' } }, tags: ['x','y'] };
const { user: { name, addr: { city, zip = '00000' } }, tags: [first] } = data;
if (name !== 'ann') throw new Error('name');
if (city !== 'NY') throw new Error('city');
if (zip !== '00000') throw new Error('zip');
if (first !== 'x') throw new Error('first');

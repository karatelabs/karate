// indexOf/lastIndexOf are IsStrictlyEqual; includes is SameValueZero.
if ([NaN].indexOf(NaN) !== -1) throw new Error('indexOf NaN');
if ([NaN].lastIndexOf(NaN) !== -1) throw new Error('lastIndexOf NaN');
if ([1, 2].indexOf('1') !== -1) throw new Error('indexOf no coercion');
if (![NaN].includes(NaN)) throw new Error('includes NaN');
if ([1, 2].includes('1')) throw new Error('includes no coercion');

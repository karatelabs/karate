// A 0x prefix is stripped when the radix is 16, implied or explicit.
if (parseInt('0x1f', 16) !== 31) throw new Error('explicit radix 16');
if (parseInt('0x1f') !== 31) throw new Error('implied radix 16');
if (parseInt('1f', 16) !== 31) throw new Error('no prefix');
if (parseInt('0x1f', 10) !== 0) throw new Error('radix 10 stops at x');
if (parseInt('ff', 16) !== 255) throw new Error('hex digits');

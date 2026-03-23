package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsArrayTest extends EvalBase {

    @Test
    void testDev() {

    }

    @Test
    void testArray() {
        matchEval("[]", "[]");
        matchEval("[ 1 ]", "[ 1 ]");
        matchEval("[ 1, 2, 3 ]", "[ 1, 2, 3 ]");
        matchEval("[ true ]", "[ true ]");
        matchEval("[ 'a' ]", "[ 'a' ]");
        matchEval("[ \"a\" ]", "[ 'a' ]");
        matchEval("[ (1 + 2) ]", "[ 3 ]");
        matchEval("[ a ]", "[ 5 ]", "{ a: 5 }");
    }

    @Test
    void testArraySparse() {
        matchEval("[,]", "[null]");
    }

    @Test
    void testArraySpread() {
        matchEval("var arr1 = [1, 2, 3]; var arr2 = [...arr1]; arr2", "[1, 2, 3]");
        matchEval("var arr1 = [1, 2]; var arr2 = [...arr1, 3, 4]; arr2", "[1, 2, 3, 4]");
        matchEval("var arr1 = [2, 3]; var arr2 = [1, ...arr1, 4]; arr2", "[1, 2, 3, 4]");
        matchEval("var arr1 = [3, 4]; var arr2 = [1, 2, ...arr1]; arr2", "[1, 2, 3, 4]");
        matchEval("var arr1 = [1, 2]; var arr2 = [3, 4]; var arr3 = [...arr1, ...arr2]; arr3", "[1, 2, 3, 4]");
        matchEval("var arr1 = [1, [2, 3]]; var arr2 = [...arr1, 4]; arr2", "[1, [2, 3], 4]");
        // strings also get spread
        matchEval("var str = 'abc'; var arr = [...str]; arr", "['a', 'b', 'c']");
    }

    @Test
    void testArrayProp() {
        assertEquals(2, eval("a = [1, 2]; a[1]"));
        assertEquals("bar", eval("a = ['bar']; a[0]"));
    }

    @Test
    void testArrayMutation() {
        eval("var a = [1, 2]; a[1] = 3");
        match(get("a"), "[1, 3]");
    }

    @Test
    void testArrayApi() {
        match(eval("[1, 2, 3].length"), "3");
        match(eval("[1, 2, 3].map(x => x * 2)"), "[2, 4, 6]");
        match(eval("Array.prototype.map.call([1, 2, 3], x => x * 2)"), "[2, 4, 6]");
        match(eval("[].map.call([1, 2, 3], x => x * 2)"), "[2, 4, 6]");
        match(eval("[].map.call([1, 2, 3], String)"), "['1', '2', '3']");
        match(eval("[1, 2, 3].join()"), "1,2,3");
        match(eval("[1, 2, 3].join(', ')"), "1, 2, 3");
        match(eval("[].map.call({0:'a',1:'b'}, (x, i) => x + i)"), "['a0','b1']");
        match(eval("Array.from([1, 2, 3])"), "[1, 2, 3]");
        match(eval("Array.from([1, 2, 3], x => x * 2)"), "[2, 4, 6]");
        match(eval("Array.from({ length: 3 }, (v, i) => i)"), "[0, 1, 2]");
        // todo prevent leaking undefined like this
        match(eval("Array.from({ length: 3 })"), "[undefined, undefined, undefined]");
        assertEquals(2, eval("[1, 2, 3].find(x => x % 2 === 0)"));
        assertNull(eval("[1, 2, 3].find(x => x % 5 === 0)"));
        assertEquals(1, eval("[1, 2, 3].findIndex(x => x % 2 === 0)"));
        assertEquals(-1, eval("[1, 2, 3].findIndex(x => x % 5 === 0)"));
        assertEquals(2, eval("[1, 2, 3].findIndex((val, idx, arr) => idx === 2)"));
        assertEquals(4, eval("[1, 2, 3].push(2)"));
        eval("var a = []; var b = a.push(1, 2, 3);");
        match(get("a"), "[1, 2, 3]");
        assertEquals(3, get("b"));
        match(eval("[1, 2, 3].reverse()"), "[3, 2, 1]");
        assertEquals(true, eval("[1, 2, 3].includes(2)"));
        assertEquals(1, eval("[1, 2, 3].indexOf(2)"));
        assertEquals(-1, eval("[1, 2, 3].indexOf(5)"));
        match(eval("[1, 2, 3, 4, 5].slice(1, 4)"), "[2, 3, 4]");
        match(eval("[1, 2, 3, 4, 5].slice(2)"), "[3, 4, 5]");
        match(eval("[1, 2, 3, 4, 5].slice(1, -1)"), "[2, 3, 4]");
        match(eval("[1, 2, 3, 4, 5].slice(-3, -1)"), "[3, 4]");
        match(eval("[1, 2, 3, 4, 5].slice(-3)"), "[3, 4, 5]");
        match(eval("[1, 2, 3, 4, 5].slice(0, 10)"), "[1, 2, 3, 4, 5]");
        match(eval("[1, 2, 3, 4, 5].slice(10, 1)"), "[]");
        eval("var sum = 0; [1, 2, 3].forEach(function(value) { sum += value; });");
        assertEquals(6, get("sum"));
        eval("var result = []; [1, 2, 3].forEach(function(value, index) { result.push(value * index); });");
        match(get("result"), "[0, 2, 6]");
        match(eval("[1, 2, 3].concat([4, 5])"), "[1, 2, 3, 4, 5]");
        match(eval("[1, 2, 3].concat(4, 5)"), "[1, 2, 3, 4, 5]");
        match(eval("[1, 2, 3].concat([4, 5], 6, [7, 8])"), "[1, 2, 3, 4, 5, 6, 7, 8]");
        match(eval("[].concat('a', 'b', 'c')"), "['a', 'b', 'c']");
        assertEquals(true, eval("[2, 4, 6].every(x => x % 2 === 0)"));
        assertEquals(false, eval("[2, 3, 6].every(x => x % 2 === 0)"));
        assertEquals(true, eval("[].every(x => false)"));
        assertEquals(true, eval("[1, 2, 3].every((val, idx, arr) => arr.length === 3)"));
        assertEquals(true, eval("[1, 2, 3].some(x => x % 2 === 0)"));
        assertEquals(false, eval("[1, 3, 5].some(x => x % 2 === 0)"));
        assertEquals(false, eval("[].some(x => true)"));
        assertEquals(true, eval("[1, 2, 3].some((val, idx, arr) => idx === 1)"));
        assertEquals(6, eval("[1, 2, 3].reduce((acc, val) => acc + val)"));
        assertEquals(10, eval("[1, 2, 3].reduce((acc, val) => acc + val, 4)"));
        assertEquals("123", eval("[1, 2, 3].reduce((acc, val) => acc + val, '')"));
        assertEquals("0-1-2-3", eval("[1, 2, 3].reduce((acc, val, idx) => acc + '-' + val, 0)"));
        eval("var indices = []; var arrayFirstItems = []; [10, 20, 30].reduce((acc, val, idx, arr) => { indices.push(idx); arrayFirstItems.push(arr[0]); return acc + val; }, 0);");
        NodeUtils.match(get("indices"), "[0, 1, 2]");
        NodeUtils.match(get("arrayFirstItems"), "[10, 10, 10]"); // verifying we receive the actual array
        try {
            eval("[].reduce((acc, val) => acc + val)");
            fail("should throw exception for empty array with no initial value");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("empty array"));
        }
        assertEquals("initial", eval("[].reduce((acc, val) => acc + val, 'initial')"));
        assertEquals(6, eval("[1, 2, 3].reduceRight((acc, val) => acc + val)"));
        assertEquals("321", eval("[1, 2, 3].reduceRight((acc, val) => acc + val, '')"));
        try {
            eval("[].reduceRight((acc, val) => acc + val)");
            fail("should throw exception for empty array with no initial value");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("empty array"));
        }
        assertEquals("initial", eval("[].reduceRight((acc, val) => acc + val, 'initial')"));
        eval("var indices = []; [10, 20, 30].reduceRight((acc, val, idx) => { indices.push(idx); return acc + val; }, 0);");
        NodeUtils.match(get("indices"), "[2, 1, 0]"); // indices are in reverse order
        match(eval("[1, 2, [3, 4]].flat()"), "[1, 2, 3, 4]");
        match(eval("[1, 2, [3, [4, 5]]].flat()"), "[1, 2, 3, [4, 5]]");
        match(eval("[1, 2, [3, [4, 5]]].flat(2)"), "[1, 2, 3, 4, 5]");
        // match(eval("[1, 2, [3, [4, [5, 6]]]].flat(Infinity)"), "[1, 2, 3, 4, 5, 6]");
        match(eval("[1, 2, [3, 4]].flat(0)"), "[1, 2, [3, 4]]");
        match(eval("[].flat()"), "[]");
        match(eval("[1, 2, null, undefined, [3, 4]].flat()"), "[1, 2, null, undefined, 3, 4]");
        // test flatMap() method
        match(eval("[1, 2, 3].flatMap(x => [x, x * 2])"), "[1, 2, 2, 4, 3, 6]");
        match(eval("[1, 2, 3].flatMap(x => x * 2)"), "[2, 4, 6]"); // non-array results are also valid
        match(eval("[\"hello\", \"world\"].flatMap(word => word.split(''))"), "['h', 'e', 'l', 'l', 'o', 'w', 'o', 'r', 'l', 'd']");
        match(eval("[].flatMap(x => [x, x * 2])"), "[]");
        match(eval("[[1], [2, 3], [4, 5, 6]].flatMap(x => x)"), "[1, 2, 3, 4, 5, 6]");
        match(eval("[1, 2, 3].flatMap((x, i) => [x, i])"), "[1, 0, 2, 1, 3, 2]"); // check index is passed correctly
        eval("var data = [{id: 1, values: [10, 20]}, {id: 2, values: [30, 40]}];"
                + "var result = data.flatMap(item => item.values.map(val => ({id: item.id, value: val})));");
        NodeUtils.match(get("result"), "[{id: 1, value: 10}, {id: 1, value: 20}, {id: 2, value: 30}, {id: 2, value: 40}]");
        match(eval("[3, 1, 4, 1, 5, 9].sort()"), "[1, 1, 3, 4, 5, 9]");
        match(eval("['banana', 'apple', 'orange', 'grape'].sort()"), "['apple', 'banana', 'grape', 'orange']");
        match(eval("[10, 2, 5, 1].sort((a, b) => a - b)"), "[1, 2, 5, 10]"); // numeric sort
        match(eval("[10, 2, 5, 1].sort((a, b) => b - a)"), "[10, 5, 2, 1]"); // descending numeric sort
        // sort() with objects
        eval("var items = [{name: 'Edward', value: 21}, {name: 'Sharpe', value: 37}, {name: 'And', value: 45}, {name: 'The', value: -12}];" +
                "items.sort((a, b) => a.value - b.value);");
        NodeUtils.match(get("items"), "[{name: 'The', value: -12}, {name: 'Edward', value: 21}, {name: 'Sharpe', value: 37}, {name: 'And', value: 45}]");
        // fill() tests
        match(eval("[1, 2, 3].fill(4)"), "[4, 4, 4]");
        match(eval("[1, 2, 3].fill(4, 1)"), "[1, 4, 4]");
        match(eval("[1, 2, 3].fill(4, 1, 2)"), "[1, 4, 3]");
        match(eval("[1, 2, 3].fill(4, 1, 1)"), "[1, 2, 3]");
        match(eval("[1, 2, 3].fill(4, -3, -2)"), "[4, 2, 3]");
        match(eval("[1, 2, 3].fill(4, NaN, NaN)"), "[1, 2, 3]");
        match(eval("[1, 2, 3].fill(4, 3, 3)"), "[1, 2, 3]");
        // splice() tests
        match(eval("var a = [1, 2, 3]; a.splice(1, 1); a"), "[1, 3]");
        match(eval("var a = [1, 2, 3]; a.splice(1, 1, 'a', 'b'); a"), "[1, 'a', 'b', 3]");
        match(eval("var a = [1, 2, 3]; a.splice(1, 0, 'a'); a"), "[1, 'a', 2, 3]");
        match(eval("var a = [1, 2, 3]; a.splice(0, 3); a"), "[]");
        match(eval("var a = [1, 2, 3]; a.splice(-1, 1); a"), "[1, 2]");
        match(eval("var a = [1, 2, 3]; a.splice(10, 1); a"), "[1, 2, 3]");
        match(eval("var a = [1, 2, 3]; a.splice(0, 0, 'a', 'b'); a"), "['a', 'b', 1, 2, 3]");
        match(eval("var a = [1, 2, 3]; var removed = a.splice(0, 2); removed"), "[1, 2]");
        // shift() tests
        match(eval("var a = [1, 2, 3]; var shifted = a.shift(); shifted"), "1");
        match(eval("var a = [1, 2, 3]; a.shift(); a"), "[2, 3]");
        match(eval("var a = []; var shifted = a.shift(); shifted"), "null");
        // unshift() tests
        match(eval("var a = [1, 2, 3]; var newLength = a.unshift(4, 5); newLength"), "5");
        match(eval("var a = [1, 2, 3]; a.unshift(4, 5); a"), "[4, 5, 1, 2, 3]");
        match(eval("var a = []; a.unshift(1); a"), "[1]");
        match(eval("var a = []; var newLength = a.unshift(); newLength"), "0");
        // pop() tests
        match(eval("var a = [1, 2, 3]; var popped = a.pop(); popped"), "3");
        match(eval("var a = [1, 2, 3]; a.pop(); a"), "[1, 2]");
        match(eval("var a = []; var popped = a.pop(); popped"), null);
        // at() tests
        assertEquals(2, eval("[1, 2, 3].at(1)"));
        assertEquals(3, eval("[1, 2, 3].at(-1)"));
        assertEquals(2, eval("[1, 2, 3].at(-2)"));
        assertNull(eval("[1, 2, 3].at(3)"));
        assertNull(eval("[1, 2, 3].at(-4)"));
        assertNull(eval("[].at(0)"));
        // copyWithin() tests
        match(eval("[1, 2, 3, 4, 5].copyWithin(0, 3)"), "[4, 5, 3, 4, 5]");
        match(eval("[1, 2, 3, 4, 5].copyWithin(1, 3)"), "[1, 4, 5, 4, 5]");
        match(eval("[1, 2, 3, 4, 5].copyWithin(0, 3, 4)"), "[4, 2, 3, 4, 5]");
        match(eval("[1, 2, 3, 4, 5].copyWithin(1, 2, 4)"), "[1, 3, 4, 4, 5]");
        match(eval("[1, 2, 3, 4, 5].copyWithin(-2, 0, 2)"), "[1, 2, 3, 1, 2]");
        // keys(), values(), entries() tests
        match(eval("[1, 2, 3].keys()"), "[0, 1, 2]");
        match(eval("[1, 2, 3].values()"), "[1, 2, 3]");
        match(eval("[].values()"), "[]");
        match(eval("[1, 2, 3].entries()"), "[[0, 1], [1, 2], [2, 3]]");
        // Array.isArray() and Array.of() tests
        assertEquals(true, eval("Array.isArray([])"));
        assertEquals(true, eval("Array.isArray([1, 2, 3])"));
        assertEquals(false, eval("Array.isArray({})"));
        assertEquals(false, eval("Array.isArray('foo')"));
        assertEquals(false, eval("Array.isArray(123)"));
        match(eval("Array.of(1, 2, 3)"), "[1, 2, 3]");
        match(eval("Array.of('a', 'b', 'c')"), "['a', 'b', 'c']");
        match(eval("Array.of(1, 'a', true)"), "[1, 'a', true]");
        match(eval("Array.of()"), "[]");
        // findLast() and findLastIndex() tests
        assertEquals(4, eval("[1, 2, 3, 4, 5].findLast(x => x % 2 === 0)"));
        assertNull(eval("[1, 3, 5, 7].findLast(x => x % 2 === 0)"));
        assertNull(eval("[].findLast(x => true)"));
        assertEquals(4, eval("[1, 2, 3, 4, 2].findLastIndex(x => x % 2 === 0)"));
        assertEquals(2, eval("[1, 2, 3, 4, 2].findLastIndex(x => x === 3)"));
        assertEquals(-1, eval("[1, 3, 5, 7].findLastIndex(x => x % 2 === 0)"));
        assertEquals(-1, eval("[].findLastIndex(x => true)"));
        // group() tests
        eval("var result = [1, 2, 3, 4, 5].group(x => x % 2 === 0 ? 'even' : 'odd');");
        match(get("result"), "{ even: [2, 4], odd: [1, 3, 5] }");
        eval("var result = ['apple', 'banana', 'cherry'].group(x => x[0]);");
        match(get("result"), "{ a: ['apple'], b: ['banana'], c: ['cherry'] }");
        eval("var result = [].group(x => x);");
        match(get("result"), "{}");
        // with() tests
        match(eval("[1, 2, 3, 4].with(1, 'a')"), "[1, 'a', 3, 4]");
        match(eval("[1, 2, 3, 4].with(-1, 'a')"), "[1, 2, 3, 'a']");
        match(eval("[1, 2, 3, 4].with(10, 'a')"), "[1, 2, 3, 4]"); // index out of bounds, returns copy
        match(eval("[1, 2, 3, 4].with(-10, 'a')"), "[1, 2, 3, 4]"); // negative index out of bounds
        match(eval("[].with(0, 'a')"), "[]"); // empty array returns copy
        // indexOf with fromIndex
        assertEquals(1, eval("[1, 2, 3, 2].indexOf(2)"));
        assertEquals(3, eval("[1, 2, 3, 2].indexOf(2, 2)"));
        assertEquals(-1, eval("[1, 2, 3, 2].indexOf(2, 4)"));
        assertEquals(1, eval("[1, 2, 3, 2].indexOf(2, -4)"));
        assertEquals(3, eval("[1, 2, 3, 2].indexOf(2, -2)"));
        assertEquals(-1, eval("[1, 2, 3, 2].indexOf(4, 0)"));
        // lastIndexOf with fromIndex
        assertEquals(3, eval("[1, 2, 3, 2].lastIndexOf(2)"));
        assertEquals(1, eval("[1, 2, 3, 2].lastIndexOf(2, 2)"));
        assertEquals(1, eval("[1, 2, 3, 2].lastIndexOf(2, 1)"));
        assertEquals(-1, eval("[1, 2, 3, 2].lastIndexOf(2, 0)"));
        assertEquals(1, eval("[1, 2, 3, 2].lastIndexOf(2, -2)"));
        assertEquals(-1, eval("[1, 2, 3, 2].lastIndexOf(4, 0)"));
    }

    @Test
    void testArrayConstructor() {
        match(eval("new Array(1, 2, 3)"), "[1, 2, 3]");
        match(eval("new Array(3)"), "[null, null, null]");
    }

    @Test
    void testJavaNativeArrayMethods() {
        // Test that Java native arrays (String[], int[], etc.) work with JS array methods
        // This exercises Terms.toJsArray() conversion in JsArrayPrototype
        Engine engine = new Engine();

        // String array
        String[] strings = {"a", "b", "c"};
        engine.put("items", strings);
        assertEquals(3, engine.eval("items.length"));
        assertEquals("a", engine.eval("items[0]"));
        NodeUtils.match(engine.eval("items.map(x => x.toUpperCase())"), "['A', 'B', 'C']");
        NodeUtils.match(engine.eval("items.filter(x => x !== 'b')"), "['a', 'c']");
        assertEquals("a-b-c", engine.eval("items.join('-')"));

        // int array
        int[] numbers = {1, 2, 3, 4, 5};
        engine.put("nums", numbers);
        assertEquals(5, engine.eval("nums.length"));
        assertEquals(1, engine.eval("nums[0]"));
        assertEquals(15, engine.eval("nums.reduce((a, b) => a + b, 0)"));
        NodeUtils.match(engine.eval("nums.map(x => x * 2)"), "[2, 4, 6, 8, 10]");
        NodeUtils.match(engine.eval("nums.filter(x => x % 2 === 0)"), "[2, 4]");

        // Object array with mixed types
        Object[] mixed = {"hello", 42, true};
        engine.put("mixed", mixed);
        assertEquals(3, engine.eval("mixed.length"));
        assertEquals("hello", engine.eval("mixed[0]"));
        assertEquals(42, engine.eval("mixed[1]"));
        assertEquals(true, engine.eval("mixed[2]"));
        // forEach should work
        engine.eval("var result = []; mixed.forEach(x => result.push(x))");
        NodeUtils.match(engine.get("result"), "['hello', 42, true]");
    }

    @Test
    void testUint8ArrayLength() {
        // Test that byte[] converted to JsUint8Array reports correct length
        // This exercises the fix in JsUint8Array.getMember()
        Engine engine = new Engine();
        engine.setExternalBridge(new ExternalBridge() {});

        byte[] bytes = new byte[]{1, 2, 3, (byte) 255};
        engine.put("bytes", bytes);

        // Length should be correct
        assertEquals(4, engine.eval("bytes.length"));

        // Element access should work
        assertEquals(1, engine.eval("bytes[0]"));
        assertEquals(2, engine.eval("bytes[1]"));
        assertEquals(3, engine.eval("bytes[2]"));
        assertEquals(255, engine.eval("bytes[3]")); // unsigned byte

        // Array methods should work on byte arrays
        assertEquals(true, engine.eval("bytes.includes(255)"));
        assertEquals(3, engine.eval("bytes.indexOf(255)"));
    }

}

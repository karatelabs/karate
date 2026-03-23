package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsObjectTest extends EvalBase {

    @Test
    void testDev() {

    }

    // =================================================================================================
    // ES6 Constructor Compliance Tests: new Foo() vs Foo()
    // =================================================================================================

    // -------------------------------------------------------------------------
    // Date: new Date() returns Date object, Date() returns STRING (ES6 spec)
    // -------------------------------------------------------------------------

    @Test
    void testDateConstructorWithNew() {
        // new Date() should return a Date object
        assertEquals("object", eval("typeof new Date()"));
        assertEquals(true, eval("new Date() instanceof Date"));
        assertEquals(true, eval("new Date(0) instanceof Date"));
    }

    @Test
    void testDateFunctionWithoutNew() {
        // Date() without new should ALWAYS return a string (ES6 spec)
        // Arguments are ignored when called without new
        assertEquals("string", eval("typeof Date()"));
        assertEquals("string", eval("typeof Date(0)"));
        assertEquals("string", eval("typeof Date(2025, 0, 1)"));
        assertEquals("string", eval("typeof Date('invalid')"));
    }

    @Test
    void testDateFunctionIgnoresArguments() {
        // ES6: Date() ignores all arguments and returns current time as string
        // The string should represent the current time, not the argument
        Object result1 = eval("Date()");
        Object result2 = eval("Date(0)");  // epoch timestamp ignored
        assertTrue(result1 instanceof String);
        assertTrue(result2 instanceof String);
        // Both should be current time strings (can't check exact value but type is correct)
    }

    @Test
    void testNewDateVsDateEquality() {
        // new Date() creates object, Date() creates string - never equal
        assertEquals(false, eval("new Date(0) === Date(0)"));
        assertEquals(false, eval("new Date(0) == Date(0)"));
    }

    // -------------------------------------------------------------------------
    // Array: Both new Array() and Array() should return Array
    // -------------------------------------------------------------------------

    @Test
    void testArrayConstructorWithNew() {
        assertEquals("object", eval("typeof new Array()"));
        assertEquals(true, eval("new Array() instanceof Array"));
        assertEquals(true, eval("Array.isArray(new Array())"));
        matchEval("new Array()", "[]");
        matchEval("new Array(1, 2, 3)", "[1, 2, 3]");
    }

    @Test
    void testArrayFunctionWithoutNew() {
        // Array() without new should behave the same as new Array()
        assertEquals("object", eval("typeof Array()"));
        assertEquals(true, eval("Array() instanceof Array"));
        assertEquals(true, eval("Array.isArray(Array())"));
        matchEval("Array()", "[]");
        matchEval("Array(1, 2, 3)", "[1, 2, 3]");
    }

    @Test
    void testArraySingleNumericArgument() {
        // Array(n) and new Array(n) create array with n empty slots
        assertEquals(3, eval("new Array(3).length"));
        assertEquals(3, eval("Array(3).length"));
        matchEval("new Array(3)", "[null, null, null]");
        matchEval("Array(3)", "[null, null, null]");
    }

    @Test
    void testNewArrayVsArrayEquality() {
        // Both should create equivalent arrays
        assertEquals(true, eval("Array.isArray(new Array())"));
        assertEquals(true, eval("Array.isArray(Array())"));
        assertEquals(true, eval("new Array(1,2,3).length === Array(1,2,3).length"));
    }

    // -------------------------------------------------------------------------
    // Object: Both new Object() and Object() should return Object
    // -------------------------------------------------------------------------

    @Test
    void testObjectConstructorWithNew() {
        assertEquals("object", eval("typeof new Object()"));
        matchEval("new Object()", "{}");
    }

    @Test
    void testObjectFunctionWithoutNew() {
        // Object() without new should behave the same as new Object()
        assertEquals("object", eval("typeof Object()"));
        matchEval("Object()", "{}");
    }

    @Test
    void testObjectWithPrimitiveArgument() {
        // Object(primitive) wraps the primitive
        assertEquals("object", eval("typeof Object(42)"));
        assertEquals("object", eval("typeof Object('hello')"));
        assertEquals("object", eval("typeof Object(true)"));
    }

    // -------------------------------------------------------------------------
    // String: new String() returns object, String() returns primitive
    // -------------------------------------------------------------------------

    @Test
    void testStringConstructorWithNew() {
        assertEquals("object", eval("typeof new String()"));
        assertEquals("object", eval("typeof new String('hello')"));
        assertEquals(true, eval("new String('x') instanceof String"));
        assertEquals("hello", eval("new String('hello').valueOf()"));
    }

    @Test
    void testStringFunctionWithoutNew() {
        // String() without new returns primitive string
        assertEquals("string", eval("typeof String()"));
        assertEquals("string", eval("typeof String('hello')"));
        assertEquals("string", eval("typeof String(123)"));
        assertEquals("123", eval("String(123)"));
        assertEquals("true", eval("String(true)"));
    }

    @Test
    void testNewStringVsStringEquality() {
        // Loose equality works, strict does not
        assertEquals(true, eval("new String('hello') == 'hello'"));
        assertEquals(false, eval("new String('hello') === 'hello'"));
        assertEquals(true, eval("String('hello') === 'hello'"));
    }

    // -------------------------------------------------------------------------
    // Number: new Number() returns object, Number() returns primitive
    // -------------------------------------------------------------------------

    @Test
    void testNumberConstructorWithNew() {
        assertEquals("object", eval("typeof new Number()"));
        assertEquals("object", eval("typeof new Number(42)"));
        assertEquals(true, eval("new Number(5) instanceof Number"));
        assertEquals(42, eval("new Number(42).valueOf()"));
    }

    @Test
    void testNumberFunctionWithoutNew() {
        // Number() without new returns primitive number
        assertEquals("number", eval("typeof Number()"));
        assertEquals("number", eval("typeof Number(42)"));
        assertEquals("number", eval("typeof Number('123')"));
        assertEquals(123, eval("Number('123')"));
        assertEquals(0, eval("Number()"));
    }

    @Test
    void testNewNumberVsNumberEquality() {
        // Loose equality works, strict does not
        assertEquals(true, eval("new Number(5) == 5"));
        assertEquals(false, eval("new Number(5) === 5"));
        assertEquals(true, eval("Number(5) === 5"));
    }

    @Test
    void testNumberArithmetic() {
        // Both should work in arithmetic
        assertEquals(6, eval("new Number(5) + 1"));
        assertEquals(6, eval("Number(5) + 1"));
    }

    // -------------------------------------------------------------------------
    // Boolean: new Boolean() returns object, Boolean() returns primitive
    // -------------------------------------------------------------------------

    @Test
    void testBooleanConstructorWithNew() {
        assertEquals("object", eval("typeof new Boolean()"));
        assertEquals("object", eval("typeof new Boolean(true)"));
        assertEquals(true, eval("new Boolean(false) instanceof Boolean"));
    }

    @Test
    void testBooleanFunctionWithoutNew() {
        // Boolean() without new returns primitive boolean
        assertEquals("boolean", eval("typeof Boolean()"));
        assertEquals("boolean", eval("typeof Boolean(true)"));
        assertEquals("boolean", eval("typeof Boolean(1)"));
        assertEquals(true, eval("Boolean(1)"));
        assertEquals(false, eval("Boolean(0)"));
        assertEquals(false, eval("Boolean()"));
    }

    @Test
    void testBooleanObjectTruthiness() {
        // new Boolean(false) is truthy because it's an object!
        assertEquals(true, eval("!!new Boolean(false)"));
        // But Boolean(false) returns primitive false
        assertEquals(false, eval("!!Boolean(false)"));
    }

    @Test
    void testNewBooleanVsBooleanEquality() {
        assertEquals(true, eval("new Boolean(true) == true"));
        assertEquals(false, eval("new Boolean(true) === true"));
        assertEquals(true, eval("Boolean(true) === true"));
    }

    // -------------------------------------------------------------------------
    // RegExp: Both new RegExp() and RegExp() should return RegExp
    // -------------------------------------------------------------------------

    @Test
    void testRegExpConstructorWithNew() {
        assertEquals("object", eval("typeof new RegExp('test')"));
        assertEquals(true, eval("new RegExp('test') instanceof RegExp"));
        assertEquals("/test/", eval("new RegExp('test').toString()"));
        assertEquals("/test/i", eval("new RegExp('test', 'i').toString()"));
    }

    @Test
    void testRegExpFunctionWithoutNew() {
        // RegExp() without new should behave same as new RegExp()
        assertEquals("object", eval("typeof RegExp('test')"));
        assertEquals(true, eval("RegExp('test') instanceof RegExp"));
        assertEquals("/test/", eval("RegExp('test').toString()"));
        assertEquals("/test/gi", eval("RegExp('test', 'gi').toString()"));
    }

    @Test
    void testRegExpTest() {
        assertEquals(true, eval("new RegExp('hello').test('hello world')"));
        assertEquals(true, eval("RegExp('hello').test('hello world')"));
        assertEquals(false, eval("new RegExp('xyz').test('hello world')"));
        assertEquals(false, eval("RegExp('xyz').test('hello world')"));
    }

    // -------------------------------------------------------------------------
    // Error: Both new Error() and Error() should return Error
    // -------------------------------------------------------------------------

    @Test
    void testErrorConstructorWithNew() {
        assertEquals("object", eval("typeof new Error()"));
        assertEquals("object", eval("typeof new Error('message')"));
        assertEquals(true, eval("new Error() instanceof Error"));
        assertEquals("test", eval("new Error('test').message"));
    }

    @Test
    void testErrorFunctionWithoutNew() {
        // Error() without new should behave same as new Error() in ES6
        assertEquals("object", eval("typeof Error()"));
        assertEquals("object", eval("typeof Error('message')"));
        assertEquals(true, eval("Error() instanceof Error"));
        assertEquals("test", eval("Error('test').message"));
    }

    // -------------------------------------------------------------------------
    // Constructor return value behavior
    // -------------------------------------------------------------------------

    @Test
    void testConstructorReturningObject() {
        // When constructor explicitly returns an object, that object is used
        assertEquals(42, eval("function Foo() { return { x: 42 }; }; new Foo().x"));
    }

    @Test
    void testConstructorReturningPrimitive() {
        // When constructor returns a primitive, 'this' is returned instead
        assertEquals(true, eval("function Foo() { this.x = 1; return 42; }; new Foo().x === 1"));
        assertEquals(true, eval("function Foo() { this.x = 1; return 'hello'; }; new Foo().x === 1"));
        assertEquals(true, eval("function Foo() { this.x = 1; return true; }; new Foo().x === 1"));
    }

    @Test
    void testConstructorReturningNull() {
        // Returning null should still use 'this'
        assertEquals(true, eval("function Foo() { this.x = 1; return null; }; new Foo().x === 1"));
    }

    @Test
    void testConstructorReturningUndefined() {
        // Returning undefined (explicit or implicit) uses 'this'
        assertEquals(true, eval("function Foo() { this.x = 1; return undefined; }; new Foo().x === 1"));
        assertEquals(true, eval("function Foo() { this.x = 1; }; new Foo().x === 1"));
    }

    // -------------------------------------------------------------------------
    // typeof checks for wrapper objects
    // -------------------------------------------------------------------------

    @Test
    void testTypeofWrapperObjects() {
        // All wrapper objects have typeof "object"
        assertEquals("object", eval("typeof new String('x')"));
        assertEquals("object", eval("typeof new Number(1)"));
        assertEquals("object", eval("typeof new Boolean(true)"));
        assertEquals("object", eval("typeof new Date()"));
        assertEquals("object", eval("typeof new Array()"));
        assertEquals("object", eval("typeof new Object()"));
        assertEquals("object", eval("typeof new RegExp('x')"));
        assertEquals("object", eval("typeof new Error()"));
    }

    @Test
    void testTypeofPrimitiveConversions() {
        // Function calls (without new) return primitives for String/Number/Boolean
        assertEquals("string", eval("typeof String('x')"));
        assertEquals("number", eval("typeof Number(1)"));
        assertEquals("boolean", eval("typeof Boolean(true)"));
        // These still return objects
        assertEquals("string", eval("typeof Date()"));  // ES6: Date() returns string!
        assertEquals("object", eval("typeof Array()"));
        assertEquals("object", eval("typeof Object()"));
        assertEquals("object", eval("typeof RegExp('x')"));
        assertEquals("object", eval("typeof Error()"));
    }

    @Test
    void testObjectNumericBracketAccess() {
        // Test that objects can be accessed with numeric indices using bracket notation
        // In standard JavaScript, obj[3] is equivalent to obj['3'] for objects
        eval("var obj = {1: 0.4, 2: 0.6, 3: 0.75, 4: 0.85, 5: 0.95, 6: 1};"
                + "var value = obj[3];");
        assertEquals(0.75, get("value"));

        // Also test with string keys - should work the same way
        eval("var obj2 = {'1': 0.4, '2': 0.6, '3': 0.75};"
                + "var value2 = obj2[3];");
        assertEquals(0.75, get("value2"));

        // Test that both numeric and string access return the same value
        eval("var obj3 = {5: 'hello'};"
                + "var numAccess = obj3[5];"
                + "var strAccess = obj3['5'];");
        assertEquals("hello", get("numAccess"));
        assertEquals("hello", get("strAccess"));

        // Test setting with numeric indices
        eval("var obj4 = {};"
                + "obj4[1] = 'one';"
                + "obj4[2] = 'two';"
                + "var val1 = obj4[1];"
                + "var val2 = obj4['2'];");
        assertEquals("one", get("val1"));
        assertEquals("two", get("val2"));
        NodeUtils.match(get("obj4"), "{ '1': 'one', '2': 'two' }");
    }

    @Test
    void testObject() {
        matchEval("{}", "{}");
        matchEval("{ a: 1 }", "{ a: 1 }");
        matchEval("{ a: 1, b: 2 }", "{ a: 1, b: 2 }");
        matchEval("{ 'a': 1 }", "{ a: 1 }");
        matchEval("{ \"a\": 1 }", "{ a: 1 }");
        matchEval("{ a: 'b' }", "{ a: 'b' }");
        matchEval("{ a: true }", "{ a: true }");
        matchEval("{ a: (1 + 2) }", "{ a: 3 }");
        matchEval("{ a: b }", "{ a: 5 }", "{ b: 5 }");
    }

    @Test
    void testObjectMutation() {
        assertEquals(3, eval("a.b = 1 + 2", "{ a: {} }"));
        NodeUtils.match(get("a"), "{ b: 3 }");
        eval("var a = { foo: 1, bar: 2 }; delete a.foo");
        NodeUtils.match(get("a"), "{ bar: 2 }");
        eval("var a = { foo: 1, bar: 2 }; delete a['bar']");
        NodeUtils.match(get("a"), "{ foo: 1 }");
    }

    @Test
    void testObjectProp() {
        assertEquals(2, eval("a = { b: 2 }; a.b"));
        assertEquals(2, eval("a = { b: 2 }; a['b']"));
    }

    @Test
    void testObjectPropReservedWords() {
        assertEquals(2, eval("a = { 'null': 2 }; a.null"));
    }

    @Test
    void testObjectFunction() {
        assertEquals("foo", eval("a = { b: function(){ return this.c }, c: 'foo' }; a.b()"));
    }

    @Test
    void testObjectEnhanced() {
        eval("a = 1; b = { a }");
        NodeUtils.match(get("b"), "{ a: 1 }");
    }

    @Test
    void testObjectPrototype() {
        String js = "function Dog(name){ this.name = name }; var dog = new Dog('foo');"
                + " Dog.prototype.toString = function(){ return this.name }; ";
        assertEquals("foo", eval(js + "dog.toString()"));
        assertEquals(true, eval(js + "dog.constructor === Dog"));
        assertEquals(true, eval(js + "dog instanceof Dog"));
        assertEquals(true, eval(js + "dog instanceof dog.constructor"));
    }

    @Test
    void testPrototypePropertySetToNull() {
        // Edge case: when a property is explicitly set to null on child,
        // it should NOT continue looking up the prototype chain
        String js = "function Animal() { this.sound = 'generic' };"
                + "function Cat() { this.sound = null };" // explicitly set to null
                + "Cat.prototype = new Animal();"
                + "var cat = new Cat();";
        assertNull(eval(js + "cat.sound")); // should be null, not 'generic'

        // Test setting property to null AFTER object creation (via prototype chain)
        String js2 = "function Parent() { this.value = 'parent' };"
                + "function Child() {}"
                + "Child.prototype = new Parent();"
                + "var child = new Child();"
                + "child.value = null;"; // set to null after creation
        assertNull(eval(js2 + "child.value")); // should be null, not 'parent'

        // Test using bracket notation access
        String js3 = "function Base() { this.prop = 'base' };"
                + "function Derived() { this.prop = null };"
                + "Derived.prototype = new Base();"
                + "var obj = new Derived();";
        assertNull(eval(js3 + "obj['prop']")); // bracket access should also return null
    }

    @Test
    void testConstructorThis() {
        eval("function Dog(name) { this.name = name }; var dog = new Dog('Fido'); var name = dog.name");
        assertEquals("Fido", get("name"));
    }

    @Test
    void testObjectApi() {
        matchEval("Object.keys({ a: 1, b: 2 })", "['a', 'b']");
        matchEval("Object.values({ a: 1, b: 2 })", "[1, 2]");
        matchEval("Object.entries({ a: 1, b: 2 })", "[['a', 1], ['b', 2]]");
        matchEval("Object.assign({}, { a: 1 }, { b: 2 })", "{ a: 1, b: 2 }");
        matchEval("Object.assign({ a: 0 }, { a: 1, b: 2 })", "{ a: 1, b: 2 }");
        matchEval("Object.fromEntries([['a', 1], ['b', 2]])", "{ a: 1, b: 2 }");
        matchEval("Object.fromEntries(Object.entries({ a: 1, b: 2 }))", "{ a: 1, b: 2 }");
        assertEquals(true, eval("Object.is(42, 42)"));
        assertEquals(true, eval("Object.is('foo', 'foo')"));
        assertEquals(false, eval("Object.is('foo', 'bar')"));
        assertEquals(false, eval("Object.is(null, undefined)"));
        assertEquals(true, eval("Object.is(null, null)"));
        assertEquals(true, eval("Object.is(NaN, NaN)"));
        // assertEquals(false, eval("Object.is(0, -0)"));
        matchEval("{}.valueOf()", "{}");
        matchEval("var obj = { a: 1, b: 2 }; obj.valueOf()", "{ a: 1, b: 2 }");
        matchEval("var x = { a: 0.5 }; Object.entries(x).map(y => [y[0], y[1], typeof y[1]])", "[[a, 0.5, number]]");
    }

    @Test
    void testObjectSpread() {
        matchEval("var obj1 = {a: 1, b: 2}; var obj2 = {...obj1}; obj2", "{ a: 1, b: 2 }");
        matchEval("var obj1 = {a: 1, b: 2}; var obj2 = {...obj1, b: 3}; obj2", "{ a: 1, b: 3 }");
        matchEval("var obj1 = {a: 1}; var obj2 = {b: 2}; var obj3 = {...obj1, ...obj2}; obj3", "{ a: 1, b: 2 }");
        matchEval("var obj1 = {a: 1, b: 2}; var obj2 = {b: 3, c: 4}; var obj3 = {...obj1, ...obj2}; obj3", "{ a: 1, b: 3, c: 4 }");
        matchEval("var obj1 = {a: 1, b: 2}; var obj2 = {b: 3, c: 4}; var obj3 = {...obj2, ...obj1}; obj3", "{ b: 2, c: 4, a: 1 }");
    }

    @Test
    void testPrototypeChainBasic() {
        // Basic prototype chain: instance inherits from constructor's prototype
        assertEquals(42, eval("function Foo() {}; Foo.prototype.bar = 42; var f = new Foo(); f.bar"));
    }

    @Test
    void testPrototypeChainInstanceOf() {
        // instanceof should work with prototype chain
        assertEquals(true, eval("function Foo() {}; var f = new Foo(); f instanceof Foo"));
    }

    @Test
    void testPrototypeChainProtoGetter() {
        // __proto__ getter should return the prototype delegate
        assertEquals(true, eval("function Foo() {}; var f = new Foo(); f.__proto__ === Foo.prototype"));
    }

    @Test
    void testPrototypeChainGetPrototypeOf() {
        // Object.getPrototypeOf should return the prototype delegate
        assertEquals(true, eval("function Foo() {}; var f = new Foo(); Object.getPrototypeOf(f) === Foo.prototype"));
    }

    @Test
    void testPrototypeChainInheritance() {
        // Prototype chain inheritance via Object.create
        assertEquals("sound", eval(
                "function Animal() {}; Animal.prototype.speak = function() { return 'sound'; };"
                        + "function Dog() {}; Dog.prototype = Object.create(Animal.prototype);"
                        + "var d = new Dog(); d.speak()"));
    }

    @Test
    void testPrototypeChainOwnPropertyShadows() {
        // Own property should shadow prototype property
        assertEquals(1, eval("function Foo() { this.bar = 1; }; Foo.prototype.bar = 2; var f = new Foo(); f.bar"));
    }

    @Test
    void testPrototypeChainMultiLevelInheritance() {
        // Multi-level prototype chain via Object.create
        // Exercises JsFunction.getMember fix for prototype assignment
        assertEquals("animal walks", eval(
                "function Animal() {};"
                        + "Animal.prototype.walk = function() { return 'animal walks'; };"
                        + "function Dog() {};"
                        + "Dog.prototype = Object.create(Animal.prototype);"
                        + "Dog.prototype.bark = function() { return 'dog barks'; };"
                        + "function Puppy() {};"
                        + "Puppy.prototype = Object.create(Dog.prototype);"
                        + "var p = new Puppy();"
                        + "p.walk()"));

        assertEquals("dog barks", eval(
                "function Animal() {};"
                        + "Animal.prototype.walk = function() { return 'animal walks'; };"
                        + "function Dog() {};"
                        + "Dog.prototype = Object.create(Animal.prototype);"
                        + "Dog.prototype.bark = function() { return 'dog barks'; };"
                        + "function Puppy() {};"
                        + "Puppy.prototype = Object.create(Dog.prototype);"
                        + "var p = new Puppy();"
                        + "p.bark()"));
    }

    @Test
    void testPrototypeChainAssignmentOverridesDefault() {
        // Verify that prototype assignment properly overrides the default functionPrototype
        // This tests the core fix in JsFunction.getMember
        assertEquals(true, eval(
                "function Base() {};"
                        + "Base.prototype.isBase = true;"
                        + "function Derived() {};"
                        + "Derived.prototype = Object.create(Base.prototype);"
                        + "var d = new Derived();"
                        + "d.isBase"));

        // The default functionPrototype should NOT be used after assignment
        assertEquals(false, eval(
                "function Base() {};"
                        + "Base.prototype.isBase = true;"
                        + "function Derived() {};"
                        + "var defaultProto = Derived.prototype;"
                        + "Derived.prototype = Object.create(Base.prototype);"
                        + "Derived.prototype === defaultProto"));
    }

    @Test
    void testPrototypeChainWithConstructorProperty() {
        // Object.create should not affect constructor property unless explicitly set
        assertEquals(true, eval(
                "function Animal() {};"
                        + "function Dog() {};"
                        + "Dog.prototype = Object.create(Animal.prototype);"
                        + "Dog.prototype.constructor = Dog;"
                        + "var d = new Dog();"
                        + "d.constructor === Dog"));
    }

}

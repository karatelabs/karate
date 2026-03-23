package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExternalBridgeTest extends EvalBase {

    final ExternalBridge bridge = new ExternalBridge() {

    };

    @Override
    Object eval(String text, String vars) {
        engine = new Engine();
        engine.setExternalBridge(bridge);
        return engine.eval(text);
    }

    @Test
    void testDev() {

    }

    @Test
    void testObjectLiteralWithFunctionProperty() {
        // This replicates the karate-core test failure where foo.bar() fails
        // when the external bridge is enabled
        engine = new Engine();
        engine.setExternalBridge(bridge);
        Object result = engine.eval("var foo = { bar: function(){ return 'baz' } }; foo.bar()");
        assertEquals("baz", result);
    }

    @Test
    void testObjectLiteralWithNestedFunctionProperty() {
        // Chained property access: foo.bar.baz()
        engine = new Engine();
        engine.setExternalBridge(bridge);
        Object result = engine.eval("var foo = { bar: { baz: function(){ return 'deep' } } }; foo.bar.baz()");
        assertEquals("deep", result);
    }

    @Test
    void testObjectLiteralFunctionPropertyTwoSteps() {
        // Mimics karate-core scenario where def and call are separate steps
        engine = new Engine();
        engine.setExternalBridge(bridge);
        // Step 1: def foo = { bar: function(){ return 'baz' } }
        engine.eval("var foo = { bar: function(){ return 'baz' } }");
        // Step 2: def result = foo.bar()
        Object result = engine.eval("foo.bar()");
        assertEquals("baz", result);
    }

    @Test
    void testObjectLiteralFunctionPropertyViaGet() {
        // Check what foo.bar returns when accessed
        engine = new Engine();
        engine.setExternalBridge(bridge);
        engine.eval("var foo = { bar: function(){ return 'baz' } }");
        Object bar = engine.eval("foo.bar");
        assertNotNull(bar, "foo.bar should not be null");
        assertTrue(bar instanceof JsCallable, "foo.bar should be JsCallable but was: " + bar.getClass());
    }

    @Test
    void testConstructAndCall() {
        ExternalAccess type = bridge.forType("java.util.Properties");
        Object o = type.construct(new Object[]{});
        assertEquals("java.util.Properties", o.getClass().getName());
        ExternalAccess instance = bridge.forInstance(o);
        assertEquals(0, instance.invokeMethod("size", JavaUtils.EMPTY));
        instance.invokeMethod("put", new Object[]{"foo", 5});
        assertEquals(5, instance.invokeMethod("get", new Object[]{"foo"}));
    }

    @Test
    void testGet() {
        DemoPojo dp = new DemoPojo();
        dp.setStringValue("foo");
        dp.setIntValue(5);
        dp.setBooleanValue(true);
        ExternalAccess instance = bridge.forInstance(dp);
        assertEquals("foo", instance.getProperty("stringValue"));
        assertEquals(5, instance.getProperty("intValue"));
        assertEquals(true, instance.getProperty("booleanValue"));
        ObjectLike ol = (ObjectLike) instance;
        NodeUtils.match(ol.toMap(), "{ stringValue: 'foo', integerArray: null, intValue: 5, instanceField: 'instance-field', booleanValue: true, doubleValue: 0.0, intArray: null }");
    }

    @Test
    void testSet() {
        DemoPojo dp = new DemoPojo();
        ExternalAccess instance = bridge.forInstance(dp);
        instance.setProperty("stringValue", "bar");
        instance.setProperty("intValue", 10);
        instance.setProperty("booleanValue", true);
        assertEquals("bar", dp.getStringValue());
        assertEquals(10, dp.getIntValue());
        assertTrue(dp.isBooleanValue());
    }

    @Test
    void testSetSpecial() {
        DemoPojo dp = new DemoPojo();
        ExternalAccess instance = bridge.forInstance(dp);
        instance.setProperty("doubleValue", 10);
        instance.setProperty("booleanValue", Boolean.TRUE);
        assertEquals(10, dp.getDoubleValue());
        assertTrue(dp.isBooleanValue());
    }

    @Test
    void testVarArgs() {
        DemoPojo dp = new DemoPojo();
        ExternalAccess instance = bridge.forInstance(dp);
        JavaInvokable method = instance.getMethod("varArgs");
        assertEquals("foo", method.invoke(new Object[]{null, "foo"}));
        assertEquals("bar", method.invoke(new Object[]{null, "foo", "bar"}));
    }

    @Test
    void testMethodOverload() {
        DemoPojo dp = new DemoPojo();
        ExternalAccess instance = bridge.forInstance(dp);
        JavaInvokable method = instance.getMethod("doWork");
        assertEquals("hello", method.invoke(new Object[]{}));
        assertEquals("hellofoo", method.invoke(new Object[]{"foo"}));
        assertEquals("hellofootrue", method.invoke(new Object[]{"foo", true}));
    }

    @Test
    void testArrayLengthAndMap() {
        List<Object> list = NodeUtils.fromJson("['foo', 'bar']");
        JsArray jl = new JsArray(list);
        assertEquals(2, jl.getMember("length"));
    }

    @Test
    void testJavaInterop() {
        eval("var DemoUtils = Java.type('io.karatelabs.js.DemoUtils'); var b = DemoUtils.doWork()");
        assertEquals("hello", get("b"));
        eval("var DemoUtils = Java.type('io.karatelabs.js.DemoUtils'); var b = DemoUtils.doWork; var c = b()");
        assertEquals("hello", get("c"));
        eval("var DemoPojo = Java.type('io.karatelabs.js.DemoPojo'); var b = new DemoPojo(); var c = b.doWork()");
        assertEquals("hello", get("c"));
        eval("var DemoPojo = Java.type('io.karatelabs.js.DemoPojo'); var b = new DemoPojo(); b.stringValue = 'foo'; var c = b.stringValue");
        assertEquals("foo", get("c"));
        eval("var DemoPojo = Java.type('io.karatelabs.js.DemoPojo'); var b = new DemoPojo('foo'); var c = b.stringValue");
        assertEquals("foo", get("c"));
        eval("var DemoPojo = Java.type('io.karatelabs.js.DemoPojo'); var b = new DemoPojo('foo', 42); var c = b.stringValue; var d = b.intValue");
        assertEquals("foo", get("c"));
        assertEquals(42, get("d"));
        eval("var DemoPojo = Java.type('io.karatelabs.js.DemoPojo'); var b = new DemoPojo(); var c = b.stringValue");
        assertNull(get("c"));
        eval("var DemoPojo = Java.type('io.karatelabs.js.DemoPojo'); var b = new DemoPojo('foo'); b.integerArray = [1, 2]; var c = b.integerArray; var d = b.integerArray[1]");
        NodeUtils.match(get("c"), "[1, 2]");
        assertEquals(2, get("d"));
        eval("var DemoPojo = Java.type('io.karatelabs.js.DemoPojo'); var b = new DemoPojo('foo'); b.intArray = [1, 2]; var c = b.intArray; var d = b.intArray[1]");
        NodeUtils.match(get("c"), "[1, 2]");
        assertEquals(2, get("d"));
        assertEquals("static-field", eval("var DemoPojo = Java.type('io.karatelabs.js.DemoPojo'); DemoPojo.staticField"));
        assertEquals("static-field-changed", eval("var DemoPojo = Java.type('io.karatelabs.js.DemoPojo'); DemoPojo.staticField = 'static-field-changed'; DemoPojo.staticField"));
        assertEquals("foo", eval("io.karatelabs.js.DemoPojo.staticField = 'foo'; var a = io.karatelabs.js.DemoPojo.staticField"));
        assertEquals("foo", get("a"));
        assertEquals("instance-field", eval("var DemoPojo = Java.type('io.karatelabs.js.DemoPojo'); var a = new DemoPojo(); a.instanceField"));
        eval("var DemoPojo = Java.type('io.karatelabs.js.DemoPojo'); var b = new DemoPojo(); var c = b.doWork; var d = c()");
        assertEquals("hello", get("d"));
        eval("var DemoPojo = Java.type('io.karatelabs.js.DemoPojo'); var b = new DemoPojo('foo'); b.integerArray = [1, 2]; var c = b.doIntegerArray()");
        NodeUtils.match(get("c"), "[1, 2]");
    }

    @Test
    void testJavaInteropException() {
        try {
            eval("var DemoUtils = Java.type('io.karatelabs.js.DemoUtils'); var b = DemoUtils.doWorkException; var c = b()");
            fail("expected exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("cannot invoke static method io.karatelabs.js.DemoUtils#doWorkException"));
        }
        try {
            eval("var DemoUtils = Java.type('io.karatelabs.js.DemoUtils'); var b = DemoUtils.doWorkException; var c = b().foo");
            fail("expected exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("expression: b() - cannot invoke static method io.karatelabs.js.DemoUtils#doWorkException: java.lang.reflect.InvocationTargetException"));
        }
        try {
            eval("var DemoPojo = Java.type('io.karatelabs.js.DemoPojo'); var b = new DemoPojo(); var c = b.doWorkException()");
            fail("expected exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("cannot invoke instance method io.karatelabs.js.DemoPojo#doWorkException"));
        }
        try {
            eval("var DemoUtils = Java.type('io.karatelabs.js.DemoUtils'); var b = DemoUtils.doWorkException()");
            fail("expected exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("cannot invoke static method io.karatelabs.js.DemoUtils#doWorkException"));
        }
        try {
            eval("var DemoSimpleObject = Java.type('io.karatelabs.js.DemoSimpleObject'); var b = new DemoSimpleObject(); var c = b.doWorkException()");
            fail("expected exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("failed"));
        }
        try {
            eval("var DemoSimpleObject = Java.type('io.karatelabs.js.DemoSimpleObject'); var b = new DemoSimpleObject(); var c = b.inner.doWorkException()");
            fail("expected exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("failed"));
        }
    }

    @Test
    void testJavaToConversion() {
        assertEquals(DemoUtils.class, eval("var DemoUtils = Java.type('io.karatelabs.js.DemoUtils'); Java.to(DemoUtils)"));
    }

    @Test
    void testJavaInteropJdk() {
        assertEquals("bar", eval("var props = new java.util.Properties(); props.put('foo', 'bar'); props.get('foo')"));
        assertEquals(new BigDecimal(123123123123L), eval("new java.math.BigDecimal(123123123123)"));
        assertEquals(String.CASE_INSENSITIVE_ORDER, eval("java.lang.String.CASE_INSENSITIVE_ORDER"));
        assertInstanceOf(UUID.class, eval("java.util.UUID.randomUUID()"));
    }

    @Test
    void testJavaInteropJdkSpecial() {
        assertEquals("aGVsbG8=", eval("var Base64 = Java.type('java.util.Base64'); Base64.getEncoder().encodeToString('hello'.getBytes())"));
    }

    @Test
    void testJavaStringConstructorWithByteArrayAndCharset() {
        // V1 compatibility: new java.lang.String(byteArray, 'utf-8') should work
        // This pattern is used in karate-demo request.feature to convert request body bytes to string
        engine = new Engine();
        engine.setExternalBridge(bridge);
        byte[] bytes = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        engine.put("requestBody", bytes);
        Object result = engine.eval("new java.lang.String(requestBody, 'utf-8')");
        assertEquals("hello", result);
    }

    @Test
    void testStaticGetterPropertyAccess() {
        // V1 compatibility: Base64.encoder should work like Base64.getEncoder()
        assertEquals("aGVsbG8=", eval("var Base64 = Java.type('java.util.Base64'); Base64.encoder.encodeToString('hello'.getBytes())"));
        // Also test decoder - note: byte[] is converted to List by JS engine
        Object result = eval("var Base64 = Java.type('java.util.Base64'); Base64.decoder.decode('aGVsbG8=')");
        assertInstanceOf(List.class, result);
    }

    @Test
    void testJavaInteropArrayListMethodCall() {
        // This replicates the pattern used in V1's sort-array.js
        // Test with fully qualified name (like Properties test above)
        eval("var list = new java.util.ArrayList(); list.add('hello'); var size = list.size()");
        assertEquals(1, get("size"));
    }

    @Test
    void testJavaInteropArrayListViaJavaType() {
        // Test with Java.type pattern (this is what V1's sort-array.js uses)
        eval("var ArrayList = Java.type('java.util.ArrayList'); var list = new ArrayList(); list.add('hello'); var size = list.size()");
        assertEquals(1, get("size"));
    }

    @Test
    void testJavaInteropHashMapMethodCall() {
        // Test Java method calls on HashMap - ensures Map types also fall through to external bridge
        eval("var HashMap = Java.type('java.util.HashMap'); var map = new HashMap(); map.put('key', 'value'); var size = map.size()");
        assertEquals(1, get("size"));
    }

    @Test
    void testJavaInteropLinkedHashMapMethodCall() {
        // Test with LinkedHashMap to ensure the external bridge works for Java Map implementations
        eval("var map = new java.util.LinkedHashMap(); map.put('a', 1); map.put('b', 2); var size = map.size()");
        assertEquals(2, get("size"));
    }

    @Test
    void testImmutableListMethodCall() {
        // Test that List.of() (which returns ImmutableCollections$ListN) can have methods called
        // This is a regression test for module access restrictions on internal JDK classes
        engine = new Engine();
        engine.setExternalBridge(bridge);
        List<String> immutableList = List.of("a", "b", "c");
        engine.put("list", immutableList);
        // This should work - calling size() on an immutable list
        Object size = engine.eval("list.size()");
        assertEquals(3, size);
    }

    @Test
    void testImmutableListLengthProperty() {
        // Test that length property works on immutable lists (via JsArray wrapping)
        engine = new Engine();
        engine.setExternalBridge(bridge);
        List<String> immutableList = List.of("x", "y");
        engine.put("list", immutableList);
        // length should work via JsArray prototype
        Object length = engine.eval("list.length");
        assertEquals(2, length);
    }

    @Test
    void testEmptyImmutableListMethodCall() {
        // Test that List.of() with no args (empty immutable list) works
        engine = new Engine();
        engine.setExternalBridge(bridge);
        List<String> emptyList = List.of();
        engine.put("list", emptyList);
        Object size = engine.eval("list.size()");
        assertEquals(0, size);
    }

    @Test
    void testImmutableMapMethodCall() {
        // Test that Map.of() (which returns ImmutableCollections$MapN) can have methods called
        engine = new Engine();
        engine.setExternalBridge(bridge);
        Map<String, Object> immutableMap = Map.of("a", 1, "b", 2);
        engine.put("map", immutableMap);
        assertEquals(2, engine.eval("map.size()"));
        assertEquals(1, engine.eval("map.get('a')"));
        assertEquals(true, engine.eval("map.containsKey('b')"));
    }

    @Test
    void testEvalFunctionWithJavaType() {
        // Replicates what happens with call read('file.js') for a JS file containing Java.type
        // First, evaluate the function definition (like read() does)
        String jsFileContent = """
            function fn(array) {
              var ArrayList = Java.type('java.util.ArrayList');
              var list = new ArrayList();
              for (var i = 0; i < array.length; i++) {
                list.add(array[i]);
              }
              return list.size();
            }
            """;
        Object fn = eval(jsFileContent);
        assertNotNull(fn, "Function should be returned from eval");
        assertTrue(fn instanceof JsCallable, "Should be callable");

        // Now call the function with an argument
        JsCallable callable = (JsCallable) fn;
        Object result = callable.call(null, new Object[]{List.of("a", "b", "c")});
        assertEquals(3, result);
    }

    // =================================================================================================================
    // Java → JS Type Conversion Tests
    // =================================================================================================================

    @Test
    void testDateConversion() {
        engine = new Engine();
        engine.setExternalBridge(bridge);
        long millis = 1609459200000L; // 2021-01-01T00:00:00Z

        // java.util.Date should be usable as JS Date
        engine.put("javaDate", new Date(millis));
        assertEquals(millis, engine.eval("javaDate.getTime()"));
        assertEquals(2021, engine.eval("javaDate.getFullYear()"));
        assertEquals("object", engine.eval("typeof javaDate"));
    }

    @Test
    void testInstantConversion() {
        engine = new Engine();
        engine.setExternalBridge(bridge);
        long millis = 1609459200000L;

        // Instant should work as JS Date
        engine.put("instant", Instant.ofEpochMilli(millis));
        assertEquals(millis, engine.eval("instant.getTime()"));
        assertEquals(2021, engine.eval("instant.getFullYear()"));
    }

    @Test
    void testLocalDateTimeConversion() {
        engine = new Engine();
        engine.setExternalBridge(bridge);

        // LocalDateTime should work as JS Date
        LocalDateTime ldt = LocalDateTime.of(2025, 3, 15, 10, 30, 0);
        engine.put("localDT", ldt);
        assertEquals(2025, engine.eval("localDT.getFullYear()"));
        assertEquals(2, engine.eval("localDT.getMonth()"));  // 0-indexed
        assertEquals(15, engine.eval("localDT.getDate()"));
        assertEquals(10, engine.eval("localDT.getHours()"));
        assertEquals(30, engine.eval("localDT.getMinutes()"));
    }

    @Test
    void testLocalDateConversion() {
        engine = new Engine();
        engine.setExternalBridge(bridge);

        // LocalDate should work as JS Date (at start of day)
        LocalDate ld = LocalDate.of(2025, 6, 20);
        engine.put("localDate", ld);
        assertEquals(2025, engine.eval("localDate.getFullYear()"));
        assertEquals(5, engine.eval("localDate.getMonth()"));  // 0-indexed
        assertEquals(20, engine.eval("localDate.getDate()"));
        assertEquals(0, engine.eval("localDate.getHours()"));  // start of day
    }

    @Test
    void testZonedDateTimeConversion() {
        engine = new Engine();
        engine.setExternalBridge(bridge);

        // ZonedDateTime should work as JS Date
        ZonedDateTime zdt = ZonedDateTime.of(2025, 12, 25, 14, 30, 0, 0, ZoneId.of("UTC"));
        engine.put("zonedDT", zdt);
        // Note: getFullYear etc use local timezone, so we test getTime() which is UTC millis
        long expectedMillis = zdt.toInstant().toEpochMilli();
        assertEquals(expectedMillis, engine.eval("zonedDT.getTime()"));
    }

    @Test
    void testNestedMapConversion() {
        engine = new Engine();
        engine.setExternalBridge(bridge);

        // Nested map with Date should have Date converted
        Map<String, Object> inner = new HashMap<>();
        inner.put("created", new Date(1609459200000L));
        inner.put("name", "test");

        Map<String, Object> outer = new HashMap<>();
        outer.put("data", inner);
        outer.put("timestamp", new Date(1609459200000L));

        engine.put("obj", outer);
        assertEquals(1609459200000L, engine.eval("obj.timestamp.getTime()"));
        assertEquals(1609459200000L, engine.eval("obj.data.created.getTime()"));
        assertEquals("test", engine.eval("obj.data.name"));
    }

    @Test
    void testNestedListConversion() {
        engine = new Engine();
        engine.setExternalBridge(bridge);

        // List containing Dates should have Dates converted
        List<Object> list = List.of(
                new Date(1609459200000L),
                new Date(1609545600000L),
                "not a date"
        );

        engine.put("dates", list);
        assertEquals(1609459200000L, engine.eval("dates[0].getTime()"));
        assertEquals(1609545600000L, engine.eval("dates[1].getTime()"));
        assertEquals("not a date", engine.eval("dates[2]"));
    }

    @Test
    void testEvalWithMapConversion() {
        engine = new Engine();
        engine.setExternalBridge(bridge);

        // evalWith should also convert dates in the vars map
        Map<String, Object> vars = new HashMap<>();
        vars.put("startDate", new Date(1609459200000L));
        vars.put("endDate", Instant.ofEpochMilli(1609545600000L));

        Object result = engine.evalWith("startDate.getTime() + endDate.getTime()", vars);
        assertEquals(1609459200000L + 1609545600000L, ((Number) result).longValue());
    }

    @Test
    void testByteArrayConversion() {
        engine = new Engine();
        engine.setExternalBridge(bridge);

        // byte[] should be converted to JsUint8Array
        byte[] bytes = new byte[]{1, 2, 3, (byte) 255};
        engine.put("bytes", bytes);
        assertEquals(4, engine.eval("bytes.length"));
        assertEquals(1, engine.eval("bytes[0]"));
        assertEquals(255, engine.eval("bytes[3]"));  // unsigned
    }

    @Test
    void testJavaArrayConvertedToJsList() {
        engine = new Engine();
        engine.setExternalBridge(bridge);

        // Java array should be converted to a JS-compatible list
        String[] items = {"a", "b", "c"};
        engine.put("items", items);

        // Basic access should work
        assertEquals("a", engine.eval("items[0]"));
        assertEquals(3, engine.eval("items.length"));

        // JS array methods should work after conversion
        Object mapped = engine.eval("items.map(x => x.toUpperCase())");
        assertEquals(List.of("A", "B", "C"), mapped);

        Object filtered = engine.eval("items.filter(x => x !== 'b')");
        assertEquals(List.of("a", "c"), filtered);

        Object joined = engine.eval("items.join('-')");
        assertEquals("a-b-c", joined);
    }

    @Test
    void testJavaIntArrayConvertedToJsList() {
        engine = new Engine();
        engine.setExternalBridge(bridge);

        // Primitive int array
        int[] numbers = {1, 2, 3, 4, 5};
        engine.put("numbers", numbers);

        assertEquals(1, engine.eval("numbers[0]"));
        assertEquals(5, engine.eval("numbers.length"));

        // JS array methods
        Object sum = engine.eval("numbers.reduce((a, b) => a + b, 0)");
        assertEquals(15, sum);

        Object doubled = engine.eval("numbers.map(x => x * 2)");
        assertEquals(List.of(2, 4, 6, 8, 10), doubled);
    }

    @Test
    void testJavaObjectArrayConvertedToJsList() {
        engine = new Engine();
        engine.setExternalBridge(bridge);

        // Object array with mixed types
        Object[] mixed = {"hello", 42, true};
        engine.put("mixed", mixed);

        assertEquals("hello", engine.eval("mixed[0]"));
        assertEquals(42, engine.eval("mixed[1]"));
        assertEquals(true, engine.eval("mixed[2]"));
        assertEquals(3, engine.eval("mixed.length"));

        // forEach should work
        engine.eval("var result = []; mixed.forEach(x => result.push(x))");
        assertEquals(List.of("hello", 42, true), engine.get("result"));
    }

    @Test
    void testJavaListBracketAccess() {
        engine = new Engine();
        engine.setExternalBridge(bridge);

        // This replicates the mock multipart access pattern: requestParts['myFile'][0]
        Map<String, Object> filePart = new HashMap<>();
        filePart.put("name", "myFile");
        filePart.put("filename", "test.xlsx");
        filePart.put("value", new byte[]{1, 2, 3});

        List<Map<String, Object>> partsList = List.of(filePart);
        Map<String, List<Map<String, Object>>> requestParts = new HashMap<>();
        requestParts.put("myFile", partsList);

        engine.put("requestParts", requestParts);

        // This should work: access list element via bracket notation
        Object result = engine.eval("requestParts['myFile'][0]");
        assertNotNull(result);
        assertInstanceOf(Map.class, result);
        assertEquals("myFile", ((Map<?, ?>) result).get("name"));
    }

    @Test
    void testXmlDocumentPassthrough() {
        engine = new Engine();
        engine.setExternalBridge(bridge);

        // Create an XML document and verify it passes through JS correctly
        org.w3c.dom.Document doc = io.karatelabs.common.Xml.toXmlDoc("<root><foo>bar</foo></root>");
        engine.put("doc", doc);

        // Verify typeof
        assertEquals("object", engine.eval("typeof doc"));

        // Verify it can be passed to a function and received as a Node
        JsInvokable testFn = args -> args[0] instanceof org.w3c.dom.Node ? "success" : "not a node";
        engine.put("testFn", testFn);
        assertEquals("success", engine.eval("testFn(doc)"));
    }

    // =================================================================================================================
    // Interface Static Fields Tests
    // =================================================================================================================

    @Test
    void testInterfaceStaticFieldAccess() {
        // Test that static fields on interfaces can be accessed (like Keys.ENTER)
        assertEquals("foo-value", eval("var DemoInterface = Java.type('io.karatelabs.js.DemoInterface'); DemoInterface.FOO"));
        assertEquals("bar-value", eval("var DemoInterface = Java.type('io.karatelabs.js.DemoInterface'); DemoInterface.BAR"));
    }

    @Test
    void testInterfaceStaticFieldUnicode() {
        // Test unicode values like Key.ENTER = "\uE007"
        assertEquals("\uE007", eval("var DemoInterface = Java.type('io.karatelabs.js.DemoInterface'); DemoInterface.ENTER"));
        assertEquals("\uE004", eval("var DemoInterface = Java.type('io.karatelabs.js.DemoInterface'); DemoInterface.TAB"));
        assertEquals("\uE00C", eval("var DemoInterface = Java.type('io.karatelabs.js.DemoInterface'); DemoInterface.ESCAPE"));
    }

    @Test
    void testInterfaceStaticFieldWithPut() {
        // Test when interface is put as JavaType wrapper (like engine.put("Key", new JavaType(Keys.class)))
        engine = new Engine();
        engine.setExternalBridge(bridge);
        engine.put("DemoInterface", new JavaType(DemoInterface.class));
        assertEquals("foo-value", engine.eval("DemoInterface.FOO"));
        assertEquals("bar-value", engine.eval("DemoInterface.BAR"));
        assertEquals("\uE007", engine.eval("DemoInterface.ENTER"));
    }

    @Test
    void testXmlPathViaSimpleObject() {
        engine = new Engine();
        engine.setExternalBridge(bridge);

        // Create a SimpleObject with xmlPath method (similar to karate object)
        SimpleObject myKarate = name -> {
            if ("xmlPath".equals(name)) {
                return (JsInvokable) args -> {
                    org.w3c.dom.Node node = (org.w3c.dom.Node) args[0];
                    String path = args[1].toString();
                    org.w3c.dom.NodeList nodeList = io.karatelabs.common.Xml.getNodeListByPath(node, path);
                    return nodeList.getLength() > 0 ? nodeList.item(0).getTextContent() : null;
                };
            }
            return null;
        };

        org.w3c.dom.Document doc = io.karatelabs.common.Xml.toXmlDoc("<root><foo>bar</foo></root>");
        engine.put("doc", doc);
        engine.put("myKarate", myKarate);

        assertEquals("bar", engine.eval("myKarate.xmlPath(doc, '/root/foo')"));
    }

    // =================================================================================================================
    // Undefined → Null Conversion at JS/Java Boundary Tests
    // =================================================================================================================

    @Test
    void testUndefinedPropertyPassedToJavaMethodBecomesNull() {
        // Simulates: var user = { name: 'John' }; pojo.echoValue(user.version)
        // user.version is undefined, should become null when passed to Java
        engine = new Engine();
        engine.setExternalBridge(bridge);
        engine.eval("""
                var DemoPojo = Java.type('io.karatelabs.js.DemoPojo');
                var pojo = new DemoPojo();
                var user = { name: 'John' };
                var result = pojo.describeValue(user.version);
                """);
        // user.version is undefined, should be converted to null at Java boundary
        assertEquals("null", engine.get("result"));
    }

    @Test
    void testExplicitUndefinedPassedToJavaMethodBecomesNull() {
        // Simulates: pojo.echoValue(undefined)
        engine = new Engine();
        engine.setExternalBridge(bridge);
        engine.eval("""
                var DemoPojo = Java.type('io.karatelabs.js.DemoPojo');
                var pojo = new DemoPojo();
                var result = pojo.describeValue(undefined);
                """);
        assertEquals("null", engine.get("result"));
    }

    @Test
    void testUndefinedFromMapMissingKeyPassedToJava() {
        // Simulates the exact DynamoDB scenario:
        // var user = utils.getUserById(userId); // Returns Map from DynamoDB
        // utils.updateUser(user.version);  // version field doesn't exist
        engine = new Engine();
        engine.setExternalBridge(bridge);
        Map<String, Object> user = new HashMap<>();
        user.put("userId", "123");
        user.put("name", "John");
        // Note: 'version' key is intentionally missing
        engine.put("user", user);
        engine.eval("""
                var DemoPojo = Java.type('io.karatelabs.js.DemoPojo');
                var pojo = new DemoPojo();
                var result = pojo.describeValue(user.version);
                """);
        assertEquals("null", engine.get("result"));
    }

    @Test
    void testNullPassedToJavaMethodRemainsNull() {
        // Explicit null should still work
        engine = new Engine();
        engine.setExternalBridge(bridge);
        engine.eval("""
                var DemoPojo = Java.type('io.karatelabs.js.DemoPojo');
                var pojo = new DemoPojo();
                var result = pojo.describeValue(null);
                """);
        assertEquals("null", engine.get("result"));
    }

    @Test
    void testDefinedValuePassedToJavaMethodWorks() {
        // Sanity check: defined values should pass through normally
        engine = new Engine();
        engine.setExternalBridge(bridge);
        engine.eval("""
                var DemoPojo = Java.type('io.karatelabs.js.DemoPojo');
                var pojo = new DemoPojo();
                var user = { name: 'John', version: 42 };
                var result = pojo.echoValue(user.version);
                """);
        assertEquals(42, engine.get("result"));
    }

    // =================================================================================================================
    // JavaMirror → Java Conversion at JS/Java Boundary Tests
    // These tests verify that JsDate, JsUint8Array, etc. are unwrapped to their Java equivalents
    // =================================================================================================================

    @Test
    void testJsDatePassedToJavaMethodBecomesJavaDate() {
        // new Date() returns JsDate (a JavaMirror), should be unwrapped to java.util.Date
        engine = new Engine();
        engine.setExternalBridge(bridge);
        engine.eval("""
                var DemoPojo = Java.type('io.karatelabs.js.DemoPojo');
                var pojo = new DemoPojo();
                var d = new Date(1609459200000);
                var result = pojo.describeType(d);
                """);
        // JsDate implements JavaMirror, should be unwrapped to java.util.Date
        assertEquals("java.util.Date", engine.get("result"));
    }

    @Test
    void testJsDateFromExpressionPassedToJavaMethod() {
        // Date stored in object property, then passed to Java
        engine = new Engine();
        engine.setExternalBridge(bridge);
        engine.eval("""
                var DemoPojo = Java.type('io.karatelabs.js.DemoPojo');
                var pojo = new DemoPojo();
                var obj = { created: new Date(1609459200000) };
                var result = pojo.describeType(obj.created);
                """);
        assertEquals("java.util.Date", engine.get("result"));
    }

    @Test
    void testJsDatePassedToTypedJavaMethod() {
        // Java method with Date parameter type should receive java.util.Date
        engine = new Engine();
        engine.setExternalBridge(bridge);
        engine.eval("""
                var DemoPojo = Java.type('io.karatelabs.js.DemoPojo');
                var pojo = new DemoPojo();
                var d = new Date(1609459200000);
                var result = pojo.getDateMillis(d);
                """);
        assertEquals(1609459200000L, engine.get("result"));
    }

    @Test
    void testJsDateFromArrayPassedToJavaMethod() {
        // Date stored in array, then passed to Java
        engine = new Engine();
        engine.setExternalBridge(bridge);
        engine.eval("""
                var DemoPojo = Java.type('io.karatelabs.js.DemoPojo');
                var pojo = new DemoPojo();
                var arr = [new Date(1609459200000), new Date(1609545600000)];
                var result = pojo.describeType(arr[0]);
                """);
        assertEquals("java.util.Date", engine.get("result"));
    }

    @Test
    void testJsDateFromFunctionReturnPassedToJavaMethod() {
        // Date returned from a function, then passed to Java
        engine = new Engine();
        engine.setExternalBridge(bridge);
        engine.eval("""
                var DemoPojo = Java.type('io.karatelabs.js.DemoPojo');
                var pojo = new DemoPojo();
                function getDate() { return new Date(1609459200000); }
                var result = pojo.describeType(getDate());
                """);
        assertEquals("java.util.Date", engine.get("result"));
    }

    @Test
    void testJsDateFromTernaryExpressionPassedToJavaMethod() {
        // Date from conditional expression
        engine = new Engine();
        engine.setExternalBridge(bridge);
        engine.eval("""
                var DemoPojo = Java.type('io.karatelabs.js.DemoPojo');
                var pojo = new DemoPojo();
                var flag = true;
                var d = flag ? new Date(1609459200000) : null;
                var result = pojo.describeType(d);
                """);
        assertEquals("java.util.Date", engine.get("result"));
    }

    @Test
    void testJsUint8ArrayPassedToJavaMethodBecomesByteArray() {
        // Uint8Array (JsUint8Array is a JavaMirror) should be unwrapped to byte[]
        engine = new Engine();
        engine.setExternalBridge(bridge);
        engine.eval("""
                var DemoPojo = Java.type('io.karatelabs.js.DemoPojo');
                var pojo = new DemoPojo();
                var bytes = new Uint8Array([1, 2, 3, 255]);
                var result = pojo.describeType(bytes);
                """);
        // JsUint8Array should be unwrapped to byte[]
        assertEquals("[B", engine.get("result")); // [B is the JVM name for byte[]
    }

    @Test
    void testJsUint8ArrayPassedToTypedJavaMethod() {
        // Java method with byte[] parameter should receive byte[]
        engine = new Engine();
        engine.setExternalBridge(bridge);
        engine.eval("""
                var DemoPojo = Java.type('io.karatelabs.js.DemoPojo');
                var pojo = new DemoPojo();
                var bytes = new Uint8Array([1, 2, 3, 255]);
                var result = pojo.getByteArrayLength(bytes);
                """);
        assertEquals(4, engine.get("result"));
    }

    @Test
    void testJsUint8ArrayFromPropertyPassedToJavaMethod() {
        // Uint8Array stored in object property
        engine = new Engine();
        engine.setExternalBridge(bridge);
        engine.eval("""
                var DemoPojo = Java.type('io.karatelabs.js.DemoPojo');
                var pojo = new DemoPojo();
                var obj = { data: new Uint8Array([10, 20, 30]) };
                var result = pojo.getByteArrayLength(obj.data);
                """);
        assertEquals(3, engine.get("result"));
    }

    @Test
    void testMultipleJavaMirrorArgsPassedToJavaMethod() {
        // Multiple JavaMirror args in one call
        engine = new Engine();
        engine.setExternalBridge(bridge);
        // Use a SimpleObject to capture args
        java.util.List<Object> capturedArgs = new java.util.ArrayList<>();
        SimpleObject utils = name -> {
            if ("capture".equals(name)) {
                return (JavaCallable) (context, args) -> {
                    for (Object arg : args) {
                        capturedArgs.add(arg);
                    }
                    return null;
                };
            }
            return null;
        };
        engine.put("utils", utils);
        engine.eval("""
                var d = new Date(1609459200000);
                var bytes = new Uint8Array([1, 2, 3]);
                utils.capture(d, bytes, undefined);
                """);
        assertEquals(3, capturedArgs.size());
        // JsDate should be unwrapped to java.util.Date
        assertInstanceOf(Date.class, capturedArgs.get(0), "JsDate should be unwrapped to Date");
        // JsUint8Array should be unwrapped to byte[]
        assertInstanceOf(byte[].class, capturedArgs.get(1), "JsUint8Array should be unwrapped to byte[]");
        // undefined should be null
        assertNull(capturedArgs.get(2), "undefined should be null");
    }

    @Test
    void testDateNowPassedToJavaMethod() {
        // Date.now() returns a number, not a Date - this should work already
        engine = new Engine();
        engine.setExternalBridge(bridge);
        engine.eval("""
                var DemoPojo = Java.type('io.karatelabs.js.DemoPojo');
                var pojo = new DemoPojo();
                var result = pojo.describeType(Date.now());
                """);
        // Date.now() returns a long/number, not JsDate
        assertEquals("java.lang.Long", engine.get("result"));
    }

    // =================================================================================================================
    // JS Function Passed to Java Code Tests
    // These tests verify that JS functions can be received by Java code expecting JavaCallable
    // =================================================================================================================

    @Test
    void testJsFunctionPassedToJavaMethodAsCallable() {
        // Simulates: proc.waitForOutput(function(line) { return line.indexOf('ready') >= 0 })
        // Java code checks: if (args[0] instanceof JavaCallable)
        engine = new Engine();
        engine.setExternalBridge(bridge);
        java.util.List<Object> capturedResults = new java.util.ArrayList<>();
        SimpleObject utils = name -> {
            if ("forEach".equals(name)) {
                return (JavaCallable) (context, args) -> {
                    if (args.length < 2 || !(args[1] instanceof JavaCallable)) {
                        throw new RuntimeException("forEach requires a function argument");
                    }
                    List<?> list = (List<?>) args[0];
                    JavaCallable fn = (JavaCallable) args[1];
                    for (Object item : list) {
                        Object result = fn.call(context, new Object[]{item});
                        capturedResults.add(result);
                    }
                    return null;
                };
            }
            return null;
        };
        engine.put("utils", utils);
        engine.eval("""
                var items = ['a', 'b', 'c'];
                utils.forEach(items, function(x) { return x.toUpperCase() });
                """);
        assertEquals(List.of("A", "B", "C"), capturedResults);
    }

    @Test
    void testJsArrowFunctionPassedToJavaMethodAsCallable() {
        // Arrow function syntax
        engine = new Engine();
        engine.setExternalBridge(bridge);
        java.util.List<Object> capturedResults = new java.util.ArrayList<>();
        SimpleObject utils = name -> {
            if ("map".equals(name)) {
                return (JavaCallable) (context, args) -> {
                    if (args.length < 2 || !(args[1] instanceof JavaCallable)) {
                        throw new RuntimeException("map requires a function argument");
                    }
                    List<?> list = (List<?>) args[0];
                    JavaCallable fn = (JavaCallable) args[1];
                    java.util.List<Object> result = new java.util.ArrayList<>();
                    for (Object item : list) {
                        result.add(fn.call(context, new Object[]{item}));
                    }
                    return result;
                };
            }
            return null;
        };
        engine.put("utils", utils);
        Object result = engine.eval("""
                var items = [1, 2, 3];
                utils.map(items, x => x * 2);
                """);
        assertEquals(List.of(2, 4, 6), result);
    }

    @Test
    void testJsFunctionFromVariablePassedToJavaMethod() {
        // Function stored in variable, then passed
        engine = new Engine();
        engine.setExternalBridge(bridge);
        final Object[] capturedFn = new Object[1];
        SimpleObject utils = name -> {
            if ("captureCallable".equals(name)) {
                return (JavaCallable) (context, args) -> {
                    capturedFn[0] = args[0];
                    if (args[0] instanceof JavaCallable jc) {
                        return jc.call(context, new Object[]{"test"});
                    }
                    throw new RuntimeException("Not a JavaCallable: " + args[0].getClass().getName());
                };
            }
            return null;
        };
        engine.put("utils", utils);
        Object result = engine.eval("""
                var fn = function(x) { return 'received: ' + x };
                utils.captureCallable(fn);
                """);
        assertEquals("received: test", result);
        assertInstanceOf(JavaCallable.class, capturedFn[0], "JS function should be instanceof JavaCallable");
    }

}

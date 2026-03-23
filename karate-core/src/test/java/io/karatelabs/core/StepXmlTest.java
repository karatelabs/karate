/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.core;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class StepXmlTest {

    @Test
    void testXmlKeyword() {
        ScenarioRuntime sr = run("""
            * def xmlStr = '<root><foo>bar</foo></root>'
            * xml doc = xmlStr
            """);
        assertPassed(sr);
        Object doc = get(sr, "doc");
        assertInstanceOf(Node.class, doc);
    }

    @Test
    void testXmlStringKeyword() {
        ScenarioRuntime sr = run("""
            * def xmlStr = '<root><foo>bar</foo></root>'
            * xml doc = xmlStr
            * xmlstring result = doc
            """);
        assertPassed(sr);
        Object result = get(sr, "result");
        assertInstanceOf(String.class, result);
        assertTrue(result.toString().contains("<root>"));
        assertTrue(result.toString().contains("<foo>bar</foo>"));
    }

    @Test
    void testKarateXmlPath() {
        ScenarioRuntime sr = run("""
            * def xmlStr = '<root><foo>bar</foo></root>'
            * xml doc = xmlStr
            * def result = karate.xmlPath(doc, '/root/foo')
            """);
        assertPassed(sr);
        assertEquals("bar", get(sr, "result"));
    }

    @Test
    void testKarateXmlPathCount() {
        ScenarioRuntime sr = run("""
            * def xmlStr = '<root><item>1</item><item>2</item><item>3</item></root>'
            * xml doc = xmlStr
            * def count = karate.xmlPath(doc, 'count(/root/item)')
            """);
        assertPassed(sr);
        assertEquals(3, get(sr, "count"));
    }

    @Test
    void testKarateXmlPathMultiple() {
        ScenarioRuntime sr = run("""
            * def xmlStr = '<root><item>a</item><item>b</item></root>'
            * xml doc = xmlStr
            * def items = karate.xmlPath(doc, '/root/item')
            """);
        assertPassed(sr);
        Object items = get(sr, "items");
        assertInstanceOf(java.util.List.class, items);
        @SuppressWarnings("unchecked")
        java.util.List<Object> list = (java.util.List<Object>) items;
        assertEquals(2, list.size());
    }

    @Test
    void testKarateSetXml() {
        ScenarioRuntime sr = run("""
            * karate.setXml('doc', '<root><foo/></root>')
            * def value = karate.xmlPath(doc, '/root/foo')
            """);
        assertPassed(sr);
        Object doc = get(sr, "doc");
        assertInstanceOf(Node.class, doc);
        assertEquals("", get(sr, "value")); // empty element returns empty string
    }

    @Test
    void testKaratePrettyXml() {
        ScenarioRuntime sr = run("""
            * def xmlStr = '<root><foo>bar</foo></root>'
            * xml doc = xmlStr
            * def pretty = karate.prettyXml(doc)
            """);
        assertPassed(sr);
        String pretty = (String) get(sr, "pretty");
        assertTrue(pretty.contains("<root>"));
        assertTrue(pretty.contains("  <foo>bar</foo>")); // indented
    }

    @Test
    void testXmlWithDocString() {
        ScenarioRuntime sr = run("""
            * text xmlStr =
              \"\"\"
              <root>
                <name>John</name>
                <age>30</age>
              </root>
              \"\"\"
            * xml doc = xmlStr
            * def name = karate.xmlPath(doc, '/root/name')
            * def age = karate.xmlPath(doc, '/root/age')
            """);
        assertPassed(sr);
        assertEquals("John", get(sr, "name"));
        assertEquals("30", get(sr, "age"));
    }

    @Test
    void testXmlAttribute() {
        ScenarioRuntime sr = run("""
            * def xmlStr = '<root id="123"><foo bar="baz">text</foo></root>'
            * xml doc = xmlStr
            * def id = karate.xmlPath(doc, '/root/@id')
            * def bar = karate.xmlPath(doc, '/root/foo/@bar')
            """);
        assertPassed(sr);
        assertEquals("123", get(sr, "id"));
        assertEquals("baz", get(sr, "bar"));
    }

    @Test
    void testXmlPathOnString() {
        ScenarioRuntime sr = run("""
            * def result = karate.xmlPath('<root><foo>bar</foo></root>', '/root/foo')
            """);
        assertPassed(sr);
        assertEquals("bar", get(sr, "result"));
    }

    @Test
    void testXmlNotPresent() {
        ScenarioRuntime sr = run("""
            * def xmlStr = '<root><foo>bar</foo></root>'
            * xml doc = xmlStr
            * def nope = karate.xmlPath(doc, '/root/nope')
            """);
        assertPassed(sr);
        assertNull(get(sr, "nope"));
    }

    @Test
    void testXmlNestedElement() {
        ScenarioRuntime sr = run("""
            * def xmlStr = '<root><parent><child>value</child></parent></root>'
            * xml doc = xmlStr
            * def parentNode = karate.xmlPath(doc, '/root/parent')
            """);
        assertPassed(sr);
        Object parentNode = get(sr, "parentNode");
        // Parent has child elements, so returns as XML node
        assertInstanceOf(Node.class, parentNode);
    }

    @Test
    void testKaratePretty() {
        // karate.pretty() should also handle XML
        ScenarioRuntime sr = run("""
            * def xmlStr = '<root><foo>bar</foo></root>'
            * xml doc = xmlStr
            * def pretty = karate.pretty(doc)
            """);
        assertPassed(sr);
        String pretty = (String) get(sr, "pretty");
        assertTrue(pretty.contains("<root>"));
    }

    @Test
    void testXmlLiteral() {
        // XML literal - direct XML in def without quotes
        ScenarioRuntime sr = run("""
            * def cat = <cat><name>Billie</name><age>5</age></cat>
            * def name = karate.xmlPath(cat, '/cat/name')
            * def age = karate.xmlPath(cat, '/cat/age')
            """);
        assertPassed(sr);
        Object cat = get(sr, "cat");
        assertInstanceOf(Node.class, cat);
        assertEquals("Billie", get(sr, "name"));
        assertEquals("5", get(sr, "age"));
    }

    @Test
    void testXmlLiteralWithAttributes() {
        ScenarioRuntime sr = run("""
            * def item = <item id="123" status="active">content</item>
            * def id = karate.xmlPath(item, '/item/@id')
            * def status = karate.xmlPath(item, '/item/@status')
            """);
        assertPassed(sr);
        assertEquals("123", get(sr, "id"));
        assertEquals("active", get(sr, "status"));
    }

    @Test
    void testSetXmlPath() {
        // set keyword with XPath to modify XML
        ScenarioRuntime sr = run("""
            * def cat = <cat><name>Billie</name></cat>
            * set cat /cat/name = 'Jean'
            * def name = karate.xmlPath(cat, '/cat/name')
            """);
        assertPassed(sr);
        assertEquals("Jean", get(sr, "name"));
    }

    @Test
    void testSetXmlPathAddElement() {
        ScenarioRuntime sr = run("""
            * def cat = <cat><name>Billie</name></cat>
            * set cat /cat/age = '5'
            * def age = karate.xmlPath(cat, '/cat/age')
            """);
        assertPassed(sr);
        assertEquals("5", get(sr, "age"));
    }

    @Test
    void testMatchXmlLiteral() {
        // Match XML variable against XML literal
        ScenarioRuntime sr = run("""
            * def cat = <cat><name>Billie</name></cat>
            * match cat / == <cat><name>Billie</name></cat>
            """);
        assertPassed(sr);
    }

    @Test
    void testMatchXmlAfterSet() {
        ScenarioRuntime sr = run("""
            * def cat = <cat><name>Billie</name></cat>
            * set cat /cat/name = 'Jean'
            * match cat / == <cat><name>Jean</name></cat>
            """);
        assertPassed(sr);
    }

    @Test
    void testXmlEmbeddedExpression() {
        // Embedded expression in XML text content
        ScenarioRuntime sr = run("""
            * def name = 'Billie'
            * def cat = <cat><name>#(name)</name></cat>
            * def result = karate.xmlPath(cat, '/cat/name')
            """);
        assertPassed(sr);
        assertEquals("Billie", get(sr, "result"));
    }

    @Test
    void testXmlEmbeddedExpressionInAttribute() {
        // Embedded expression in XML attribute
        ScenarioRuntime sr = run("""
            * def id = '123'
            * def item = <item id="#(id)">content</item>
            * def result = karate.xmlPath(item, '/item/@id')
            """);
        assertPassed(sr);
        assertEquals("123", get(sr, "result"));
    }

    @Test
    void testXmlOptionalEmbeddedExpression() {
        // Optional embedded expression - null value removes element
        ScenarioRuntime sr = run("""
            * def optional = null
            * def cat = <cat><name>Billie</name><age>##(optional)</age></cat>
            * def age = karate.xmlPath(cat, '/cat/age')
            """);
        assertPassed(sr);
        // age element should be removed when optional is null
        assertNull(get(sr, "age"));
    }

    @Test
    void testSetXmlWithTable() {
        // set with table - auto-build XML from XPath paths
        ScenarioRuntime sr = run("""
            * set search /acc:getAccountByPhoneNumber
              | path                        | value |
              | acc:phoneNumber             | 1234  |
              | acc:phoneNumberSearchOption | 'all' |
            * match search ==
              \"\"\"
              <acc:getAccountByPhoneNumber>
                  <acc:phoneNumber>1234</acc:phoneNumber>
                  <acc:phoneNumberSearchOption>all</acc:phoneNumberSearchOption>
              </acc:getAccountByPhoneNumber>
              \"\"\"
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateSetXmlPath() {
        // karate.set() with XPath
        ScenarioRuntime sr = run("""
            * karate.setXml('temp', '<query/>')
            * karate.set('temp', '/query/name/firstName', 'John')
            * karate.set('temp', '/query/name/lastName', 'Smith')
            * karate.set('temp', '/query/age', 20)
            * match temp == <query><name><firstName>John</firstName><lastName>Smith</lastName></name><age>20</age></query>
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateRemoveXmlPath() {
        // karate.remove() with XPath
        ScenarioRuntime sr = run("""
            * def base = <query><name>foo</name></query>
            * karate.remove('base', '/query/name')
            * match base == <query/>
            """);
        assertPassed(sr);
    }

    @Test
    void testXmlEmbeddedExpressions() {
        // Test embedded expressions in XML literals
        ScenarioRuntime sr = run("""
            * def phoneNumber = '123456'
            * def search = { wireless: true, voip: false }
            * def xml =
              \"\"\"
              <root>
                  <phone>#(phoneNumber)</phone>
                  <flag>#(search.wireless)</flag>
              </root>
              \"\"\"
            * match xml /root/phone == '123456'
            * match xml /root/flag == 'true'
            """);
        assertPassed(sr);
    }

    @Test
    void testReadXmlWithEmbeddedExpressions() throws Exception {
        // Create temp XML file with embedded expressions
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("karate-test");
        java.nio.file.Path xmlFile = tempDir.resolve("test.xml");
        java.nio.file.Files.writeString(xmlFile, """
            <root>
                <phone>#(phoneNumber)</phone>
                <flag>#(search.wireless)</flag>
            </root>
            """);
        try {
            ScenarioRuntime sr = runFromDir(tempDir, """
                * def phoneNumber = '123456'
                * def search = { wireless: true, voip: false }
                * def xml = read('test.xml')
                * match xml /root/phone == '123456'
                * match xml /root/flag == 'true'
                * xmlstring xmlStr = xml
                * match xmlStr contains '<phone>123456</phone>'
                """);
            assertPassed(sr);
        } finally {
            java.nio.file.Files.deleteIfExists(xmlFile);
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void testSetXmlWithMultiColumnIndexedTable() {
        ScenarioRuntime sr = run("""
            * set search /queries/query
                | path           | 1        | 2      | 3       |
                | name/firstName | 'John'   | 'Jane' |         |
                | name/lastName  | 'Smith'  | 'Doe'  | 'Waldo' |
                | age            | 20       |        |         |
            * match search/queries/query[1] == <query><name><firstName>John</firstName><lastName>Smith</lastName></name><age>20</age></query>
            * match search/queries/query[2] == <query><name><firstName>Jane</firstName><lastName>Doe</lastName></name></query>
            * match search/queries/query[3] == <query><name><lastName>Waldo</lastName></name></query>
            """);
        assertPassed(sr);
    }

    @Test
    void testCdataWithXmlEmbeddedExpression() {
        ScenarioRuntime sr = run("""
            * def foo = <bar>baz</bar>
            * def xml =
            \"\"\"
            <ResponseSet vers="1.0">
                <Response><![CDATA[#(foo)]]></Response>
            </ResponseSet>
            \"\"\"
            * match xml ==
            \"\"\"
            <ResponseSet vers="1.0">
                <Response><![CDATA[<bar>baz</bar>]]></Response>
            </ResponseSet>
            \"\"\"
            """);
        assertPassed(sr);
    }

    @Test
    void testJsonKeywordWithXmlLiteral() {
        ScenarioRuntime sr = run("""
            * json response = <response><foo><bar>baz</bar></foo></response>
            * match response.response.foo.bar == 'baz'
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateLog() {
        ScenarioRuntime sr = run("""
            * def xml = <root><foo>bar</foo></root>
            * karate.log(xml)
            * karate.log('test message', { key: 'value' })
            """);
        assertPassed(sr);
    }

    @Test
    void testBareXpathOnResponse() {
        // Bare XPath starting with // should use 'response' variable implicitly
        ScenarioRuntime sr = run("""
            * def response = <teachers><teacher department="science"><subject>math</subject></teacher></teachers>
            * def teacher = //teacher[@department='science']
            * match teacher == <teacher department="science"><subject>math</subject></teacher>
            """);
        assertPassed(sr);
    }

    @Test
    void testBareXpathOnResponseWithDocstring() {
        // Bare XPath with response set via docstring
        ScenarioRuntime sr = run("""
            * def response =
              \"\"\"
              <teachers>
                <teacher department="science">
                  <subject>math</subject>
                </teacher>
              </teachers>
              \"\"\"
            * def teacher = //teacher[@department='science']
            * match teacher == <teacher department="science"><subject>math</subject></teacher>
            """);
        assertPassed(sr);
    }

    @Test
    void testGetXmlXpath() {
        // get keyword with XML and XPath
        ScenarioRuntime sr = run("""
            * def xml =
              \"\"\"
              <root>
                <hello><there>everyone</there></hello>
                <hello><there>anyone</there></hello>
              </root>
              \"\"\"
            * def nodes = get xml //hello
            * match nodes[0] == <hello><there>everyone</there></hello>
            * match nodes[1] == <hello><there>anyone</there></hello>
            """);
        assertPassed(sr);
    }

    @Test
    void testGetIndexedXmlXpath() {
        // get[N] keyword with XML and XPath - returns text content for simple elements
        ScenarioRuntime sr = run("""
            * def xml =
              \"\"\"
              <root>
                <item>first</item>
                <item>second</item>
                <item>third</item>
              </root>
              \"\"\"
            * def second = get[1] xml //item
            * match second == 'second'
            """);
        assertPassed(sr);
    }

    @Test
    void testGetIndexedXmlXpathWithChildren() {
        // get[N] with nested elements returns XML node
        ScenarioRuntime sr = run("""
            * def xml =
              \"\"\"
              <root>
                <item><name>first</name></item>
                <item><name>second</name></item>
              </root>
              \"\"\"
            * def second = get[1] xml //item
            * match second == <item><name>second</name></item>
            """);
        assertPassed(sr);
    }

    @Test
    void testXmlJsonStyleAccess() {
        // XML nodes can be accessed via JS dot notation like JSON
        ScenarioRuntime sr = run("""
            * def foo =
              \"\"\"
              <records>
                <record index="1">a</record>
                <record index="2">b</record>
                <record index="3" foo="bar">c</record>
              </records>
              \"\"\"
            * assert foo.records.record.length == 3
            * match foo.records.record[0] == { _: 'a', '@': { index: '1' } }
            * match foo.records.record[1] == { _: 'b', '@': { index: '2' } }
            * match foo.records.record[2] == { _: 'c', '@': { index: '3', foo: 'bar' } }
            """);
        assertPassed(sr);
    }

    @Test
    void testXmlJsonStyleAccessNested() {
        // Nested XML elements as JSON
        ScenarioRuntime sr = run("""
            * def xml =
              \"\"\"
              <root>
                <user>
                  <name>John</name>
                  <age>30</age>
                </user>
              </root>
              \"\"\"
            * assert xml.root.user.name == 'John'
            * assert xml.root.user.age == '30'
            """);
        assertPassed(sr);
    }

    @Test
    void testMatchXpathFunction() {
        // XPath functions like count() in match expressions
        ScenarioRuntime sr = run("""
            * def foo =
              \"\"\"
              <records>
                <record index="1">a</record>
                <record index="2">b</record>
                <record index="3" foo="bar">c</record>
              </records>
              \"\"\"
            * match foo count(/records//record) == 3
            * match foo //record[@index=2] == 'b'
            * match foo //record[@foo='bar'] == 'c'
            """);
        assertPassed(sr);
    }

    @Test
    void testXmlToStringViaJs() {
        // V1 behavior: string strVar = (xmlVar) should convert XML to its string representation
        // The parentheses forces JS evaluation, and string keyword converts result
        ScenarioRuntime sr = run("""
            * def xmlVar = <root><foo>bar</foo></root>
            * string strVar = (xmlVar)
            * match strVar == '<root><foo>bar</foo></root>'
            """);
        assertPassed(sr);
    }

    @Test
    void testXmlToJsonWithAttributes() {
        // XML with attributes converts to JSON with special keys: @ for attributes, _ for text content
        ScenarioRuntime sr = run("""
            * def xmlVar = <root><foo fizz="buzz">bar</foo></root>
            * json jsonVar = xmlVar
            * match jsonVar == { root: { foo: { _: 'bar', @: { fizz: 'buzz' }}}}
            * match jsonVar $.root.foo._ == 'bar'
            * match jsonVar $.root.foo.@ == { fizz: 'buzz' }
            * match jsonVar $.root.foo.@.fizz == 'buzz'
            """);
        assertPassed(sr);
    }

    @Test
    void testXmlToJsonWithJsonPathDeep() {
        // JSONPath with recursive descent on XML-to-JSON with @ keys
        ScenarioRuntime sr = run("""
            * def xmlVar = <root><foo fizz="buzz">bar</foo></root>
            * json jsonVar = xmlVar
            * match jsonVar $..foo.@.fizz == ['buzz']
            * match jsonVar $..@.fizz contains 'buzz'
            * match jsonVar $..foo.@ contains { fizz: 'buzz' }
            """);
        assertPassed(sr);
    }

    @Test
    void testMatchBareXpathOnResponse() {
        // V1 SOAP syntax: match /Envelope/Body/AddResponse == ...
        // Bare XPath starting with / should use implicit 'response' variable
        ScenarioRuntime sr = run("""
            * def response =
              \"\"\"
              <Envelope>
                <Body>
                  <AddResponse>
                    <AddResult>5</AddResult>
                  </AddResponse>
                </Body>
              </Envelope>
              \"\"\"
            * match /Envelope/Body/AddResponse/AddResult == '5'
            """);
        assertPassed(sr);
    }

    @Test
    void testMatchBareXpathOnResponseNested() {
        // Match bare XPath on response with nested structure
        ScenarioRuntime sr = run("""
            * def response =
              \"\"\"
              <Envelope>
                <Body>
                  <AddResponse>
                    <AddResult>5</AddResult>
                  </AddResponse>
                </Body>
              </Envelope>
              \"\"\"
            * match /Envelope/Body/AddResponse == <AddResponse><AddResult>5</AddResult></AddResponse>
            """);
        assertPassed(sr);
    }

}

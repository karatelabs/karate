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
package io.karatelabs.core.mock;

import io.karatelabs.core.MockServer;
import io.karatelabs.core.ScenarioRuntime;
import io.karatelabs.http.ApacheHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code configure xmlNamespaces} and namespace-correct XML {@code match}: two backends answer
 * the same SOAP call with different prefixes, and one set of test-declared prefixes reads, edits
 * and compares both. Also every lane that parses XML text honours {@code xmlNamespaceAware},
 * the mock's {@code request} included.
 */
class XmlNamespacesTest {

    private static MockServer server;
    private static int port;

    static final String NAMESPACES = """
            * configure xmlNamespaceAware = true
            * configure xmlNamespaces = { soap: 'http://schemas.xmlsoap.org/soap/envelope/', c: 'urn:customer' }
            """;

    @BeforeAll
    static void startServer() {
        server = MockServer.featureString("""
            Feature: two backends

            Background:
              * configure xmlNamespaceAware = true
              * configure xmlNamespaces = { c: 'urn:customer' }

            Scenario: pathMatches('/old')
              * def response = <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><c:getCustomerResponse xmlns:c="urn:customer"><c:name>Ann</c:name></c:getCustomerResponse></soap:Body></soap:Envelope>

            Scenario: pathMatches('/new')
              * def response = <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><getCustomerResponse xmlns="urn:customer"><name>Ann</name></getCustomerResponse></s:Body></s:Envelope>

            Scenario: pathMatches('/echo')
              * def response = { id: '#(karate.xmlPath(request, "//c:id"))', bodyPath: '#(bodyPath("//c:id"))' }
            """)
                .start();
        port = server.getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stopAsync();
    }

    private static ScenarioRuntime call(String steps) {
        return run(new ApacheHttpClient(), NAMESPACES + "* url 'http://localhost:" + port + "'\n" + steps);
    }

    @Test
    void onePathReadsBothBackends() {
        for (String backend : new String[]{"old", "new"}) {
            ScenarioRuntime sr = call("""
                * path '%s'
                * method get
                * status 200
                * match /soap:Envelope/soap:Body/c:getCustomerResponse/c:name == 'Ann'
                * match response /soap:Envelope/soap:Body/c:getCustomerResponse/c:name == 'Ann'
                * def name = get response //c:name
                * match name == 'Ann'
                * match karate.xmlPath(response, '//c:getCustomerResponse/c:name') == 'Ann'
                * match response == <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><c:getCustomerResponse xmlns:c="urn:customer"><c:name>#string</c:name></c:getCustomerResponse></soap:Body></soap:Envelope>
                * match response contains <x:Envelope xmlns:x="http://schemas.xmlsoap.org/soap/envelope/"><x:Body><getCustomerResponse xmlns="urn:customer"><name>Ann</name></getCustomerResponse></x:Body></x:Envelope>
                * copy doc = response
                * set doc /soap:Envelope/soap:Body/c:getCustomerResponse/c:name = 'Bob'
                * set doc /soap:Envelope/soap:Body/c:getCustomerResponse/c:id = '7'
                * match doc //c:name == 'Bob'
                * match doc //c:id == '7'
                * remove doc /soap:Envelope/soap:Body/c:getCustomerResponse/c:id
                * match doc count(//c:id) == 0
                * eval karate.set('doc', '/soap:Envelope/soap:Body/c:getCustomerResponse/c:name', 'Cy')
                * match doc //c:name == 'Cy'
                * eval karate.remove('doc', '/soap:Envelope/soap:Body/c:getCustomerResponse/c:name')
                * match doc count(//c:name) == 0
                """.formatted(backend));
            assertPassed(sr);
        }
    }

    @Test
    void aCreatedElementDeclaresItsNamespace() {
        ScenarioRuntime sr = call("""
            * path 'new'
            * method get
            * copy doc = response
            * set doc /soap:Envelope/soap:Header/c:trace = 't'
            * xmlstring text = doc
            * match text contains '<c:trace xmlns:c="urn:customer">t</c:trace>'
            * xml again = text
            * match again //c:trace == 't'
            """);
        assertPassed(sr);
    }

    @Test
    void namespaceAwareMatchComparesUrisNotPrefixes() {
        ScenarioRuntime sr = run("""
            * configure xmlNamespaceAware = true
            * def a = <s:Envelope xmlns:s="urn:env"><s:Body><x:R xmlns:x="urn:r" x:id="1"><x:v>1</x:v></x:R></s:Body></s:Envelope>
            * def b = <soap:Envelope xmlns:soap="urn:env" xmlns:y="urn:r"><soap:Body><R xmlns="urn:r" y:id="1"><v>1</v></R></soap:Body></soap:Envelope>
            * def c = <s:Envelope xmlns:s="urn:OTHER"><s:Body><x:R xmlns:x="urn:r" x:id="1"><x:v>1</x:v></x:R></s:Body></s:Envelope>
            * def d = <s:Envelope xmlns:s="urn:env"><s:Body><x:R xmlns:x="urn:r" id="1"><x:v>1</x:v></x:R></s:Body></s:Envelope>
            * match a == b
            * match a contains b
            * match a != c
            * match a !contains c
            * match a != d
            * match karate.match(a, c).message contains "actual does not contain key - '{urn:OTHER}Envelope'"
            """);
        assertPassed(sr);
    }

    // a side that cannot be re-parsed namespace-aware (an undeclared prefix) makes the pair
    // compare by prefixed names, as two prefix-blind documents do
    @Test
    void anExpectedParsedBeforeTheFlagIsComparedByNamespace() {
        ScenarioRuntime sr = run("""
            * def plain = <soap:Envelope xmlns:soap="urn:env"><soap:Body>1</soap:Body></soap:Envelope>
            * def other = <soap:Envelope xmlns:soap="urn:OTHER"><soap:Body>1</soap:Body></soap:Envelope>
            * def undeclared = <soap:Envelope><soap:Body>1</soap:Body></soap:Envelope>
            * configure xmlNamespaceAware = true
            * def aware = <s:Envelope xmlns:s="urn:env"><s:Body>1</s:Body></s:Envelope>
            * match aware == plain
            * match aware contains plain
            * match aware != other
            * match aware == undeclared
            """);
        assertPassed(sr);
    }

    @Test
    void prefixBlindMatchIsUnchanged() {
        ScenarioRuntime sr = run("""
            * def a = <s:Envelope xmlns:s="urn:env"><s:Body>1</s:Body></s:Envelope>
            * def b = <soap:Envelope xmlns:soap="urn:env"><soap:Body>1</soap:Body></soap:Envelope>
            * def c = <s:Envelope xmlns:s="urn:OTHER"><s:Body>1</s:Body></s:Envelope>
            * match a == b
            * match a == c
            * match a !contains b
            """);
        assertPassed(sr);
    }

    @Test
    void configureXmlNamespacesValidatesAndClears() {
        ScenarioRuntime sr = run("""
            * match karate.config.xmlNamespaces == {}
            * configure xmlNamespaces = { c: 'urn:customer' }
            * match karate.config.xmlNamespaces == { c: 'urn:customer' }
            * def doc = <x:a xmlns:x="urn:customer"><x:b>1</x:b></x:a>
            * match doc //c:b == '#notpresent'
            * configure xmlNamespaces = null
            * match karate.config.xmlNamespaces == {}
            """);
        assertPassed(sr);
        assertFailedWith(run("* configure xmlNamespaces = 'urn:x'"), "expects a map of prefix to namespace URI");
        assertFailedWith(run("* configure xmlNamespaces = { c: 1 }"), "namespace URI string for prefix 'c'");
        assertFailedWith(run("* configure xmlNamespaces = { xmlns: 'urn:x' }"), "invalid prefix: 'xmlns'");
    }

    @Test
    void everyTextParsingLaneHonoursTheFlag() {
        ScenarioRuntime sr = run("""
            * configure xmlNamespaceAware = true
            * configure xmlNamespaces = { c: 'urn:customer' }
            * def parsed = karate.fromString('<x:a xmlns:x="urn:customer"><x:b>1</x:b></x:a>')
            * match parsed //c:b == '1'
            * eval karate.setXml('set', '<x:a xmlns:x="urn:customer"><x:b>1</x:b></x:a>')
            * match set //c:b == '1'
            * eval karate.setXml('set', '/c:a/c:b', '<x:c xmlns:x="urn:customer">2</x:c>')
            * match set //c:b/c:c == '2'
            * match karate.xmlPath('<x:a xmlns:x="urn:customer"><x:b>1</x:b></x:a>', '//c:b') == '1'
            * match karate.match('<x:a xmlns:x="urn:customer"/>', '<y:a xmlns:y="urn:customer"/>').pass == true
            * match karate.match('<x:a xmlns:x="urn:customer"/>', '<x:a xmlns:x="urn:other"/>').pass == false
            * def pretty = karate.prettyXml('<x:a xmlns:x="urn:customer"/>')
            * match pretty contains 'xmlns:x="urn:customer"'
            * def undeclared = karate.fromString('<x:a/>')
            * match undeclared == '<x:a/>'
            """);
        assertPassed(sr);
        assertEquals("urn:customer", ((Document) get(sr, "parsed")).getDocumentElement().getNamespaceURI());
        assertEquals("urn:customer", ((Document) get(sr, "set")).getDocumentElement().getNamespaceURI());
    }

    @Test
    void theMockRequestHonoursTheMockConfig() {
        ScenarioRuntime sr = call("""
            * path 'echo'
            * request <x:Envelope xmlns:x="http://schemas.xmlsoap.org/soap/envelope/"><x:Body><y:get xmlns:y="urn:customer"><y:id>42</y:id></y:get></x:Body></x:Envelope>
            * method post
            * status 200
            * match response == { id: '42', bodyPath: '42' }
            """);
        assertPassed(sr);
    }

    @Test
    void aNodeSavedBeforeTheFlagIsInsertedByNamespace() {
        ScenarioRuntime sr = run("""
            * def item = <p:item xmlns:p="urn:x"><p:v>1</p:v></p:item>
            * configure xmlNamespaceAware = true
            * configure xmlNamespaces = { x: 'urn:x' }
            * def doc = <r><slot/></r>
            * set doc /r/slot = item
            * match doc == <r><slot><q:item xmlns:q="urn:x"><q:v>1</q:v></q:item></slot></r>
            * match doc /r/slot/x:item/x:v == '1'
            """);
        assertPassed(sr);
    }

}

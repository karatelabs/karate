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
import org.w3c.dom.Element;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code configure xmlNamespaceAware} end to end against a SOAP mock: with the flag on, the
 * response and XML literals parse namespace-aware (a namespace-checking serializer such as Saxon
 * sees a namespace-aware DOM), and prefixed XPath, match, set and remove work on them. The
 * default keeps the prefix-blind DOM, where unprefixed XPath reaches prefixed elements.
 */
class XmlNamespaceAwareTest {

    static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";

    private static MockServer server;
    private static int port;

    @BeforeAll
    static void startServer() {
        server = MockServer.featureString("""
            Feature: soap mock

            Scenario: pathMatches('/calc')
              * def response =
                \"\"\"
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <ns2:AddResponse xmlns:ns2="urn:calc"><ns2:AddResult>5</ns2:AddResult></ns2:AddResponse>
                  </soap:Body>
                </soap:Envelope>
                \"\"\"
              * def responseHeaders = { 'Content-Type': 'text/xml' }
            """)
                .start();
        port = server.getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stopAsync();
    }

    @Test
    void defaultResponseIsPrefixBlind() {
        ScenarioRuntime sr = run(new ApacheHttpClient(), """
            * url 'http://localhost:%d'
            * path 'calc'
            * request <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body/></soap:Envelope>
            * method post
            * status 200
            * match /Envelope/Body/AddResponse/AddResult == '5'
            """.formatted(port));
        assertPassed(sr);
        Element root = ((Document) get(sr, "response")).getDocumentElement();
        assertNull(root.getNamespaceURI());
        assertNull(root.getLocalName());
    }

    @Test
    void namespaceAwareResponse() {
        ScenarioRuntime sr = run(new ApacheHttpClient(), """
            * configure xmlNamespaceAware = true
            * url 'http://localhost:%d'
            * path 'calc'
            * def req = <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body/></soap:Envelope>
            * request req
            * method post
            * status 200
            * match /soap:Envelope/soap:Body/ns2:AddResponse/ns2:AddResult == '5'
            * match response //ns2:AddResult == '5'
            * def result = get response //ns2:AddResponse
            * match result == <ns2:AddResponse xmlns:ns2="urn:calc"><ns2:AddResult>5</ns2:AddResult></ns2:AddResponse>
            * match response ==
              \"\"\"
              <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                  <ns2:AddResponse xmlns:ns2="urn:calc"><ns2:AddResult>#string</ns2:AddResult></ns2:AddResponse>
                </soap:Body>
              </soap:Envelope>
              \"\"\"
            * copy copy = response
            * set copy /soap:Envelope/soap:Body/ns2:AddResponse/ns2:AddResult = '6'
            * set copy /soap:Envelope/soap:Body/ns2:AddResponse/ns2:Note = 'n'
            * match copy //ns2:AddResult == '6'
            * match copy //ns2:Note == 'n'
            * remove copy /soap:Envelope/soap:Body/ns2:AddResponse/ns2:Note
            * match copy count(//ns2:Note) == 0
            * xmlstring text = copy
            * match text contains '<ns2:AddResult>6</ns2:AddResult>'
            * def pretty = karate.prettyXml(response)
            * match pretty contains '<ns2:AddResult>5</ns2:AddResult>'
            """.formatted(port));
        assertPassed(sr);
        Element root = ((Document) get(sr, "response")).getDocumentElement();
        assertEquals(SOAP_NS, root.getNamespaceURI());
        assertEquals("Envelope", root.getLocalName());
        assertEquals(SOAP_NS, ((Document) get(sr, "req")).getDocumentElement().getNamespaceURI());
    }

}

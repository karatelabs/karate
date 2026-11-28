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

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code soap action} picks the SOAP version from the namespace of the request body's Envelope:
 * SOAP 1.2 carries the action as a parameter of {@code application/soap+xml}, anything else is
 * sent as SOAP 1.1 with {@code text/xml} and a {@code SOAPAction} header. The mock echoes the
 * headers it received.
 */
class SoapActionTest {

    private static MockServer server;
    private static int port;

    @BeforeAll
    static void startServer() {
        server = MockServer.featureString("""
            Feature: header echo

            Scenario: pathMatches('/raw')
              * def response = requestBytes
              * def responseHeaders = { 'Content-Type': 'text/plain', 'X-SOAP-Content-Type': '#(headerValue("Content-Type"))' }

            Scenario: true
              * def response = { contentType: '#(headerValue("Content-Type"))', soapAction: '#(headerValue("SOAPAction"))' }
            """)
                .start();
        port = server.getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stopAsync();
    }

    private static ScenarioRuntime soap(String steps) {
        return run(new ApacheHttpClient(), "* url 'http://localhost:" + port + "'\n" + steps);
    }

    @Test
    void soap11IsUnchanged() {
        assertPassed(soap("""
            * request <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body/></soap:Envelope>
            * soap action 'urn:Add'
            * status 200
            * match response == { contentType: 'text/xml; charset=UTF-8', soapAction: 'urn:Add' }
            """));
    }

    @Test
    void noEnvelopeIsSoap11() {
        assertPassed(soap("""
            * request <Add><a>1</a></Add>
            * soap action 'urn:Add'
            * status 200
            * match response == { contentType: 'text/xml; charset=UTF-8', soapAction: 'urn:Add' }
            """));
    }

    @Test
    void soap12CarriesTheActionInTheContentType() {
        assertPassed(soap("""
            * request <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope"><env:Body/></env:Envelope>
            * soap action 'urn:Add'
            * status 200
            * match response == { contentType: 'application/soap+xml; charset=UTF-8; action="urn:Add"', soapAction: null }
            """));
    }

    @Test
    void soap12DetectedByNamespaceWhateverThePrefixAndFlag() {
        assertPassed(soap("""
            * configure xmlNamespaceAware = true
            * request <Envelope xmlns="http://www.w3.org/2003/05/soap-envelope"><Body/></Envelope>
            * soap action 'urn:Add'
            * status 200
            * match response.contentType == 'application/soap+xml; charset=UTF-8; action="urn:Add"'
            """));
    }

    @Test
    void soap12FromAStringBody() {
        assertPassed(soap("""
            * request '<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"><s:Body/></s:Envelope>'
            * soap action 'urn:Add'
            * status 200
            * match response.contentType == 'application/soap+xml; charset=UTF-8; action="urn:Add"'
            """));
    }

    @Test
    void soap12EmptyActionOmitsTheParameter() {
        assertPassed(soap("""
            * request <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope"><env:Body/></env:Envelope>
            * soap action ''
            * status 200
            * match response == { contentType: 'application/soap+xml; charset=UTF-8', soapAction: null }
            """));
    }

    @Test
    void configuredContentTypeWinsForSoap11() {
        assertPassed(soap("""
            * configure headers = { 'Content-Type': 'application/xml' }
            * request <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body/></soap:Envelope>
            * soap action 'urn:Add'
            * status 200
            * match response == { contentType: 'application/xml; charset=UTF-8', soapAction: 'urn:Add' }
            """));
    }

    @Test
    void configuredContentTypeWinsForSoap12() {
        assertPassed(soap("""
            * configure headers = { 'Content-Type': 'application/xml' }
            * request <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope"><env:Body/></env:Envelope>
            * soap action 'urn:Add'
            * status 200
            * match response == { contentType: 'application/xml; charset=UTF-8', soapAction: null }
            """));
    }

    @Test
    void stepHeaderContentTypeIsReplacedForSoap11() {
        assertPassed(soap("""
            * header Content-Type = 'application/xml'
            * request <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body/></soap:Envelope>
            * soap action 'urn:Add'
            * status 200
            * match response.contentType == 'text/xml; charset=UTF-8'
            """));
    }

    @Test
    void stepHeaderContentTypeIsReplacedForSoap12() {
        assertPassed(soap("""
            * header Content-Type = 'application/xml'
            * request <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope"><env:Body/></env:Envelope>
            * soap action 'urn:Add'
            * status 200
            * match response.contentType == 'application/soap+xml; charset=UTF-8; action="urn:Add"'
            """));
    }

    @Test
    void soap12ActionIsAQuotedStringParameter() {
        ScenarioRuntime sr = soap("""
            * request <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope"><env:Body/></env:Envelope>
            * soap action 'urn:add;charset=bogus;x=1'
            * status 200
            * match response == { contentType: 'application/soap+xml; charset=UTF-8; action="urn:add;charset=bogus;x=1"', soapAction: null }
            * def quoteAndBackslash = 'urn:a"b' + String.fromCharCode(92) + 'c'
            * request <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope"><env:Body/></env:Envelope>
            * soap action quoteAndBackslash
            * status 200
            """);
        assertPassed(sr);
        assertEquals("application/soap+xml; action=\"urn:a\\\"b\\\\c\"",
                ((java.util.Map<?, ?>) get(sr, "response")).get("contentType"));
    }

    @Test
    void soap12NeverSendsSoapActionFromAStepHeader() {
        assertPassed(soap("""
            * header SOAPAction = 'legacy'
            * request <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope"><env:Body/></env:Envelope>
            * soap action 'urn:Add'
            * status 200
            * match response.soapAction == null
            """));
    }

    @Test
    void soap12NeverSendsSoapActionFromConfiguredHeaders() {
        assertPassed(soap("""
            * configure headers = { SOAPAction: 'legacy' }
            * request <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope"><env:Body/></env:Envelope>
            * soap action 'urn:Add'
            * status 200
            * match response == { contentType: 'application/soap+xml; charset=UTF-8; action="urn:Add"', soapAction: null }
            * request <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body/></soap:Envelope>
            * soap action 'urn:Add'
            * status 200
            * match response.soapAction == 'legacy'
            """));
    }

    @Test
    void soap12DetectedInAStringBodyWithLeadingWhitespace() {
        ScenarioRuntime sr = soap("""
            * path 'raw'
            * def body = '\\n\\t<Envelope xmlns="http://www.w3.org/2003/05/soap-envelope"><Body/></Envelope>'
            * request body
            * soap action 'urn:Add'
            * status 200
            * match responseHeaders['X-SOAP-Content-Type'][0] == 'application/soap+xml; charset=UTF-8; action="urn:Add"'
            * def sent = new java.lang.String(responseBytes, 'UTF-8')
            * match sent == body
            """);
        assertPassed(sr);
    }

}

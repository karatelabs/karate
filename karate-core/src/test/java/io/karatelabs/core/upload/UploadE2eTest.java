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
package io.karatelabs.core.upload;

import io.karatelabs.core.MockServer;
import io.karatelabs.core.ScenarioRuntime;
import io.karatelabs.http.ApacheHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.karatelabs.core.TestUtils.*;

/**
 * E2E tests for file upload and binary content handling.
 * Uses a dedicated mock server for upload scenarios.
 */
class UploadE2eTest {

    private static MockServer server;
    private static int port;

    @BeforeAll
    static void startServer() {
        server = MockServer.featureString("""
            Feature: Upload Mock Server

            # ===== File Upload Scenarios =====

            Scenario: pathMatches('/upload/excel')
              * def filePart = requestParts['myFile'][0]
              * def response = { filename: filePart.filename, name: filePart.name, contentType: filePart.contentType }

            Scenario: pathMatches('/upload/excel/bytes')
              * def filePart = requestParts['myFile'][0]
              * def response = filePart.value

            Scenario: pathMatches('/upload/binary')
              * def response = { size: requestBytes.length }

            Scenario: pathMatches('/multipart')
              * def response = { success: true }

            Scenario: pathMatches('/multipart/json')
              * json jsonField = requestParams['jsonData'][0]
              * def response = jsonField

            Scenario: pathMatches('/multipart/mixed')
              * def textField = requestParams['textField'][0]
              * json jsonField = requestParams['jsonField'][0]
              * def response = { text: textField, json: jsonField }

            # ===== Compression Scenarios =====

            Scenario: pathMatches('/brotli')
              * def responseHeaders = { 'Content-Encoding': 'br', 'Content-Type': 'application/json' }
              * def response = karate.readAsBytes('classpath:io/karatelabs/core/upload/brotli.bin')

            Scenario: pathMatches('/gzip')
              * def responseHeaders = { 'Content-Encoding': 'gzip', 'Content-Type': 'application/json' }
              * def response = karate.readAsBytes('classpath:io/karatelabs/core/upload/gzip.bin')

            Scenario: pathMatches('/deflate')
              * def responseHeaders = { 'Content-Encoding': 'deflate', 'Content-Type': 'application/json' }
              * def response = karate.readAsBytes('classpath:io/karatelabs/core/upload/deflate.bin')

            # ===== Download Scenarios =====

            Scenario: pathMatches('/download')
              * def response = read('classpath:io/karatelabs/core/upload/test.pdf.zip')

            Scenario: pathMatches('/download/pdf')
              * def responseHeaders = { 'Content-Type': 'application/pdf' }
              * def response = read('classpath:io/karatelabs/core/upload/test.pdf.zip')

            # ===== Response Type Scenarios =====

            Scenario: pathMatches('/json')
              * def response = { hello: 'world' }

            Scenario: pathMatches('/xml')
              * xml response = '<hello>world</hello>'

            Scenario: pathMatches('/string')
              * def response = 'hello world'

            Scenario: pathMatches('/malformed')
              * def response = '{ invalid json }'

            # ===== Header-Based Routing =====

            Scenario: pathMatches('/headers') && headerContains('val', 'foo')
              * def response = { val: 'foo' }

            Scenario: pathMatches('/headers') && headerContains('val', 'bar')
              * def response = { val: 'bar' }

            # ===== Call Within Mock =====

            Scenario: pathMatches('/call-shared')
              * def fromCaller = 'world'
              * call read('classpath:io/karatelabs/core/upload/called.feature')
              * def response = message

            Scenario: pathMatches('/call-isolated')
              * def fromCaller = 'world'
              * def result = call read('classpath:io/karatelabs/core/upload/called.feature')
              * def response = result.message
            """)
            .port(0)
            .start();

        port = server.getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stopAsync();
        }
    }

    // ===== Multipart Upload Tests =====

    @Test
    void testMultipartFileUploadWithDefaults() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Multipart Upload

            Scenario: Upload file with default settings
            * url 'http://localhost:%d'
            * path '/upload/excel'
            * multipart file myFile = { read: 'classpath:io/karatelabs/core/upload/test.xlsx' }
            * method post
            * status 200
            * match response.filename == 'test.xlsx'
            * match response.name == 'myFile'
            * match response.contentType == 'application/octet-stream'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testMultipartFileUploadWithCustomSettings() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Multipart Upload Custom

            Scenario: Upload file with custom filename and content type
            * url 'http://localhost:%d'
            * path '/upload/excel'
            * multipart file myFile = { read: 'classpath:io/karatelabs/core/upload/test.xlsx', filename: 'custom.xlsx', contentType: 'text/csv' }
            * method post
            * status 200
            * match response.filename == 'custom.xlsx'
            * match response.contentType == 'text/csv'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testMultipartFileUploadAndGetBytes() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Multipart Upload Get Bytes

            Scenario: Upload file and verify bytes returned
            * def originalBytes = karate.readAsBytes('classpath:io/karatelabs/core/upload/test.xlsx')
            * url 'http://localhost:%d'
            * path '/upload/excel/bytes'
            * multipart file myFile = { read: 'classpath:io/karatelabs/core/upload/test.xlsx' }
            * method post
            * status 200
            * match responseBytes == originalBytes
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testMultipartWithBinaryFile() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Multipart Binary

            Scenario: Upload binary file with multipart
            * url 'http://localhost:%d'
            * path '/multipart'
            * multipart file myFile = { read: 'classpath:io/karatelabs/core/upload/test.pdf.zip', filename: 'test.pdf.zip', contentType: 'application/octet-stream' }
            * multipart field message = 'multipart test'
            * method post
            * status 200
            * match response == { success: true }
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testMultipartWithJsonField() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Multipart with JSON Field

            Scenario: Send JSON as multipart field
            * url 'http://localhost:%d'
            * path '/multipart/json'
            * multipart field jsonData = { hello: 'world', count: 42 }
            * method post
            * status 200
            * match response == { hello: 'world', count: 42 }
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testMultipartWithMixedFields() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Multipart with Mixed Fields

            Scenario: Send text and JSON fields together
            * url 'http://localhost:%d'
            * path '/multipart/mixed'
            * multipart field textField = 'plain text value'
            * multipart field jsonField = { nested: { data: 'value' }, list: [1, 2, 3] }
            * method post
            * status 200
            * match response.text == 'plain text value'
            * match response.json == { nested: { data: 'value' }, list: [1, 2, 3] }
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Binary Upload Tests =====

    @Test
    void testBinaryUpload() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Binary Upload

            Scenario: Upload raw binary content
            * url 'http://localhost:%d'
            * path '/upload/binary'
            * def body = read('classpath:io/karatelabs/core/upload/test.pdf.zip')
            * request body
            * method post
            * status 200
            * match response.size == '#number'
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Download Tests =====

    @Test
    void testBinaryDownload() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Binary Download

            Scenario: Download binary file
            * url 'http://localhost:%d'
            * path '/download'
            * method get
            * status 200
            * match responseBytes == '#notnull'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testBinaryResponseMatchesRead() {
        // V1 compatibility: response for binary content-type should be byte[]
        // and should match read() of the same file
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Binary Response Matches Read

            Scenario: Binary response should match read() of same file
            * url 'http://localhost:%d'
            * path '/download/pdf'
            * method get
            * status 200
            * def expected = read('classpath:io/karatelabs/core/upload/test.pdf.zip')
            * match response == expected
            * match responseBytes == expected
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Content Decompression Tests =====

    @Test
    void testBrotliDecompression() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Brotli Decompression

            Scenario: Auto-decompress brotli response
            * url 'http://localhost:%d'
            * path '/brotli'
            * method get
            * status 200
            * match response == { hello: 'world' }
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testGzipDecompression() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Gzip Decompression

            Scenario: Auto-decompress gzip response
            * url 'http://localhost:%d'
            * path '/gzip'
            * method get
            * status 200
            * match response == { hello: 'world' }
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testDeflateDecompression() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Deflate Decompression

            Scenario: Auto-decompress deflate response
            * url 'http://localhost:%d'
            * path '/deflate'
            * method get
            * status 200
            * match response == { hello: 'world' }
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Response Type Tests =====

    @Test
    void testResponseTypeJson() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Response Type JSON

            Scenario: JSON response should have type 'json'
            * url 'http://localhost:%d'
            * path '/json'
            * method get
            * status 200
            * match responseType == 'json'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testResponseTypeXml() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Response Type XML

            Scenario: XML response should have type 'xml'
            * url 'http://localhost:%d'
            * path '/xml'
            * method get
            * status 200
            * match responseType == 'xml'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testResponseTypeString() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Response Type String

            Scenario: Plain text response should have type 'string'
            * url 'http://localhost:%d'
            * path '/string'
            * method get
            * status 200
            * match responseType == 'string'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testResponseTypeMalformedJson() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Response Type Malformed

            Scenario: Malformed JSON should have type 'string'
            * url 'http://localhost:%d'
            * path '/malformed'
            * method get
            * status 200
            * match responseType == 'string'
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Header Contains Tests =====

    @Test
    void testHeaderContainsFoo() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Header Contains

            Scenario: Route by header value 'foo'
            * url 'http://localhost:%d'
            * path '/headers'
            * header val = 'foo'
            * method get
            * status 200
            * match response == { val: 'foo' }
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testHeaderContainsBar() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Header Contains

            Scenario: Route by header value 'bar'
            * url 'http://localhost:%d'
            * path '/headers'
            * header val = 'bar'
            * method get
            * status 200
            * match response == { val: 'bar' }
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Retry with Multipart Tests =====

    @Test
    void testMultipartWithRetry() {
        // Regression test: retry with multipart should not fail with "Header already encoded"
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Multipart with Retry

            Scenario: Retry multipart upload
            * def count = { value: 0 }
            * configure retry = { interval: 100 }
            * def done = function(){ return count.value++ == 1 }
            * url 'http://localhost:%d'
            * path '/multipart'
            * multipart file myFile = { read: 'classpath:io/karatelabs/core/upload/test.pdf.zip', filename: 'test.pdf.zip', contentType: 'application/octet-stream' }
            * multipart field message = 'retry test'
            * retry until done()
            * method post
            * status 200
            * match response == { success: true }
            """.formatted(port));

        assertPassed(sr);
    }

    // ===== Call Within Mock Tests =====

    @Test
    void testCallSharedInMock() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Call Shared in Mock

            Scenario: Call feature with shared scope in mock
            * url 'http://localhost:%d'
            * path '/call-shared'
            * method get
            * status 200
            * match response == 'hello world'
            """.formatted(port));

        assertPassed(sr);
    }

    @Test
    void testCallIsolatedInMock() {
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
            Feature: Test Call Isolated in Mock

            Scenario: Call feature with isolated scope in mock
            * url 'http://localhost:%d'
            * path '/call-isolated'
            * method get
            * status 200
            * match response == 'hello world'
            """.formatted(port));

        assertPassed(sr);
    }

}

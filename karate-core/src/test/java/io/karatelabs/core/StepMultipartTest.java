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

import static io.karatelabs.core.InMemoryHttpClient.*;
import static io.karatelabs.core.TestUtils.*;

class StepMultipartTest {

    @Test
    void testMultipartField() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("Content-Type");
            if (contentType != null && contentType.contains("multipart")) {
                return json("{ \"received\": true }");
            }
            return status(400);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/upload'
            * multipart field name = 'John'
            * method post
            * status 200
            * match response.received == true
            """);
        assertPassed(sr);
    }

    @Test
    void testMultipartFields() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("Content-Type");
            if (contentType != null && contentType.contains("multipart")) {
                return json("{ \"received\": true }");
            }
            return status(400);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/upload'
            * multipart fields { name: 'John', email: 'john@test.com' }
            * method post
            * status 200
            """);
        assertPassed(sr);
    }

    @Test
    void testMultipartFieldWithVariable() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("Content-Type");
            if (contentType != null && contentType.contains("multipart")) {
                return json("{ \"received\": true }");
            }
            return status(400);
        });

        ScenarioRuntime sr = run(client, """
            * def userName = 'Alice'
            * url 'http://test/upload'
            * multipart field name = userName
            * method post
            * status 200
            """);
        assertPassed(sr);
    }

    @Test
    void testMultipartEntity() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("Content-Type");
            if (contentType != null && contentType.contains("multipart")) {
                return json("{ \"received\": true }");
            }
            return status(400);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/upload'
            * multipart entity { name: 'file', value: 'test content', contentType: 'text/plain' }
            * method post
            * status 200
            """);
        assertPassed(sr);
    }

    @Test
    void testMultipartFileWithValue() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("Content-Type");
            if (contentType != null && contentType.contains("multipart")) {
                return json("{ \"received\": true }");
            }
            return status(400);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/upload'
            * multipart file document = { value: 'file contents', filename: 'test.txt', contentType: 'text/plain' }
            * method post
            * status 200
            """);
        assertPassed(sr);
    }

    @Test
    void testMultipleMultipartFields() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("Content-Type");
            if (contentType != null && contentType.contains("multipart")) {
                return json("{ \"received\": true }");
            }
            return status(400);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/upload'
            * multipart field firstName = 'John'
            * multipart field lastName = 'Doe'
            * multipart field email = 'john@example.com'
            * method post
            * status 200
            """);
        assertPassed(sr);
    }

    @Test
    void testMultipartFileWithJson() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("Content-Type");
            if (contentType != null && contentType.contains("multipart")) {
                return json("{ \"received\": true }");
            }
            return status(400);
        });

        ScenarioRuntime sr = run(client, """
            * def data = { name: 'test', count: 42 }
            * url 'http://test/upload'
            * multipart file jsonData = { value: data, filename: 'data.json', contentType: 'application/json' }
            * method post
            * status 200
            """);
        assertPassed(sr);
    }

    @Test
    void testMultipartFilesArray() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("Content-Type");
            if (contentType != null && contentType.contains("multipart")) {
                return json("{ \"received\": true }");
            }
            return status(400);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/upload'
            * multipart files [{ name: 'file1', value: 'content1', filename: 'a.txt' }, { name: 'file2', value: 'content2', filename: 'b.txt' }]
            * method post
            * status 200
            """);
        assertPassed(sr);
    }

    @Test
    void testMultipartFieldsMissingMapFails() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> json("{}"));

        ScenarioRuntime sr = run(client, """
            * url 'http://test/upload'
            * multipart fields 'not a map'
            * method post
            """);
        assertFailed(sr);
    }

    @Test
    void testMultipartFilesMap() {
        // V1 compatibility: map where keys are part names
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("Content-Type");
            if (contentType != null && contentType.contains("multipart")) {
                return json("{ \"received\": true }");
            }
            return status(400);
        });

        ScenarioRuntime sr = run(client, """
            * def json = {}
            * set json.myFile1 = { value: 'content1', filename: 'file1.txt', contentType: 'text/plain' }
            * set json.myFile2 = { value: 'content2', filename: 'file2.txt', contentType: 'text/plain' }
            * url 'http://test/upload'
            * multipart files json
            * method post
            * status 200
            """);
        assertPassed(sr);
    }

    @Test
    void testMultipartFilesInvalidTypeFails() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> json("{}"));

        ScenarioRuntime sr = run(client, """
            * url 'http://test/upload'
            * multipart files 'not a list or map'
            * method post
            """);
        assertFailed(sr);
    }

    @Test
    void testMixedMultipartFieldsAndFile() {
        InMemoryHttpClient client = new InMemoryHttpClient(req -> {
            String contentType = req.getHeader("Content-Type");
            if (contentType != null && contentType.contains("multipart")) {
                return json("{ \"received\": true }");
            }
            return status(400);
        });

        ScenarioRuntime sr = run(client, """
            * url 'http://test/upload'
            * multipart field description = 'My document'
            * multipart file document = { value: 'file contents', filename: 'doc.txt' }
            * method post
            * status 200
            """);
        assertPassed(sr);
    }

}

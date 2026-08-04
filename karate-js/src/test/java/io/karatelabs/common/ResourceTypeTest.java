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
package io.karatelabs.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceTypeTest {

    @Test
    void testFromContentType() {
        assertEquals(ResourceType.JSON, ResourceType.fromContentType("application/json"));
        assertEquals(ResourceType.TEXT, ResourceType.fromContentType("text/plain;charset=UTF-8"));
        assertEquals(ResourceType.CSV, ResourceType.fromContentType("text/csv"));
        assertEquals(ResourceType.YAML, ResourceType.fromContentType("application/x-yaml"));
        assertEquals(ResourceType.YAML, ResourceType.fromContentType("text/yml"));
        assertEquals(ResourceType.TOML, ResourceType.fromContentType("application/toml"));
        assertEquals(ResourceType.MARKDOWN, ResourceType.fromContentType("text/markdown"));
        assertNull(ResourceType.fromContentType("application/vnd.acme.thing"));
    }

    @Test
    void testFromFileExtension() {
        assertEquals(ResourceType.CSV, ResourceType.fromFileExtension("data/report.csv"));
        assertEquals(ResourceType.YAML, ResourceType.fromFileExtension("openapi.yaml"));
        assertEquals(ResourceType.YAML, ResourceType.fromFileExtension("config.yml"));
        assertEquals(ResourceType.TOML, ResourceType.fromFileExtension("telegraf.toml"));
        assertEquals(ResourceType.MARKDOWN, ResourceType.fromFileExtension("README.md"));
        assertNull(ResourceType.fromFileExtension("archive.tar"));
    }

    @Test
    void testAddedTypesDoNotShadowTheOnesBeforeThem() {
        // the lookup returns the first declaration whose contentLike matches, so a new
        // arrival must never sit between a content-type and the type that already claimed it
        assertEquals(ResourceType.CSS, ResourceType.fromContentType("text/css"));
        assertEquals(ResourceType.HTML, ResourceType.fromContentType("text/html"));
        assertEquals(ResourceType.XML, ResourceType.fromContentType("application/xml"));
    }

}

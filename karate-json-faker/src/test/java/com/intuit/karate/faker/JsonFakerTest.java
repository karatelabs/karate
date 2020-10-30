/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
package com.intuit.karate.faker;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

// important: do not use @RunWith(Karate.class) !
/**
 *
 * @author sixdouglas
 */
public class JsonFakerTest {

    @Test
    void testNewGeneration() {
        final JsonFaker jsonFaker = new JsonFaker();
    }

    @Test
    void testStartGeneration() throws JsonProcessingException {
        final JsonFaker jsonFaker = new JsonFaker();
        String schema = null;
        String object = jsonFaker.generate(schema);
        assertThat(object).isNull();
    }

    @Test()
    void testStartGenerationWithInput() throws URISyntaxException, IOException {
        final JsonFaker jsonFaker = new JsonFaker();
        Assertions.assertThrows(JsonProcessingException.class, () -> jsonFaker.generate("schema"));
    }

    @Test
    void testStartGenerationWithSchema() throws URISyntaxException, IOException {
        final JsonFaker jsonFaker = new JsonFaker();
        String schema = new String(Files.readAllBytes(Paths.get(JsonFakerTest.class.getResource("/product-structure.json").toURI())));
        String object = jsonFaker.generate(schema);
        System.out.println("object = " + object);
        assertThat(object).isNotBlank();
    }
}

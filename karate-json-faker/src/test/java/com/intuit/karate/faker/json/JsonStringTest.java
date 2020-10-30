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
package com.intuit.karate.faker.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static com.intuit.karate.faker.json.JsonObject.JSON_MAIN_OBJECT_PATTERN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author sixdouglas
 */
class JsonStringTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MY_STR = "myStr";
    private static final String VALIDATE_STR_JSON_REGEX = "^\\{ \"" + MY_STR + "\" : \".*\" \\}$";

    @Test
    void generateValue() {
        JsonString jsonString = new JsonString();
        String value = jsonString.generateValue();
        assertThat(value).isNotBlank();

    }

    @Test
    void generateStringValue() {
        JsonString jsonString = new JsonString();
        jsonString.setName(MY_STR);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonString.generateValue());
        assertThat(value).matches(VALIDATE_STR_JSON_REGEX);
    }

    @Test
    void generateStringValueWithMinLength() throws JsonProcessingException {
        JsonString jsonString = new JsonString();
        jsonString.setName(MY_STR);
        jsonString.setMinLength(15);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonString.generateValue());
        assertThat(value).matches(VALIDATE_STR_JSON_REGEX);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final int length = jsonNode.at("/" + MY_STR).asText().length();
        assertThat(length)
                .describedAs("Expect JSon '%s', to have its property value of length %d to be GreaterThanOrEqualTo %d", value, length, 15)
                .isGreaterThanOrEqualTo(15);
    }

    @Test
    void generateStringValueWithMaxLength() throws JsonProcessingException {
        JsonString jsonString = new JsonString();
        jsonString.setName(MY_STR);
        jsonString.setMaxLength(15);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonString.generateValue());
        assertThat(value).matches(VALIDATE_STR_JSON_REGEX);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final int length = jsonNode.at("/" + MY_STR).asText().length();
        assertThat(length)
                .describedAs("Expect JSon '%s', to have its property value of length %d to be GreaterThanOrEqualTo %d", value, length, 15)
                .isLessThanOrEqualTo(15);
    }

    @Test
    void generateStringValueWithMinAndMaxLength() throws JsonProcessingException {
        JsonString jsonString = new JsonString();
        jsonString.setName(MY_STR);
        jsonString.setMinLength(10);
        jsonString.setMaxLength(15);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonString.generateValue());
        assertThat(value).matches(VALIDATE_STR_JSON_REGEX);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final int length = jsonNode.at("/" + MY_STR).asText().length();
        assertThat(length)
                .describedAs("Expect JSon '%s', to have its property value of length %d to be GreaterThanOrEqualTo %d", value, length, 10)
                .isGreaterThanOrEqualTo(10);
        assertThat(length)
                .describedAs("Expect JSon '%s', to have its property value of length %d to be LessThanOrEqualTo %d", value, length, 15)
                .isLessThanOrEqualTo(15);
    }
}
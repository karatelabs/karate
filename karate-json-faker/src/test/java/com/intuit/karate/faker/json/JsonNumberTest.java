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
class JsonNumberTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MY_NUM = "myNum";
    public static final String VALIDATE_NUM_JSON_REGEX = "^\\{ \"" + MY_NUM + "\" : [-]?\\d+\\.[0-9E]* \\}$";

    @Test
    void generateValue() {
        JsonNumber jsonNumber = new JsonNumber();
        String value = jsonNumber.generateValue();
        assertThat(value).isNotBlank();
    }

    @Test
    void generateIntegerValue() {
        JsonNumber jsonNumber = new JsonNumber();
        jsonNumber.setName(MY_NUM);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonNumber.generateValue());
        assertThat(value).matches(VALIDATE_NUM_JSON_REGEX);
    }

    @Test
    void generateIntegerValueWithMin() throws JsonProcessingException {
        JsonNumber jsonNumber = new JsonNumber();
        jsonNumber.setName(MY_NUM);
        jsonNumber.setMinimum(2);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonNumber.generateValue());
        assertThat(value).matches(VALIDATE_NUM_JSON_REGEX);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final long actual = jsonNode.at("/" + MY_NUM).asLong();
        assertThat(actual)
                .describedAs("Expect JSon '%s', to have its property value evaluated as %d but was %d", value, 2L, actual)
                .isGreaterThanOrEqualTo(2L);
    }

    @Test
    void generateIntegerValueWithMax() throws JsonProcessingException {
        JsonNumber jsonNumber = new JsonNumber();
        jsonNumber.setName(MY_NUM);
        jsonNumber.setMaximum(2);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonNumber.generateValue());
        assertThat(value).matches(VALIDATE_NUM_JSON_REGEX);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final long actual = jsonNode.at("/" + MY_NUM).asLong();
        assertThat(actual)
                .describedAs("Expect JSon '%s', to have its property value evaluated as %d but was %d", value, 2L, actual)
                .isLessThan(2L);
    }

    @Test
    void generateIntegerValueWithMinIncludedAndMax() throws JsonProcessingException {
        JsonNumber jsonNumber = new JsonNumber();
        jsonNumber.setName(MY_NUM);
        jsonNumber.setMinimum(2);
        jsonNumber.setMaximum(3);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonNumber.generateValue());
        assertThat(value).matches(VALIDATE_NUM_JSON_REGEX);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final long actual = jsonNode.at("/" + MY_NUM).asLong();
        assertThat(actual)
                .describedAs("Expect JSon '%s', to have its property value evaluated as %d but was %d", value, 2L, actual)
                .isEqualTo(2L);
    }

    @Test
    void generateIntegerValueWithMinExcludedAndMax() throws JsonProcessingException {
        JsonNumber jsonNumber = new JsonNumber();
        jsonNumber.setName(MY_NUM);
        jsonNumber.setMinimum(20);
        jsonNumber.setMinExclu(true);
        jsonNumber.setMaximum(22);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonNumber.generateValue());
        assertThat(value).matches(VALIDATE_NUM_JSON_REGEX);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final long actual = jsonNode.at("/" + MY_NUM).asLong();
        assertThat(actual)
                .describedAs("Expect JSon '%s', to have its property value evaluated as %d but was %d", value, 21L, actual)
                .isEqualTo(21L);
    }

    @Test
    void generateIntegerValueWithMinExcludedAndMaxIncluded() throws JsonProcessingException {
        JsonNumber jsonNumber = new JsonNumber();
        jsonNumber.setName(MY_NUM);
        jsonNumber.setMinimum(30);
        jsonNumber.setMinExclu(true);
        jsonNumber.setMaximum(31);
        jsonNumber.setMaxExclu(false);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonNumber.generateValue());
        assertThat(value).matches(VALIDATE_NUM_JSON_REGEX);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final long actual = jsonNode.at("/" + MY_NUM).asLong();
        assertThat(actual)
                .describedAs("Expect JSon '%s', to have its property value evaluated as %d but was %d", value, 31L, actual)
                .isEqualTo(31L);
    }

    @Test
    void generateIntegerValueWithMinIncludedAndMaxIncluded() throws JsonProcessingException {
        JsonNumber jsonNumber = new JsonNumber();
        jsonNumber.setName(MY_NUM);
        jsonNumber.setMinimum(50);
        jsonNumber.setMaximum(50);
        jsonNumber.setMaxExclu(false);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonNumber.generateValue());
        assertThat(value).matches(VALIDATE_NUM_JSON_REGEX);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final long actual = jsonNode.at("/" + MY_NUM).asLong();
        assertThat(actual)
                .describedAs("Expect JSon '%s', to have its property value evaluated as %d but was %d", value, 50L, actual)
                .isEqualTo(50L);
    }
}
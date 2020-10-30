package com.intuit.karate.faker.json;

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
class JsonIntegerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MY_INT = "myInt";
    private static final String VALIDATE_INT_JSON_REGEX = "^\\{ \"" + MY_INT + "\" : [-]?\\d* \\}$";

    @Test
    void generateValue() {
        JsonInteger jsonInteger = new JsonInteger();
        String value = jsonInteger.generateValue();
        assertThat(value).isNotBlank();
    }

    @Test
    void generateIntegerValue() {
        JsonInteger jsonInteger = new JsonInteger();
        jsonInteger.setName(MY_INT);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonInteger.generateValue());
        assertThat(value).matches(VALIDATE_INT_JSON_REGEX);
    }

    @Test
    void generateIntegerValueWithMin() throws JsonProcessingException {
        JsonInteger jsonInteger = new JsonInteger();
        jsonInteger.setName(MY_INT);
        jsonInteger.setMinimum(2);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonInteger.generateValue());
        assertThat(value).matches(VALIDATE_INT_JSON_REGEX);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final int actual = jsonNode.at("/" + MY_INT).asInt();
        assertThat(actual)
                .describedAs("Expect JSon '%s', to have its property value evaluated as greater or equal to %d but was %d", value, 2, actual)
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void generateIntegerValueWithMax() throws JsonProcessingException {
        JsonInteger jsonInteger = new JsonInteger();
        jsonInteger.setName(MY_INT);
        jsonInteger.setMaximum(2);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonInteger.generateValue());
        assertThat(value).matches(VALIDATE_INT_JSON_REGEX);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final int actual = jsonNode.at("/" + MY_INT).asInt();
        assertThat(actual)
                .describedAs("Expect JSon '%s', to have its property value evaluated as less than %d but was %d", value, 2, actual)
                .isLessThan(2);
    }

    @Test
    void generateIntegerValueWithMinIncludedAndMax() throws JsonProcessingException {
        JsonInteger jsonInteger = new JsonInteger();
        jsonInteger.setName(MY_INT);
        jsonInteger.setMinimum(2);
        jsonInteger.setMaximum(3);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonInteger.generateValue());
        assertThat(value).matches(VALIDATE_INT_JSON_REGEX);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final int actual = jsonNode.at("/" + MY_INT).asInt();
        assertThat(actual)
                .describedAs("Expect JSon '%s', to have its property value evaluated as %d but was %d", value, 2, actual)
                .isEqualTo(2);
    }

    @Test
    void generateIntegerValueWithMinExcludedAndMax() throws JsonProcessingException {
        JsonInteger jsonInteger = new JsonInteger();
        jsonInteger.setName(MY_INT);
        jsonInteger.setMinimum(20);
        jsonInteger.setMinExclu(true);
        jsonInteger.setMaximum(22);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonInteger.generateValue());
        assertThat(value).matches(VALIDATE_INT_JSON_REGEX);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final int actual = jsonNode.at("/" + MY_INT).asInt();
        assertThat(actual)
                .describedAs("Expect JSon '%s', to have its property value evaluated as %d but was %d", value, 21, actual)
                .isEqualTo(21);
    }

    @Test
    void generateIntegerValueWithMinExcludedAndMaxIncluded() throws JsonProcessingException {
        JsonInteger jsonInteger = new JsonInteger();
        jsonInteger.setName(MY_INT);
        jsonInteger.setMinimum(30);
        jsonInteger.setMinExclu(true);
        jsonInteger.setMaximum(31);
        jsonInteger.setMaxExclu(false);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonInteger.generateValue());
        assertThat(value).matches(VALIDATE_INT_JSON_REGEX);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final int actual = jsonNode.at("/" + MY_INT).asInt();
        assertThat(actual)
                .describedAs("Expect JSon '%s', to have its property value evaluated as %d but was %d", value, 31, actual)
                .isEqualTo(31);
    }

    @Test
    void generateIntegerValueWithMinIncludedAndMaxIncluded() throws JsonProcessingException {
        JsonInteger jsonInteger = new JsonInteger();
        jsonInteger.setName(MY_INT);
        jsonInteger.setMinimum(50);
        jsonInteger.setMaximum(50);
        jsonInteger.setMaxExclu(false);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonInteger.generateValue());
        assertThat(value).matches(VALIDATE_INT_JSON_REGEX);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final int actual = jsonNode.at("/" + MY_INT).asInt();
        assertThat(actual)
                .describedAs("Expect JSon '%s', to have its property value evaluated as %d but was %d", value, 50, actual)
                .isEqualTo(50);
    }

    @Test
    void generateIntegerValueWithMultipleOf() throws JsonProcessingException {
        JsonInteger jsonInteger = new JsonInteger();
        jsonInteger.setName(MY_INT);
        jsonInteger.setMultipleOf(7);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonInteger.generateValue());
        assertThat(value).matches(VALIDATE_INT_JSON_REGEX);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final int actual = jsonNode.at("/" + MY_INT).asInt() % 7;
        assertThat(actual)
                .describedAs("Expect JSon '%s', to have its property value evaluated as %d but was %d", value, 0, actual)
                .isEqualTo(0);
    }
}
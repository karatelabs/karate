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

import static com.intuit.karate.faker.json.AbstractJsonObject.JSON_MAIN_OBJECT_PATTERN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author sixdouglas
 */
class JsonObjectTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MY_OBJECT = "myObject";
    private static final String VALIDATE_OBJECT_JSON_REGEX = "^\\{ \"" + MY_OBJECT + "\" : \\{ .* \\} \\}$";
    private static final String VALIDATE_MAIN_OBJECT_JSON_REGEX = "^\\{ .* \\}$";

    @Test
    void generateValue() {
        JsonObject jsonObject = new JsonObject(MY_OBJECT);
        jsonObject.addProperties("oneBoolean", new JsonBoolean());
        String value = jsonObject.generateValue();
        assertThat(value).isNotBlank();
    }

    @Test
    void generateValueWithProperty() {
        JsonObject jsonObject = new JsonObject(MY_OBJECT);
        jsonObject.addProperties("oneBoolean", new JsonBoolean("myBool"));
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonObject.generateValue());
        assertThat(value).matches(VALIDATE_OBJECT_JSON_REGEX);
    }

    @Test
    void generateValueMainObjectWithProperty() {
        JsonObject jsonObject = new JsonObject(MY_OBJECT);
        jsonObject.setMainObject(true);
        jsonObject.addProperties("oneBoolean", new JsonBoolean("myBool"));
        String value = jsonObject.generateValue();
        assertThat(value).matches(VALIDATE_MAIN_OBJECT_JSON_REGEX);
    }

    @Test
    void generateValueMainObjectWithMultiplePropertiesAllRequired() throws JsonProcessingException {
        JsonObject jsonObject = new JsonObject(MY_OBJECT);
        jsonObject.setMainObject(true);
        jsonObject.addProperties("oneBoolean", new JsonBoolean("myBool"));
        jsonObject.addProperties("oneInteger", new JsonInteger("myInteger"));
        jsonObject.addProperties("oneNumber", new JsonNumber("myNumber"));
        jsonObject.addRequired("oneBoolean");
        jsonObject.addRequired("oneInteger");
        jsonObject.addRequired("oneNumber");
        String value = jsonObject.generateValue();
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        assertThat(value).matches(VALIDATE_MAIN_OBJECT_JSON_REGEX);
        assertThat(jsonNode.size()).isEqualTo(3);
    }

    @Test
    void generateValueMainObjectWithMultipleProperties() throws JsonProcessingException {
        JsonObject jsonObject = new JsonObject(MY_OBJECT);
        jsonObject.setMainObject(true);
        jsonObject.addProperties("oneBoolean", new JsonBoolean("myBool"));
        jsonObject.addProperties("oneInteger", new JsonInteger("myInteger"));
        jsonObject.addProperties("oneNumber", new JsonNumber("myNumber"));
        jsonObject.addProperties("oneString", new JsonNumber("myString"));
        jsonObject.addRequired("oneBoolean");
        jsonObject.addRequired("oneNumber");
        String value = jsonObject.generateValue();
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        System.out.println("value = " + value);
        assertThat(value).matches(VALIDATE_MAIN_OBJECT_JSON_REGEX);
        assertThat(jsonNode.size()).isGreaterThanOrEqualTo(2);
    }
}
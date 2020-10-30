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
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intuit.karate.faker.json.AbstractJsonObject.JSON_MAIN_OBJECT_PATTERN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author sixdouglas
 */
class JsonArrayTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MY_ARRAY = "myArray";
    private static final String VALIDATE_ARRAY_JSON_REGEX = "^\\{ \"" + MY_ARRAY + "\" : \\[ \\d+(?:, \\d+)* \\] \\}$";

    @Test
    void generateValue() {
        JsonArray jsonArray = new JsonArray();
        jsonArray.setItems(new JsonBoolean());
        String value = jsonArray.generateValue();
        assertThat(value).isNotBlank();
    }

    @Test
    void generateValueArrayValue() {
        JsonArray jsonArray = new JsonArray(MY_ARRAY);
        final JsonInteger jsonInteger = new JsonInteger();
        jsonInteger.setMaximum(50);
        jsonInteger.setMinimum(5);
        jsonArray.setItems(jsonInteger);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonArray.generateValue());
        assertThat(value).matches(VALIDATE_ARRAY_JSON_REGEX);
    }

    @Test
    void generateValueArrayValueFixedQuantity() throws JsonProcessingException {
        JsonArray jsonArray = new JsonArray(MY_ARRAY);
        jsonArray.setMaxItems(6);
        jsonArray.setMinItems(5);
        jsonArray.setItems(new JsonInteger());
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonArray.generateValue());
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final JsonNode jsonArrayNode = jsonNode.at("/" + MY_ARRAY);
        assertThat(jsonArrayNode.isArray()).isTrue();
        assertThat(jsonArrayNode.size()).isEqualTo(5);
    }

    @Test
    void generateValueArrayValueWithNonUniqueValues() throws JsonProcessingException {
        JsonArray jsonArray = new JsonArray(MY_ARRAY);
        jsonArray.setMaxItems(20);
        jsonArray.setMinItems(2);
        jsonArray.setUniqueItems(false);
        final JsonInteger jsonInteger = new JsonInteger();
        jsonInteger.setMinimum(1);
        jsonInteger.setMaximum(10);
        jsonArray.setItems(jsonInteger);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonArray.generateValue());
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final JsonNode jsonArrayNode = jsonNode.at("/" + MY_ARRAY);
        assertThat(jsonArrayNode.isArray()).isTrue();
        final ArrayNode arrayNode = (ArrayNode) jsonArrayNode;
        List<Integer> arrayItemsList = new ArrayList<>();
        arrayNode.elements().forEachRemaining(arrayItem -> arrayItemsList.add(arrayItem.asInt()));
        Set<Integer> arrayItemsSet = new HashSet<>(arrayItemsList);
        assertThat(arrayItemsSet.size()).isLessThanOrEqualTo(arrayItemsList.size());
    }

    @Test
    void generateValueArrayValueWithUniqueValues() throws JsonProcessingException {
        JsonArray jsonArray = new JsonArray(MY_ARRAY);
        jsonArray.setMaxItems(20);
        jsonArray.setMinItems(2);
        jsonArray.setUniqueItems(true);
        final JsonInteger jsonInteger = new JsonInteger();
        jsonInteger.setMinimum(1);
        jsonInteger.setMaximum(10);
        jsonArray.setItems(jsonInteger);
        String value = String.format(JSON_MAIN_OBJECT_PATTERN, jsonArray.generateValue());
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(value);
        final JsonNode jsonArrayNode = jsonNode.at("/" + MY_ARRAY);
        assertThat(jsonArrayNode.isArray()).isTrue();
        final ArrayNode arrayNode = (ArrayNode) jsonArrayNode;
        List<Integer> arrayItemsList = new ArrayList<>();
        arrayNode.elements().forEachRemaining(arrayItem -> arrayItemsList.add(arrayItem.asInt()));
        assertThat(arrayItemsList).doesNotHaveDuplicates();
    }
}
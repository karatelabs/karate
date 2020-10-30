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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.karate.faker.json.AbstractJsonObject;
import com.intuit.karate.faker.json.JsonObject;
import com.intuit.karate.faker.json.JsonObjectsFactory;

/**
 *
 * @author sixdouglas
 */
public class JsonFaker {
    private final ObjectMapper mapper = new ObjectMapper();

    public String generate(String schemaString) throws JsonProcessingException {
        if (schemaString == null) {
            return null;
        }

        JsonNode jsonSchema = getJsonFromString(schemaString);
        AbstractJsonObject mainObject = JsonObjectsFactory.getInstance().build(null, jsonSchema, jsonSchema);
        if (mainObject instanceof JsonObject) {
            mainObject.setMainObject(true);
        }
        return mainObject.generateValue();
    }

    private JsonNode getJsonFromString(String schemaContent) throws JsonProcessingException {
        return mapper.readTree(schemaContent);
    }
}

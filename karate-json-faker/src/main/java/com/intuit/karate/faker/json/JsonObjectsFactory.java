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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;

/**
 *
 * @author sixdouglas
 */
public class JsonObjectsFactory {
    private static JsonObjectsFactory jsonObjectsFactory = null;

    private JsonObjectsFactory() {
    }

    public static JsonObjectsFactory getInstance() {
        if (jsonObjectsFactory == null) {
            jsonObjectsFactory = new JsonObjectsFactory();
        }

        return jsonObjectsFactory;
    }

    public AbstractJsonObject build(String name, JsonNode jsonNode, JsonNode jsonSchema) {
        JsonNode workingNode = jsonNode;
        final JsonNode refNode = jsonNode.at("/$ref");
        if (refNode.getNodeType() != JsonNodeType.MISSING) {
            final JsonNode referencedNode = jsonSchema.at(refNode.asText().substring(1));
            if (referencedNode.getNodeType() != JsonNodeType.MISSING) {
                workingNode = referencedNode;
            } else {
                return null;
            }
        }

        final JsonNode typeNode = workingNode.at("/type");
        if (typeNode.getNodeType() == JsonNodeType.MISSING) {
            return null;
        }

        final AbstractJsonObject output;
        switch (workingNode.at("/type").asText().toLowerCase()) {
            case "string":
                output = buildJsonString(name, workingNode);
                break;
            case "number":
                output = buildJsonNumber(name, workingNode);
                break;
            case "integer":
                output = buildJsonInteger(name, workingNode);
                break;
            case "boolean":
                output = new JsonBoolean(name);
                break;
            case "array":
                output = buildJsonArray(name, workingNode, jsonSchema);
                break;
            case "object":
                output = buildJsonObject(name, workingNode, jsonSchema);
                break;
            default:
                output = null;
                break;
        }

        return output;
    }

    private AbstractJsonObject buildJsonString(String name, JsonNode jsonNode) {
        JsonString jsonString = new JsonString(name);
        JsonNode node = jsonNode.at("/minLength");
        if (node.getNodeType() != JsonNodeType.MISSING) {
            jsonString.setMinLength(node.asInt());
        }
        node = jsonNode.at("/maxLength");
        if (node.getNodeType() != JsonNodeType.MISSING) {
            jsonString.setMaxLength(node.asInt());
        }
        node = jsonNode.at("/format");
        if (node.getNodeType() != JsonNodeType.MISSING) {
            jsonString.setFormat(node.asText());
        }
        return jsonString;
    }

    private AbstractJsonObject buildJsonInteger(String name, JsonNode jsonNode) {
        JsonInteger jsonInteger = new JsonInteger(name);
        fillNumberValues(jsonNode, jsonInteger);
        JsonNode node;
        node = jsonNode.at("/multipleOf");
        if (node.getNodeType() != JsonNodeType.MISSING) {
            jsonInteger.setMultipleOf(node.asInt());
        }
        return jsonInteger;
    }

    private AbstractJsonObject buildJsonNumber(String name, JsonNode jsonNode) {
        JsonNumber jsonNumber = new JsonNumber(name);
        fillNumberValues(jsonNode, jsonNumber);
        return jsonNumber;
    }

    private void fillNumberValues(JsonNode jsonNode, JsonNumber jsonNumber) {
        JsonNode node = jsonNode.at("/minimum");
        if (node.getNodeType() != JsonNodeType.MISSING) {
            jsonNumber.setMinimum(node.asInt());
        }
        node = jsonNode.at("/minExclu");
        if (node.getNodeType() != JsonNodeType.MISSING) {
            jsonNumber.setMinExclu(node.asBoolean());
        }
        node = jsonNode.at("/maximum");
        if (node.getNodeType() != JsonNodeType.MISSING) {
            jsonNumber.setMaximum(node.asInt());
        }
        node = jsonNode.at("/maxExclu");
        if (node.getNodeType() != JsonNodeType.MISSING) {
            jsonNumber.setMaxExclu(node.asBoolean());
        }
    }

    private AbstractJsonObject buildJsonArray(String name, JsonNode jsonNode, JsonNode jsonSchema) {
        final JsonNode itemsNode = jsonNode.at("/items");
        if (itemsNode.getNodeType() == JsonNodeType.MISSING) {
            return null;
        }
        final AbstractJsonObject jsonItems = JsonObjectsFactory.getInstance().build(name, itemsNode, jsonSchema);
        final JsonArray jsonArray = new JsonArray(name);
        jsonArray.setItems(jsonItems);
        JsonNode node = jsonNode.at("/maxItems");
        if (node.getNodeType() != JsonNodeType.MISSING) {
            jsonArray.setMaxItems(node.asInt());
        }
        node = jsonNode.at("/minItems");
        if (node.getNodeType() != JsonNodeType.MISSING) {
            jsonArray.setMinItems(node.asInt());
        }
        node = jsonNode.at("/uniqueItems");
        if (node.getNodeType() != JsonNodeType.MISSING) {
            jsonArray.setUniqueItems(node.asBoolean());
        }

        return jsonArray;
    }

    private AbstractJsonObject buildJsonObject(String name, JsonNode jsonNode, JsonNode jsonSchema) {
        JsonObject mainObject = new JsonObject(name);
        jsonNode.at("/properties").fields().forEachRemaining(jsonNodeEntry -> {
            AbstractJsonObject jsonObject = JsonObjectsFactory.getInstance().build(jsonNodeEntry.getKey(), jsonNodeEntry.getValue(), jsonSchema);

            if (jsonObject == null) {
                return;
            }
            mainObject.addProperties(jsonNodeEntry.getKey(), jsonObject);
        });

        jsonNode.at("/required").elements().forEachRemaining(jsonNodeItem -> mainObject.addRequired(jsonNodeItem.asText()));

        return mainObject;
    }
}

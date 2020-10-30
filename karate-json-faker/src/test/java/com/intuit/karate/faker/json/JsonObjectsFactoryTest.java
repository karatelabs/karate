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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author sixdouglas
 */
class JsonObjectsFactoryTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void buildBoolean() throws URISyntaxException, IOException {
        // GIVEN
        JsonBoolean jsonBoolean = new JsonBoolean("myBool");

        String schema = new String(Files.readAllBytes(Paths.get(JsonObjectsFactoryTest.class.getResource("/boolean-simple-structure.json").toURI())));
        JsonNode jsonSchema = OBJECT_MAPPER.readTree(schema);

        // WHEN
        AbstractJsonObject mainObject = JsonObjectsFactory.getInstance().build("myBool", jsonSchema, jsonSchema);

        //THEN
        assertThat(mainObject).isExactlyInstanceOf(JsonBoolean.class);
        assertThat(mainObject).usingRecursiveComparison().isEqualTo(jsonBoolean);
    }

    @Test
    void buildString() throws URISyntaxException, IOException {
        // GIVEN
        JsonString jsonString = new JsonString("myString");
        jsonString.setMaxLength(50);
        jsonString.setMinLength(5);
        jsonString.setFormat("email");

        String schema = new String(Files.readAllBytes(Paths.get(JsonObjectsFactoryTest.class.getResource("/string-simple-structure.json").toURI())));
        JsonNode jsonSchema = OBJECT_MAPPER.readTree(schema);

        // WHEN
        AbstractJsonObject mainObject = JsonObjectsFactory.getInstance().build("myString", jsonSchema, jsonSchema);

        //THEN
        assertThat(mainObject).isExactlyInstanceOf(JsonString.class);
        assertThat(mainObject).usingRecursiveComparison().isEqualTo(jsonString);
    }

    @Test
    void buildNumber() throws URISyntaxException, IOException {
        // GIVEN
        JsonNumber jsonNumber = new JsonNumber("myNumber");
        jsonNumber.setMaximum(50);
        jsonNumber.setMinimum(5);
        jsonNumber.setMaxExclu(true);
        jsonNumber.setMinExclu(false);

        String schema = new String(Files.readAllBytes(Paths.get(JsonObjectsFactoryTest.class.getResource("/number-simple-structure.json").toURI())));
        JsonNode jsonSchema = OBJECT_MAPPER.readTree(schema);

        // WHEN
        AbstractJsonObject mainObject = JsonObjectsFactory.getInstance().build("myNumber", jsonSchema, jsonSchema);

        //THEN
        assertThat(mainObject).isExactlyInstanceOf(JsonNumber.class);
        assertThat(mainObject).usingRecursiveComparison().isEqualTo(jsonNumber);
    }

    @Test
    void buildInteger() throws URISyntaxException, IOException {
        // GIVEN
        JsonInteger jsonInteger = new JsonInteger("myInteger");
        jsonInteger.setMaximum(50);
        jsonInteger.setMinimum(5);
        jsonInteger.setMaxExclu(true);
        jsonInteger.setMinExclu(false);
        jsonInteger.setMultipleOf(20);

        String schema = new String(Files.readAllBytes(Paths.get(JsonObjectsFactoryTest.class.getResource("/integer-simple-structure.json").toURI())));
        JsonNode jsonSchema = OBJECT_MAPPER.readTree(schema);

        // WHEN
        AbstractJsonObject mainObject = JsonObjectsFactory.getInstance().build("myInteger", jsonSchema, jsonSchema);

        //THEN
        assertThat(mainObject).isExactlyInstanceOf(JsonInteger.class);
        assertThat(mainObject).usingRecursiveComparison().isEqualTo(jsonInteger);
    }

    @Test
    void buildArray() throws URISyntaxException, IOException {
        // GIVEN
        JsonArray jsonArray = new JsonArray();
        jsonArray.setUniqueItems(true);
        jsonArray.setMinItems(5);
        jsonArray.setMaxItems(20);
        final JsonString jsonString = new JsonString();
        jsonString.setMaxLength(50);
        jsonArray.setItems(jsonString);

        String schema = new String(Files.readAllBytes(Paths.get(JsonObjectsFactoryTest.class.getResource("/array-simple-structure.json").toURI())));
        JsonNode jsonSchema = OBJECT_MAPPER.readTree(schema);

        // WHEN
        AbstractJsonObject mainObject = JsonObjectsFactory.getInstance().build(null, jsonSchema, jsonSchema);

        //THEN
        assertThat(mainObject).isExactlyInstanceOf(JsonArray.class);
        assertThat(mainObject).usingRecursiveComparison().isEqualTo(jsonArray);
    }

    @Test
    void buildObject() throws URISyntaxException, IOException {
        // GIVEN
        JsonObject jsonObject = new JsonObject();
        jsonObject.setMainObject(false);
        jsonObject.addRequired("name");
        final JsonString jsonString = new JsonString("name");
        jsonString.setMaxLength(50);
        jsonObject.addProperties("name", jsonString);

        String schema = new String(Files.readAllBytes(Paths.get(JsonObjectsFactoryTest.class.getResource("/object-simple-structure.json").toURI())));
        JsonNode jsonSchema = OBJECT_MAPPER.readTree(schema);

        // WHEN
        AbstractJsonObject mainObject = JsonObjectsFactory.getInstance().build(null, jsonSchema, jsonSchema);

        //THEN
        assertThat(mainObject).isExactlyInstanceOf(JsonObject.class);
        assertThat(mainObject).usingRecursiveComparison().isEqualTo(jsonObject);
    }

    @Test
    void buildProductObject() throws URISyntaxException, IOException {
        // GIVEN
        final JsonString nameJsonString = new JsonString("name");
        nameJsonString.setMaxLength(50);

        JsonNumber priceJsonNumber = new JsonNumber("price");
        priceJsonNumber.setMaximum(1000);
        priceJsonNumber.setMinimum(0);

        JsonNumber lengthJsonNumber = new JsonNumber("length");
        lengthJsonNumber.setMaximum(1000);
        lengthJsonNumber.setMinimum(0);

        JsonNumber widthJsonNumber = new JsonNumber("width");
        widthJsonNumber.setMaximum(1000);
        widthJsonNumber.setMinimum(0);

        JsonNumber heightJsonNumber = new JsonNumber("height");
        heightJsonNumber.setMaximum(1000);
        heightJsonNumber.setMinimum(0);

        JsonNumber latitudeJsonNumber = new JsonNumber("latitude");
        latitudeJsonNumber.setMaximum(90);
        latitudeJsonNumber.setMinimum(-90);

        JsonNumber longitudeJsonNumber = new JsonNumber("longitude");
        longitudeJsonNumber.setMaximum(180);
        longitudeJsonNumber.setMinimum(-180);

        JsonArray tagsJsonArray = new JsonArray("tags");
        tagsJsonArray.setUniqueItems(false);
        tagsJsonArray.setMinItems(1);
        tagsJsonArray.setMaxItems(20);
        final JsonString jsonString = new JsonString("tags");
        jsonString.setMaxLength(50);
        tagsJsonArray.setItems(jsonString);

        JsonObject jsonDimensionObject = new JsonObject("dimensions");
        jsonDimensionObject.addProperties("length", lengthJsonNumber);
        jsonDimensionObject.addProperties("width", widthJsonNumber);
        jsonDimensionObject.addProperties("height", heightJsonNumber);
        jsonDimensionObject.addRequired("length");
        jsonDimensionObject.addRequired("width");

        JsonObject jsonLocationObject = new JsonObject("warehouseLocation");
        jsonLocationObject.addProperties("latitude", latitudeJsonNumber);
        jsonLocationObject.addProperties("longitude", longitudeJsonNumber);
        jsonLocationObject.addRequired("latitude");
        jsonLocationObject.addRequired("longitude");

        JsonObject jsonProductObject = new JsonObject();
        jsonProductObject.setMainObject(false);
        jsonProductObject.addProperties("price", priceJsonNumber);
        jsonProductObject.addProperties("name", nameJsonString);
        jsonProductObject.addProperties("tags", tagsJsonArray);
        jsonProductObject.addProperties("dimensions", jsonDimensionObject);
        jsonProductObject.addProperties("warehouseLocation", jsonLocationObject);
        jsonProductObject.addRequired("name");
        jsonProductObject.addRequired("price");
        jsonProductObject.addRequired("dimensions");

        String schema = new String(Files.readAllBytes(Paths.get(JsonObjectsFactoryTest.class.getResource("/product-structure.json").toURI())));
        JsonNode jsonSchema = OBJECT_MAPPER.readTree(schema);

        // WHEN
        AbstractJsonObject mainObject = JsonObjectsFactory.getInstance().build(null, jsonSchema, jsonSchema);

        //THEN
        assertThat(mainObject).isExactlyInstanceOf(JsonObject.class);
        assertThat(mainObject).usingRecursiveComparison().isEqualTo(jsonProductObject);
    }
}
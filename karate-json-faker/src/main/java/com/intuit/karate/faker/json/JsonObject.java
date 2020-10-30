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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 *
 * @author sixdouglas
 */
public class JsonObject extends AbstractJsonObject {
    private final Map<String, AbstractJsonObject> properties = new HashMap<>();
    private final Set<String> required = new HashSet<>();

    public JsonObject() {
    }

    public JsonObject(String name) {
        super(name);
    }

    @Override
    public String generateValue() {
        final Map<String, AbstractJsonObject> workingProperties = new HashMap<>(properties);
        final List<String> notRequiredProperties = workingProperties.keySet().stream()
                .filter(jsonObjectEntry -> !required.contains(jsonObjectEntry))
                .collect(Collectors.toList());

        if (notRequiredProperties.size() > 0) {
            int anInt = ThreadLocalRandom.current().nextInt(0, notRequiredProperties.size());
            Collections.shuffle(notRequiredProperties);
            notRequiredProperties
                    .subList(0, anInt)
                    .forEach(workingProperties::remove);
        }

        String propertiesString = workingProperties.values()
                .stream()
                .map(AbstractJsonObject::generateValue)
                .collect(Collectors.joining(", "));
        if (mainObject) {
            return String.format(JSON_MAIN_OBJECT_PATTERN, propertiesString);
        } else {
            return String.format(JSON_PROPERTY_OBJECT_PATTERN, getName(), propertiesString);
        }
    }

    public Map<String, AbstractJsonObject> getProperties() {
        return properties;
    }

    public void addProperties(String name, AbstractJsonObject property) {
        this.properties.put(name, property);
    }

    public Set<String> getRequired() {
        return required;
    }

    public void addRequired(String required) {
        this.required.add(required);
    }
}

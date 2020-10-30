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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 * @author sixdouglas
 */
public class JsonArray extends AbstractJsonObject {
    private AbstractJsonObject items;
    private int minItems = 1;
    private int maxItems = 150;
    private boolean uniqueItems = false;

    public JsonArray() {
        super();
    }

    public JsonArray(String name) {
        super(name);
    }

    @Override
    public String generateValue() {
        int anInt = ThreadLocalRandom.current().nextInt(minItems, maxItems);
        List<String> itemsList = IntStream.range(1, anInt)
                .mapToObj(operand -> items.generateValue())
                .collect(Collectors.toList());

        if (uniqueItems) {
            Set<String> itemsSet = new HashSet<>(itemsList);
            itemsList.clear();
            itemsList.addAll(itemsSet);
        } else {
            itemsList.addAll(itemsList.subList(0, itemsList.size() / 3));
        }

        return String.format(JSON_ARRAY_PATTERN, getName(), String.join(", ", itemsList));
    }

    public AbstractJsonObject getItems() {
        return items;
    }

    public void setItems(AbstractJsonObject items) {
        items.setMainObject(true);
        this.items = items;
    }

    public int getMinItems() {
        return minItems;
    }

    public void setMinItems(int minItems) {
        this.minItems = minItems;
    }

    public int getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    public boolean isUniqueItems() {
        return uniqueItems;
    }

    public void setUniqueItems(boolean uniqueItems) {
        this.uniqueItems = uniqueItems;
    }
}

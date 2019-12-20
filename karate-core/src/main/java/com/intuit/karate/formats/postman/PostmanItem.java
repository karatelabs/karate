/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.formats.postman;

import java.util.List;
import java.util.Optional;

/**
 * @author vmchukky
 */

//http://schema.getpostman.com/json/collection/v2.1.0/docs/index.html
public class PostmanItem {

    private String name;
    private PostmanRequest request;             // schema says request is mandatory but have seen example collections without it
    private Optional<PostmanItem> parent;       // top level item (without a parent becomes a scenario)
    private Optional<List<PostmanItem>> items;  // items within an item become part of the same scenario associated with parent

    public PostmanItem() {
        this.items = Optional.empty();
        this.parent = Optional.empty();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = (name == null) ? "" : name;
    }

    public PostmanRequest getRequest() {
        return request;
    }

    public void setRequest(PostmanRequest request) {
        this.request = request;
    }

    public Optional<PostmanItem> getParent() {
        return parent;
    }

    public void setParent(Optional<PostmanItem> parent) {
        this.parent = parent;
    }

    public Optional<List<PostmanItem>> getItems() {
        return items;
    }

    public void setItems(Optional<List<PostmanItem>> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[name: ").append(name);
        if (items.isPresent()) {
            sb.append(items.toString());
        } else {
            sb.append(", request: ").append(request.toString());
        }
        sb.append("]");
        return sb.toString();
    }

    public String convert() {
        StringBuilder sb = new StringBuilder();

        sb.append(parent.isPresent() ? "# " : "\tScenario: ");
        sb.append(name).append(System.lineSeparator());
        if (items.isPresent()) {
            for (PostmanItem item : items.get()) {
                sb.append(item.convert());
            }
        } else {
            RequestBuilder builder = new RequestBuilder();
            sb.append(builder.addUrl(request.getUrl())
                    .addHeaders(request.getHeaders())
                    .addBody(request.getBody())
                    .addMethod(request.getMethod())
                    .build());
        }
        return sb.toString();
    }

}

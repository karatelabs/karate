package com.intuit.karate.mock.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class KarateMessage<T> {
    private final Map<String, String> properties = new HashMap<>();
    private final Map<String, String> headers = new HashMap<>();
    private T body;

    public void addProperties(Map<String, String> propertiesToAdd) {
        if (propertiesToAdd != null) {
            properties.putAll(propertiesToAdd);
        }
    }
    public void removeProperties(Map<String, String> propertiesToRemove) {
        if (propertiesToRemove != null) {
            propertiesToRemove.keySet().forEach(properties::remove);
        }
    }

    public void setProperties(Map<String, String> newProperties) {
        if (newProperties != null) {
            properties.clear();
            properties.putAll(newProperties);
        }
    }

    public void addHeaders(Map<String, String> headersToAdd) {
        if (headersToAdd != null) {
            headers.putAll(headersToAdd);
        }
    }

    public void removeHeaders(Map<String, String> headersToRemove) {
        if (headersToRemove != null) {
            headersToRemove.keySet().forEach(headers::remove);
        }
    }

    public void setHeaders(Map<String, String> newHeaders) {
        if (newHeaders != null) {
            headers.clear();
            headers.putAll(newHeaders);
        }
    }

    public void setBody(T body) {
        this.body = body;
    }

    public Map<String, String> getProperties() {
        return new HashMap<>(properties);
    }

    public Map<String, String> getHeaders() {
        return new HashMap<>(headers);
    }

    public T getBody() {
        return body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KarateMessage that = (KarateMessage) o;
        return Objects.equals(properties, that.properties) &&
                Objects.equals(headers, that.headers) &&
                Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties, headers, body);
    }

    @Override
    public String toString() {
        return "KarateMessage{" +
                "properties=" + properties +
                ", headers=" + headers +
                ", body='" + body + '\'' +
                '}';
    }
}

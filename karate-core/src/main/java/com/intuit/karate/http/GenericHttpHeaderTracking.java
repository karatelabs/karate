package com.intuit.karate.http;

import java.util.HashMap;
import java.util.Map;

public class GenericHttpHeaderTracking implements HttpHeaderTracking {

    private final Map<String, String> httpHeaderReference = new HashMap<>();

    @Override
    public void putHeaderReference(String originalHeader) {
        if (originalHeader == null) {
            return;
        }

        httpHeaderReference.put(originalHeader.toLowerCase(), originalHeader);
    }

    @Override
    public String getOriginalHeader(String headerReference) {
        if (headerReference == null) {
            return null;
        }

        return httpHeaderReference.getOrDefault(headerReference.toLowerCase(), headerReference);
    }

}

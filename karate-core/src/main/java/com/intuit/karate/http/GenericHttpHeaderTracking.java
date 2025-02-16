package com.intuit.karate.http;

import java.util.HashMap;
import java.util.Map;

public class GenericHttpHeaderTracking implements HttpHeaderTracking {

    private final Map<String, String> httpHeaders = new HashMap<>();

    @Override
    public void putHeader(String originalHeader) {
        if (originalHeader == null) {
            return;
        }

        httpHeaders.put(originalHeader.toLowerCase(), originalHeader);
    }

    @Override
    public String getOriginalHeader(String header) {
        if (header == null) {
            return null;
        }

        return httpHeaders.getOrDefault(header.toLowerCase(), header);
    }

}

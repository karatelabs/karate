package com.intuit.karate.http;

public interface HttpHeaderTracking {

    void putHeader(String originalHeader);

    String getOriginalHeader(String header);

}

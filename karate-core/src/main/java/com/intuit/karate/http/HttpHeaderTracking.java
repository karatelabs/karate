package com.intuit.karate.http;

public interface HttpHeaderTracking {

    void putHeaderReference(String originalHeader);

    String getOriginalHeader(String headerReference);

}

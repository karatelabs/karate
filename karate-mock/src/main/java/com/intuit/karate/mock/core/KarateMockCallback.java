package com.intuit.karate.mock.core;

@FunctionalInterface
public interface KarateMockCallback {
    KarateMessage receive(KarateMessage req);
}

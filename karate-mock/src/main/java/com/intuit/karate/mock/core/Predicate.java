package com.intuit.karate.mock.core;

@FunctionalInterface
public interface Predicate {
    boolean check(KarateMessage req);
}

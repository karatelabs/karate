package com.intuit.karate.mock.core;

public class PredicateImpl1 implements Predicate{
    @Override
    public boolean check(KarateMessage req) {
        if(((String)req.getBody()).contains("Test1")){
            return true;
        } else {
            return false;
        }
    }
}

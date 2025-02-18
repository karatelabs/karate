package com.intuit.karate.mock.core;

public class PredicateImpl2 implements Predicate{
    @Override
    public boolean check(KarateMessage req) {
        if(((String)req.getBody()).contains("TestPayload")){
            return true;
        } else {
            return false;
        }
    }
}

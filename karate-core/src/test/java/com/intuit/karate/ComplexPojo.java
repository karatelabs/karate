package com.intuit.karate;

import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class ComplexPojo {
    
    private String foo;
    private int bar;
    private Map<String, String> baz;
    private List<ComplexPojo> ban;

    public String getFoo() {
        return foo;
    }

    public void setFoo(String foo) {
        this.foo = foo;
    }

    public int getBar() {
        return bar;
    }

    public void setBar(int bar) {
        this.bar = bar;
    }

    public Map<String, String> getBaz() {
        return baz;
    }

    public void setBaz(Map<String, String> baz) {
        this.baz = baz;
    } 

    public List<ComplexPojo> getBan() {
        return ban;
    }

    public void setBan(List<ComplexPojo> ban) {
        this.ban = ban;
    }        
    
}

package io.karatelabs.core.interop;

/**
 * Simple POJO for testing Java interop with Karate's JavaScript engine.
 */
public class SimplePojo {

    private String foo;
    private int bar;

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

}

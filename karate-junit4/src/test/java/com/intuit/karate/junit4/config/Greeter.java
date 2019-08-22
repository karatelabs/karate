package com.intuit.karate.junit4.config;

/**
 *
 * @author pthomas3
 */
public class Greeter {
    
    public static final Greeter INSTANCE = new Greeter();
    
    private Greeter() {
        // only static methods
    }
    
    public String sayHello(String name) {
        return "Hello " + name + "!";
    }
    
}

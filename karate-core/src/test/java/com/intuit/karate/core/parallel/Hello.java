package com.intuit.karate.core.parallel;

import java.util.function.Function;

/**
 *
 * @author pthomas3
 */
public class Hello {

    public static String sayHello(String message) {
        return "hello " + message;
    }

    public static Function<String, String> sayHelloFactory() {
        return s -> sayHello(s);
    }

}

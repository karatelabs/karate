package com.intuit.karate.graal;

import java.util.function.Function;

/**
 *
 * @author pthomas3
 */
public class StaticPojo {

    public static String sayHello(String input) {
        return "hello " + input;
    }

    public static Function<String, String> sayHelloFactory() {
        return s -> sayHello(s);
    }

}

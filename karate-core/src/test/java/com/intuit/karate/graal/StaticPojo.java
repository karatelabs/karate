package com.intuit.karate.graal;

import com.intuit.karate.Json;
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
    
    public static String convert(Object o) {
        return Json.of(o).toString();
    }

}

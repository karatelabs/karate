package com.intuit.karate.demo.util;

/**
 *
 * @author pthomas3
 */
public class FizzBuzz {

    public static boolean isMultiple(int n, int i) {
        return n % i == 0;
    }

    public static boolean isFizzy(int n) {
        return isMultiple(n, 3);
    }

    public static boolean isBuzzy(int n) {
        return isMultiple(n, 5);
    }

    public static String process(int n) {
        return isFizzy(n) ? isBuzzy(n)
                ? "FizzBuzz"
                : "Fizz"
                : isBuzzy(n)
                ? "Buzz"
                : n + "";
    }

}

package com.intuit.karate.core;

import java.util.List;

/**
 *
 * @author pthomas3
 */
public class StaticUtils {
    
    private StaticUtils() {
        // only static methods
    }
    
    public static String concat(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            sb.append(s);
        }
        return sb.toString();
    }
    
    public static String fromInt(int num) {
        return "value is " + num;
    }    
    
    public static String fromDouble(double num) {
        return "value is " + num;
    }
    
    public static String fromNumber(Number num) {
        return "value is " + num.doubleValue();
    }    
    
}

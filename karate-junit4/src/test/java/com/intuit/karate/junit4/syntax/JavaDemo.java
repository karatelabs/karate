package com.intuit.karate.junit4.syntax;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class JavaDemo { // this class is for demo, called from the syntax.feature
    
    private static final Logger logger = LoggerFactory.getLogger(JavaDemo.class);
    
    public Map<String, Object> doWork(String fromJs) {
        logger.info("function called from karate with argument: {}", fromJs);
        Map<String, Object> map = new HashMap<>();
        map.put("someKey", "hello " + fromJs);
        return map;
    }
    
    public static String doWorkStatic(String fromJs) {
        return "hello " + fromJs;
    }
    
}

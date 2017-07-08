package com.intuit.karate.demo.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class JavaDemo {

    private static final Logger logger = LoggerFactory.getLogger(JavaDemo.class);

    public static String getName(Map<String, Object> map) {
        logger.debug("java got map: {}", map);
        return (String) map.get("name");
    }

    public static List<String> getNames(List<Map<String, Object>> list) {
        logger.debug("java got list: {}", list);
        List<String> temp = new ArrayList(list.size());
        for (Map<String, Object> map : list) {
            temp.add((String) map.get("name"));
        }
        return temp;
    }

}

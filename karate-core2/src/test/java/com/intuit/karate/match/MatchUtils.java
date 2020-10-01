package com.intuit.karate.match;

import com.intuit.karate.XmlUtils;
import com.intuit.karate.data.Json;
import com.intuit.karate.data.JsonUtils;

/**
 *
 * @author pthomas3
 */
public class MatchUtils {
    
    public static Object parse(Object o) {
        if (o instanceof String) {
            String s = (String) o;
            if (JsonUtils.isJson(s)) {
                return new Json(s).asMapOrList();
            } else if (XmlUtils.isXml(s)) {
                return XmlUtils.toXmlDoc(s);
            } else {
                return o;
            }
        } else {
            return o;
        }
    }    
    
}

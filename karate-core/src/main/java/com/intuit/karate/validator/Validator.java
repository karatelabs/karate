package com.intuit.karate.validator;

import com.intuit.karate.ScriptValue;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public interface Validator {
    
    ValidationResult validate(ScriptValue value);
    
    public static Map<String, Validator> getDefaults() {
        Map<String, Validator> map = new HashMap<>();
        map.put("ignore", IgnoreValidator.INSTANCE);
        map.put("null", NullValidator.INSTANCE);
        map.put("notnull", NotNullValidator.INSTANCE);
        map.put("present", IgnoreValidator.INSTANCE); // re-use ignore, json key logic is in Script.java
        map.put("uuid", UuidValidator.INSTANCE);
        map.put("string", StringValidator.INSTANCE);
        map.put("number", NumberValidator.INSTANCE);
        map.put("boolean", BooleanValidator.INSTANCE);
        map.put("array", ArrayValidator.INSTANCE);
        map.put("object", ObjectValidator.INSTANCE);
        return map;        
    }
    
}

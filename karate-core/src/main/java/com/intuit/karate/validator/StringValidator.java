package com.intuit.karate.validator;

import com.intuit.karate.ScriptValue;

/**
 *
 * @author pthomas3
 */
public class StringValidator implements Validator {

    @Override
    public ValidationResult validate(ScriptValue value) {
        if (!value.isStringOrStream()) {
            return ValidationResult.fail("not a string");
        } else {
            return ValidationResult.PASS;
        }
    }
    
    public static final StringValidator INSTANCE = new StringValidator();
  
}

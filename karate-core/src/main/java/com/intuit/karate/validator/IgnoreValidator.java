package com.intuit.karate.validator;

import com.intuit.karate.ScriptValue;

/**
 *
 * @author pthomas3
 */
public class IgnoreValidator implements Validator {

    @Override
    public ValidationResult validate(ScriptValue value) {
        return ValidationResult.PASS;
    }
    
    public static final IgnoreValidator INSTANCE = new IgnoreValidator();
  
}

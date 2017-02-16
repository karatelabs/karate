package com.intuit.karate.validator;

import com.intuit.karate.ScriptValue;

/**
 *
 * @author pthomas3
 */
public class BooleanValidator implements Validator {

    @Override
    public ValidationResult validate(ScriptValue value) {
        Object o = value.getValue();
        if (o == null || !Boolean.class.isAssignableFrom(o.getClass())) {
            return ValidationResult.fail("not a boolean");
        }
        return ValidationResult.PASS;
    }
    
    public static final BooleanValidator INSTANCE = new BooleanValidator();
  
}

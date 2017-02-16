package com.intuit.karate.validator;

import com.intuit.karate.ScriptValue;

/**
 *
 * @author pthomas3
 */
public class NumberValidator implements Validator {

    @Override
    public ValidationResult validate(ScriptValue value) {
        if (value.getType() != ScriptValue.Type.PRIMITIVE) {
            return ValidationResult.fail("not a number");
        }
        Object o = value.getValue();
        if (Boolean.class.isAssignableFrom(o.getClass())) {
            return ValidationResult.fail("not a number");
        }
        return ValidationResult.PASS;
    }
    
    public static final NumberValidator INSTANCE = new NumberValidator();
  
}

package com.intuit.karate.validator;

import com.intuit.karate.ScriptValue;

/**
 *
 * @author pthomas3
 */
public class NotNullValidator implements Validator {

    @Override
    public ValidationResult validate(ScriptValue value) {
        if (value.isNull()) {
            return ValidationResult.fail("null");
        }
        return ValidationResult.PASS;
    }
    
    public static final NotNullValidator INSTANCE = new NotNullValidator();
    
}

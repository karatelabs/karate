package com.intuit.karate.validator;

import com.intuit.karate.ScriptValue;

/**
 *
 * @author pthomas3
 */
public class NullValidator implements Validator {

    @Override
    public ValidationResult validate(ScriptValue value) {
        if (!value.isNull()) {
            return ValidationResult.fail("not-null");
        }
        return ValidationResult.PASS;
    }

    public static final NullValidator INSTANCE = new NullValidator();
    
}

package com.intuit.karate.validator;

import com.intuit.karate.ScriptValue;

/**
 *
 * @author pthomas3
 */
public class ObjectValidator implements Validator {

    @Override
    public ValidationResult validate(ScriptValue value) {
        return value.isMapLike() ? ValidationResult.PASS : ValidationResult.fail("not a json object");
    }

    public static final ObjectValidator INSTANCE = new ObjectValidator();

}

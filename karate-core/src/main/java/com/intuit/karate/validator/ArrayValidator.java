package com.intuit.karate.validator;

import com.intuit.karate.ScriptValue;

/**
 *
 * @author pthomas3
 */
public class ArrayValidator implements Validator {

    @Override
    public ValidationResult validate(ScriptValue value) {
        return value.isListLike() ? ValidationResult.PASS : ValidationResult.fail("not an array or list");
    }

    public static final ArrayValidator INSTANCE = new ArrayValidator();

}

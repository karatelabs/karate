package com.intuit.karate.validator;

import com.intuit.karate.ScriptValue;
import com.jayway.jsonpath.DocumentContext;

/**
 *
 * @author pthomas3
 */
public class ArrayValidator implements Validator {

    @Override
    public ValidationResult validate(ScriptValue value) {
        switch (value.getType()) {
            case JSON:
                DocumentContext doc = value.getValue(DocumentContext.class);
                if (!doc.jsonString().startsWith("[")) {
                    return ValidationResult.fail("not an array or list");
                } else {
                    return ValidationResult.PASS;
                }
            case LIST:
                return ValidationResult.PASS;
            default:
                return ValidationResult.fail("not an array or list");
        }
    }

    public static final ArrayValidator INSTANCE = new ArrayValidator();

}

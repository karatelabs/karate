package com.intuit.karate.validator;

import com.intuit.karate.ScriptValue;
import com.jayway.jsonpath.DocumentContext;

/**
 *
 * @author pthomas3
 */
public class ObjectValidator implements Validator {

    @Override
    public ValidationResult validate(ScriptValue value) {
        switch (value.getType()) {
            case JSON:
                DocumentContext doc = value.getValue(DocumentContext.class);
                if (!doc.jsonString().startsWith("{")) {
                    return ValidationResult.fail("not a json object");
                } else {
                    return ValidationResult.PASS;
                }
            case MAP:
                return ValidationResult.PASS;
            default:
                return ValidationResult.fail("not a json object");
        }
    }

    public static final ObjectValidator INSTANCE = new ObjectValidator();

}

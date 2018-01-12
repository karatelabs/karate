package com.intuit.karate.validator;

import com.intuit.karate.ScriptValue;
import java.util.UUID;

/**
 *
 * @author pthomas3
 */
public class UuidValidator implements Validator {

    @Override
    public ValidationResult validate(ScriptValue value) {
        if (!value.isStringOrStream()) {
            return ValidationResult.fail("not a string");
        }
        String strValue = value.getAsString();
        try {
            UUID uuid = UUID.fromString(strValue);
            return ValidationResult.PASS;
        } catch (Exception e) {
            return ValidationResult.fail("not a valid #uuid");
        }
    }
    
    public static final UuidValidator INSTANCE = new UuidValidator();
    
}

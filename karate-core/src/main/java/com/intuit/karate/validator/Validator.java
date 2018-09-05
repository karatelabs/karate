package com.intuit.karate.validator;

import com.intuit.karate.ScriptValue;

/**
 *
 * @author pthomas3
 */
public interface Validator {
    
    ValidationResult validate(ScriptValue value);    
    
}

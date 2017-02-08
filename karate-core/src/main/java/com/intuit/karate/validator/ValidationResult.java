package com.intuit.karate.validator;

/**
 *
 * @author pthomas3
 */
public class ValidationResult {
    
    private final boolean pass;
    private final String message;
    
    public static final ValidationResult PASS = new ValidationResult(true, null);
    
    private ValidationResult(boolean pass, String message) {
        this.pass = pass;
        this.message = message;
    }

    public boolean isPass() {
        return pass;
    }    

    public String getMessage() {
        return message;
    }
    
    public static ValidationResult fail(String message) {
        return new ValidationResult(false, message);
    }
    
}

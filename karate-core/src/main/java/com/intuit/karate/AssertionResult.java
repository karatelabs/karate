package com.intuit.karate;

/**
 *
 * @author pthomas3
 */
public class AssertionResult {
    
    protected final String message;
    protected final boolean pass;
    
    public static final AssertionResult PASS = new AssertionResult(true, null);
    
    private AssertionResult(boolean pass, String message) {
        this.pass = pass;
        this.message = message;
    }
    
    public static AssertionResult fail(String message) {
        return new AssertionResult(false, message);
    }

    @Override
    public String toString() {
        return pass ? "PASSED" : "FAILED, " + message;            
    }        
    
}

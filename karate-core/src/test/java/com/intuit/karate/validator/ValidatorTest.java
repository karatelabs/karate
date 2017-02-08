package com.intuit.karate.validator;

import com.intuit.karate.ScriptValue;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class ValidatorTest {
    
    private final Validator IGNORE = IgnoreValidator.INSTANCE;
    private final Validator NOT_NULL = NotNullValidator.INSTANCE;
    private final Validator NULL = NullValidator.INSTANCE;
    
    @Test
    public void testSimpleValidators() {
        ScriptValue sv = new ScriptValue(null);
        assertTrue(IGNORE.validate(sv).isPass());
        assertTrue(NULL.validate(sv).isPass());
        assertFalse(NOT_NULL.validate(sv).isPass());
        sv = new ScriptValue(1);
        assertTrue(IGNORE.validate(sv).isPass());
        assertFalse(NULL.validate(sv).isPass());
        assertTrue(NOT_NULL.validate(sv).isPass());
        sv = new ScriptValue("foo");
        assertTrue(IGNORE.validate(sv).isPass());
        assertFalse(NULL.validate(sv).isPass());
        assertTrue(NOT_NULL.validate(sv).isPass());         
    }
    
    @Test
    public void testRegexValidator() {
        Validator v = new RegexValidator("a");
        assertFalse(v.validate(ScriptValue.NULL).isPass());
        assertFalse(v.validate(new ScriptValue("b")).isPass());
        assertFalse(v.validate(new ScriptValue(1)).isPass());
        assertTrue(v.validate(new ScriptValue("a")).isPass());
        v = new RegexValidator("[\\d]{5}");
        assertFalse(v.validate(ScriptValue.NULL).isPass());
        assertFalse(v.validate(new ScriptValue(1)).isPass());
        assertFalse(v.validate(new ScriptValue("b")).isPass());
        assertFalse(v.validate(new ScriptValue("1111")).isPass());
        assertTrue(v.validate(new ScriptValue("11111")).isPass());
    }
    
    @Test
    public void testUuidValidator() {
        Validator v = new UuidValidator();
        assertTrue(v.validate(new ScriptValue("a9f7a56b-8d5c-455c-9d13-808461d17b91")).isPass());
        assertFalse(v.validate(new ScriptValue("a9f7a56b-8d5c-455c-9d13")).isPass());
        assertFalse(v.validate(new ScriptValue("a9f7a56b8d5c455c9d13808461d17b91")).isPass());
    }
    
}

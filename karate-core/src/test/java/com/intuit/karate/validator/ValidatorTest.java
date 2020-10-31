package com.intuit.karate.validator;

import com.intuit.karate.ScriptValue;
import com.jayway.jsonpath.JsonPath;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ValidatorTest {

    final Validator IGNORE = IgnoreValidator.INSTANCE;
    final Validator NOT_NULL = NotNullValidator.INSTANCE;
    final Validator NULL = NullValidator.INSTANCE;
    final Validator STRING = StringValidator.INSTANCE;
    final Validator NUMBER = NumberValidator.INSTANCE;
    final Validator BOOLEAN = BooleanValidator.INSTANCE;
    final Validator ARRAY = ArrayValidator.INSTANCE;
    final Validator OBJECT = ObjectValidator.INSTANCE;

    @Test
    void testSimpleValidators() {
        ScriptValue sv = new ScriptValue(null);
        assertTrue(IGNORE.validate(sv).isPass());
        assertTrue(NULL.validate(sv).isPass());
        assertFalse(NOT_NULL.validate(sv).isPass());
        assertFalse(NUMBER.validate(sv).isPass());
        assertFalse(BOOLEAN.validate(sv).isPass());
        assertFalse(STRING.validate(sv).isPass());
        assertFalse(ARRAY.validate(sv).isPass());
        assertFalse(OBJECT.validate(sv).isPass());

        sv = new ScriptValue(1);
        assertTrue(IGNORE.validate(sv).isPass());
        assertFalse(NULL.validate(sv).isPass());
        assertTrue(NOT_NULL.validate(sv).isPass());
        assertTrue(NUMBER.validate(sv).isPass());
        assertFalse(BOOLEAN.validate(sv).isPass());
        assertFalse(STRING.validate(sv).isPass());
        assertFalse(ARRAY.validate(sv).isPass());
        assertFalse(OBJECT.validate(sv).isPass());

        sv = new ScriptValue(true);
        assertTrue(IGNORE.validate(sv).isPass());
        assertFalse(NULL.validate(sv).isPass());
        assertTrue(NOT_NULL.validate(sv).isPass());
        assertFalse(NUMBER.validate(sv).isPass());
        assertTrue(BOOLEAN.validate(sv).isPass());
        assertFalse(STRING.validate(sv).isPass());
        assertFalse(ARRAY.validate(sv).isPass());
        assertFalse(OBJECT.validate(sv).isPass());

        sv = new ScriptValue("foo");
        assertTrue(IGNORE.validate(sv).isPass());
        assertFalse(NULL.validate(sv).isPass());
        assertTrue(NOT_NULL.validate(sv).isPass());
        assertFalse(NUMBER.validate(sv).isPass());
        assertFalse(BOOLEAN.validate(sv).isPass());
        assertTrue(STRING.validate(sv).isPass());
        assertFalse(ARRAY.validate(sv).isPass());
        assertFalse(OBJECT.validate(sv).isPass());

        sv = new ScriptValue(JsonPath.parse("[1, 2]"));
        assertTrue(IGNORE.validate(sv).isPass());
        assertFalse(NULL.validate(sv).isPass());
        assertTrue(NOT_NULL.validate(sv).isPass());
        assertFalse(NUMBER.validate(sv).isPass());
        assertFalse(BOOLEAN.validate(sv).isPass());
        assertFalse(STRING.validate(sv).isPass());
        assertTrue(ARRAY.validate(sv).isPass());
        assertFalse(OBJECT.validate(sv).isPass());

        sv = new ScriptValue(JsonPath.parse("{ foo: 'bar' }"));
        assertTrue(IGNORE.validate(sv).isPass());
        assertFalse(NULL.validate(sv).isPass());
        assertTrue(NOT_NULL.validate(sv).isPass());
        assertFalse(NUMBER.validate(sv).isPass());
        assertFalse(BOOLEAN.validate(sv).isPass());
        assertFalse(STRING.validate(sv).isPass());
        assertFalse(ARRAY.validate(sv).isPass());
        assertTrue(OBJECT.validate(sv).isPass());
    }

    @Test
    void testRegexValidator() {
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
    void testUuidValidator() {
        Validator v = new UuidValidator();
        assertTrue(v.validate(new ScriptValue("a9f7a56b-8d5c-455c-9d13-808461d17b91")).isPass());
        assertFalse(v.validate(new ScriptValue("a9f7a56b-8d5c-455c-9d13")).isPass());
        assertFalse(v.validate(new ScriptValue("a9f7a56b8d5c455c9d13808461d17b91")).isPass());
    }

}

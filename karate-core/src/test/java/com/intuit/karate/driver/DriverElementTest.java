package com.intuit.karate.driver;

import com.intuit.karate.JsonUtils;
import com.intuit.karate.ScriptValue;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class DriverElementTest {

    @Test
    public void testToJson() {
        JsonUtils.toJsonDoc("{}");
        Element de = DriverElement.locatorExists(null, "foo");
        List list = Collections.singletonList(de);
        ScriptValue sv = new ScriptValue(list);
        sv.getAsString();
    }

}

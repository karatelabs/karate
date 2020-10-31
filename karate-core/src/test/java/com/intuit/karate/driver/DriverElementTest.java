package com.intuit.karate.driver;

import com.intuit.karate.JsonUtils;
import com.intuit.karate.ScriptValue;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class DriverElementTest {

    @Test
    void testToJson() {
        JsonUtils.toJsonDoc("{}");
        Element de = DriverElement.locatorExists(null, "foo");
        List list = Collections.singletonList(de);
        ScriptValue sv = new ScriptValue(list);
        sv.getAsString();
    }

}

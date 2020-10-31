package com.intuit.karate;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class TxTest {

    @Test
    void testTransform() {
        Map<String, Object> input = new Match().def("input", "{ firstName: 'Billie', lastName: 'Jean',"
                + " kittens: [{ kittenName: 'Bob', kittenAge: 2}, { kittenName: 'Wild', kittenAge: 3}]}").allAsMap();
        Map<String, Object> output = Runner.runFeature(getClass(), "tx-cat-json.feature", input, false);
        Match.equals(output.get("output"), "{ name: { first: 'Billie', last: 'Jean' },"
                + " kittens: [{ name: 'Bob', age: 2}, { name: 'Wild', age: 3 }] }");
    }

}

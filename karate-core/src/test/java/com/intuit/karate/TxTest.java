package com.intuit.karate;

import com.intuit.karate.Runner;
import java.util.Map;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class TxTest {
    
    @Test
    public void testTransform() {
        Map<String, Object> input = new Match().def("input", "{ firstName: 'Billie', lastName: 'Jean',"
                + " kittens: [{ kittenName: 'Bob', kittenAge: 2}, { kittenName: 'Wild', kittenAge: 3}]}").allAsMap();
        Map<String, Object> output = Runner.runFeature(getClass(), "tx-cat-json.feature", input, false);
        Match.equals(output.get("output"), "{ name: { first: 'Billie', last: 'Jean' }," 
                + " kittens: [{ name: 'Bob', age: 2}, { name: 'Wild', age: 3 }] }");
    }
    
}

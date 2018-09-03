package demo.callnested;

import cucumber.api.CucumberOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/callnested/call-nested.feature")
public class CallNestedRunner extends TestBase {
    
}

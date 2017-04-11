package demo.callonce;

import cucumber.api.CucumberOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/callonce/call-once.feature")
public class CallOnceRunner extends TestBase {
    
}

package demo.polling;

import cucumber.api.CucumberOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/polling/polling.feature")
public class PollingRunner extends TestBase {
    
}

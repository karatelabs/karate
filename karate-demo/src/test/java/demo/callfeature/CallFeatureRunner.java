package demo.callfeature;

import cucumber.api.CucumberOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/callfeature/caller.feature")
public class CallFeatureRunner extends TestBase {
    
}

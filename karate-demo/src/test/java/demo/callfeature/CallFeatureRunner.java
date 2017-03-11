package demo.callfeature;

import cucumber.api.CucumberOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/callfeature/call-feature.feature")
public class CallFeatureRunner extends TestBase {
    
}

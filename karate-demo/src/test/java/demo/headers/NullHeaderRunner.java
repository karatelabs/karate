package demo.headers;

import cucumber.api.CucumberOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/headers/null-header.feature")
public class NullHeaderRunner extends TestBase {
    
}

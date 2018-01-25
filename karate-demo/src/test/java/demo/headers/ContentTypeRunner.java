package demo.headers;

import cucumber.api.CucumberOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/headers/content-type.feature")
public class ContentTypeRunner extends TestBase {
    
}

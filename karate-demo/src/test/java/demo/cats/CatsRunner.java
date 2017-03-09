package demo.cats;

import cucumber.api.CucumberOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/cats/cats.feature")
public class CatsRunner extends TestBase {
    
}

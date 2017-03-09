package demo.cats;

import cucumber.api.CucumberOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/cats/kittens.feature")
public class KittensRunner extends TestBase {
    
}

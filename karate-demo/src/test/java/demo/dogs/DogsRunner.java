package demo.dogs;

import cucumber.api.CucumberOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/dogs/dogs.feature")
public class DogsRunner extends TestBase {
    
}

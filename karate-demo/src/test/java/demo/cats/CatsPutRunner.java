package demo.cats;

import cucumber.api.CucumberOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/cats/cats-put.feature")
public class CatsPutRunner extends TestBase {
    
}

package demo.cats;

import cucumber.api.CucumberOptions;
import demo.BaseTest;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/cats/kittens.feature")
public class KittensTest extends BaseTest {
    
}

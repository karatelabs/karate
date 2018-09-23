package demo.cats;

import com.intuit.karate.KarateOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@KarateOptions(features = "classpath:demo/cats/kittens.feature")
public class KittensRunner extends TestBase {
    
}

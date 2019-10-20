package demo.error;

import com.intuit.karate.KarateOptions;
import demo.TestBase;

/**
 *
 * @author nsehgal
 */
@KarateOptions(features = {"classpath:demo/error/no-url.feature"})
public class NoUrlErrorRunner extends TestBase {
    
}

package demo.env;

import com.intuit.karate.KarateOptions;
import demo.TestBase;

@KarateOptions(features = "classpath:demo/env/env.feature", tags = {"@devunit"}, env = "devunit")
public class EnvDevEnvRunner extends TestBase {

}

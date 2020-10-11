package demo.env;

import com.intuit.karate.KarateOptions;
import demo.TestBase;

@KarateOptions(features = "classpath:demo/env/env.feature", tags = {"@demo"}, env = "demo")
public class EnvDemoEnvRunner extends TestBase {

}

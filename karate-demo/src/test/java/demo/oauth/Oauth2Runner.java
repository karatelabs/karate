package demo.oauth;

import com.intuit.karate.junit4.Karate;
import cucumber.api.CucumberOptions;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
@CucumberOptions(features = "classpath:demo/oauth/oauth2.feature")
public class Oauth2Runner {

    @BeforeClass
    public static void beforeClass() {
        // skip 'callSingle' in karate-config.js
        System.setProperty("karate.env", "mock");
    }

}

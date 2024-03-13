package karate;

import com.intuit.karate.junit5.Karate;

class NoFeatureNoScenarioTest {

    @Karate.Test
    Karate testValidTagWithIgnoreJunitNoScenarioAssertion() {
        return Karate.run("noFeatureNoScenario")
                     .tags("@smoke")
                     .ignoreJunitNoScenariosAssertion(true)
                     .relativeTo(getClass());
    }
    @Karate.Test
    Karate testInvalidTagWithIgnoreJunitNoScenarioAssertion() {
        return Karate.run("noFeatureNoScenario")
                     .tags("@tagnotexist")
                     .ignoreJunitNoScenariosAssertion(true)
                     .relativeTo(getClass());
    }

}

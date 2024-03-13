package karate;

import com.intuit.karate.junit5.Karate;

class NoFeatureNoScenarioTest {

    @Karate.Test
    Karate testHasScenariosWithFailWhenNoScenariosFound() {
        return Karate.run("noFeatureNoScenario")
                     .tags("@smoke")
                     .failWhenNoScenariosFound(true)
                     .relativeTo(getClass());
    }

    @Karate.Test
    Karate testNoScenarios() {
        return Karate.run("noFeatureNoScenario")
                     .tags("@tagnotexist")
                     .failWhenNoScenariosFound(false)
                     .relativeTo(getClass());
    }
}

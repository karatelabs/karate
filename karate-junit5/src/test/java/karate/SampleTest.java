package karate;

import com.intuit.karate.junit5.Karate;

class SampleTest {

    @Karate.Test
    Karate testSystemProperty() {
        return Karate.run("system-property")
            .systemProperty("system-property-name", "system-property-value")
            .relativeTo(getClass());
    }

    @Karate.Test
    Karate testEnvironment() {
        return Karate.run("karate-env")
            .karateEnv("local")
            .relativeTo(getClass());
    }

    @Karate.Test
    Karate testSample() {
        return Karate.run("sample").relativeTo(getClass());
    }
    
    @Karate.Test
    Karate testTags() {
        return Karate.run("tags").tags("@second").relativeTo(getClass());
    }

    @Karate.Test
    Karate testFullPath() {
        return Karate.run("classpath:karate/tags.feature").tags("@first");
    }
    
    @Karate.Test
    Karate testAll() {
        return Karate.run()
            .karateEnv("local")
            .systemProperty("system-property-name", "system-property-value")
            .relativeTo(getClass());
    }    

}

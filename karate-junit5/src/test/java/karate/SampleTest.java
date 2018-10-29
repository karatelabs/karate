package karate;

import com.intuit.karate.junit5.Karate;
import org.junit.jupiter.api.TestFactory;

class SampleTest {

    @TestFactory
    Object testSample() {
        return Karate.feature("sample").relativeTo(getClass()).run();
    }
    
    @TestFactory
    Object testTags() {
        return Karate.feature("tags").tags("@second").relativeTo(getClass()).run();
    }

    @TestFactory
    Object testFullPath() {
        return Karate
                .feature("classpath:karate/tags.feature")
                .tags("@first").run();
    }

}

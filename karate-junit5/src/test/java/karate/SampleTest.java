package karate;

import com.intuit.karate.junit5.Karate;
import org.junit.jupiter.api.TestFactory;

public class SampleTest {

    @TestFactory
    public Object testSample() {
        return Karate.feature("sample").relativeTo(getClass()).run();
    }
    
    @TestFactory
    public Object testTags() {
        return Karate.feature("tags").tags("@second").relativeTo(getClass()).run();
    }

    @TestFactory
    public Object testFullPath() {
        return Karate
                .feature("classpath:karate/tags.feature")
                .tags("@first").run();
    }

}
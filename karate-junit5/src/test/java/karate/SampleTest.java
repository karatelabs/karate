package karate;

import com.intuit.karate.junit5.Karate;
import com.intuit.karate.junit5.KarateTest;

class SampleTest {

    @KarateTest
    Karate testSample() {
        return Karate.feature("sample").relativeTo(getClass()).build();
    }
    
    @KarateTest
    Karate testTags() {
        return Karate.feature("tags").tags("@second").relativeTo(getClass()).build();
    }

    @KarateTest
    Karate testFullPath() {
        return Karate
                .feature("classpath:karate/tags.feature")
                .tags("@first")
                .build();
    }

}

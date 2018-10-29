package karate;

import com.intuit.karate.junit5.Karate;
import com.intuit.karate.junit5.KarateFactory;

class SampleTest {

    @KarateFactory
    Karate testSample() {
        return Karate.feature("sample").relativeTo(getClass()).build();
    }
    
    @KarateFactory
    Karate testTags() {
        return Karate.feature("tags").tags("@second").relativeTo(getClass()).build();
    }

    @KarateFactory
    Karate testFullPath() {
        return Karate
                .feature("classpath:karate/tags.feature")
                .tags("@first")
                .build();
    }

}

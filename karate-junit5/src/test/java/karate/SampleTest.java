package karate;

import com.intuit.karate.junit5.Karate;

class SampleTest {

    @Karate.Test
    Karate testSample() {
        return Karate.feature("sample").relativeTo(getClass()).build();
    }
    
    @Karate.Test
    Karate testTags() {
        return Karate.feature("tags").tags("@second").relativeTo(getClass()).build();
    }

    @Karate.Test
    Karate testFullPath() {
        return Karate
                .feature("classpath:karate/tags.feature")
                .tags("@first")
                .build();
    }

}

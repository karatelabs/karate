package karate;

import com.intuit.karate.junit5.Karate;

class SampleTest {

    @Karate.Test
    Karate testSample() {
        return new Karate().feature("sample").relativeTo(getClass());
    }
    
    @Karate.Test
    Karate testTags() {
        return new Karate().feature("tags").tags("@second").relativeTo(getClass());
    }

    @Karate.Test
    Karate testFullPath() {
        return new Karate()
                .feature("classpath:karate/tags.feature")
                .tags("@first");
    }

}

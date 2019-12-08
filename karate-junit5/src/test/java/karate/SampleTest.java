package karate;

import com.intuit.karate.junit5.Karate;
import static com.intuit.karate.junit5.Karate.karate;

class SampleTest {

    @Karate.Test
    Karate testSample() {
        return karate("sample").relativeTo(getClass());
    }
    
    @Karate.Test
    Karate testTags() {
        return karate("tags").tags("@second").relativeTo(getClass());
    }

    @Karate.Test
    Karate testFullPath() {
        return karate("classpath:karate/tags.feature").tags("@first");
    }

}

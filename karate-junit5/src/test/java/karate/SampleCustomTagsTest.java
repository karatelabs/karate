package karate;

import com.intuit.karate.junit5.Karate;
import com.intuit.karate.Results;


class SampleCustomTagsTest {

    @Karate.Test
    Karate testXrayTags() {
        return Karate.run("classpath:karate/customTags.feature").outputJunitXml(true);
    }
}

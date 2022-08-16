package karate;

import com.intuit.karate.junit5.Karate;
import org.junit.jupiter.api.BeforeEach;
import com.intuit.karate.Results;


class SampleCustomTagsTest {
    
    @BeforeEach
    void beforeEach() {
        System.setProperty("custom_tags", "test, requirement");
        System.setProperty("custom_xml_tags", "test_key, requirement");
    }

    @Karate.Test
    Karate testXrayTags() {
        return Karate.run("classpath:karate/customTags.feature").outputJunitXml(true);
    }
}

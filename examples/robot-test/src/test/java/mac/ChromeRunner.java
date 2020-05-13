package mac;

import com.intuit.karate.junit5.Karate;

/**
 *
 * @author pthomas3
 */
class ChromeRunner {
    
    @Karate.Test
    Karate testChrome() {
        return Karate.run("classpath:mac/chrome.feature");
    }      
    
}

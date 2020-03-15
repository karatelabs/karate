package chrome;

import com.intuit.karate.junit5.Karate;

/**
 *
 * @author pthomas3
 */
class ChromeRunner {
    
    @Karate.Test
    Karate test() {
        return Karate.run("classpath:chrome/chrome.feature");
    }      
    
}

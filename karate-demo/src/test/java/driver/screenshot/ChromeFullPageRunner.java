package driver.screenshot;

import com.intuit.karate.FileUtils;
import com.intuit.karate.driver.chrome.Chrome;
import java.io.File;
import java.util.Collections;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ChromeFullPageRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(ChromeFullPageRunner.class);  
    
    @Test
    public void testChrome() throws Exception {
        Chrome chrome = Chrome.startHeadless();
        chrome.setUrl("https://github.com/intuit/karate/graphs/contributors");
        byte[] bytes = chrome.pdf(Collections.EMPTY_MAP);
        FileUtils.writeToFile(new File("target/fullscreen.pdf"), bytes);
        bytes = chrome.screenshot(true);
        FileUtils.writeToFile(new File("target/fullscreen.png"), bytes);
        chrome.quit();
    }
    
}

package driver.demo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.intuit.karate.FileUtils;
import com.intuit.karate.driver.chrome.Chrome;
import com.intuit.karate.driver.microsoft.EdgeChromium;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 *
 * @author pthomas3
 */
public class Demo07JavaRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(Demo07JavaRunner.class);
    
    @Test
    public void testDevtoolAccessibility() throws Exception {
        
        Chrome driver = Chrome.start();        
        driver.setUrl("https://github.com/login");
        driver.injectAndRunAxe(3);
        driver.quit();
    }
}

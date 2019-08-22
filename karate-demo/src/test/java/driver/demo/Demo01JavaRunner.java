package driver.demo;

import com.intuit.karate.FileUtils;
import com.intuit.karate.driver.chrome.Chrome;
import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Demo01JavaRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(Demo01JavaRunner.class);  
    
    @Test
    public void testChrome() throws Exception {
        
        Chrome driver = Chrome.start();        
        driver.setUrl("https://github.com/login");
        driver.input("#login_field", "dummy");
        driver.input("#password", "world");
        driver.submit().click("input[name=commit]");
        String html = driver.html("#js-flash-container");
        assertTrue(html.contains("Incorrect username or password."));
        driver.setUrl("https://google.com");
        driver.input("input[name=q]", "karate dsl");
        driver.submit().click("input[name=btnI]");
        assertEquals("https://github.com/intuit/karate", driver.getUrl());
        byte[] bytes = driver.screenshot();
        // byte[] bytes = driver.screenshotFull();
        FileUtils.writeToFile(new File("target/screenshot.png"), bytes);        
        driver.quit();
    }
    
}

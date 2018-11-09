package driver.demo;

import com.intuit.karate.FileUtils;
import com.intuit.karate.driver.Driver;
import com.intuit.karate.driver.chrome.ChromeDevToolsDriver;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Demo01PdfRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(Demo01PdfRunner.class);  
    
    @Test
    public void testChrome() throws Exception {
        Map<String, Object> options = new HashMap();
        options.put("type", "chrome");
        options.put("start", true);
        options.put("headless", true);
        Driver driver = ChromeDevToolsDriver.start(options, null);        
        driver.setLocation("https://github.com/login");
        Thread.sleep(2000);
        byte[] bytes = driver.pdf(Collections.EMPTY_MAP);
        FileUtils.writeToFile(new File("target/github.pdf"), bytes);
        bytes = driver.screenshot();
        FileUtils.writeToFile(new File("target/github.png"), bytes);
        driver.quit();
    }
    
}

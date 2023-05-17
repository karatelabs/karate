package driver.demo;

import com.intuit.karate.FileUtils;
import com.intuit.karate.driver.chrome.Chrome;
import java.io.File;
import com.intuit.karate.driver.microsoft.EdgeChromium;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author pthomas3
 */
class Demo01JavaRunner {

    static final Logger logger = LoggerFactory.getLogger(Demo01JavaRunner.class);

    @Test
    void testChrome() throws Exception {
        Chrome driver = Chrome.start();
        driver.setUrl("https://github.com/login");
        driver.input("#login_field", "dummy");
        driver.input("#password", "world");
        driver.submit().click("input[name=commit]");
        String html = driver.html(".flash-error");
        assertTrue(html.contains("Incorrect username or password."));
        driver.setUrl("https://google.com");
        driver.input("textarea[name=q]", "karate dsl");
        driver.submit().click("input[name=btnI]");
        assertEquals("https://github.com/karatelabs/karate", driver.getUrl());
        byte[] bytes = driver.screenshot();
        // byte[] bytes = driver.screenshotFull();
        FileUtils.writeToFile(new File("target/screenshot.png"), bytes);
        driver.quit();
    }

    // @Test
    void testEdge() throws Exception {
        EdgeChromium driver = EdgeChromium.start();
        driver.setUrl("https://github.com/login");
        driver.input("#login_field", "dummy");
        driver.input("#password", "world");
        driver.submit().click("input[name=commit]");
        String html = driver.html(".flash-error");
        assertTrue(html.contains("Incorrect username or password."));
        driver.setUrl("https://google.com");
        driver.input("textarea[name=q]", "karate dsl");
        driver.submit().click("input[name=btnI]");
        assertEquals("https://github.com/karatelabs/karate", driver.getUrl());
        byte[] bytes = driver.screenshot();
        // byte[] bytes = driver.screenshotFull();
        FileUtils.writeToFile(new File("target/screenshot.png"), bytes);
        driver.quit();
    }

}

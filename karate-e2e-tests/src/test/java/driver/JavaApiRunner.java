package driver;

import com.intuit.karate.Http;
import com.intuit.karate.Match;
import com.intuit.karate.driver.Driver;
import com.intuit.karate.driver.chrome.Chrome;
import com.intuit.karate.http.HttpServer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JavaApiRunner {

    static HttpServer server;

    @BeforeAll
    static void beforeAll() {
        server = ServerStarter.start(0);
    }

    @Test
    void testChromeHybrid() {
        Driver driver = Driver.start("chrome");
        String serverUrl = "http://localhost:" + server.getPort();
        driver.setUrl(serverUrl + "/05");
        Map response = Http.to(serverUrl + "/api/05").get().json().asMap();
        Match.that(response).isEqualTo("{ message: 'hello world' }");
        driver.click("button");
        driver.waitForText("#containerDiv", "hello world");        
        Map<String, Object> config = new HashMap();
        config.put("patterns", Collections.singletonList(Collections.singletonMap("urlPattern", "*/api/*")));
        config.put("mock", "src/test/java/driver/05_mock.feature"); // classpath: will also work
        Chrome chrome = (Chrome) driver; // intercept() is only supported by chrome
        chrome.intercept(config);
        driver.click("button");
        driver.waitForText("#containerDiv", "hello faked");        
        driver.quit();
    }

}

package driver;

import com.intuit.karate.Http;
import com.intuit.karate.Json;
import com.intuit.karate.Match;
import com.intuit.karate.driver.Driver;
import com.intuit.karate.driver.chrome.Chrome;
import com.intuit.karate.http.HttpServer;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class JavaApiRunner {

    static HttpServer server;
    static String serverUrl;

    @BeforeAll
    static void beforeAll() {
        server = ServerStarter.start(0);
        serverUrl = "http://localhost:" + server.getPort();
    }

    @Test
    void testChromeHybrid() {
        Driver driver = Driver.start("chrome");
        driver.setUrl(serverUrl + "/05");
        
        Map response = Http.to(serverUrl + "/api/05").get().json().asMap();
        Match.that(response).isEqualTo("{ message: 'hello world' }");
        
        driver.click("button");
        driver.waitForText("#containerDiv", "hello world");
        
        Chrome chrome = (Chrome) driver; // intercept() is only supported by chrome
        Json json = Json.of("{ patterns: [{ urlPattern: '*/api/*' }] }");
        json.set("mock", "src/test/java/driver/05_mock.feature");
        chrome.intercept(json.asMap());
        
        driver.click("button");
        driver.waitForText("#containerDiv", "hello faked");
        driver.quit();
    }

}

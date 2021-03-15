package driver;

import com.intuit.karate.Match;
import com.intuit.karate.driver.Driver;
import com.intuit.karate.http.HttpServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class JavaApiPlaywrightRunner {

    static HttpServer server;
    static String serverUrl;

    @BeforeAll
    static void beforeAll() {
        server = ServerStarter.start(0);
        serverUrl = "http://localhost:" + server.getPort();
    }

    @Test
    void testPlaywright() {
        Driver driver = Driver.start("playwright");
        driver.setUrl(serverUrl + "/01");
        driver.waitForUrl(serverUrl + "/01");
        Match.that(driver.getTitle()).isEqualTo("Page 01");
        Match.that(driver.text("#pageLoadCount")).isEqualTo("1");
        driver.refresh();
        driver.waitForText("#pageLoadCount", "2");
        driver.reload();
        driver.waitForText("#pageLoadCount", "3");        
        driver.quit();
    }

}

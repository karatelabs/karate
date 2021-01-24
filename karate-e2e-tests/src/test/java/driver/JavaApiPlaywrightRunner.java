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

    @BeforeAll
    static void beforeAll() {
        server = ServerStarter.start(0);
    }

    @Test
    void testHybrid() {
        Driver driver = Driver.start("playwright");
        String serverUrl = "http://localhost:" + server.getPort();
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

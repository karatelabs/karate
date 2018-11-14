package driver.core;

import com.intuit.karate.ui.App;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class Test01UiRunner {

    @Test
    public void testUi() {
        System.setProperty("web.url.base", "http://localhost:8080");
        App.run("src/test/java/driver/core/test-01.feature", "mock");
    }

}

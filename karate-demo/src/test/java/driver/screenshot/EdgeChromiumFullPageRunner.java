package driver.screenshot;

import com.intuit.karate.FileUtils;
import com.intuit.karate.driver.microsoft.EdgeChromium;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 *
 * @author sixdouglas
 */
class EdgeChromiumFullPageRunner {
    
    static final Logger logger = LoggerFactory.getLogger(EdgeChromiumFullPageRunner.class);
    
    @Test
    void testEdge() {
        EdgeChromium edgeChromium = EdgeChromium.startHeadless();
        edgeChromium.setUrl("https://github.com/karatelabs/karate/graphs/contributors");
        byte[] bytes = edgeChromium.pdf(Collections.EMPTY_MAP);
        FileUtils.writeToFile(new File("target/fullscreen.pdf"), bytes);
        bytes = edgeChromium.screenshot(true);
        FileUtils.writeToFile(new File("target/fullscreen.png"), bytes);
        edgeChromium.quit();
    }
    
}

package driver.screenshot;

import com.intuit.karate.FileUtils;
import com.intuit.karate.driver.microsoft.EdgeChromium;

import java.io.File;
import java.util.Collections;

/**
 *
 * @author sixdouglas
 */
public class EdgeChromiumPdfRunner {

    public static void main(String[] args) {
        EdgeChromium edgeChromium = EdgeChromium.startHeadless();
        edgeChromium.setUrl("https://github.com/login");
        byte[] bytes = edgeChromium.pdf(Collections.EMPTY_MAP);
        FileUtils.writeToFile(new File("target/github.pdf"), bytes);
        bytes = edgeChromium.screenshot();
        FileUtils.writeToFile(new File("target/github.png"), bytes);
        edgeChromium.quit();
    }
    
}

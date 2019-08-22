package driver.screenshot;

import com.intuit.karate.FileUtils;
import com.intuit.karate.driver.chrome.Chrome;
import java.io.File;
import java.util.Collections;

/**
 *
 * @author pthomas3
 */
public class ChromePdfRunner {

    public static void main(String[] args) {
        Chrome chrome = Chrome.startHeadless();
        chrome.setUrl("https://github.com/login");
        byte[] bytes = chrome.pdf(Collections.EMPTY_MAP);
        FileUtils.writeToFile(new File("target/github.pdf"), bytes);
        bytes = chrome.screenshot();
        FileUtils.writeToFile(new File("target/github.png"), bytes);
        chrome.quit();
    }
    
}

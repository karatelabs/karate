package com.intuit.karate.shell;

import java.io.File;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class ChromeRunner {
    
    private static final String CHROME_PATH = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
    
    @Test
    public void testChrome() {
        File target = new File("target/chrome" + System.currentTimeMillis());
        target.mkdirs();
        Command command = new Command(target, 
                CHROME_PATH, 
                "--remote-debugging-port=9222", 
                "--no-first-run", 
                "--user-data-dir=" + target.getAbsolutePath());
        command.run();
    }
    
}

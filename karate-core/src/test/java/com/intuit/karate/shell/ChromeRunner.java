package com.intuit.karate.shell;

import java.io.File;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ChromeRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(ChromeRunner.class);
    
    private static final String CHROME_PATH = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
    
    @Test
    public void testChrome() throws Exception {
        String uniqueName = System.currentTimeMillis() + "";
        File profileDir = new File("target/chrome" + uniqueName);
        String logFile = profileDir.getPath() + File.separator + "karate.log";
        CommandThread command = new CommandThread(logFile, profileDir, 
                CHROME_PATH, 
                "--remote-debugging-port=9222", 
                "--no-first-run", 
                "--user-data-dir=" + profileDir.getAbsolutePath());
        command.start();
        int exitCode = command.waitSync();
        logger.debug("exit code: {}", exitCode);
    }
    
}

package com.intuit.karate;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FileUtilsTester {
    
    private static final Logger logger = LoggerFactory.getLogger(FileUtilsTester.class);
    
    @Test
    public void testWaitForKeyboard() {
        FileUtils.waitForSocket(0);
    }
    
}

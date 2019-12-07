package com.intuit.karate.job;

import com.intuit.karate.shell.Command;
import java.io.File;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class JobUtilsRunner {
    
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(Command.class);
    
    @Test
    public void testZip() {
        File src = new File("target/foo");
        File dest = new File("target/test.zip");
        JobUtils.zip(src, dest);
        JobUtils.unzip(dest, new File("target/unzip"));
    }
    
}

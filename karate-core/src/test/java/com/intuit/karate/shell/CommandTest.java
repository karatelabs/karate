package com.intuit.karate.shell;

import java.io.File;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

import com.intuit.karate.FileUtils;

/**
 *
 * @author pthomas3
 */
public class CommandTest {
    
    @Test
    public void testCommand() {
    	String cmd = FileUtils.isWindows() ? "print \"hello\"" : "ls";
		CommandThread command = new CommandThread(null, null, "target/command.log", new File("src"), cmd, "-al");
		command.start();
        int exitCode = command.waitSync();
		assertEquals(exitCode, 0);        
    }
    
}

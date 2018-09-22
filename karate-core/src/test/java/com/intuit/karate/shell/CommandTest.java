package com.intuit.karate.shell;

import java.io.File;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class CommandTest {
    
    @Test
    public void testCommand() {
    	String os = System.getProperty("os.name", "");
    	String cmd = os.toLowerCase().contains("windows") ? "print \"hello\"" : "ls";
		CommandThread command = new CommandThread(CommandTest.class, "target/command.log", new File("src"), cmd, "-al");
		command.start();
        int exitCode = command.waitSync();
		assertEquals(exitCode, 0);        
    }
    
}

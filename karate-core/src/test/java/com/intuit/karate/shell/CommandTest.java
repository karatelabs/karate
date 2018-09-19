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
		CommandThread command = new CommandThread(CommandTest.class, "target/command.log", new File("src"), "ls", "-al");
		command.start();
        int exitCode = command.waitSync();
		assertEquals(exitCode, 0);        
    }
    
}

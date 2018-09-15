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
		Command command = new Command(new File("src"), "ls", "-al");
		int exitCode = command.run();
		assertEquals(exitCode, 0);        
    }
    
}

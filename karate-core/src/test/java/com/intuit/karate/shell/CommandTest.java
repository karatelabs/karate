package com.intuit.karate.shell;

import java.io.File;
import static org.junit.Assert.*;
import org.junit.Test;

import com.intuit.karate.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class CommandTest {

    private static final Logger logger = LoggerFactory.getLogger(CommandTest.class);

    @Test
    public void testCommand() {
        String cmd = FileUtils.isOsWindows() ? "print \"hello\"" : "ls";
        Command command = new Command(false, null, null, "target/command.log", new File("src"), cmd, "-al");
        command.start();
        int exitCode = command.waitSync();
        assertEquals(exitCode, 0);
    }

    @Test
    public void testCommandReturn() {
        String cmd = FileUtils.isOsWindows() ? "cmd /c dir" : "ls";
        String result = Command.execLine(new File("target"), cmd);
        assertTrue(result.contains("karate"));
    }

}

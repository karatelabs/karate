package com.intuit.karate.shell;

import java.io.File;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import com.intuit.karate.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class CommandTest {

    static final Logger logger = LoggerFactory.getLogger(CommandTest.class);

    @Test
    void testCommand() {
        String cmd = FileUtils.isOsWindows() ? "print \"hello\"" : "ls";
        Command command = new Command(false, null, null, "target/command.log", new File("src"), cmd, "-al");
        command.start();
        int exitCode = command.waitSync();
        assertEquals(exitCode, 0);
    }

    @Test
    void testCommandReturn() {
        String cmd = FileUtils.isOsWindows() ? "cmd /c dir" : "ls";
        String result = Command.execLine(new File("target"), cmd);
        assertTrue(result.contains("karate"));
    }

    @Test
    void testTokenize() {
        String[] args = Command.tokenize("hello \"foo bar\" world");
        assertEquals(3, args.length);
        assertEquals("hello", args[0]);
        assertEquals("foo bar", args[1]);
        assertEquals("world", args[2]);
        args = Command.tokenize("-Dexec.classpathScope=test \"-Dexec.args=-f json test\"");
        assertEquals(2, args.length);
        assertEquals("-Dexec.classpathScope=test", args[0]);
        assertEquals("-Dexec.args=-f json test", args[1]);
    }

}

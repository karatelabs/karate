package com.intuit.karate.debug;

import org.junit.Test;

/**
 * mvn exec:java -Dexec.mainClass="com.intuit.karate.cli.Main" -Dexec.args="-d 4711" -Dexec.classpathScope=test
 * @author pthomas3
 */
public class DapServerRunner {
    
    @Test
    public void testDap() {
        DapServer server = new DapServer(4711);
        server.waitSync();
    }
    
}

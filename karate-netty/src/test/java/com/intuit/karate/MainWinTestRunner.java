package com.intuit.karate;

import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class MainWinTestRunner {
    
    @Test
    public void testMain1() {
        System.setProperty("karate.config.dir", "C:/Users/pthomas3/git/karate/karate-netty/src/test/java");
        Main.main(new String[]{"-t", "~@ignore", "src/test/java/com/intuit/karate"});                
    }
    
    @Test
    public void testMain2() {
        System.setProperty("karate.config.dir", "src/test/java");
        Main.main(new String[]{"-t", "~@ignore", "src/test/java/com/intuit/karate"});                
    }    
    
    @Test
    public void testCurrentDirectory1() {
        System.setProperty("karate.config.dir", "");        
        Main.main(new String[]{"-t", "~@ignore", "src/test/java/com/intuit/karate"});
    } 
    
    @Test
    public void testCurrentDirectory2() {
        System.setProperty("karate.config.dir", ".");        
        Main.main(new String[]{"-t", "~@ignore", "src/test/java/com/intuit/karate"});
    } 

    @Test
    public void testCurrentDirectory3() {
        System.setProperty("karate.config.dir", "C:/Users/pthomas3/git/karate/karate-netty");        
        Main.main(new String[]{"-t", "~@ignore", "src/test/java/com/intuit/karate"});
    }    
    
}

package com.intuit.karate;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class KarateOptionsTest {
    
    @Test
    public void testArgs() {
        KarateOptions options = KarateOptions.parseStringArgs(new String[]{});
        assertNull(options.features);
        assertNull(options.tags);
        assertNull(options.name);
        options = KarateOptions.parseStringArgs(new String[]{"--name", "foo"});
        assertNull(options.features);
        assertNull(options.tags);
        assertEquals("foo", options.name);
        options = KarateOptions.parseStringArgs(new String[]{"--tags", "~@ignore"});
        assertNull(options.features);
        assertEquals("~@ignore", options.tags.get(0));
        assertNull(options.name); 
        options = KarateOptions.parseStringArgs(new String[]{"--tags", "~@ignore", "foo.feature"});        
        assertEquals("foo.feature", options.features.get(0));
        assertEquals("~@ignore", options.tags.get(0));
        assertNull(options.name);         
        
    }
    
}

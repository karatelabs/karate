package com.intuit.karate.robot.win;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class SearchPathTest {
    
    private static final Logger logger = LoggerFactory.getLogger(SearchPathTest.class);

    @Test
    public void testPathParsing() {
        List<PathSearch.Chunk> list = PathSearch.split("//hello/world//foo/bar");
        logger.debug("list: {}", list);
        PathSearch.Chunk first = list.get(0);
        assertTrue(first.anyDepth);
        assertEquals("hello", first.controlType);
        PathSearch.Chunk second = list.get(1);
        assertFalse(second.anyDepth);
        assertEquals("world", second.controlType); 
        PathSearch.Chunk third = list.get(2);
        assertTrue(third.anyDepth);
        assertEquals("foo", third.controlType);         
    }    
    
    @Test
    public void testPathEdge() {
        List<PathSearch.Chunk> list = PathSearch.split("/hello//world");
        logger.debug("list: {}", list);
        PathSearch.Chunk first = list.get(0);
        assertFalse(first.anyDepth);
        assertEquals("hello", first.controlType);
        PathSearch.Chunk second = list.get(1);
        assertTrue(second.anyDepth);  
        assertEquals("world", second.controlType);
    }    
    
}

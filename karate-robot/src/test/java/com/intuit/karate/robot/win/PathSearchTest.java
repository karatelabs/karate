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
public class PathSearchTest {

    private static final Logger logger = LoggerFactory.getLogger(PathSearchTest.class);

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

    @Test
    public void testIndex() {
        List<PathSearch.Chunk> list = PathSearch.split("/hello[3]//world");
        logger.debug("list: {}", list);
        PathSearch.Chunk first = list.get(0);
        assertFalse(first.anyDepth);
        assertEquals("hello", first.controlType);
        assertEquals(2, first.index);
        PathSearch.Chunk second = list.get(1);
        assertTrue(second.anyDepth);
        assertEquals(-1, second.index);
        assertEquals("world", second.controlType);
    }

    @Test
    public void testClassName() {
        List<PathSearch.Chunk> list = PathSearch.split("/hello[3]//world.Foo/.Bar");
        logger.debug("list: {}", list);
        PathSearch.Chunk first = list.get(0);
        assertFalse(first.anyDepth);
        assertEquals("hello", first.controlType);
        assertNull(first.className);
        assertEquals(2, first.index);
        PathSearch.Chunk second = list.get(1);
        assertTrue(second.anyDepth);
        assertEquals("world", second.controlType);
        assertEquals("Foo", second.className);
        PathSearch.Chunk third = list.get(2);
        assertFalse(third.anyDepth);
        assertEquals(null, third.controlType);
        assertEquals("Bar", third.className);
    }

    @Test
    public void testOnlyName() {
        List<PathSearch.Chunk> list = PathSearch.split("//foo//{Bar One}/{Baz}");
        logger.debug("list: {}", list);
        PathSearch.Chunk first = list.get(0);
        assertTrue(first.anyDepth);
        assertEquals("foo", first.controlType);
        assertNull(first.className);
        assertNull(first.name);
        assertEquals(-1, first.index);
        PathSearch.Chunk second = list.get(1);
        assertTrue(second.anyDepth);
        assertEquals(null, second.controlType);
        assertEquals(null, second.className);
        assertEquals("Bar One", second.name);
        PathSearch.Chunk third = list.get(2);
        assertFalse(third.anyDepth);
        assertEquals(null, third.controlType);
        assertEquals("Baz", third.name);      
    }
    
    @Test
    public void testOnlyName2() {
        List<PathSearch.Chunk> list = PathSearch.split("//listitem/{Taxpayer Information}");
        logger.debug("list: {}", list);
        PathSearch.Chunk first = list.get(0);
        assertTrue(first.anyDepth);
        assertEquals("listitem", first.controlType);
        assertNull(first.className);
        assertNull(first.name);
        assertEquals(-1, first.index);
        PathSearch.Chunk second = list.get(1);
        assertFalse(second.anyDepth);
        assertEquals(null, second.controlType);
        assertEquals(null, second.className);
        assertEquals("Taxpayer Information", second.name); 
        assertNotNull(second.nameCondition);
    }    

}

package io.karatelabs.core.interop;

import io.karatelabs.core.ScenarioRuntime;
import io.karatelabs.core.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Java interop via Java.type() and related features.
 */
class InteropTest {

    @Test
    void testJavaTypeAndNewPojo() {
        // Test Java.type() to load a class and use new to create an instance
        ScenarioRuntime sr = run("""
            * def Pojo = Java.type('io.karatelabs.core.interop.SimplePojo')
            * def pojo = new Pojo()
            * json jsonVar = pojo
            * match jsonVar == { foo: null, bar: 0 }
            """);
        assertPassed(sr);
    }

    @Test
    void testPojoSetAndGet() {
        // Test setting and getting POJO properties
        ScenarioRuntime sr = run("""
            * def Pojo = Java.type('io.karatelabs.core.interop.SimplePojo')
            * def pojo = new Pojo()
            * pojo.foo = 'hello'
            * pojo.bar = 42
            * match pojo.foo == 'hello'
            * match pojo.bar == 42
            """);
        assertPassed(sr);
    }

    @Test
    void testPojoToJson() {
        // Test converting POJO to JSON
        ScenarioRuntime sr = run("""
            * def Pojo = Java.type('io.karatelabs.core.interop.SimplePojo')
            * def pojo = new Pojo()
            * pojo.foo = 'test'
            * pojo.bar = 5
            * json jsonVar = pojo
            * match jsonVar == { foo: 'test', bar: 5 }
            """);
        assertPassed(sr);
    }

    @Test
    void testPojoToXml() {
        // Test converting POJO to XML
        ScenarioRuntime sr = run("""
            * def Pojo = Java.type('io.karatelabs.core.interop.SimplePojo')
            * def pojo = new Pojo()
            * xml xmlVar = pojo
            * match xmlVar == <root><foo></foo><bar>0</bar></root>
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateToBean() {
        // Test karate.toBean() to create a POJO from JSON
        ScenarioRuntime sr = run("""
            * def className = 'io.karatelabs.core.interop.SimplePojo'
            * def testJson = { foo: 'hello', bar: 5 }
            * def testPojo = karate.toBean(testJson, className)
            * assert testPojo.foo == 'hello'
            * assert testPojo.bar == 5
            """);
        assertPassed(sr);
    }

    @Test
    void testCatConstructorAndSetters() {
        // Test creating Cat with constructor and setters
        ScenarioRuntime sr = run("""
            * def Cat = Java.type('io.karatelabs.core.interop.Cat')
            * def toJson = function(x){ return karate.toJson(x, true) }
            * def billie = new Cat()
            * billie.id = 1
            * billie.name = 'Billie'
            * match toJson(billie) == { id: 1, name: 'Billie' }
            """);
        assertPassed(sr);
    }

    @Test
    void testCatToBeanWithKittens() {
        // Test karate.toBean() with nested Cat objects
        ScenarioRuntime sr = run("""
            * def catType = 'io.karatelabs.core.interop.Cat'
            * def toCat = function(x){ return karate.toBean(x, catType) }
            * def toJson = function(x){ return karate.toJson(x, true) }
            * def billie = toCat({ id: 1, name: 'Billie' })
            * def bob = toCat({ id: 2, name: 'Bob' })
            * def wild = toCat({ id: 3, name: 'Wild' })
            * billie.addKitten(bob)
            * billie.addKitten(wild)
            * def result = toJson(billie)
            * match result == { id: 1, name: 'Billie', kittens: [{ id: 2, name: 'Bob' }, { id: 3, name: 'Wild' }] }
            """);
        assertPassed(sr);
    }

    @Test
    void testCatKittensWithToJava() {
        // Test setting kittens list using karate.toJava()
        ScenarioRuntime sr = run("""
            * def catType = 'io.karatelabs.core.interop.Cat'
            * def toCat = function(x){ return karate.toBean(x, catType) }
            * def toJson = function(x){ return karate.toJson(x, true) }
            * def billie = toCat({ id: 1, name: 'Billie' })
            * def names = ['Bob', 'Wild']
            * def fun = function(n, i){ return { id: i + 2, name: n } }
            * def kittens = karate.map(names, fun)
            * billie.kittens = karate.toJava(kittens)
            * match toJson(billie) contains { kittens: '#[2]' }
            """);
        assertPassed(sr);
    }

}

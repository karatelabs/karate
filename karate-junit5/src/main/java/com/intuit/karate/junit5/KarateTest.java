package com.intuit.karate.junit5;

import org.junit.jupiter.api.TestFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestFactory
public @interface KarateTest {
    
}

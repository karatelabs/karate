package com.intuit.karate.core;

import cucumber.api.java.en.When;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class StepRuntimeTest {

    static final Logger logger = LoggerFactory.getLogger(StepRuntimeTest.class);



    @ParameterizedTest
    @MethodSource("testParameters")
    public void testConversionMethodToStringAndBack(String methodSignature, Class<?> methodClass, Method method, List<String> args, String karateExpr) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        StepRuntime.MethodMatch methodMatch = StepRuntime.MethodMatch.getBySignatureAndArgs(methodSignature);

        Assertions.assertNotNull(methodMatch);
        Assertions.assertEquals(method, methodMatch.method);
        Assertions.assertEquals(args, methodMatch.args);
        Assertions.assertEquals(methodSignature, methodMatch.toString());

        // it's ok reflection here, just for unit testing.
        Method findMethodsMatchingMethod = StepRuntime.class.getDeclaredMethod("findMethodsMatching", String.class);
        findMethodsMatchingMethod.setAccessible(true);
        List<StepRuntime.MethodMatch> methodMatchList = (List<StepRuntime.MethodMatch>) findMethodsMatchingMethod.invoke(StepRuntime.class, karateExpr);

        Assertions.assertTrue(methodMatchList.stream().anyMatch(m -> m.getMethod().equals(method)));
        Assertions.assertTrue(methodMatchList.stream().anyMatch(m -> m.getArgs().equals(args)));
        Assertions.assertTrue(methodMatchList.stream().anyMatch(m -> m.toString().equals(methodSignature)));
        System.out.println();
    }

    @Test
    public void testConversionMethodWithNoParams() throws ClassNotFoundException, NoSuchMethodException {
        StepRuntime.MethodMatch methodMatch = StepRuntime.MethodMatch.getBySignatureAndArgs("com.intuit.karate.ScenarioActions.getFailedReason() []");
        Assertions.assertNotNull(methodMatch);
        Assertions.assertEquals(Class.forName("com.intuit.karate.ScenarioActions").getMethod("getFailedReason"), methodMatch.method);
        Assertions.assertEquals(new ArrayList<>(), methodMatch.args);
        Assertions.assertEquals("com.intuit.karate.ScenarioActions.getFailedReason() null", methodMatch.toString());
    }

    @ParameterizedTest
    @MethodSource("methodPatternAndKeywords")
    public void testMethodPatternAndKeywordMatch(Method scenarioActionMethod, String keyword) throws IllegalAccessException, NoSuchFieldException {
        // test for some most used Karate keywords
        When when = scenarioActionMethod.getDeclaredAnnotation(When.class);
        final String methodRegex;
        if (when != null) {
            methodRegex = when.value();
        } else {
            Action action = scenarioActionMethod.getDeclaredAnnotation(Action.class);
            if (action != null) {
                methodRegex = action.value();
            } else {
                methodRegex = null;
            }
        }

        // it's ok reflection here, just for unit testing.
        Field patternsField = StepRuntime.class.getDeclaredField("PATTERNS");
        patternsField.setAccessible(true);
        Collection<StepRuntime.MethodPattern> patterns = (Collection<StepRuntime.MethodPattern>) patternsField.get(null);

        Assertions.assertNotNull(methodRegex);
        Assertions.assertTrue(patterns.stream().anyMatch(p -> p.regex.contentEquals(methodRegex) && p.keyword.equalsIgnoreCase(keyword)));;

    }

    private static Stream<Arguments> testParameters() throws ClassNotFoundException, NoSuchMethodException {
        return Stream.of(
                Arguments.of("com.intuit.karate.ScenarioActions.print(java.lang.String) [\"'name:', name\"]",
                        com.intuit.karate.ScenarioActions.class,
                        com.intuit.karate.ScenarioActions.class.getMethod("print", String.class),
                        new ArrayList<String>() { { add("'name:', name"); }},
                        "print 'name:', name"),
                Arguments.of("com.intuit.karate.ScenarioActions.configure(java.lang.String,java.lang.String) [\"continueOnStepFailure\",\"true\"]",
                        com.intuit.karate.ScenarioActions.class,
                        com.intuit.karate.ScenarioActions.class.getMethod("configure", String.class, String.class),
                        new ArrayList<String>() { { add("continueOnStepFailure"); add("true"); }},
                        "configure continueOnStepFailure = true"),
                Arguments.of("com.intuit.karate.ScenarioActions.print(java.lang.String) [\"\\\"name:\\\", name\"]",
                        com.intuit.karate.ScenarioActions.class,
                        com.intuit.karate.ScenarioActions.class.getMethod("print", String.class),
                        new ArrayList<String>() { { add("\"name:\", name"); }},
                        "print \"name:\", name"),
                Arguments.of("com.intuit.karate.ScenarioActions.print(java.lang.String) [\"'test with\\/slash'\"]", // JSON escapes forward slash
                        com.intuit.karate.ScenarioActions.class,
                        com.intuit.karate.ScenarioActions.class.getMethod("print", String.class),
                        new ArrayList<String>() { { add("'test with/slash'"); }},
                        "print 'test with/slash'")

        );
    }

    private static Stream<Arguments> methodPatternAndKeywords() throws ClassNotFoundException, NoSuchMethodException {
        return Stream.of(
                Arguments.of(com.intuit.karate.ScenarioActions.class.getMethod("match", String.class, String.class, String.class, String.class),
                "match"),
                Arguments.of(com.intuit.karate.ScenarioActions.class.getMethod("assertTrue", String.class),
                "assert"),
                Arguments.of(com.intuit.karate.ScenarioActions.class.getMethod("status", int.class),
                "status"),
                Arguments.of(com.intuit.karate.ScenarioActions.class.getMethod("eval", String.class),
                "eval"),
                Arguments.of(com.intuit.karate.ScenarioActions.class.getMethod("evalIf", String.class),
                "if")


        );
    }
}

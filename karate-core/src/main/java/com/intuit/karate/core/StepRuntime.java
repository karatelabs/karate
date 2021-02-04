/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.core;

import com.intuit.karate.Actions;
import com.intuit.karate.Json;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.KarateException;
import com.intuit.karate.ScenarioActions;
import com.intuit.karate.StringUtils;
import cucumber.api.java.en.When;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author pthomas3
 */
public class StepRuntime {

    private StepRuntime() {
        // only static methods
    }

    static class MethodPattern {

        final String regex;
        final Method method;
        final Pattern pattern;
        final String keyword;

        MethodPattern(Method method, String regex) {
            this.regex = regex;
            this.method = method;
            try {
                pattern = Pattern.compile(regex);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // assuming all @When or @Action start with a ^, get the first word
            keyword = regex.substring(1).split(" ")[0];
        }

        List<String> match(String text) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.lookingAt()) {
                List<String> args = new ArrayList(matcher.groupCount());
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    int startIndex = matcher.start(i);
                    args.add(startIndex == -1 ? null : matcher.group(i));
                }
                return args;
            } else {
                return null;
            }
        }

        @Override
        public String toString() {
            return "\n" + pattern + " " + method.toGenericString();
        }

    }

    public static class MethodMatch {

        private static final Pattern METHOD_REGEX_PATTERN = Pattern.compile("([a-zA-Z_$][a-zA-Z\\d_$\\.]*)*\\.([a-zA-Z_$][a-zA-Z\\d_$]*?)\\((.*)\\)");

        final Method method;
        final List<String> args;

        MethodMatch(Method method, List<String> args) {
            this.method = method;
            this.args = args;
        }

        Object[] convertArgs(Object last) {
            Class[] types = method.getParameterTypes();
            Object[] result = new Object[types.length];
            int i = 0;
            for (String arg : args) {
                Class type = types[i];
                if (List.class.isAssignableFrom(type)) {
                    result[i] = StringUtils.split(arg, ',', false);
                } else if (int.class.isAssignableFrom(type)) {
                    result[i] = Integer.valueOf(arg);
                } else { // string
                    result[i] = arg;
                }
                i++;
            }
            if (last != null) {
                result[i] = last;
            }
            return result;
        }

        public static MethodMatch getBySignatureAndArgs(String methodReference) {

            String methodSignature = methodReference.substring(0, methodReference.indexOf(' '));
            String referenceArgs = methodReference.substring(methodReference.indexOf(' ') + 1);
            Matcher methodMatch = METHOD_REGEX_PATTERN.matcher(methodSignature);

            Method method = null;
            if (methodMatch.find()) {
                try {
                    String className = methodMatch.group(1);
                    String methodName = methodMatch.group(2);
                    String params = methodMatch.group(3);
                    List<String> paramList = Arrays.asList(params.split(","));
                    method = Class.forName(className).getMethod(methodName, paramList.stream().map(param -> {
                        try {
                            return Class.forName(param);
                        } catch (ClassNotFoundException e) {
                            return null;
                        }
                    }).filter(Objects::nonNull).toArray(Class<?>[]::new));
                } catch (ClassNotFoundException | NoSuchMethodException e) {
                    return null;
                }
            }

            List<String> args = "null".equalsIgnoreCase(referenceArgs) ? null : Json.of(JsonUtils.fromJson(referenceArgs)).asList();
            return new MethodMatch(method, args);
        }

        public Method getMethod() {
            return method;
        }

        public List<String> getArgs() {
            return args;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(method.getDeclaringClass().getName());
            sb.append(".");
            sb.append(method.getName());
            sb.append("(");
            StringJoiner sj = new StringJoiner(",");
            for (Class<?> parameterType : method.getParameterTypes()) {
                sj.add(parameterType.getTypeName());
            }
            sb.append(sj);
            sb.append(")");

            return sb.toString() + " " + (args == null || args.isEmpty() ? "null" : JsonUtils.toJson(args));
        }

    }

    private static final Collection<MethodPattern> PATTERNS;
    private static final Map<String, Collection<Method>> KEYWORDS_METHODS;
    public static final Collection<Method> METHOD_MATCH;

    static {
        Map<String, MethodPattern> temp = new HashMap();
        List<MethodPattern> overwrite = new ArrayList();
        KEYWORDS_METHODS = new HashMap();
        for (Method method : ScenarioActions.class.getMethods()) {
            When when = method.getDeclaredAnnotation(When.class);
            if (when != null) {
                String regex = when.value();
                MethodPattern methodPattern = new MethodPattern(method, regex);
                temp.put(regex, methodPattern);

                Collection<Method> keywordMethods = KEYWORDS_METHODS.computeIfAbsent(methodPattern.keyword, k -> new HashSet<>());
                keywordMethods.add(methodPattern.method);
            } else {
                Action action = method.getDeclaredAnnotation(Action.class);
                if (action != null) {
                    String regex = action.value();
                    MethodPattern methodPattern = new MethodPattern(method, regex);
                    overwrite.add(methodPattern);
                }
            }
        }

        for (MethodPattern mp : overwrite) {
            temp.put(mp.regex, mp);

            Collection<Method> keywordMethods = KEYWORDS_METHODS.computeIfAbsent(mp.keyword, k -> new HashSet<>());
            keywordMethods.add(mp.method);
        }
        PATTERNS = temp.values();
        METHOD_MATCH = findMethodsByKeyword("match");
    }

    private static List<MethodMatch> findMethodsMatching(String text) {
        List<MethodMatch> matches = new ArrayList(1);
        for (MethodPattern pattern : PATTERNS) {
            List<String> args = pattern.match(text);
            if (args != null) {
                matches.add(new MethodMatch(pattern.method, args));
            }
        }
        return matches;
    }

    public static Collection<Method> findMethodsByKeywords(List<String> text) {
        Collection<Method> methods = new HashSet();
        text.forEach(m -> {
            methods.addAll(findMethodsByKeyword(m));
        });
        return methods;
    }

    public static Collection<Method> findMethodsByKeyword(String text) {
        return KEYWORDS_METHODS.get(text);
    }

    private static long getElapsedTimeNanos(long startTime) {
        return System.nanoTime() - startTime;
    }

    public static Result execute(Step step, Actions actions) {
        String text = step.getText();
        List<MethodMatch> matches = findMethodsMatching(text);
        if (matches.isEmpty()) {
            KarateException e = new KarateException("no step-definition method match found for: " + text);
            return Result.failed(0, e, step);
        } else if (matches.size() > 1) {
            KarateException e = new KarateException("more than one step-definition method matched: " + text + " - " + matches);
            return Result.failed(0, e, step);
        }
        MethodMatch match = matches.get(0);
        Object last;
        if (step.getDocString() != null) {
            last = step.getDocString();
        } else if (step.getTable() != null) {
            last = step.getTable().getRowsAsMaps();
        } else {
            last = null;
        }
        Object[] args;
        try {
            args = match.convertArgs(last);
        } catch (Exception ignored) { // edge case where user error causes [request =] to match [request docstring]
            KarateException e = new KarateException("no step-definition method match found for: " + text);
            return Result.failed(0, e, step);
        }
        long startTime = System.nanoTime();
        try {
            match.method.invoke(actions, args);
            if (actions.isAborted()) {
                return Result.aborted(getElapsedTimeNanos(startTime), match);
            } else if (actions.isFailed()) {
                return Result.failed(getElapsedTimeNanos(startTime), actions.getFailedReason(), step, match);
            } else {
                return Result.passed(getElapsedTimeNanos(startTime), match);
            }
        } catch (InvocationTargetException e) {
            return Result.failed(getElapsedTimeNanos(startTime), e.getTargetException(), step, match);
        } catch (Exception e) {
            return Result.failed(getElapsedTimeNanos(startTime), e, step, match);
        }
    }

}

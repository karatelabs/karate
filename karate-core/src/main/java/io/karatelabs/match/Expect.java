/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.match;

import io.karatelabs.common.Json;
import io.karatelabs.js.Context;
import io.karatelabs.js.JavaCallable;
import io.karatelabs.js.JsRegex;
import io.karatelabs.js.SimpleObject;
import io.karatelabs.js.Terms;
import io.karatelabs.parser.Node;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Chai-style BDD assertion API for Karate.
 * <p>
 * Usage:
 * <pre>
 * karate.expect(actual).to.equal(expected)
 * karate.expect(actual).to.be.a('string')
 * karate.expect(actual).to.have.property('name')
 * karate.expect(actual).to.not.equal(unexpected)
 * </pre>
 */
public class Expect implements SimpleObject {

    private final Object subject;
    private final boolean negated;
    private final BiConsumer<Context, Result> onResult;
    private final boolean throwOnFailure;
    private final Supplier<Context> contextSupplier;

    // Lazy-initialized chain objects
    private final SimpleObject expectTo;
    private final SimpleObject expectToBe;
    private final SimpleObject expectToBeAt;
    private final SimpleObject expectToDeep;
    private final SimpleObject expectToHave;
    private final SimpleObject expectToHaveAll;
    private final SimpleObject expectToHaveAny;
    private final SimpleObject expectToHaveNested;

    public Expect(Object subject) {
        this(subject, false, null, true, null);
    }

    public Expect(Object subject, BiConsumer<Context, Result> onResult) {
        this(subject, false, onResult, true, null);
    }

    /**
     * Creates an Expect instance with control over throw behavior.
     *
     * @param subject        the value to make assertions on
     * @param negated        whether assertions should be negated
     * @param onResult       callback for handling assertion results (can be null)
     * @param throwOnFailure if true, throws AssertionError on failures; if false, only calls onResult
     */
    public Expect(Object subject, boolean negated, BiConsumer<Context, Result> onResult, boolean throwOnFailure) {
        this(subject, negated, onResult, throwOnFailure, null);
    }

    /**
     * Creates an Expect instance with full control over behavior.
     *
     * @param subject         the value to make assertions on
     * @param negated         whether assertions should be negated
     * @param onResult        callback for handling assertion results (can be null)
     * @param throwOnFailure  if true, throws AssertionError on failures; if false, only calls onResult
     * @param contextSupplier optional supplier for getting a context when none is available (e.g., for property-style assertions)
     */
    public Expect(Object subject, boolean negated, BiConsumer<Context, Result> onResult, boolean throwOnFailure, Supplier<Context> contextSupplier) {
        this.subject = subject;
        this.negated = negated;
        this.onResult = onResult;
        this.throwOnFailure = throwOnFailure;
        this.contextSupplier = contextSupplier;

        // Initialize chain objects
        this.expectToBeAt = initExpectToBeAt();
        this.expectToBe = initExpectToBe();
        this.expectToDeep = initExpectToDeep();
        this.expectToHaveAll = initExpectToHaveAll();
        this.expectToHaveAny = initExpectToHaveAny();
        this.expectToHaveNested = initExpectToHaveNested();
        this.expectToHave = initExpectToHave();
        this.expectTo = initExpectTo();
    }

    // ========== Result Handling ==========

    /**
     * Extracts the source expression from the JS Context AST.
     * Used for including the original expression in error messages.
     */
    public static String getSourceFromContext(Context context) {
        if (context == null) {
            return null;
        }
        try {
            Node node = context.getNode();
            if (node != null) {
                node = node.getParent();
            }
            return node != null ? node.getTextIncludingWhitespace() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void handleResult(Context context, Result result) {
        if (negated) {
            result = result.pass ? Result.fail("expected condition to be false") : Result.PASS;
        }
        // Use context supplier if context is null and supplier is available
        Context effectiveContext = context;
        if (effectiveContext == null && contextSupplier != null) {
            effectiveContext = contextSupplier.get();
        }
        if (onResult != null) {
            onResult.accept(effectiveContext, result);
        }
        if (!result.pass && throwOnFailure) {
            String message = result.message;
            String source = getSourceFromContext(effectiveContext);
            if (source != null) {
                message = source + "\n" + message;
            }
            throw new AssertionError(message);
        }
    }

    private JavaCallable match(Match.Type type) {
        return (context, args) -> {
            Result result = Match.evaluate(subject, null, null).is(type, args[0]);
            handleResult(context, result);
            return null;
        };
    }

    private JavaCallable matchChainable(Match.Type type) {
        return (context, args) -> {
            Result result = Match.evaluate(subject, null, null).is(type, args[0]);
            handleResult(context, result);
            return new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
        };
    }

    private JavaCallable handle(java.util.function.Function<Object, String> handler) {
        return (context, args) -> {
            String message = handler.apply(args[0]);
            Result result = message == null ? Result.PASS : Result.fail(message);
            handleResult(context, result);
            return null;
        };
    }

    private JavaCallable handleChainable(java.util.function.Function<Object, String> handler) {
        return (context, args) -> {
            String message = handler.apply(args[0]);
            Result result = message == null ? Result.PASS : Result.fail(message);
            handleResult(context, result);
            return new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
        };
    }

    // ========== Chain Initializers ==========

    private SimpleObject initExpectToBeAt() {
        return key -> switch (key) {
            case "least" -> handle(rhs -> {
                if (subject instanceof Number actual && rhs instanceof Number expected) {
                    return actual.doubleValue() >= expected.doubleValue() ? null : subject + " is not at least: " + rhs;
                } else {
                    return subject + " is not at least: " + rhs;
                }
            });
            case "most" -> handle(rhs -> {
                if (subject instanceof Number actual && rhs instanceof Number expected) {
                    return actual.doubleValue() <= expected.doubleValue() ? null : subject + " is not at most: " + rhs;
                } else {
                    return subject + " is not at most: " + rhs;
                }
            });
            default -> throw new RuntimeException("expect().to.be.at - no such api: " + key);
        };
    }

    @SuppressWarnings("unchecked")
    private SimpleObject initExpectToBe() {
        return key -> switch (key) {
            case "not" -> new Expect(subject, !negated, onResult, throwOnFailure, contextSupplier).expectToBe;
            case "that", "and", "which" -> this;
            case "a", "an" -> handleChainable(rhs -> {
                String expected = rhs + "";
                String actual;
                if (subject instanceof List) {
                    actual = "array";
                } else {
                    actual = Terms.typeOf(subject);
                }
                return expected.equals(actual) ? null : "actual: " + actual + ", expected: " + expected;
            });
            case "true", "false", "_true", "_false" -> {
                String k = key;
                if (k.charAt(0) == '_') {
                    k = k.substring(1);
                }
                Result result = Boolean.valueOf(k).equals(subject) ? Result.PASS : Result.fail("actual: " + subject + ", expected: " + k);
                handleResult(null, result);
                yield new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
            }
            case "null", "_null" -> {
                Result result = subject == null ? Result.PASS : Result.fail("actual: " + subject + ", expected: null");
                handleResult(null, result);
                yield new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
            }
            case "undefined", "_undefined" -> {
                boolean isUndefined = subject == null || subject == Terms.UNDEFINED;
                Result result = isUndefined ? Result.PASS : Result.fail("actual: " + subject + ", expected: undefined");
                handleResult(null, result);
                yield new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
            }
            case "ok" -> {
                Result result = Terms.isTruthy(subject) ? Result.PASS : Result.fail("actual: " + subject + ", expected: (truthy)");
                handleResult(null, result);
                yield new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
            }
            case "empty" -> {
                Result result;
                if (subject instanceof String s) {
                    result = s.isEmpty() ? Result.PASS : Result.fail("actual: " + subject + ", expected: ''");
                } else if (subject instanceof Map<?, ?> map) {
                    result = map.isEmpty() ? Result.PASS : Result.fail("actual: map with " + map.size() + " items, expected: empty");
                } else if (subject instanceof List<?> list) {
                    result = list.isEmpty() ? Result.PASS : Result.fail("actual: list with " + list.size() + " items, expected: empty");
                } else {
                    result = Result.fail("actual: " + subject + ", expected: (empty)");
                }
                handleResult(null, result);
                yield new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
            }
            case "oneOf" -> (JavaCallable) (context, args) -> {
                Result result = Match.evaluate(args[0], null, null).contains(subject);
                handleResult(context, result);
                return new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
            };
            case "above" -> handle(rhs -> {
                if (subject instanceof Number actual && rhs instanceof Number expected) {
                    return actual.doubleValue() > expected.doubleValue() ? null : subject + " is not above: " + rhs;
                } else {
                    return subject + " is not above: " + rhs;
                }
            });
            case "below" -> handle(rhs -> {
                if (subject instanceof Number actual && rhs instanceof Number expected) {
                    return actual.doubleValue() < expected.doubleValue() ? null : subject + " is not below: " + rhs;
                } else {
                    return subject + " is not below: " + rhs;
                }
            });
            case "within" -> (JavaCallable) (context, args) -> {
                Number actual = (Number) subject;
                Number first = (Number) args[0];
                Number second = (Number) args[1];
                Result result;
                if (actual.doubleValue() >= first.doubleValue() && actual.doubleValue() <= second.doubleValue()) {
                    result = Result.PASS;
                } else {
                    result = Result.fail(actual + " not within " + first + ", " + second);
                }
                handleResult(context, result);
                return new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
            };
            case "closeTo", "approximately" -> (JavaCallable) (context, args) -> {
                Number actual = (Number) subject;
                Number expected = (Number) args[0];
                Number delta = (Number) args[1];
                Result result;
                if (Math.abs(actual.doubleValue() - expected.doubleValue()) <= delta.doubleValue()) {
                    result = Result.PASS;
                } else {
                    result = Result.fail(actual + " not close to " + expected + " ± " + delta);
                }
                handleResult(context, result);
                return new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
            };
            case "at" -> expectToBeAt;
            default -> throw new RuntimeException("expect().to.be - no such api: " + key);
        };
    }

    private SimpleObject initExpectToDeep() {
        return key -> switch (key) {
            case "include" -> match(Match.Type.CONTAINS_DEEP);
            default -> throw new RuntimeException("expect().to.deep - no such api: " + key);
        };
    }

    @SuppressWarnings("unchecked")
    private SimpleObject initExpectToHaveAll() {
        return key -> switch (key) {
            case "keys" -> (JavaCallable) (context, args) -> {
                Result result;
                if (subject instanceof Map<?, ?> map) {
                    result = Match.evaluate(map.keySet(), null, null).contains(args[0]);
                } else {
                    result = Result.fail("not an object");
                }
                handleResult(context, result);
                return new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
            };
            default -> throw new RuntimeException("expect().to.have.all - no such api: " + key);
        };
    }

    @SuppressWarnings("unchecked")
    private SimpleObject initExpectToHaveAny() {
        return key -> switch (key) {
            case "keys" -> (JavaCallable) (context, args) -> {
                Result result;
                if (subject instanceof Map<?, ?> map) {
                    result = Match.evaluate(map.keySet(), null, null).containsAny(args[0]);
                } else {
                    result = Result.fail("not an object");
                }
                handleResult(context, result);
                return new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
            };
            default -> throw new RuntimeException("expect().to.have.any - no such api: " + key);
        };
    }

    private SimpleObject initExpectToHaveNested() {
        return key -> {
            if ("property".equals(key)) {
                return (JavaCallable) (context, args) -> {
                    String path = args[0] + "";
                    Json json;
                    try {
                        json = Json.of(subject);
                    } catch (Exception e) {
                        Result fail = Result.fail("expected to have property: " + path);
                        handleResult(context, fail);
                        return new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
                    }
                    Result result;
                    if (args.length > 1) {
                        Object actual = json.get(path, null);
                        result = Match.evaluate(actual, null, null)._equals(args[1]);
                    } else {
                        result = json.pathExists(path) ? Result.PASS : Result.fail("expected to have property: " + path);
                    }
                    handleResult(context, result);
                    return new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
                };
            } else {
                throw new RuntimeException("expect().to.have.nested - no such api: " + key);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private SimpleObject initExpectToHave() {
        return key -> switch (key) {
            case "not" -> new Expect(subject, !negated, onResult, throwOnFailure, contextSupplier).expectToHave;
            case "that", "and", "which" -> this;
            case "length" -> handleChainable(rhs -> {
                if (rhs instanceof Number n) {
                    String message = "actual: " + subject + ", expected: (length=" + n + ")";
                    if (subject instanceof String s) {
                        return s.length() == n.intValue() ? null : message;
                    } else if (subject instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) subject;
                        return n.equals(map.get("length")) ? null : message;
                    } else if (subject instanceof List<?> l) {
                        return l.size() == n.intValue() ? null : message;
                    } else {
                        return message;
                    }
                } else {
                    return "not a number: " + rhs;
                }
            });
            case "property" -> (JavaCallable) (context, args) -> {
                String k = args[0] + "";
                Result result;
                Object propertyValue = null;
                if (subject instanceof Map<?, ?> map) {
                    if (args.length > 1) {
                        if (map.containsKey(k)) {
                            Object v = map.get(k);
                            propertyValue = v;
                            result = Match.evaluate(v, null, null)._equals(args[1]);
                        } else {
                            result = Result.fail("property " + k + " not found");
                        }
                    } else {
                        if (map.containsKey(k)) {
                            propertyValue = map.get(k);
                            result = Result.PASS;
                        } else {
                            result = Result.fail("does not contain property: " + k);
                        }
                    }
                } else {
                    result = Result.fail("not an object");
                }
                handleResult(context, result);
                // Return chainable with property value as new subject for .and.equal() chaining
                return new Expect(propertyValue != null ? propertyValue : subject, false, onResult, throwOnFailure, contextSupplier);
            };
            case "keys" -> (JavaCallable) (context, args) -> {
                Result result;
                if (subject instanceof Map<?, ?> map) {
                    result = Match.evaluate(map.keySet(), null, null).containsOnly(args[0]);
                } else {
                    result = Result.fail("not an object");
                }
                handleResult(context, result);
                return new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
            };
            case "all" -> expectToHaveAll;
            case "any" -> expectToHaveAny;
            case "nested" -> expectToHaveNested;
            default -> throw new RuntimeException("expect().to.have - no such api: " + key);
        };
    }

    private SimpleObject initExpectTo() {
        return key -> switch (key) {
            case "that", "and", "which" -> this;
            case "equal", "eql" -> match(Match.Type.EQUALS);
            case "include", "contain" -> matchChainable(Match.Type.CONTAINS);
            case "deep" -> expectToDeep;
            case "be" -> expectToBe;
            case "exist" -> {
                Result result = (subject != null && subject != Terms.UNDEFINED) ? Result.PASS : Result.fail("actual: " + subject + ", expected: (exists)");
                handleResult(null, result);
                yield new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
            }
            case "have" -> expectToHave;
            case "match" -> (JavaCallable) (context, args) -> {
                Result result;
                if (subject instanceof String s) {
                    if (args[0] instanceof JsRegex regex) {
                        result = regex.test(s) ? Result.PASS : Result.fail("actual: " + s + ", expected: " + regex);
                    } else {
                        result = Result.fail("not a regex: " + args[0]);
                    }
                } else {
                    result = Result.fail("not a string");
                }
                handleResult(context, result);
                return new Expect(subject, false, onResult, throwOnFailure, contextSupplier);
            };
            case "not" -> new Expect(subject, !negated, onResult, throwOnFailure, contextSupplier).expectTo;
            default -> throw new RuntimeException("expect().to - no such api: " + key);
        };
    }

    // ========== SimpleObject Implementation ==========

    @Override
    public Object jsGet(String key) {
        if ("to".equals(key)) {
            return expectTo;
        }
        if ("not".equals(key)) {
            return new Expect(subject, true, onResult, throwOnFailure, contextSupplier);
        }
        if ("that".equals(key) || "and".equals(key) || "which".equals(key)) {
            return this; // language chains
        }
        if ("be".equals(key) || "is".equals(key)) {
            return expectToBe; // shorthand for .to.be
        }
        if ("have".equals(key) || "has".equals(key)) {
            return expectToHave; // shorthand for .to.have
        }
        if ("include".equals(key) || "contain".equals(key)) {
            return matchChainable(Match.Type.CONTAINS); // shorthand for .to.include / .to.contain
        }
        if ("equal".equals(key) || "eql".equals(key)) {
            return match(Match.Type.EQUALS); // shorthand for .to.equal / .to.eql
        }
        throw new RuntimeException("expect() - no such api: " + key);
    }

    // ========== Getters for Composition ==========

    public Object getSubject() {
        return subject;
    }

    public boolean isNegated() {
        return negated;
    }

    public BiConsumer<Context, Result> getOnResult() {
        return onResult;
    }

    public boolean isThrowOnFailure() {
        return throwOnFailure;
    }

    public Supplier<Context> getContextSupplier() {
        return contextSupplier;
    }

}

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
package io.karatelabs.core;

import io.karatelabs.output.LogMask;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * KarateConfig has two field-by-field copy routines that must stay in lockstep: copyFrom
 * (wholesale, caller -> callee and back) and copyChangedFrom (the three-way merge a callonce
 * cache hit replays through). A newly added configure key that is wired into only one of them
 * fails silently - the key would just stop propagating out of a cached callonce, with nothing
 * to see until a user reports it. These sweep every declared field reflectively so that
 * omission is a test failure instead.
 */
class KarateConfigCopyTest {

    // Derived from the logging map by configureLogging, so copyChangedFrom carries it with
    // logging rather than diffing it independently - asserted separately below.
    private static final String DERIVED_FIELD = "compiledMask";

    @Test
    void testEveryFieldIsCarriedByCopyFrom() throws Exception {
        for (Field field : mutableFields()) {
            KarateConfig source = new KarateConfig();
            Object changed = distinctValue(field, field.get(source));
            field.set(source, changed);

            KarateConfig target = new KarateConfig();
            target.copyFrom(source);

            assertEquals(changed, field.get(target), "copyFrom does not carry: " + field.getName());
        }
    }

    @Test
    void testEveryChangedFieldIsCarriedByCopyChangedFrom() throws Exception {
        for (Field field : mutableFields()) {
            KarateConfig before = new KarateConfig();
            KarateConfig after = before.copy();
            Object changed = distinctValue(field, field.get(after));
            field.set(after, changed);

            KarateConfig target = new KarateConfig();
            target.copyChangedFrom(before, after);

            assertEquals(changed, field.get(target), "copyChangedFrom does not carry: " + field.getName());
        }
    }

    @Test
    void testFieldsTheCalleeLeftAloneKeepTheTargetsOwnValue() throws Exception {
        KarateConfig before = new KarateConfig();
        KarateConfig after = before.copy();  // the callee changed nothing

        // The target stands in for a later scenario whose own config differs from the caller
        // state captured when the callonce actually ran.
        KarateConfig target = new KarateConfig();
        Map<String, Object> own = new HashMap<>();
        for (Field field : mutableFields()) {
            Object value = distinctValue(field, field.get(target));
            field.set(target, value);
            own.put(field.getName(), value);
        }

        target.copyChangedFrom(before, after);

        for (Field field : mutableFields()) {
            assertEquals(own.get(field.getName()), field.get(target),
                    "copyChangedFrom overwrote a field the callee never touched: " + field.getName());
        }
    }

    @Test
    void testCompiledMaskFollowsTheLoggingMap() {
        KarateConfig before = new KarateConfig();
        KarateConfig after = before.copy();
        after.configure("logging", Map.of("mask", Map.of("headers", List.of("Authorization"))));
        assertNotNull(after.getCompiledMask(), "sanity: configuring a mask should compile one");

        KarateConfig target = new KarateConfig();
        target.copyChangedFrom(before, after);

        assertSame(after.getCompiledMask(), target.getCompiledMask(),
                "a mask set by the callee must arrive with the logging map it was compiled from");
    }

    private static List<Field> mutableFields() {
        List<Field> fields = new ArrayList<>();
        for (Field field : KarateConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            if (DERIVED_FIELD.equals(field.getName())) {
                continue;
            }
            field.setAccessible(true);
            fields.add(field);
        }
        return fields;
    }

    /**
     * A value guaranteed to differ from {@code current}, so that failing to copy the field is
     * always visible. Deliberately exhaustive over the field types in use - a new field of an
     * unhandled type fails here rather than being skipped.
     */
    @SuppressWarnings("unchecked")
    private static Object distinctValue(Field field, Object current) {
        Class<?> type = field.getType();
        if (type == int.class) {
            return (Integer) current + 1;
        }
        if (type == boolean.class) {
            return !((Boolean) current);
        }
        if (type == String.class || type == Object.class) {
            return "changed-" + field.getName();
        }
        if (type == Charset.class) {
            return StandardCharsets.UTF_8.equals(current) ? StandardCharsets.US_ASCII : StandardCharsets.UTF_8;
        }
        if (Map.class.isAssignableFrom(type)) {
            Map<String, Object> map = new HashMap<>((Map<String, Object>) current);
            map.put("changed-" + field.getName(), true);
            return map;
        }
        if (type == LogMask.class) {
            return LogMask.fromMap(Map.of("headers", List.of("changed-" + field.getName())));
        }
        throw new AssertionError("no distinct value known for field type " + type.getName()
                + " (" + field.getName() + ") - extend distinctValue()");
    }

}

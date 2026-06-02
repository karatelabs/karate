/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The JS-facing {@code karate.embed(...)} multi-part object form
 * ({@code {name, parts:[{role, mime?, data|path|url}], meta}}) — the emit surface ext
 * recipes (e.g. image-comparison) use. The legacy single-part {@code embed(data, mime?, name?)}
 * form must keep working unchanged.
 */
class KarateEmbedMultiPartTest {

    private static StepResult.Embed onlyEmbed(ScenarioRuntime sr) {
        StepResult.Embed found = null;
        for (StepResult step : sr.getResult().getStepResults()) {
            if (step.getEmbeds() == null) {
                continue;
            }
            for (StepResult.Embed e : step.getEmbeds()) {
                assertNull(found, "expected exactly one embed");
                found = e;
            }
        }
        assertNotNull(found, "no embed emitted");
        return found;
    }

    @Test
    void multiPartObjectFormWithInlineDataAndMeta() {
        // PNG magic bytes (89 'P' 'N' 'G' ...) so mime is auto-detected on the baseline part
        ScenarioRuntime sr = run("""
                * def parts = [{ role: 'baseline', data: [137,80,78,71,13,10,26,10] }, { role: 'latest', mime: 'image/png', data: [1,2,3] }]
                * eval karate.embed({ name: 'image-comparison', parts: parts, meta: { mismatchPercentage: 2.5, pass: false } })
                """);
        assertPassed(sr);

        StepResult.Embed embed = onlyEmbed(sr);
        assertEquals("image-comparison", embed.getName());

        List<StepResult.Part> parts = embed.getParts();
        assertEquals(2, parts.size());

        assertEquals("baseline", parts.get(0).getRole());
        assertEquals("image/png", parts.get(0).getMime(), "auto-detected from PNG magic bytes");
        assertArrayEquals(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}, parts.get(0).getData());

        assertEquals("latest", parts.get(1).getRole());
        assertEquals("image/png", parts.get(1).getMime(), "explicit mime honoured");
        assertArrayEquals(new byte[]{1, 2, 3}, parts.get(1).getData());

        assertEquals(false, embed.getMeta().get("pass"));
        assertEquals(2.5, ((Number) embed.getMeta().get("mismatchPercentage")).doubleValue());
    }

    @Test
    void partWithPathIsReadToBytes() {
        ScenarioRuntime sr = run("""
                * eval karate.embed({ name: 'doc', parts: [{ role: 'primary', path: 'classpath:io/karatelabs/core/embed-part.txt' }] })
                """);
        assertPassed(sr);
        StepResult.Embed embed = onlyEmbed(sr);
        assertEquals("hello embed\n", new String(embed.getParts().get(0).getData(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void partWithUrlCarriesNoInlineBytes() {
        ScenarioRuntime sr = run("""
                * eval karate.embed({ name: 'x', parts: [{ role: 'diff', mime: 'image/png', url: 'ext/image/assets/c.png' }] })
                """);
        assertPassed(sr);
        StepResult.Part part = onlyEmbed(sr).getParts().get(0);
        assertEquals("ext/image/assets/c.png", part.getUrl());
        assertNull(part.getData());
    }

    @Test
    void missingRoleIsRejected() {
        ScenarioRuntime sr = run("""
                * eval karate.embed({ name: 'x', parts: [{ data: [1,2,3] }] })
                """);
        assertFailed(sr);
    }

    @Test
    void legacySinglePartStillWorks() {
        ScenarioRuntime sr = run("""
                * eval karate.embed('hello', 'text/plain', 'note')
                """);
        assertPassed(sr);
        StepResult.Embed embed = onlyEmbed(sr);
        assertEquals("note", embed.getName());
        List<StepResult.Part> parts = embed.getParts();
        assertEquals(1, parts.size());
        assertEquals("primary", parts.get(0).getRole());
        assertEquals("text/plain", parts.get(0).getMime());
    }
}

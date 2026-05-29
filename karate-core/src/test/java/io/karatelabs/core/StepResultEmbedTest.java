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

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wire-shape contract for {@link StepResult.Embed} (IMAGE_SPIKE.md §3.6). The shape is
 * uniform — {@code {name, parts:[{role, mime, data|url|file}], meta}} — for both single-
 * and multi-asset embeds. There is no legacy flat ({@code mime_type}) form (clean break;
 * v2 has no released embed consumers).
 */
class StepResultEmbedTest {

    @Test
    @SuppressWarnings("unchecked")
    void singleInlineEmbedEmitsOnePrimaryPart() {
        byte[] bytes = {1, 2, 3};
        StepResult.Embed embed = new StepResult.Embed(bytes, "image/png", "screenshot.png");
        Map<String, Object> map = embed.toMap();

        assertEquals("screenshot.png", map.get("name"));
        assertFalse(map.containsKey("mime_type"), "no legacy flat shape");
        assertFalse(map.containsKey("meta"), "no meta when none supplied");

        List<Map<String, Object>> parts = (List<Map<String, Object>>) map.get("parts");
        assertEquals(1, parts.size());
        assertEquals("primary", parts.get(0).get("role"));
        assertEquals("image/png", parts.get(0).get("mime"));
        assertEquals(Base64.getEncoder().encodeToString(bytes), parts.get(0).get("data"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void writtenPartEmitsFileReferenceNotData() {
        StepResult.Embed embed = new StepResult.Embed(new byte[]{9}, "text/html", "doc");
        embed.setFileName("001_doc.html");   // HtmlReportWriter sets this after writing bytes
        Map<String, Object> part = ((List<Map<String, Object>>) embed.toMap().get("parts")).get(0);
        assertEquals("001_doc.html", part.get("file"));
        assertFalse(part.containsKey("data"), "file reference replaces inline data once written");
    }

    @Test
    @SuppressWarnings("unchecked")
    void multiPartEmbedEmitsPartsAndMeta() {
        StepResult.Embed embed = new StepResult.Embed(
                "image-comparison",
                List.of(
                        new StepResult.Part("baseline", "image/png", "ext/image/assets/a.png"),
                        new StepResult.Part("current", "image/png", "ext/image/assets/b.png"),
                        new StepResult.Part("diff", "image/png", "ext/image/assets/c.png")),
                Map.of("mismatchPercent", 2.3, "passed", false));
        Map<String, Object> map = embed.toMap();

        assertEquals("image-comparison", map.get("name"));
        assertFalse(map.containsKey("mime_type"), "multi-part must not use the flat shape");

        List<Map<String, Object>> parts = (List<Map<String, Object>>) map.get("parts");
        assertEquals(3, parts.size());
        assertEquals("baseline", parts.get(0).get("role"));
        assertEquals("image/png", parts.get(0).get("mime"));
        assertEquals("ext/image/assets/a.png", parts.get(0).get("url"));
        assertFalse(parts.get(0).containsKey("data"), "url part carries no inline bytes");

        Map<String, Object> meta = (Map<String, Object>) map.get("meta");
        assertEquals(false, meta.get("passed"));
    }
}

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
package io.karatelabs.http;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Each header as postman-request's hawk.js (postman-runtime 7.56.3's) writes it for the same input. */
class HawkAuthHandlerTest {

    static Map<String, Object> config(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        map.put("authId", "dh37fgj492je");
        map.put("authKey", "werxhqb98rpaxn39848xrunpaw3489ruxnpa98w4rxn");
        map.put("algorithm", "sha256");
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    @Test
    void theHeaderMacMatchesHawks() {
        HawkAuthHandler hawk = new HawkAuthHandler(config("timestamp", "1353832234", "nonce", "j4h3g2", "extraData", "some-app-ext-data"));
        assertEquals("Hawk id=\"dh37fgj492je\", ts=\"1353832234\", nonce=\"j4h3g2\", ext=\"some-app-ext-data\","
                + " mac=\"6R4rV5iE+NPoym+WwjeHzjAGXUtLNIxmo1vpMofpLAE=\"", hawk.header("GET", "http://example.com:8000/resource/1?b=1&a=2", null));
    }

    @Test
    void aPayloadHashIsSignedAndSent() {
        String hash = HawkAuthHandler.payloadHash("sha256", "text/plain; charset=utf-8", "Thank you for flying Hawk".getBytes(StandardCharsets.UTF_8));
        assertEquals("Yi9LfIIFRtBEPt74PVmbTF/xVAwPn7ub15ePICfgnuY=", hash);
        HawkAuthHandler hawk = new HawkAuthHandler(config("timestamp", "1353832234", "nonce", "j4h3g2", "extraData", "some-app-ext-data"));
        assertEquals("Hawk id=\"dh37fgj492je\", ts=\"1353832234\", nonce=\"j4h3g2\", hash=\"Yi9LfIIFRtBEPt74PVmbTF/xVAwPn7ub15ePICfgnuY=\","
                + " ext=\"some-app-ext-data\", mac=\"aSe1DERmZuRl3pI36/9BdZmnErTw3sNzOOAUlfeKjVw=\"",
                hawk.header("POST", "http://example.com:8000/resource/1?b=1&a=2", hash));
    }

    @Test
    void sha1WithAppDelegationAndAnEscapedExt() {
        HawkAuthHandler hawk = new HawkAuthHandler(config("algorithm", "sha1", "timestamp", "1353832234", "nonce", "abc",
                "extraData", "a\\\\b\"c\nd", "app", "someAppId", "delegation", "del"));
        assertEquals("Hawk id=\"dh37fgj492je\", ts=\"1353832234\", nonce=\"abc\", ext=\"a\\\\\\\\b\\\"c\nd\","
                + " mac=\"Kj93ORx3YtyoQkX54AzfQJeDLNE=\", app=\"someAppId\", dlg=\"del\"", hawk.header("get", "https://Example.com/r", null));
    }

    @Test
    void aGeneratedNonceAndTimestamp() {
        Clock clock = Clock.fixed(Instant.ofEpochSecond(1700000000), ZoneOffset.UTC);
        String header = new HawkAuthHandler(config("timestamp", ""), clock).header("GET", "http://h.test/", null);
        assertTrue(header.matches("Hawk id=\"dh37fgj492je\", ts=\"1700000000\", nonce=\"[A-Za-z0-9]{6}\", mac=\"[^\"]+\""), header);
    }

    @Test
    void anUnknownAlgorithmSendsAnEmptyHeaderAndNoCredentialsSendNone() {
        HttpRequestBuilder builder = new HttpRequestBuilder(null).url("http://h.test/a").method("GET");
        new HawkAuthHandler(config("algorithm", "md5")).apply(builder);
        assertEquals("", builder.getHeader("Authorization"));
        HttpRequestBuilder none = new HttpRequestBuilder(null).url("http://h.test/a").method("GET").header("Authorization", "x");
        new HawkAuthHandler(config("authKey", "")).apply(none);
        assertEquals("x", none.getHeader("Authorization"));
    }

    @Test
    void applyHashesTheBodyWhenAsked() {
        HttpRequestBuilder builder = new HttpRequestBuilder(null).url("http://example.com:8000/resource/1?b=1&a=2").method("POST")
                .header("Content-Type", "text/plain").body("Thank you for flying Hawk");
        new HawkAuthHandler(config("timestamp", "1353832234", "nonce", "j4h3g2", "extraData", "some-app-ext-data",
                "includePayloadHash", true)).apply(builder);
        assertTrue(builder.getHeader("Authorization").contains("hash=\"Yi9LfIIFRtBEPt74PVmbTF/xVAwPn7ub15ePICfgnuY=\""));
        assertTrue(builder.getHeader("Authorization").endsWith("mac=\"aSe1DERmZuRl3pI36/9BdZmnErTw3sNzOOAUlfeKjVw=\""));
    }

}

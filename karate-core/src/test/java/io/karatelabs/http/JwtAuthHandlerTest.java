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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthHandlerTest {

    static final Base64.Decoder B64URL = Base64.getUrlDecoder();

    static Map<String, Object> config(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    static String pem(KeyPair pair) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    static KeyPair rsa() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    static KeyPair ec(String curve) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec(curve));
        return gen.generateKeyPair();
    }

    static String part(String token, int i) {
        return new String(B64URL.decode(token.split("\\.")[i]), StandardCharsets.UTF_8);
    }

    static byte[] signingInput(String token) {
        return token.substring(0, token.lastIndexOf('.')).getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void hs256MatchesKnownVector() {
        // the jwt.io example token
        JwtAuthHandler handler = new JwtAuthHandler(config("algorithm", "HS256", "secret", "your-256-bit-secret",
                "header", "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "payload", "{\"sub\":\"1234567890\",\"name\":\"John Doe\",\"iat\":1516239022}"));
        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ"
                + ".SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c", handler.token());
    }

    @Test
    void hs256SignatureMatchesRfc7515() {
        // RFC 7515 A.1: the HMAC over its signing input, with its base64url key
        String input = "eyJ0eXAiOiJKV1QiLA0KICJhbGciOiJIUzI1NiJ9"
                + ".eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFtcGxlLmNvbS9pc19yb290Ijp0cnVlfQ";
        byte[] key = B64URL.decode("AyM1SysPpbyDfgZld3umj1qzKObwVMkoqQ-EstJQLr_T-1qS0gZH75aKtMN3Yj0iPS4hcgUuTwjAzZr1Z9CAow");
        byte[] sig = JwtAuthHandler.hmac("HS256", key, input.getBytes(StandardCharsets.US_ASCII));
        assertEquals("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", Base64.getUrlEncoder().withoutPadding().encodeToString(sig));
    }

    @Test
    void serializesAsJavaScriptDoes() {
        // the token jose (postman-runtime's signer) produces for this header, payload and secret
        JwtAuthHandler handler = new JwtAuthHandler(config("algorithm", "HS256", "secret", "secret",
                "header", "{\"typ\":\"JWT\"}",
                "payload", "{\"n\":1.0,\"big\":12345678901234567890,\"e\":1e21,\"s\":1e-7,\"f\":0.1,"
                        + "\"u\":\"é\\u2028</x>\\u0001\",\"neg\":-0,\"arr\":[1,true,null],\"dup\":1,\"dup\":2}"));
        assertEquals("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJuIjoxLCJiaWciOjEyMzQ1Njc4OTAxMjM0NTY3MDAwLCJlIjoxZSsyMSwicyI6MWUtNywiZiI6MC4xLCJ1Ijoiw6nigKg8L3g-XHUwMDAxIiwibmVnIjowLCJhcnIiOlsxLHRydWUsbnVsbF0sImR1cCI6Mn0"
                + ".myPi266hNcOdHJzWqPUr5hnJ_jm127WNkMETMMFvQOM", handler.token());
    }

    @Test
    void headerTakesTheAlgorithmAndNoTyp() {
        String token = new JwtAuthHandler(config("algorithm", "HS384", "secret", "s", "payload", "")).token();
        assertEquals("{\"alg\":\"HS384\"}", part(token, 0));
        assertEquals("{}", part(token, 1));
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS512");
        header.put("kid", "k1");
        token = new JwtAuthHandler(config("algorithm", "HS256", "secret", "s", "header", header,
                "payload", Map.of("a", 1))).token();
        assertEquals("{\"alg\":\"HS256\",\"kid\":\"k1\"}", part(token, 0));
        assertEquals("{\"a\":1}", part(token, 1));
    }

    @Test
    void hmacFamilyVerifies() throws Exception {
        for (String alg : List.of("HS256", "HS384", "HS512")) {
            String token = new JwtAuthHandler(config("algorithm", alg, "secret", "c2VjcmV0LWtleQ==",
                    "isSecretBase64Encoded", true, "payload", "{}")).token();
            Mac mac = Mac.getInstance("Hmac" + "SHA" + alg.substring(2));
            mac.init(new SecretKeySpec("secret-key".getBytes(StandardCharsets.UTF_8), mac.getAlgorithm()));
            byte[] expected = mac.doFinal(signingInput(token));
            assertArrayEquals(expected, B64URL.decode(token.split("\\.")[2]), alg);
        }
    }

    @Test
    void rsaFamilyVerifies() throws Exception {
        KeyPair pair = rsa();
        for (String alg : List.of("RS256", "RS384", "RS512")) {
            String token = new JwtAuthHandler(config("algorithm", alg, "privateKey", pem(pair), "payload", "{\"a\":1}")).token();
            Signature verifier = Signature.getInstance("SHA" + alg.substring(2) + "withRSA");
            verifier.initVerify(pair.getPublic());
            verifier.update(signingInput(token));
            assertTrue(verifier.verify(B64URL.decode(token.split("\\.")[2])), alg);
        }
    }

    @Test
    void pssFamilyVerifies() throws Exception {
        KeyPair pair = rsa();
        for (String alg : List.of("PS256", "PS384", "PS512")) {
            String token = new JwtAuthHandler(config("algorithm", alg, "privateKey", pem(pair), "payload", "{\"a\":1}")).token();
            String sha = "SHA-" + alg.substring(2);
            int bits = Integer.parseInt(alg.substring(2));
            Signature verifier = Signature.getInstance("RSASSA-PSS");
            verifier.setParameter(new PSSParameterSpec(sha, "MGF1", new MGF1ParameterSpec(sha), bits / 8, 1));
            verifier.initVerify(pair.getPublic());
            verifier.update(signingInput(token));
            assertTrue(verifier.verify(B64URL.decode(token.split("\\.")[2])), alg);
        }
    }

    @Test
    void ecdsaFamilyVerifiesAsJoseRAndS() throws Exception {
        Map<String, String> curves = Map.of("ES256", "secp256r1", "ES384", "secp384r1", "ES512", "secp521r1");
        Map<String, Integer> sizes = Map.of("ES256", 32, "ES384", 48, "ES512", 66);
        for (String alg : List.of("ES256", "ES384", "ES512")) {
            KeyPair pair = ec(curves.get(alg));
            String token = new JwtAuthHandler(config("algorithm", alg, "privateKey", pem(pair), "payload", "{\"a\":1}")).token();
            byte[] sig = B64URL.decode(token.split("\\.")[2]);
            int n = sizes.get(alg);
            assertEquals(2 * n, sig.length, alg); // r || s, each fixed length, not DER
            assertTrue(verifyDer(alg, pair.getPublic(), signingInput(token), sig, n), alg);
        }
    }

    /** Re-encodes r || s as DER and verifies with the JDK's plain ECDSA. */
    static boolean verifyDer(String alg, PublicKey key, byte[] input, byte[] sig, int n) throws Exception {
        byte[] r = new BigInteger(1, Arrays.copyOfRange(sig, 0, n)).toByteArray();
        byte[] s = new BigInteger(1, Arrays.copyOfRange(sig, n, 2 * n)).toByteArray();
        java.io.ByteArrayOutputStream seq = new java.io.ByteArrayOutputStream();
        for (byte[] i : List.of(r, s)) {
            seq.write(0x02);
            seq.write(i.length);
            seq.write(i);
        }
        byte[] body = seq.toByteArray();
        java.io.ByteArrayOutputStream der = new java.io.ByteArrayOutputStream();
        der.write(0x30);
        if (body.length > 127) {
            der.write(0x81);
        }
        der.write(body.length);
        der.write(body);
        Signature verifier = Signature.getInstance("SHA" + alg.substring(2) + "withECDSA");
        verifier.initVerify(key);
        verifier.update(input);
        return verifier.verify(der.toByteArray());
    }

    @Test
    void placesTheTokenInTheHeader() {
        HttpRequestBuilder builder = new HttpRequestBuilder(null);
        builder.url("http://localhost/x");
        builder.header("authorization", "old");
        JwtAuthHandler handler = new JwtAuthHandler(config("algorithm", "HS256", "secret", "s", "payload", "{}"));
        handler.apply(builder);
        assertEquals(List.of("Bearer " + handler.token()), builder.getHeaderValues("Authorization"));
        assertEquals(1, builder.getHeaders().size());

        builder = new HttpRequestBuilder(null);
        new JwtAuthHandler(config("algorithm", "HS256", "secret", "s", "payload", "{}", "headerPrefix", "")).apply(builder);
        assertEquals(handler.token(), builder.getHeader("Authorization"));

        builder = new HttpRequestBuilder(null);
        new JwtAuthHandler(config("algorithm", "HS256", "secret", "s", "payload", "{}", "headerPrefix", "JWT")).apply(builder);
        assertEquals("JWT " + handler.token(), builder.getHeader("Authorization"));
    }

    @Test
    void placesTheTokenInTheQuery() {
        HttpRequestBuilder builder = new HttpRequestBuilder(null);
        builder.url("http://localhost/x?a=1&token=old&b=2");
        JwtAuthHandler handler = new JwtAuthHandler(config("algorithm", "HS256", "secret", "s", "payload", "{}",
                "addTokenTo", "query"));
        handler.apply(builder);
        assertEquals("http://localhost/x?a=1&b=2&token=" + handler.token(), builder.getUri());
        assertNull(builder.getHeader("Authorization"));

        builder = new HttpRequestBuilder(null);
        builder.url("http://localhost/x");
        builder.param("jwt", "old");
        new JwtAuthHandler(config("algorithm", "HS256", "secret", "s", "payload", "{}", "addTokenTo", "query",
                "queryParamKey", "jwt")).apply(builder);
        assertEquals("http://localhost/x?jwt=" + handler.token(), builder.getUri());
    }

    @Test
    void refusesWhatPostmanRefuses() {
        assertEquals("invalid algorithm", fail(config("algorithm", "none", "secret", "s", "payload", "{}")));
        assertEquals("Invalid secret key. Enter a valid key.", fail(config("algorithm", "HS256", "payload", "{}")));
        assertEquals("Invalid private key. Enter a valid key.", fail(config("algorithm", "RS256", "secret", "s", "payload", "{}")));
        assertTrue(fail(config("algorithm", "HS256", "secret", "s", "header", "non-json", "payload", "{}")).startsWith("invalid json"));
        assertEquals("JWT Claims Set MUST be an object", fail(config("algorithm", "HS256", "secret", "s")));
        assertEquals("\"pkcs8\" must be PKCS#8 formatted string", fail(config("algorithm", "RS256", "privateKey", "abc", "payload", "{}")));
    }

    @Test
    void refusesAKeyOfAnotherFamilyOrSize() throws Exception {
        String ecKey = pem(ec("secp256r1"));
        assertNotNull(fail(config("algorithm", "RS256", "privateKey", ecKey, "payload", "{}")));
        assertNotNull(fail(config("algorithm", "ES384", "privateKey", ecKey, "payload", "{}")));
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(1024);
        assertEquals("RS256 requires key modulusLength to be 2048 bits or larger",
                fail(config("algorithm", "RS256", "privateKey", pem(gen.generateKeyPair()), "payload", "{}")));
    }

    static String fail(Map<String, Object> config) {
        try {
            new JwtAuthHandler(config).token();
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

}

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

import io.karatelabs.common.ResourceType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class OAuth1AuthHandlerTest {

    // node-oauth1's test key (Apache-2.0), PKCS#1
    static final String PKCS1_KEY = "-----BEGIN RSA PRIVATE KEY-----\nMIICWwIBAAKBgFKLvzM9zbm3I0+HWcHlBSqpfRY/bKs6NDLclERrzfnReFV4utjkhjaEQPPT6tHVHKrZkcxmIgwe3XrkJkUjcuingXIF+Fc3KpY61qJ4HSM50qIuHdi+C5YfuXwNrh6OOeZAhhqgSw2e2XqPfATbkYYwpIFpdVdcH/Pb2ynpd6VXAgMBAAECgYAbQE+LFyhH25Iou0KCpJ0kDHhjU+UIUlrRP8kjHYQOqXzUmtr0p903OkpHNPsc8wJX1SQxGra60aXE4HVR9fYFQNliAnSmA/ztGR4ddnirK1Gzog4y2OOkicTdSqJ/1XXtTEDSRkA0Z2DIqcWgudeSDzVjUpreYwQ/rCEZbi50AQJBAJcf9wi5bU8tdZUCg3/8MNDwHhr4If4V/9kmhsgNp+M/9tHwCbD05hCbiGS7g58DPF+6V2K30qQYq7yvBP8Te4ECQQCL1GhX/YwkD6rexi0E1bjz+RqhNLTR9kexkTfSYmL6zHeeIFSH8ROioGOJMU51lUtMNkkrKEeki5SZpkfaQOzXAkAvBnJPU6vQ7HtfH8YdiDMEgQNNLxMcxmmzf4qHK8CnNRsvnnrVho8kcdFSTwsY6t/Zhdl1TXANQeQGtYtfeAeBAkEAhUB351JSWJMtrHqCsFbTmHxNKk7F+kiObeMLpUvpM0PiwifhJmNQ6Oubr0Pzlw4c4ZXiCGSsUVxK0lmpo423pQJATYDoxVhZrKA3xDAifWoyxbyxf/WXtUGDaAOuZc/naVN5TKiqaEO6G+k3NpmOXNKsYU/Zd9e6P/TnfU74TyDDDA==\n-----END RSA PRIVATE KEY-----";

    static Map<String, Object> config(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    static OAuth1AuthHandler.Signed sign(Map<String, Object> config, String method, String uri, List<String[]> body) {
        return new OAuth1AuthHandler(config).sign(method, uri, body, null);
    }

    @Test
    void oauthCoreAppendixA() {
        OAuth1AuthHandler.Signed signed = sign(config("consumerKey", "dpf43f3p2l4k3l03", "consumerSecret", "kd94hf93k423kf44",
                "token", "nnch734d00sl2jdk", "tokenSecret", "pfkkdhi9sl3r4s00", "timestamp", "1191242096",
                "nonce", "kllo9940pd9333jh"), "GET", "http://photos.example.net/photos?file=vacation.jpg&size=original", List.of());
        assertEquals("GET&http%3A%2F%2Fphotos.example.net%2Fphotos&file%3Dvacation.jpg%26oauth_consumer_key%3Ddpf43f3p2l4k3l03"
                + "%26oauth_nonce%3Dkllo9940pd9333jh%26oauth_signature_method%3DHMAC-SHA1%26oauth_timestamp%3D1191242096"
                + "%26oauth_token%3Dnnch734d00sl2jdk%26oauth_version%3D1.0%26size%3Doriginal", signed.baseString());
        assertEquals("tR3+Ty81lMeYAr/Fid0kMTYa/WM=", signed.signature());
    }

    @Test
    void twitterExample() {
        OAuth1AuthHandler.Signed signed = sign(config("consumerKey", "xvz1evFS4wEEPTGEFPHBog",
                        "consumerSecret", "kAcSOqF21Fu85e7zjz7ZN2U4ZRhfV3WpwPAoE3Z7kBw",
                        "token", "370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb",
                        "tokenSecret", "LswwdoUaIvS8ltyTt5jkRh4J50vUPVVHtR2YPi5kE",
                        "nonce", "kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg", "timestamp", "1318622958"),
                "POST", "https://api.twitter.com/1.1/statuses/update.json?include_entities=true",
                List.<String[]>of(new String[]{"status", "Hello Ladies + Gentlemen, a signed OAuth request!"}));
        assertEquals("hCtSmYh+iHYCEqBWrE7C7hYmtUk=", signed.signature());
    }

    @Test
    void rfc5849BaseString() {
        // RFC 5849 3.4.1.1: query and form body params, a valueless one, no oauth_version
        OAuth1AuthHandler.Signed signed = sign(config("consumerKey", "9djdj82h48djs9d2", "consumerSecret", "j49sj3j29djd",
                        "token", "kkk9d7dh3k39sjv7", "tokenSecret", "dh893hdasih9", "timestamp", "137131201", "nonce", "7d8f3e4a",
                        "version", ""), "POST", "http://example.com/request?b5=%3D%253D&a3=a&c%40=&a2=r%20b",
                List.of(new String[]{"c2", ""}, new String[]{"a3", "2 q"}));
        assertEquals("POST&http%3A%2F%2Fexample.com%2Frequest&a2%3Dr%2520b%26a3%3D2%2520q%26a3%3Da%26b5%3D%253D%25253D"
                + "%26c%2540%3D%26c2%3D%26oauth_consumer_key%3D9djdj82h48djs9d2%26oauth_nonce%3D7d8f3e4a"
                + "%26oauth_signature_method%3DHMAC-SHA1%26oauth_timestamp%3D137131201%26oauth_token%3Dkkk9d7dh3k39sjv7",
                signed.baseString());
        assertEquals("KLQOrg89+C0MYbbmA9+bUKpoVhg=", signed.signature());
    }

    @Test
    void nodeOauth1Vectors() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("PLAINTEXT", "alpha&beta");
        expected.put("HMAC-SHA1", "kst31ZZPywC/vi+UVOU93hgdEMg=");
        expected.put("HMAC-SHA256", "H3w+AlCMRPSZNn8gOI6GyvEXol6R0jVqNw4rr58nZmg=");
        expected.put("HMAC-SHA512", "GF7ju0CZzAT7dM7cYx8z1km5MOrrjaQ+cJY00CX1sz052gIOOWacY55NcJkqSi28OQqcO6mhdxTOnO60uH4cEw==");
        expected.put("RSA-SHA1", "Tok9L4dAnBWzd0KNgI9/kR3fhfE2keZybvf8UYPB1/bbdQsMHQRQYL1Ui1V94ZBlijeQmyHo67XLJe1zGwCTK5fAGSOtQU0mfvj4AvG4sG5SVg8auWgm5BQt/Lhe9cQJpVNqRhV8rMxOZ6mF9STpDGSx80MY2+FG4eThexe0vkQ=");
        expected.put("RSA-SHA256", "A8ayuJe1RJd0zL4R8aBgbEY0PFviN6bk8G4QqAcGPSZfeGjFjwBlXWFysNy5iR8mm3/yWGboUMHWmtyPDtOnL2cJTc8fKhXwITFi7FmsUeA5cBK/HdWYoHLdWuNIZgDHsv8qvZCS4QD4qIILiys4uFK+W1br0zSJR9bsOTIZLng=");
        expected.put("RSA-SHA512", "ArsxDvfXOVTQBfXLbNEkWEm+xfEnzAQLM7RET04cMtHrrcm80mWWMcsMN1jykR8ZnXVsVufO565cJQgWqBJ2aWQgUa4Yu2RQWGLuIYwnaiX6TxysO/ZuV5zDlTWQdQpjUmFWKuixZouMDH7CiV37PJLKkYaJzQaGTamHsJiUubE=");
        expected.forEach((method, signature) -> {
            Map<String, Object> config = config("consumerKey", "foo", "token", "bar", "nonce", "baz", "timestamp", "1588771035",
                    "signatureMethod", method, "consumerSecret", "alpha", "tokenSecret", "beta", "privateKey", PKCS1_KEY);
            assertEquals(signature, sign(config, "GET", "https://postman.com/path", List.of()).signature(), method);
        });
        Map<String, Object> config = config("consumerKey", "foo", "token", "bar", "nonce", "baz", "timestamp", "1588771035",
                "signatureMethod", "HMAC-SHA512", "consumerSecret", "alpha");
        assertEquals("zWgwoT1cmeYYmDXiEo9ylEAwu9h7qu/EG+Ylj6n0cedV9aK/gMkgGmTylrAbFpCmEKWo6wwfyvh+YFuJl2+1EQ==",
                sign(config, "GET", "https://postman.com/path", List.of()).signature());
    }

    // postman-runtime's oauth1 authorizer (v7.56.3) on the same request -------------------------------------------------

    static final String URL = "http://Example.COM:8080/a/b%20c?x=1&y=a+b&z=%21";

    static Map<String, Object> postman(Object... kv) {
        Map<String, Object> config = config("consumerKey", "ck!key", "consumerSecret", "cs&secret", "token", "tok",
                "tokenSecret", "ts+secret", "timestamp", "1700000000", "nonce", "n0nce", "version", "1.0", "realm", "Photos",
                "privateKey", PKCS1_KEY);
        config.putAll(config(kv));
        return config;
    }

    static HttpRequest build(Map<String, Object> config, String method, String url, Consumer<HttpRequestBuilder> body) {
        HttpRequestBuilder builder = new HttpRequestBuilder(null);
        builder.url(url);
        builder.method(method);
        if (body != null) {
            body.accept(builder);
        }
        builder.auth(new OAuth1AuthHandler(config));
        return builder.build();
    }

    static String header(Object... kv) {
        return build(postman(kv), "GET", URL, null).getHeader("Authorization");
    }

    static String oauth(String signatureMethod, String signature) {
        return "OAuth realm=\"Photos\",oauth_consumer_key=\"ck%21key\",oauth_token=\"tok\",oauth_signature_method=\"" + signatureMethod
                + "\",oauth_timestamp=\"1700000000\",oauth_nonce=\"n0nce\",oauth_version=\"1.0\"," + signature;
    }

    @Test
    void headerForEverySignatureMethod() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("HMAC-SHA1", "zQumteip44d8q5%2B8u2Esct5phic%3D");
        expected.put("HMAC-SHA256", "H3BTofBXy0D2cKlIlF7lBirj4iKPCBNqb5zOC6rJhaE%3D");
        expected.put("HMAC-SHA512", "58BFN3mHv3nB3gZc2fR4hzPZ3wDmUKJWqazPTxy6LlRUxjjPyWEK7pMWDaZfzhdemqAk8hBOquF6tl0AfxUTjw%3D%3D");
        expected.put("PLAINTEXT", "cs%2526secret%26ts%252Bsecret");
        expected.put("RSA-SHA1", "Ei1arEbwnZxETlPOQbd2HsTYqaFJR6WlKjnX%2FaBJJvVNgyaScsTfnPM1ZUe7XOa3GMot71GceLAiCTy31u3kBpuIsRAYTEO5MwlHLr2ff1HFodgeUMvm9GLAHDxink4eE7nsEcOBVlF4o0AW1DWKHO73bVzJU%2FeQXKZUGJVaFxM%3D");
        expected.put("RSA-SHA256", "F8QOKiuBy%2FOovEn7YMdKcsoypqG1y3c%2B6MmWdrbTbgIBP4%2FQB8R%2BqOrWUO4bCCOqDe67b0B3ONWhj%2BFOzH6ykrdNMa6b%2FWSDI0%2BQAAGCxR3gUsF3V%2BY55eaBJ5nZmSSnFnuPgY8nz%2FlmJJR1m%2FHGPqpcfrPHMJx0FTceFGm5qdk%3D");
        expected.put("RSA-SHA512", "OrQEDdOjr9SA3snA9YSzai2yKbAQOn7vRI%2FKDHRbLu1v4MO3Kdosd52DKxlxtGySz%2FX2vYwe2SIvFkSnIKOZr1G6q8ro%2BPy7uB3fnuSm0XMxpjgGv58v1uMRfNMz8vYrM1hKrJaXJIFXw2Nr3Tktcmn9dSYS2eaG1CtS7%2FTyjsM%3D");
        expected.forEach((method, signature) ->
                assertEquals(oauth(method, "oauth_signature=\"" + signature + "\""), header("signatureMethod", method), method));
    }

    @Test
    void extensionsEmptiesAndEncoding() {
        assertEquals(oauth("HMAC-SHA1", "oauth_callback=\"http%3A%2F%2Flocalhost%2Fcb%3Fa%3D1\",oauth_verifier=\"v%201\","
                        + "oauth_signature=\"sozkL0NygZethTtA%2FIcQmnhcnuE%3D\""),
                header("callback", "http://localhost/cb?a=1", "verifier", "v 1"));
        assertEquals("OAuth oauth_consumer_key=\"ck%21key\",oauth_signature_method=\"HMAC-SHA1\",oauth_timestamp=\"1700000000\","
                + "oauth_nonce=\"n0nce\",oauth_signature=\"dRo0miiedXbYpDRTJ2KwvfM07Zc%3D\"",
                header("token", "", "tokenSecret", "", "version", "", "realm", ""));
        // empty params are signed, but not sent in the header
        assertEquals("OAuth oauth_consumer_key=\"ck%21key\",oauth_signature_method=\"HMAC-SHA1\",oauth_timestamp=\"1700000000\","
                + "oauth_nonce=\"n0nce\",oauth_signature=\"9ucB7GpnCDFPTZlaKz8TBXmYFv4%3D\"",
                header("token", "", "tokenSecret", "", "version", "", "realm", "", "addEmptyParamsToSign", true));
        assertEquals("OAuth realm=\"a b\",oauth_consumer_key=\"ck!key\",oauth_token=\"tok\",oauth_signature_method=\"HMAC-SHA1\","
                + "oauth_timestamp=\"1700000000\",oauth_nonce=\"n0nce\",oauth_version=\"1.0\",oauth_signature=\"zQumteip44d8q5+8u2Esct5phic=\"",
                header("disableHeaderEncoding", true, "realm", "a b"));
        assertEquals("OAuth realm=\"Photos\",oauth_consumer_key=\"ck%21key\",oauth_token=\"tok\",oauth_signature_method=\"HMAC-SHA1\","
                        + "oauth_timestamp=\"1700000000\",oauth_nonce=\"n0nce\",oauth_version=\"1.0\",oauth_signature=\"IJ1MIQ8rNiFsa%2FH%2FNpeHVIes7tE%3D\"",
                build(postman(), "GET", "https://example.com:443/", null).getHeader("Authorization"));
    }

    @Test
    void bodyHash() {
        HttpRequest request = build(postman("signatureMethod", "HMAC-SHA256", "includeBodyHash", true), "POST", URL,
                b -> b.body("{\"a\":\"é\"}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(oauth("HMAC-SHA256", "oauth_body_hash=\"s6CSpq9IgH%2BpSCsu4UAQVXXaom1bJLPA5gp%2BLe5mg7E%3D\","
                + "oauth_signature=\"cqqwLQONwBRmwn6tqUb%2B7ubV%2BJP7JQy3yGhWaV74bRw%3D\""), request.getHeader("Authorization"));
        request = build(postman("signatureMethod", "HMAC-SHA512", "includeBodyHash", true), "POST", URL, null);
        assertEquals(oauth("HMAC-SHA512", "oauth_body_hash=\"z4PhNX7vuL3xVChQ1m2AB9Yg5AULVxXcg%2FSpIdNs6c5H0NE8XYXysP%2BDGNKHfuwvY7kxvUdBeoGlODJ6%2BSfaPg%3D%3D\","
                + "oauth_signature=\"4KnZLg%2F6%2B7ptmJbfMq%2FzKVe%2BU76%2BSGoUdCQj9uYOgLuZUYb9nyscNzji1OIg28R4QisPT0UZuNKrun%2BYE5EoLw%3D%3D\""),
                request.getHeader("Authorization"));
        // a form body is signed by its params, not hashed; PLAINTEXT has no hash
        request = build(postman("includeBodyHash", true), "POST", URL, b -> b.formField("q", "1"));
        assertEquals(oauth("HMAC-SHA1", "oauth_signature=\"33a0fEkRzIqmVrPl8yA0Z3Yk6aE%3D\""), request.getHeader("Authorization"));
        request = build(postman("signatureMethod", "PLAINTEXT", "includeBodyHash", true), "POST", URL, b -> b.body("x"));
        assertEquals(oauth("PLAINTEXT", "oauth_signature=\"cs%2526secret%26ts%252Bsecret\""), request.getHeader("Authorization"));
    }

    @Test
    void formBodyIsSigned() {
        HttpRequest request = build(postman(), "POST", URL, b -> b.formField("a b", "c+d"));
        assertEquals(oauth("HMAC-SHA1", "oauth_signature=\"CWBKz%2FxenCYoPWmGHBE2V%2BVDZiw%3D\""), request.getHeader("Authorization"));
    }

    @Test
    void queryPlacement() {
        HttpRequest request = build(postman("addParamsTo", "query"), "GET", URL, null);
        assertNull(request.getHeader("Authorization"));
        assertEquals(URL + "&oauth_consumer_key=ck%21key&oauth_token=tok&oauth_signature_method=HMAC-SHA1&oauth_timestamp=1700000000"
                + "&oauth_nonce=n0nce&oauth_version=1.0&oauth_signature=zQumteip44d8q5%2B8u2Esct5phic%3D", request.getUrlAndPath());
        request = build(postman("addParamsTo", "query", "token", "", "addEmptyParamsToSign", true), "GET", URL, null);
        assertTrue(request.getUrlAndPath().endsWith("&oauth_token=&oauth_signature_method=HMAC-SHA1&oauth_timestamp=1700000000"
                + "&oauth_nonce=n0nce&oauth_version=1.0&oauth_signature=KDDI3wvg9cXe3pkA%2BhLwvN95hfk%3D"), request.getUrlAndPath());
        // a stale protocol param is replaced
        request = build(postman("addParamsTo", "query"), "GET", "http://example.com/p?oauth_nonce=old&k=v&flag", null);
        assertEquals("http://example.com/p?k=v&flag&oauth_consumer_key=ck%21key&oauth_token=tok&oauth_signature_method=HMAC-SHA1"
                + "&oauth_timestamp=1700000000&oauth_nonce=n0nce&oauth_version=1.0&oauth_signature=RzNj6ltJ3YcB8pyQ0sn5%2B2p2%2FOA%3D",
                request.getUrlAndPath());
    }

    @Test
    void formPlacementForAPostOrPut() {
        HttpRequest request = build(postman("addParamsTo", "query", "signatureMethod", "HMAC-SHA256"), "POST", URL,
                b -> b.formField("a b", "c+d").formField("oauth_token", "stale"));
        assertEquals(URL, request.getUrlAndPath());
        assertEquals(List.of("a b=c+d", "oauth_consumer_key=ck!key", "oauth_token=tok", "oauth_signature_method=HMAC-SHA256",
                "oauth_timestamp=1700000000", "oauth_nonce=n0nce", "oauth_version=1.0",
                "oauth_signature=XLYBGB2OcfjyONzhWU3nsmzKQCfd7Q4WW7U28XSHRRQ="), decodedForm(request.getBody()));
        // a GET's form fields are its query
        request = build(postman("addParamsTo", "query"), "GET", "http://example.com/p?x=1", b -> b.formField("q", "1"));
        assertTrue(request.getUrlAndPath().contains("&oauth_signature=AbVRiU9WPIWPcsPB80ZWrhD0S6Y%3D"), request.getUrlAndPath());
    }

    static List<String> decodedForm(byte[] body) {
        List<String> out = new ArrayList<>();
        for (String pair : new String(body, StandardCharsets.UTF_8).split("&")) {
            String[] kv = pair.split("=", 2);
            out.add(URLDecoder.decode(kv[0], StandardCharsets.UTF_8) + "=" + URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return out;
    }

    @Test
    void nonceAndTimestampPerRequest() {
        OAuth1AuthHandler handler = new OAuth1AuthHandler(config("consumerKey", "k", "consumerSecret", "s"));
        OAuth1AuthHandler.Signed a = handler.sign("GET", "http://x/", List.of(), null);
        OAuth1AuthHandler.Signed b = handler.sign("GET", "http://x/", List.of(), null);
        assertNotEquals(a.authorization(), b.authorization());
        assertTrue(a.authorization().matches("OAuth oauth_consumer_key=\"k\",oauth_signature_method=\"HMAC-SHA1\",oauth_timestamp=\"\\d{10}\","
                + "oauth_nonce=\"[0-9A-Za-z]{11}\",oauth_version=\"1.0\",oauth_signature=\"[^\"]+\""), a.authorization());
    }

    @Test
    void refusesMissingOrInvalidConfig() {
        assertEquals("auth type 'oauth1' requires 'consumerKey'", fail(config("consumerSecret", "s")));
        assertEquals("auth type 'oauth1' requires 'consumerSecret'", fail(config("consumerKey", "k", "signatureMethod", "PLAINTEXT")));
        assertEquals("auth type 'oauth1' requires 'privateKey'", fail(config("consumerKey", "k", "consumerSecret", "s", "signatureMethod", "RSA-SHA1")));
        assertEquals("auth type 'oauth1' privateKey must be a PEM RSA private key",
                fail(config("consumerKey", "k", "signatureMethod", "RSA-SHA256", "privateKey", "nope")));
        assertTrue(fail(config("consumerKey", "k", "signatureMethod", "HMAC-MD5", "consumerSecret", "s"))
                .startsWith("auth type 'oauth1' signatureMethod must be one of HMAC-SHA1"));
        assertEquals("auth type 'oauth1' addParamsTo must be 'header' or 'query': body",
                fail(config("consumerKey", "k", "consumerSecret", "s", "addParamsTo", "body")));
    }

    static String fail(Map<String, Object> config) {
        try {
            new OAuth1AuthHandler(config);
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    // a server that verifies the signature as an OAuth 1.0 provider would, from the request as received -----------------

    static final String CONSUMER_SECRET = "c&s ecret";
    static final String TOKEN_SECRET = "t+secret";
    static final Pattern HEADER_PARAM = Pattern.compile("(\\w+)=\"([^\"]*)\"");
    static HttpServer server;
    static String baseUrl;

    @BeforeAll
    static void startServer() {
        server = HttpServer.start(0, request -> {
            HttpResponse response = new HttpResponse();
            String failure = verify(request);
            response.setStatus(failure == null ? 200 : 401);
            response.setBody(failure == null ? "ok" : failure, ResourceType.TEXT);
            return response;
        });
        baseUrl = "http://localhost:" + server.getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    static String verify(HttpRequest request) {
        List<String[]> params = new ArrayList<>();
        String authorization = request.getHeader("authorization");
        if (authorization != null) {
            Matcher m = HEADER_PARAM.matcher(authorization.substring("OAuth ".length()));
            while (m.find()) {
                if (!m.group(1).equals("realm")) {
                    params.add(new String[]{decode(m.group(1)), decode(m.group(2))});
                }
            }
        }
        String urlAndPath = request.getUrlAndPath();
        int q = urlAndPath.indexOf('?');
        String path = q < 0 ? urlAndPath : urlAndPath.substring(0, q);
        if (q >= 0) {
            pairs(urlAndPath.substring(q + 1), params);
        }
        String contentType = request.getHeader("content-type");
        byte[] body = request.getBody() == null ? new byte[0] : request.getBody();
        boolean form = contentType != null && contentType.startsWith("application/x-www-form-urlencoded");
        if (form) {
            pairs(new String(body, StandardCharsets.UTF_8), params);
        }
        Map<String, String> oauth = new HashMap<>();
        List<String> normalized = new ArrayList<>();
        for (String[] p : params) {
            if (p[0].startsWith("oauth_")) {
                oauth.put(p[0], p[1]);
            }
            if (!p[0].equals("oauth_signature")) {
                normalized.add(OAuth1AuthHandler.encode(p[0]) + "=" + OAuth1AuthHandler.encode(p[1]));
            }
        }
        normalized.sort((a, b) -> a.replaceFirst("=", " ").compareTo(b.replaceFirst("=", " ")));
        String method = oauth.get("oauth_signature_method");
        String hash = oauth.get("oauth_body_hash");
        try {
            if (hash != null && !hash.equals(Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance(method.substring(method.indexOf('-') + 1).replace("SHA", "SHA-")).digest(body)))) {
                return "body hash mismatch";
            }
            String base = request.getMethod() + "&" + OAuth1AuthHandler.encode(baseUrl + path) + "&"
                    + OAuth1AuthHandler.encode(String.join("&", normalized));
            if (method.equals("PLAINTEXT")) {
                return oauth.get("oauth_signature").equals(key()) ? null : "plaintext mismatch";
            }
            byte[] signature = Base64.getDecoder().decode(oauth.get("oauth_signature"));
            String bits = method.substring(method.indexOf('-') + 4);
            if (method.startsWith("RSA")) {
                Signature verifier = Signature.getInstance("SHA" + bits + "withRSA");
                verifier.initVerify(publicKey());
                verifier.update(base.getBytes(StandardCharsets.UTF_8));
                return verifier.verify(signature) ? null : "rsa signature mismatch: " + base;
            }
            Mac mac = Mac.getInstance("HmacSHA" + bits);
            mac.init(new SecretKeySpec(key().getBytes(StandardCharsets.UTF_8), mac.getAlgorithm()));
            return MessageDigest.isEqual(mac.doFinal(base.getBytes(StandardCharsets.UTF_8)), signature) ? null : "hmac mismatch: " + base;
        } catch (Exception e) {
            return e.toString();
        }
    }

    static String key() {
        return OAuth1AuthHandler.encode(CONSUMER_SECRET) + "&" + OAuth1AuthHandler.encode(TOKEN_SECRET);
    }

    static PublicKey publicKey() throws Exception {
        RSAPrivateCrtKey key = (RSAPrivateCrtKey) OAuth1AuthHandler.privateKey(PKCS1_KEY);
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(key.getModulus(), key.getPublicExponent()));
    }

    static void pairs(String text, List<String[]> out) {
        for (String pair : text.split("&")) {
            if (!pair.isEmpty()) {
                String[] kv = pair.split("=", 2);
                out.add(new String[]{decode(kv[0]), kv.length < 2 ? "" : decode(kv[1])});
            }
        }
    }

    static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    static HttpResponse send(String method, Map<String, Object> config, Consumer<HttpRequestBuilder> setup) throws Exception {
        try (HttpClient client = new ApacheHttpClient()) {
            HttpRequestBuilder http = new HttpRequestBuilder(client);
            Map<String, Object> full = config("consumerKey", "key!", "consumerSecret", CONSUMER_SECRET, "token", "tok en",
                    "tokenSecret", TOKEN_SECRET, "privateKey", PKCS1_KEY, "realm", "Example");
            full.putAll(config);
            http.auth(new OAuth1AuthHandler(full));
            http.url(baseUrl + "/api/items");
            http.param("filter", "a b&c=d");
            http.param("tag", "y", "x");
            http.param("unicode", "ሴ");
            http.method(method);
            if (setup != null) {
                setup.accept(http);
            }
            return http.invoke();
        }
    }

    @Test
    void serverVerifiesEveryMethodAndPlacement() throws Exception {
        for (String method : OAuth1AuthHandler.METHODS) {
            for (String to : List.of("header", "query")) {
                HttpResponse response = send("GET", config("signatureMethod", method, "addParamsTo", to), null);
                assertEquals(200, response.getStatus(), method + " " + to + ": " + response.getBodyString());
                response = send("POST", config("signatureMethod", method, "addParamsTo", to),
                        b -> b.formField("name", "Ladies + Gentlemen").formField("empty", ""));
                assertEquals(200, response.getStatus(), method + " " + to + " form: " + response.getBodyString());
                response = send("PUT", config("signatureMethod", method, "addParamsTo", to, "includeBodyHash", true,
                        "callback", "oob", "verifier", "v!"), b -> b.bodyJson("{\"a\":\"é\"}"));
                assertEquals(200, response.getStatus(), method + " " + to + " body hash: " + response.getBodyString());
            }
        }
    }

    @Test
    void formPlacementGoesInTheBody() throws Exception {
        HttpResponse response = send("POST", config("addParamsTo", "query"), b -> b.formField("name", "x"));
        assertEquals(200, response.getStatus(), response.getBodyString());
        String sent = new String(response.getRequest().getBody(), StandardCharsets.UTF_8);
        assertTrue(sent.contains("&oauth_signature="), sent);
        assertFalse(response.getRequest().getUrlAndPath().contains("oauth_"), response.getRequest().getUrlAndPath());
    }

    @Test
    void wrongSecretIsRefused() throws Exception {
        HttpResponse response = send("GET", config("consumerSecret", "wrong"), null);
        assertEquals(401, response.getStatus());
    }

}

package io.karatelabs.http;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs every vector of AWS's published SigV4 test suite (see the README beside the vectors)
 * through the handler's signing core and asserts the canonical request, string to sign and
 * Authorization header byte-exact.
 */
class AwsSigV4TestSuiteTest {

    static final Path SUITE = Path.of("src/test/resources/io/karatelabs/http/aws-sig-v4-test-suite");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2015-08-30T12:36:00Z"), ZoneOffset.UTC);
    static final Pattern TOKEN = Pattern.compile("(?m)^x-amz-security-token:(.*)$");

    @TestFactory
    Stream<DynamicTest> vectors() throws IOException {
        List<Path> requests;
        try (Stream<Path> files = Files.walk(SUITE)) {
            requests = files.filter(p -> p.toString().endsWith(".req")).sorted().toList();
        }
        assertEquals(34, requests.size());
        return requests.stream().map(req -> DynamicTest.dynamicTest(
                SUITE.relativize(req.getParent()).toString(), () -> verify(req)));
    }

    static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    static void verify(Path req) throws IOException {
        String base = req.toString().substring(0, req.toString().length() - ".req".length());
        String canonicalRequest = read(Path.of(base + ".creq"));
        String stringToSign = read(Path.of(base + ".sts"));
        String authorization = read(Path.of(base + ".authz"));
        String[] lines = read(req).split("\n", -1);
        String requestLine = lines[0];
        String method = requestLine.substring(0, requestLine.indexOf(' '));
        String target = requestLine.substring(method.length() + 1, requestLine.lastIndexOf(" HTTP/"));
        int q = target.indexOf('?');
        String path = q < 0 ? target : target.substring(0, q);
        String query = q < 0 ? null : target.substring(q + 1);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        String lastName = null;
        int i = 1;
        for (; i < lines.length && !lines[i].isEmpty(); i++) {
            String line = lines[i];
            if (line.startsWith(" ") || line.startsWith("\t")) { // obsolete line folding
                List<String> values = headers.get(lastName);
                values.set(values.size() - 1, values.getLast() + "\n" + line);
                continue;
            }
            int colon = line.indexOf(':');
            lastName = line.substring(0, colon);
            headers.computeIfAbsent(lastName, k -> new ArrayList<>()).add(line.substring(colon + 1));
        }
        String body = i + 1 < lines.length ? String.join("\n", Arrays.copyOfRange(lines, i + 1, lines.length)) : "";
        // the handler adds these itself
        headers.keySet().removeIf(name -> name.equalsIgnoreCase("X-Amz-Date") || name.equalsIgnoreCase("X-Amz-Security-Token"));
        Map<String, Object> config = new HashMap<>();
        config.put("accessKey", "AKIDEXAMPLE");
        config.put("secretKey", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY");
        config.put("region", "us-east-1");
        config.put("service", "service");
        Matcher token = TOKEN.matcher(canonicalRequest);
        if (token.find()) {
            config.put("sessionToken", token.group(1));
        }
        AwsSigV4AuthHandler handler = new AwsSigV4AuthHandler(config, CLOCK);
        AwsSigV4AuthHandler.Signed signed = handler.sign(method, path, query, headers, body.getBytes(StandardCharsets.UTF_8));
        assertEquals(canonicalRequest, signed.canonicalRequest());
        assertEquals(stringToSign, signed.stringToSign());
        assertEquals(authorization, signed.authorization());
    }

}

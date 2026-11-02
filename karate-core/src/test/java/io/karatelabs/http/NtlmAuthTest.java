package io.karatelabs.http;

import io.karatelabs.core.ScenarioRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code configure auth = { type: 'ntlm' }} against a server that challenges with NTLM beside Negotiate, as IIS does:
 * the client answers with a Type 1 message, then the Type 2 challenge with a Type 3.
 */
class NtlmAuthTest {

    HttpServer server;
    final List<String> authorizations = Collections.synchronizedList(new ArrayList<>());

    @AfterEach
    void afterEach() {
        if (server != null) {
            server.stop();
        }
    }

    /** A Type 2 message: no target name or info, the NTLM and Unicode flags, a fixed challenge. */
    static String type2() {
        byte[] b = new byte[48];
        System.arraycopy("NTLMSSP\0".getBytes(), 0, b, 0, 8);
        b[8] = 2;
        b[16] = 48;
        b[20] = 0x01;
        b[21] = (byte) 0x82;
        for (int i = 0; i < 8; i++) {
            b[24 + i] = (byte) (i + 1);
        }
        b[44] = 48;
        return Base64.getEncoder().encodeToString(b);
    }

    @Test
    void testAnNtlmChallengeIsAnsweredWithTheHandshake() {
        server = HttpServer.start(0, request -> {
            String auth = request.getHeader("Authorization");
            if (auth != null) {
                authorizations.add(auth);
            }
            if (auth != null && auth.startsWith("NTLM TlRMTVNTUAAD")) {
                return HttpResponse.json("{ \"ok\": true }");
            }
            HttpResponse response = new HttpResponse();
            response.setStatus(401);
            if (auth != null && auth.startsWith("NTLM TlRMTVNTUAAB")) {
                response.setHeader("WWW-Authenticate", "NTLM " + type2());
            } else {
                response.setHeader("WWW-Authenticate", "NTLM", "Negotiate");
            }
            return response;
        });
        ScenarioRuntime sr = runFeature(new ApacheHttpClient(), """
                Feature: ntlm auth

                Scenario: challenge, negotiate, authenticate
                * configure auth = { type: 'ntlm', username: 'alice', password: 's3cret', domain: 'corp', workstation: 'ws' }
                * url 'http://localhost:%d'
                * method get
                * status 200
                """.formatted(server.getPort()));
        assertPassed(sr);
        assertEquals(2, authorizations.size(), authorizations::toString);
        assertTrue(authorizations.get(0).startsWith("NTLM TlRMTVNTUAAB"), authorizations.get(0));
        assertTrue(authorizations.get(1).startsWith("NTLM TlRMTVNTUAAD"), authorizations.get(1));
    }

}

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

import io.netty.handler.ssl.SslContext;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SslUtilsTest {

    @Test
    void keytoolIsTheExeOnWindows() {
        String home = Path.of("/jdk").toString();
        assertEquals(Path.of(home, "bin", "keytool.exe"), SslUtils.keytool(home, "Windows Server 2025"));
        assertEquals(Path.of(home, "bin", "keytool"), SslUtils.keytool(home, "Mac OS X"));
        assertEquals(Path.of(home, "bin", "keytool"), SslUtils.keytool(home, "Linux"));
    }

    @Test
    void theSelfSignedCertificateIsForLocalhost() throws Exception {
        SslUtils.SelfSigned s = SslUtils.selfSigned();
        assertEquals("CN=localhost", s.cert().getSubjectX500Principal().getName());
        s.cert().checkValidity();
        assertNotNull(s.key());
        SslContext netty = SslUtils.generateNettySslContext();
        assertTrue(netty.isServer());
        SSLContext jdk = SslUtils.generateSelfSigned();
        assertNotNull(jdk.createSSLEngine());
    }

}

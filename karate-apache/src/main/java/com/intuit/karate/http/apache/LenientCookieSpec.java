/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.http.apache;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.impl.cookie.DefaultCookieSpec;
import org.apache.http.impl.cookie.NetscapeDraftSpec;
import org.apache.http.protocol.HttpContext;

/**
 *
 * @author pthomas3
 */
public class LenientCookieSpec extends DefaultCookieSpec {
    
    public static String KARATE = "karate";
    
    public LenientCookieSpec() {
        super(new String[]{"EEE, dd-MMM-yy HH:mm:ss z", "EEE, dd MMM yyyy HH:mm:ss Z"}, false);
    }

    @Override
    public boolean match(Cookie cookie, CookieOrigin origin) {
        return true;
    }

    @Override
    public void validate(Cookie cookie, CookieOrigin origin) throws MalformedCookieException {
        // do nothing
    }        

    public static Registry<CookieSpecProvider> registry() {
        CookieSpecProvider specProvider = (HttpContext hc) -> new LenientCookieSpec();
        return RegistryBuilder.<CookieSpecProvider>create()
                .register(KARATE, specProvider).build();
    }

}

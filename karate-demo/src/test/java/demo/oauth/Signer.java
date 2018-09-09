/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package demo.oauth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

/**
 *
 * @author pthomas3
 */
public class Signer {
    
public static void sign(String token, Map<String, String> params) {
        List<String> list = new ArrayList();
        String tokenClientSlat = "";
        for (String key : params.keySet()) {
            if (key.equals("token_client_salt")) {
                tokenClientSlat = params.get(key);
            }
            String paramString = key + "=" + params.get(key);
            list.add(paramString);
        }
        Collections.sort(list);
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            sb.append(s);
        }
        sb.append(token);
        String sig = md5Hex(sb.toString());
        String tokenSig = sha256Hex(sig + tokenClientSlat);
        params.put("sig", sig);
        params.put("__NStokensig", tokenSig);
    }    
    
}

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
package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Embed {

    private String mimeType;
    private byte[] bytes;

    public static Embed forVideoFile(String fileName) {
        String html = "<video controls=\"true\" width=\"100%\"><source src=\"" + fileName + "\" type=\"video/mp4\"/></video>";
        Embed embed = new Embed();
        embed.setBytes(html.getBytes());
        embed.setMimeType("text/html");
        return embed;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public String getBase64() {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public String getAsString() {
        return FileUtils.toString(bytes);
    }
    
    public String getAsHtmlData() {
        return "data:" + getMimeType() + ";base64," + getBase64();
    }

    public Map toMap() {
        Map map = new HashMap(2);
        map.put("data", getBase64());
        map.put("mime_type", mimeType);
        return map;
    }

}

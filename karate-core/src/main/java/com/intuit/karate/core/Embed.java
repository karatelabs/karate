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
import com.intuit.karate.http.ResourceType;
import java.io.File;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Embed {

    private final File file;
    private final ResourceType resourceType;

    public Embed(File file, ResourceType resourceType) {
        this.file = file;
        this.resourceType = resourceType;
    }

    public String getAsHtmlForReport() {
        if (resourceType.isImage() || resourceType.isVideo()) {
            return getAsHtmlTag();
        } else {
            return getAsString();
        }
    }

    public static Embed fromKarateJson(Map<String, Object> map) {
        String fileName = (String) map.get("file");
        String rtName = (String) map.get("resourceType");
        File file = new File(fileName);
        ResourceType rt = ResourceType.valueOf(rtName);
        return new Embed(file, rt);
    }

    public Map<String, Object> toKarateJson() {
        Map<String, Object> map = new HashMap();
        map.put("file", file.getPath());
        map.put("resourceType", resourceType.name());
        map.put("html", getAsHtmlForReport()); // not used in fromKarateJson()
        return map;
    }

    public File getFile() {
        return file;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public byte[] getBytes() {
        return FileUtils.toBytes(file);
    }

    public String getBase64() {
        return Base64.getEncoder().encodeToString(getBytes());
    }

    public String getAsString() {
        return FileUtils.toString(file);
    }

    public String getAsHtmlData() {
        return "data:" + resourceType.contentType + ";base64," + getBase64();
    }

    public String getAsHtmlTag() {
        if (resourceType == ResourceType.MP4) {
            return "<video controls=\"true\" width=\"100%\"><source src=\"" + file.getName() + "\" type=\"video/mp4\"/></video>";
        } else if (resourceType.isImage()) {
            return "<img src=\"" + file.getName() + "\"/>";
        } else {
            return "<a href=\"" + file.getName() + "\">" + file.getName() + "</a>";
        }
    }

    public Map toMap() {
        Map map = new HashMap(2);
        if (resourceType == ResourceType.MP4) {
            byte[] bytes = FileUtils.toBytes(getAsHtmlTag());
            String base64 = Base64.getEncoder().encodeToString(bytes);
            map.put("data", base64);
            map.put("mime_type", ResourceType.HTML.contentType);
        } else {
            map.put("data", getBase64());
            map.put("mime_type", resourceType.contentType);
        }
        return map;
    }

    @Override
    public String toString() {
        return file.toString();
    }

}

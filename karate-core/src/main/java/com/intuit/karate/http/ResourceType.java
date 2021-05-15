/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public enum ResourceType {

    JS("text/javascript", vals("javascript"), vals("js")),
    JSON("application/json", vals("json"), vals("json")),
    CSS("text/css", vals("css"), vals("css")),
    ICO("image/x-icon", vals("x-icon"), vals("ico")),
    PNG("image/png", vals("png"), vals("png")),
    GIF("image/gif", vals("gif"), vals("gif")),
    JPG("image/jpeg", vals("jpeg", "jpg"), vals("jpg", "jpeg")),
    SVG("image/svg+xml", vals("svg"), vals("svg")),
    MP4("video/mp4", vals("mp4"), vals("mp4")),
    PDF("application/pdf", vals("pdf"), vals("pdf")),
    HTML("text/html", vals("html"), vals("html", "htm")),
    XML("application/xml", vals("xml"), vals("xml")),
    TEXT("text/plain", vals("plain"), vals("txt")),
    MULTIPART("multipart/form-data", vals("multipart"), vals()),
    URLENCODED("application/x-www-form-urlencoded", vals("urlencoded"), vals()),
    BINARY("application/octet-stream", vals("octet"), vals()),
    RDFXML("application/rdf+xml", vals("xml", "rdf"), vals(".rdf")),
    NTRIPLES("application/n-triples", vals("rdf"), vals(".nt")),
    TURTLE("text/turtle", vals("rdf"), vals(".ttl")),
    NQUADS("application/n-quads", vals("rdf"), vals(".nq")),
    TRIG("application/trig", vals("rdf"), vals(".trig")),
    N3("text/n3", vals("rdf"), vals(".n3")),
    JSONLD("application/ld+json", vals("json", "rdf"), vals(".jsonld"));

    private static String[] vals(String... values) {
        return values;
    }

    public final String contentType;
    public final String[] contentLike;
    public final String[] extensions;

    ResourceType(String contentType, String[] contentLike, String[] extensions) {
        this.contentType = contentType;
        this.contentLike = contentLike;
        this.extensions = extensions;
    }

    private static final Map<String, ResourceType> EXTENSION_MAP = new HashMap();

    static {
        for (ResourceType rt : ResourceType.values()) {
            for (String ext : rt.extensions) {
                EXTENSION_MAP.put(ext, rt);
            }
        }
    }

    public static ResourceType fromFileExtension(String path) {
        if (path == null) {
            return null;
        }
        int pos = path.lastIndexOf('.');
        if (pos == -1 || pos == path.length() - 1) {
            return null;
        }
        String extension = path.substring(pos + 1).trim().toLowerCase();
        ResourceType rt = EXTENSION_MAP.get(extension);
        return rt == null ? null : rt;
    }

    public String getExtension() {
        return extensions.length == 0 ? null : extensions[0];
    }

    public boolean isVideo() {
        switch (this) {
            case MP4:
                return true;
            default:
                return false;
        }
    }

    public boolean isImage() {
        switch (this) {
            case BINARY:
            case ICO:
            case PNG:
            case GIF:
            case JPG:
                return true;
            default:
                return false;
        }
    }
    
    public boolean isUrlEncodedOrMultipart() {
        switch (this) {
            case URLENCODED:
            case MULTIPART:
                return true;
            default:
                return false;
        }        
    }

    public boolean isHtml() {
        return this == HTML;
    }

    public boolean isJson() {
        switch (this) {
            case JSON:
            case JSONLD:
                return true;
            default:
                return false;
        }
    }

    public boolean isXml() {
        switch (this) {
            case XML:
            case RDFXML:
                return true;
            default:
                return false;
        }
    }

    public boolean isText() {
        return this == TEXT;
    }

    public boolean isBinary() {
        switch (this) {
            case BINARY:
            case ICO:
            case PNG:
            case GIF:
            case JPG:
            case PDF:
            case MP4:
                return true;
            default:
                return false;
        }
    }

    public static ResourceType fromContentType(String ct) {
        if (ct == null) {
            return null;
        }
        ct = ct.toLowerCase();
        for (ResourceType rt : ResourceType.values()) {
            if (ct.equals(rt.contentType)) {
                return rt;
            }
            for (String like : rt.contentLike) {
                if (ct.contains(like)) {
                    return rt;
                }
            }
        }
        return null;
    }

    public static ResourceType fromObject(Object o) {
        return fromObject(o, null);
    }

    public static ResourceType fromObject(Object o, ResourceType defaultType) {
        if (o instanceof List || o instanceof Map) {
            return JSON;
        } else if (o instanceof String) {
            return TEXT;
        } else if (o instanceof Node) {
            return XML;
        } else if (o instanceof byte[]) {
            return BINARY;
        } else {
            return defaultType;
        }
    }

}

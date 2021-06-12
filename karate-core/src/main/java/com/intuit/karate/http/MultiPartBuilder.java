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

import com.intuit.karate.Constants;
import com.intuit.karate.graal.JsValue;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.MemoryFileUpload;
import java.io.File;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class MultiPartBuilder {

    private static final Logger logger = LoggerFactory.getLogger(MultiPartBuilder.class);

    private final HttpClient client;
    private final boolean multipart;
    private final HttpPostRequestEncoder encoder;
    private Map<String, Object> formFields; // only for the edge case of GET
    private StringBuilder bodyForDisplay = new StringBuilder();

    private String contentTypeHeader;

    public String getBoundary() {
        if (contentTypeHeader == null) {
            return null;
        }
        int pos = contentTypeHeader.lastIndexOf('=');
        return pos == -1 ? null : contentTypeHeader.substring(pos + 1);
    }

    public Map<String, Object> getFormFields() {
        return formFields;
    }

    public String getContentTypeHeader() {
        return contentTypeHeader;
    }

    public boolean isMultipart() {
        return multipart;
    }

    public String getBodyForDisplay() {
        return bodyForDisplay.toString();
    }

    public MultiPartBuilder(boolean multipart, HttpClient client) {
        this.client = client;
        this.multipart = multipart;
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf("POST"), "/");
        try {
            encoder = new HttpPostRequestEncoder(request, multipart);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public MultiPartBuilder part(Map<String, Object> map) {
        String name = (String) map.get("name");
        Object value = map.get("value");
        if (!multipart) {
            String stringValue = JsValue.toString(value);
            if (formFields == null) {
                formFields = new HashMap();
            }
            formFields.put(name, stringValue);
            try {
                encoder.addBodyAttribute(name, stringValue);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            if (value instanceof File) {
                File file = (File) value;
                String filename = (String) map.get("filename");
                if (filename == null) {
                    filename = file.getName();
                }
                String contentType = (String) map.get("contentType");
                ResourceType resourceType;
                if (contentType == null) {
                    resourceType = ResourceType.fromFileExtension(filename);
                } else {
                    resourceType = ResourceType.fromContentType(contentType);
                }
                if (resourceType == null) {
                    resourceType = ResourceType.BINARY;
                }
                if (contentType == null) {
                    contentType = resourceType.contentType;
                }
                try {
                    encoder.addBodyFileUpload(name, filename, file, contentType, !resourceType.isBinary());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                String contentType = (String) map.get("contentType");
                ResourceType resourceType;
                if (contentType == null) {
                    resourceType = ResourceType.fromObject(value);
                } else {
                    resourceType = ResourceType.fromContentType(contentType);
                }               
                if (resourceType == null) {
                    resourceType = ResourceType.BINARY;
                }
                if (contentType == null) {
                    contentType = resourceType.contentType;
                }                
                Charset cs = null;
                if (!resourceType.isBinary()) {
                    String charset = (String) map.get("charset");
                    if (charset == null && client != null) { // TODO client null for unit test
                        cs = client.getConfig().getCharset();
                    } else if (charset != null) {
                        cs = Charset.forName(charset);
                    }
                }
                byte[] encoded = value == null ? Constants.ZERO_BYTES : JsValue.toBytes(value);
                String filename = (String) map.get("filename");
                if (filename == null) {
                    filename = ""; // will be treated as an inline value, behaves like null
                }
                String transferEncoding = (String) map.get("transferEncoding");
                MemoryFileUpload item = new MemoryFileUpload(name, filename, contentType, transferEncoding, cs, encoded.length);
                try {
                    item.setContent(Unpooled.wrappedBuffer(encoded));
                    encoder.addBodyHttpData(item);
                    logger.debug("multipart: {}", item);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return this;
    }

    public MultiPartBuilder part(String name, Object value) {
        Map<String, Object> map = new HashMap();
        map.put("name", name);
        map.put("value", value);
        return part(map);
    }

    public byte[] build() {
        for (InterfaceHttpData part : encoder.getBodyListAttributes()) {
            bodyForDisplay.append('\n').append(part.toString()).append('\n');
        }
        try {
            io.netty.handler.codec.http.HttpRequest request = encoder.finalizeRequest();
            contentTypeHeader = request.headers().get(HttpConstants.HDR_CONTENT_TYPE);
            // logger.debug("content type header: {}", contentTypeHeader);
            ByteBuf content;
            if (request instanceof FullHttpRequest) {
                FullHttpRequest fullRequest = (FullHttpRequest) request;
                content = fullRequest.content();
            } else {
                content = Unpooled.buffer();
                HttpContent data;
                while ((data = encoder.readChunk(ByteBufAllocator.DEFAULT)) != null) {
                    content.writeBytes(data.content());
                }
            }
            byte[] bytes = new byte[content.readableBytes()];
            content.readBytes(bytes);
            return bytes;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}

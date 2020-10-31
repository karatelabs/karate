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
package com.intuit.karate.server;

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
import io.netty.handler.codec.http.multipart.MemoryFileUpload;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class MultiPartBuilder {

    private static final Logger logger = LoggerFactory.getLogger(MultiPartBuilder.class);

    private final Charset defaultCharset;
    private final boolean multipart;
    private final HttpPostRequestEncoder encoder;
    private List<Part> formFields; // only for the edge case of GET

    private String contentTypeHeader;

    public String getBoundary() {
        if (contentTypeHeader == null) {
            return null;
        }
        int pos = contentTypeHeader.lastIndexOf('=');
        return pos == -1 ? null : contentTypeHeader.substring(pos + 1);
    }

    public List<Part> getFormFields() {
        return formFields;
    }

    public String getContentTypeHeader() {
        return contentTypeHeader;
    }

    public boolean isMultipart() {
        return multipart;
    }

    public MultiPartBuilder(boolean multipart, Charset defaultCharset) {
        this.multipart = multipart;
        this.defaultCharset = defaultCharset;
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf("POST"), "/");
        try {
            encoder = new HttpPostRequestEncoder(request, multipart);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public class Part {

        private final String name;
        private Object value;
        private String contentType;
        private String transferEncoding;
        private String filename;
        private Charset charset;

        Part(Map<String, Object> map) {
            name = (String) map.get("name");
            value = map.get("value");
            contentType = (String) map.get("contentType");
            filename = (String) map.get("filename");
            transferEncoding = (String) map.get("transferEncoding");
            String charsetString = (String) map.get("charset");
            if (charsetString != null) {
                charset = Charset.forName(charsetString);
            }
        }

        public String getName() {
            return name;
        }

        public Object getValue() {
            return value;
        }

        Part(String name) {
            this.name = name;
        }

        Part value(Object value) {
            this.value = value;
            return this;
        }

        Part filename(String filename) {
            this.filename = filename;
            return this;
        }

        Part contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        Part transferEncoding(String transferEncoding) {
            this.transferEncoding = transferEncoding;
            return this;
        }

        Part charset(String charset) {
            this.charset = Charset.forName(charset);
            return this;
        }

        MultiPartBuilder add() {
            if (!multipart) {
                String stringValue = JsValue.toString(value);
                if (formFields == null) {
                    formFields = new ArrayList();
                }
                formFields.add(new Part(name).value(stringValue));
                try {
                    encoder.addBodyAttribute(name, stringValue);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                if (contentType == null) {
                    contentType = ResourceType.fromObject(value).contentType;
                }
                byte[] bytes = value == null ? HttpConstants.ZERO_BYTES : JsValue.toBytes(value);
                if (filename == null) {
                    filename = ""; // will be treated as an inline value, behaves like null
                }
                if (charset == null) {
                    charset = defaultCharset;
                }
                MemoryFileUpload item = new MemoryFileUpload(name, filename, contentType, transferEncoding, charset, bytes.length);
                try {
                    item.setContent(Unpooled.wrappedBuffer(bytes));
                    encoder.addBodyHttpData(item);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return MultiPartBuilder.this;
        }

    }

    public MultiPartBuilder part(Map<String, Object> map) {
        return new Part(map).add();
    }

    public Part part(String name) {
        return new Part(name);
    }

    public byte[] build() {
        try {
            io.netty.handler.codec.http.HttpRequest request = encoder.finalizeRequest();
            contentTypeHeader = request.headers().get(HttpConstants.HDR_CONTENT_TYPE);
            // logger.debug("content type header: {}", contentTypeHeader);
            ByteBuf content;
            if (multipart) {
                HttpContent data = encoder.readChunk(ByteBufAllocator.DEFAULT);
                if (data == null) {
                    logger.warn("no multipart data to encode, returning zero bytes");
                    return HttpConstants.ZERO_BYTES;
                }
                content = data.content();
            } else {
                FullHttpRequest fullRequest = (FullHttpRequest) request;
                content = fullRequest.content();
            }
            byte[] bytes = new byte[content.readableBytes()];
            content.readBytes(bytes);
            return bytes;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}

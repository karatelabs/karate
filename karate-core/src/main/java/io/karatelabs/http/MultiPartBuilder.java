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
package io.karatelabs.http;

import io.karatelabs.common.Json;
import io.karatelabs.common.ResourceType;
import io.karatelabs.common.StringUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder.EncoderMode;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;

public class MultiPartBuilder {

    private static final Logger logger = LoggerFactory.getLogger(MultiPartBuilder.class);

    private final boolean multipart;
    private final String defaultCharset;
    private final HttpPostRequestEncoder encoder;
    private Map<String, Object> formFields; // only for the edge case of GET
    private final StringBuilder bodyForDisplay = new StringBuilder();

    private String contentTypeHeader;
    private byte[] builtBytes; // Cache for retry support

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

    public MultiPartBuilder(boolean multipart, String defaultCharset) {
        this.multipart = multipart;
        this.defaultCharset = defaultCharset;
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf("POST"), "/");
        try {
            encoder = new HttpPostRequestEncoder(new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE), request, multipart, CharsetUtil.UTF_8, EncoderMode.HTML5);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void addFile(String name, File file, Map<String, Object> map) {
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
    }

    @SuppressWarnings("unchecked")
    public MultiPartBuilder part(Map<String, Object> map) {
        String name = (String) map.get("name");
        Object value = map.get("value");
        if (!multipart) {
            List<String> list;
            if (value instanceof List) {
                list = (List<String>) value;
            } else {
                if (value == null) {
                    list = Collections.emptyList();
                } else {
                    list = Collections.singletonList(value.toString());
                }
            }
            if (formFields == null) {
                formFields = new HashMap<>();
            }
            for (String s : list) {
                formFields.put(name, s);
                try {
                    encoder.addBodyAttribute(name, s);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            if (value instanceof File file) {
                addFile(name, file, map);
            } else if (value instanceof List) { // recurse, hope that adding to array of fields is supported
                List<Object> list = (List<Object>) value;
                for (Object o : list) {
                    Map<String, Object> temp = new HashMap<>();
                    temp.put("name", name);
                    temp.put("value", o);
                    part(temp);
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
                    if (charset == null) {
                        charset = defaultCharset;
                    }
                    if (charset != null) {
                        cs = Charset.forName(charset);
                    }
                }
                byte[] encoded = value == null ? HttpUtils.ZERO_BYTES : Json.toBytes(value);
                String filename = (String) map.get("filename");
                if (filename == null) {
                    filename = ""; // will be treated as an inline value, behaves like null
                }
                String transferEncoding = (String) map.get("transferEncoding");
                final Charset nullable = cs;
                MemoryFileUpload item = new MemoryFileUpload(name, filename, contentType, transferEncoding, cs, encoded.length) {
                    @Override
                    public Charset getCharset() {
                        return nullable; // workaround for netty api strictness
                    }
                };
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
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("value", value);
        return part(map);
    }

    public String toCurlCommand() {
        return toCurlCommand("sh");
    }

    public String toCurlCommand(String platform) {
        if (platform == null) {
            platform = "sh";
        }
        String lineContinuation = StringUtils.getLineContinuation(platform);

        StringBuilder sb = new StringBuilder();
        Iterator<InterfaceHttpData> parts = encoder.getBodyListAttributes().iterator();
        while (parts.hasNext()) {
            InterfaceHttpData part = parts.next();
            String name = part.getName();

            if (part instanceof FileUpload fileUpload) {
                // Check if this is an actual file upload or just a text field with empty filename
                String filename = fileUpload.getFilename();

                if (filename != null && !filename.isEmpty()) {
                    // Real file upload
                    sb.append("-F ");
                    sb.append(StringUtils.shellEscapeForPlatform(name + "=@" + filename, platform));

                    // Add content type if present
                    String contentType = fileUpload.getContentType();
                    if (contentType != null && !contentType.isEmpty()) {
                        sb.append(";type=").append(contentType);
                    }
                } else {
                    // Text field stored as FileUpload (with empty filename)
                    // Get the actual string value
                    String value = null;
                    try {
                        value = fileUpload.getString();
                    } catch (Exception e) {
                        logger.error("failed to get file upload value: {}", e.getMessage());
                    }

                    if (multipart) {
                        // For multipart/form-data, use -F
                        sb.append("-F ");
                        sb.append(StringUtils.shellEscapeForPlatform(name + "=" + (value != null ? value : ""), platform));
                    } else {
                        // For application/x-www-form-urlencoded, use --data-urlencode
                        sb.append("--data-urlencode ");
                        sb.append(StringUtils.shellEscapeForPlatform(name + "=" + (value != null ? value : ""), platform));
                    }
                }
            } else if (part instanceof Attribute attr) {
                // Handle simple form fields
                String value;
                try {
                    value = attr.getValue();
                } catch (Exception e) {
                    value = null;
                    logger.error("failed to get multipart value: {}", e.getMessage());
                }

                if (multipart) {
                    // For multipart/form-data, use -F
                    sb.append("-F ");
                    sb.append(StringUtils.shellEscapeForPlatform(name + "=" + (value != null ? value : ""), platform));
                } else {
                    // For application/x-www-form-urlencoded, use --data-urlencode
                    sb.append("--data-urlencode ");
                    sb.append(StringUtils.shellEscapeForPlatform(name + "=" + (value != null ? value : ""), platform));
                }
            }

            if (parts.hasNext()) {
                sb.append(lineContinuation);
            }
        }
        return sb.toString();
    }

    public byte[] build() {
        // Return cached bytes if already built (for retry support)
        if (builtBytes != null) {
            return builtBytes;
        }
        // TODO move this to getter if possible
        for (InterfaceHttpData part : encoder.getBodyListAttributes()) {
            bodyForDisplay.append('\n').append(part.toString()).append('\n');
        }
        try {
            io.netty.handler.codec.http.HttpRequest request = encoder.finalizeRequest();
            contentTypeHeader = request.headers().get(HttpUtils.Header.CONTENT_TYPE.key);
            // logger.debug("content type header: {}", contentTypeHeader);
            ByteBuf content;
            if (request instanceof FullHttpRequest fullRequest) {
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
            builtBytes = bytes; // Cache for retry
            return bytes;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}

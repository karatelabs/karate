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

import com.intuit.karate.ScriptValue;
import com.intuit.karate.StringUtils;
import com.intuit.karate.http.HttpBody;
import com.intuit.karate.http.HttpUtils;
import com.intuit.karate.http.MultiPartItem;
import com.intuit.karate.http.MultiValuedMap;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.FormBodyPartBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.message.BasicNameValuePair;

/**
 *
 * @author pthomas3
 */
public class ApacheHttpUtils {
    
    private ApacheHttpUtils() {
        // only static methods
    }
    
    public static HttpBody toBody(HttpEntity entity) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            entity.writeTo(baos);
            return HttpBody.bytes(baos.toByteArray(), entity.getContentType().getValue());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }        
    }
    
    // all this complexity is to be able to support "bad" values such as an empty string
    private static ContentType getContentType(String mediaType, Charset charset) {
        if (!HttpUtils.isPrintable(mediaType)) {
            try {
                return ContentType.create(mediaType);
            } catch (Exception e) {                
                return null;
            }
        }
        Map<String, String> map = HttpUtils.parseContentTypeParams(mediaType);
        if (map != null) {
            String cs = map.get(HttpUtils.CHARSET);
            if (cs != null) {
                charset = Charset.forName(cs);
                map.remove(HttpUtils.CHARSET);
            }
        }
        ContentType ct = ContentType.parse(mediaType).withCharset(charset);
        if (map != null) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                ct = ct.withParameters(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }
        }
        return ct;
    }
    
    public static HttpEntity getEntity(InputStream is, String mediaType, Charset charset) {
        try {
            return new InputStreamEntity(is, is.available(), getContentType(mediaType, charset));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }         
    }    
    
    public static HttpEntity getEntity(String value, String mediaType, Charset charset) {
        try {
            ContentType ct = getContentType(mediaType, charset);
            if (ct == null) { // "bad" value such as an empty string
                StringEntity entity = new StringEntity(value);
                entity.setContentType(mediaType);
                return entity;
            } else {
                return new StringEntity(value, ct);
            }            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }         
    }
    
    public static HttpEntity getEntity(MultiValuedMap fields, String mediaType, Charset charset) {
        List<NameValuePair> list = new ArrayList<>(fields.size());
        for (Map.Entry<String, List> entry : fields.entrySet()) {
            String stringValue;
            List values = entry.getValue();
            if (values == null) {
                stringValue = null;
            } else if (values.size() == 1) {
                Object value = values.get(0);
                if (value == null) {
                    stringValue = null;
                } else if (value instanceof String) {
                    stringValue = (String) value;
                } else {
                    stringValue = value.toString();
                }
            } else {
                stringValue = StringUtils.join(values, ',');
            }
            list.add(new BasicNameValuePair(entry.getKey(), stringValue));
        }
        try {
            Charset cs = HttpUtils.parseContentTypeCharset(mediaType);
            if (cs == null) {
                cs = charset;
            }            
            String raw = URLEncodedUtils.format(list, cs);
            int pos = mediaType.indexOf(';');
            if (pos != -1) { // strip out charset param from content-type
                mediaType = mediaType.substring(0, pos);
            }
            return new StringEntity(raw, ContentType.create(mediaType, cs));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static HttpEntity getEntity(List<MultiPartItem> items, String mediaType, Charset charset) {
        boolean hasNullName = false;
        for (MultiPartItem item : items) {
            if (item.getName() == null) {
                hasNullName = true;
                break;
            }
        }
        if (hasNullName) { // multipart/related
            String boundary = HttpUtils.generateMimeBoundaryMarker();
            String text = HttpUtils.multiPartToString(items, boundary);
            ContentType ct = ContentType.parse(mediaType).withParameters(new BasicNameValuePair("boundary", boundary));
            return new StringEntity(text, ct);
        } else {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create().setContentType(ContentType.parse(mediaType));
            for (MultiPartItem item : items) {
                if (item.getValue() == null || item.getValue().isNull()) {
                    continue;
                }
                String name = item.getName();                
                if (name == null) {
                    // will never happen because we divert this flow to the home-made multi-part builder above
                    // builder.addPart(bodyPart);
                } else {
                    ScriptValue sv = item.getValue();
                    String ct = item.getContentType();                    
                    if (ct == null) {
                        ct = HttpUtils.getContentType(sv);
                    }
                    ContentType contentType = ContentType.create(ct);
                    if (HttpUtils.isPrintable(ct)) {
                        Charset cs = HttpUtils.parseContentTypeCharset(mediaType);
                        if (cs == null) {
                            cs = charset;
                        }
                        contentType = contentType.withCharset(cs);                         
                    }                   
                    FormBodyPartBuilder formBuilder = FormBodyPartBuilder.create().setName(name);
                    ContentBody contentBody;
                    String filename = item.getFilename();
                    if (filename != null) {                        
                        contentBody = new ByteArrayBody(sv.getAsByteArray(), contentType, filename);
                    } else if (sv.isStream()) {
                        contentBody = new InputStreamBody(sv.getAsStream(), contentType);
                    } else {
                        contentBody = new StringBody(sv.getAsString(), contentType);
                    }
                    formBuilder = formBuilder.setBody(contentBody);
                    builder = builder.addPart(formBuilder.build());
                }
            }
            return builder.build();
        }
    }    
    
}

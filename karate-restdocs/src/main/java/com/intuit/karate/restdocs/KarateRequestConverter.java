package com.intuit.karate.restdocs;

import com.intuit.karate.http.Cookie;
import com.intuit.karate.http.HttpRequestBuilder;
import com.intuit.karate.http.HttpUtils;
import com.intuit.karate.http.MultiPartItem;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.restdocs.operation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by rkumar32 on 7/14/17.
 */
public class KarateRequestConverter
        implements RequestConverter<HttpRequestBuilder> {
    @Override
    public OperationRequest convert(HttpRequestBuilder httpRequest) {
        return new OperationRequestFactory().create(
                extractURI(httpRequest),
                HttpMethod.valueOf(httpRequest.getMethod()),
                extractBody(httpRequest),
                extractHeaders(httpRequest),
                extractParameters(httpRequest),
                extractParts(httpRequest),
                extractCookies(httpRequest)
        );
    }

    private URI extractURI(HttpRequestBuilder httpRequest) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(httpRequest.getUrl());
        if (httpRequest.getPaths() != null) {
            for (String path : httpRequest.getPaths()) {
                builder.path(path).path("/");
            }
        }
        return builder.build().toUri();
    }

    private byte[] extractBody(HttpRequestBuilder httpRequest) {
        if (httpRequest.getBody() != null) {
            return httpRequest.getBody().getAsString().getBytes();
        }
        else {
            return new byte[0];
        }
    }

    private HttpHeaders extractHeaders(HttpRequestBuilder httpRequest) {
        HttpHeaders httpHeaders = new HttpHeaders();
        if (httpRequest.getHeaders() != null) {
            for (Map.Entry<String, List> entry : httpRequest.getHeaders().entrySet()) {
                String key = entry.getKey();
                List value = entry.getValue();
                for (Object object : value) {
                    httpHeaders.add(key, (String) object);
                }
            }
        }
        if (httpRequest.getBody() != null) {
            String contentType = HttpUtils.getContentType(httpRequest.getBody());
            httpHeaders.setContentType(
                    contentType == null ? MediaType.TEXT_PLAIN
                            : MediaType.parseMediaType(contentType));
        }
        return httpHeaders;
    }

    private Parameters extractParameters(HttpRequestBuilder httpRequest) {
        Parameters parameters = new Parameters();
        if (httpRequest.getParams() != null) {
            for (Map.Entry<String, List> entry : httpRequest.getParams().entrySet()) {
                String key = entry.getKey();
                List value = entry.getValue();
                for (Object object : value) {
                    parameters.add(key, (String) object);
                }
            }
        }

        if (httpRequest.getFormFields() != null) {
            for (Map.Entry<String, List> entry : httpRequest.getFormFields().entrySet()) {
                String key = entry.getKey();
                List value = entry.getValue();
                for (Object object : value) {
                    parameters.add(key, (String) object);
                }
            }
        }
        return parameters;
    }

    private Collection<OperationRequestPart> extractParts(HttpRequestBuilder httpRequest) {
        List<OperationRequestPart> parts = new ArrayList<>();
        if (httpRequest.getMultiPartItems() != null) {
            for (MultiPartItem multiPartItem : httpRequest.getMultiPartItems()) {
                HttpHeaders headers = new HttpHeaders();
                String contentType = HttpUtils.getContentType(multiPartItem.getValue());
                headers.setContentType(
                        contentType == null ? MediaType.TEXT_PLAIN
                                : MediaType.parseMediaType(contentType));
                parts.add(new OperationRequestPartFactory().create(
                        multiPartItem.getName(), multiPartItem.getName(),
                        multiPartItem.getValue().getAsString().getBytes(), headers));
            }
        }
        return parts;
    }

    private Collection<RequestCookie> extractCookies(HttpRequestBuilder httpRequest) {
        Collection<RequestCookie> cookies = new ArrayList<>();
        if (httpRequest.getCookies() != null) {
            for (Map.Entry<String, Cookie> cookie : httpRequest.getCookies().entrySet()) {
                cookies.add(new RequestCookie(cookie.getValue().getName(), cookie.getValue().getValue()));
            }
        }
        return cookies;
    }
}

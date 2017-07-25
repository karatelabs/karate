package com.intuit.karate.restdocs;

import com.intuit.karate.http.HttpResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.restdocs.operation.OperationResponse;
import org.springframework.restdocs.operation.OperationResponseFactory;
import org.springframework.restdocs.operation.ResponseConverter;

import java.util.List;
import java.util.Map;

/**
 * Created by rkumar32 on 7/20/17.
 */
public class KarateResponseConverter implements ResponseConverter<HttpResponse> {
    @Override
    public OperationResponse convert(HttpResponse response) {
        return new OperationResponseFactory().create(
                HttpStatus.valueOf(response.getStatus()),
                extractHeaders(response),
                extractBody(response));
    }

    private byte[] extractBody(HttpResponse response) {
        if (response.getBody() != null) {
            return response.getBody();
        }
        else {
            return new byte[0];
        }
    }

    private HttpHeaders extractHeaders(HttpResponse response) {
        HttpHeaders httpHeaders = new HttpHeaders();
        if (response.getHeaders() != null) {
            for (Map.Entry<String, List> entry : response.getHeaders().entrySet()) {
                String key = entry.getKey();
                List value = entry.getValue();
                for (Object object : value) {
                    httpHeaders.add(key, (String) object);
                }
            }
        }
        return httpHeaders;
    }
}

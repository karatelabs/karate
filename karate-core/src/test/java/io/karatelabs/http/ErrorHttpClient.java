package io.karatelabs.http;

import io.karatelabs.core.KarateConfig;

public class ErrorHttpClient implements HttpClient {

    @Override
    public HttpResponse invoke(HttpRequest request) {
        throw new RuntimeException("failed");
    }

    @Override
    public void apply(KarateConfig config) {

    }

    @Override
    public void abort() {

    }

    @Override
    public void close() {

    }

}

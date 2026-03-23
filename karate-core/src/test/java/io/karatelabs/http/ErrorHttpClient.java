package io.karatelabs.http;

public class ErrorHttpClient implements HttpClient {

    @Override
    public HttpResponse invoke(HttpRequest request) {
        throw new RuntimeException("failed");
    }

    @Override
    public void config(String key, Object value) {

    }

    @Override
    public void abort() {

    }

    @Override
    public void close() {

    }

}

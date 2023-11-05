package com.intuit.karate.http;

import java.io.IOException;

import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.util.TimeValue;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.HttpContext;

import com.intuit.karate.Logger;

/**
 * Calls will retry the call when the client throws a NoHttpResponseException.
 * This is usually the case when there is steal connection. The retry cause that
 * the connection is renewed and the second call will succeed.
 */
public class CustomHttpRequestRetryHandler implements HttpRequestRetryHandler, HttpRequestRetryStrategy
{
    private final Logger logger;

    public CustomHttpRequestRetryHandler(Logger logger)
    {
        this.logger = logger;
    }

    @Override
    public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
        return shouldRetry(exception, executionCount);
    }

    private boolean shouldRetry(IOException exception, int executionCount) {
        if (exception instanceof NoHttpResponseException && executionCount < 1)
        {
            logger.error("Thrown an NoHttpResponseException retry...");
            return true;
        }
        else
        {
            logger.error("Thrown an exception {}", exception.getMessage());
            return false;
        }
    }

    @Override
    public boolean retryRequest(HttpRequest request, IOException exception, int executionCount,
            org.apache.hc.core5.http.protocol.HttpContext context) {
        return shouldRetry(exception, executionCount);                
    }

    @Override
    public boolean retryRequest(HttpResponse response, int execCount,
            org.apache.hc.core5.http.protocol.HttpContext context) {
        return false;
     }

    @Override
    public TimeValue getRetryInterval(HttpResponse response, int execCount,
            org.apache.hc.core5.http.protocol.HttpContext context) {
        return TimeValue.ofSeconds(1); // NOt sure what the interval was in httpclient4 ... Sticking with the default value of the default http5 implementation.         
    }
}
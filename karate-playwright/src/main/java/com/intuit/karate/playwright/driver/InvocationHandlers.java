package com.intuit.karate.playwright.driver;

import com.intuit.karate.Logger;
import com.intuit.karate.driver.Driver;

import java.io.Closeable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;

public class InvocationHandlers {

    static InvocationHandler retryHandler(Object delegate, int count, int interval, Logger logger, PlaywrightDriver driver) {
        driver.retryTimeout((double)interval);
        return (proxy, method, args) -> {
            long start = System.currentTimeMillis();
            try {
                for (int i = 0; i < count; i++) {
                    try {
                        logger.debug("{} - retry #{}", method.getName(), i);

                        Object response = method.invoke(delegate, args);
                        if (response != null) {
                            return response;
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InvocationTargetException e) {

                    }
                }
            } finally {
                driver.retryTimeout(null);
            }
            String message = method + ": failed after " + (count - 1) + " retries and " + (System.currentTimeMillis() - start) + " milliseconds";
            logger.warn(message);
            throw new RuntimeException(message);
        };
    }

    static InvocationHandler submitHandler(Object delegate, Runnable waitingForPage) {
        return (proxy, method, args) -> {
            Object response = method.invoke(delegate, args);
            waitingForPage.run();
            if (waitingForPage instanceof Closeable){
                ((Closeable)waitingForPage).close();
            }
            return response;
        };
    }

}

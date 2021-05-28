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
package com.intuit.karate.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public abstract class ParallelProcessor<T> {

    private static final Logger logger = LoggerFactory.getLogger(ParallelProcessor.class);

    private final ExecutorService executor;
    private final ExecutorService monitor;
    private final Iterator<T> publisher;
    private final List<CompletableFuture> futures = new ArrayList();

    public ParallelProcessor(ExecutorService executor, Iterator<T> publisher, ExecutorService monitor) {
        this.executor = executor;
        this.publisher = publisher;
        this.monitor = monitor;
    }

    private Runnable toRunnable(final CompletableFuture prevFuture, final T next, final CompletableFuture future) {
        return () -> {
            if (prevFuture != null) {
                prevFuture.join();
            }
            try {
                process(next);
            } catch (Exception e) {
                logger.error("[parallel] input item failed: {}", e.getMessage());
            }
            future.complete(Boolean.TRUE);
        };
    }

    public void execute() {
        CompletableFuture prevFuture = null;
        while (publisher.hasNext()) {
            final CompletableFuture future = new CompletableFuture();
            futures.add(future);
            T next = publisher.next();
            boolean sync = shouldRunSynchronously(next);
            executor.submit(toRunnable(prevFuture, next, future));
            prevFuture = sync ? future : null;
        }
        final CompletableFuture[] futuresArray = futures.toArray(new CompletableFuture[futures.size()]);
        monitor.submit(() -> {
            CompletableFuture.allOf(futuresArray).join();
            onComplete();
        });
    }

    public boolean shouldRunSynchronously(T in) {
        // parallel by default
        // but allow a per work-item strategy
        return false;
    }

    public abstract void process(T in);

    public abstract void onComplete();

}

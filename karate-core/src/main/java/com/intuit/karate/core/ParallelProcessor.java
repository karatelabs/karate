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
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public abstract class ParallelProcessor<I, O> {

    private static final Logger logger = LoggerFactory.getLogger(ParallelProcessor.class);

    private final ExecutorService executor;
    private final Semaphore semaphore;
    private final Stream<I> publisher;

    public ParallelProcessor(ExecutorService executor, int batchSize, Stream<I> publisher) {
        this.executor = executor;
        semaphore = new Semaphore(batchSize);
        this.publisher = publisher;
    }

    public void execute() {
        final List<CompletableFuture> futures = new ArrayList();
        publisher.forEach(in -> {
            final CompletableFuture future = new CompletableFuture();
            futures.add(future);
            waitForHeadRoom();
            executor.submit(() -> {
                try {
                    process(in);
                    future.complete(Boolean.TRUE);
                    semaphore.release();
                } catch (Exception e) {
                    logger.error("[parallel] input item failed: {}", e.getMessage());
                }
            });
        });
        if (futures.size() > 0) {
            waitForHeadRoom();
            final CompletableFuture[] futuresArray = futures.toArray(new CompletableFuture[futures.size()]);;
            executor.submit(() -> { // will not block caller even when waiting for completion
                CompletableFuture.allOf(futuresArray).join();
                // IMPORTANT: release parent locks first ...
                onComplete();
                // before freeing up main thread
                semaphore.release();
            });
            waitForHeadRoom();
        }
    }

    private void waitForHeadRoom() {
        try {
            semaphore.acquire();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean shouldRunSynchronously(I in) {
        // parallel by default
        // but allow a per work-item strategy
        return false;
    }

    public abstract Iterator<O> process(I in);

    public abstract void onComplete();

}

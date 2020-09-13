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
public abstract class ParallelProcessor<I, O> implements Processor<I, O> {

    private static final Logger logger = LoggerFactory.getLogger(ParallelProcessor.class);

    private final ExecutorService executor;
    private final Iterator<I> publisher;
    private final List<CompletableFuture<Boolean>> futures = new ArrayList();

    private Subscriber<O> subscriber;

    public ParallelProcessor(ExecutorService executor, Iterator<I> publisher) {
        this.executor = executor;
        this.publisher = publisher;
    }

    private void execute(final boolean sync, final I in, final Subscriber<O> s) {
        Iterator<O> out = process(in);
        if (sync) {
            out.forEachRemaining(s::onNext);
        } else {
            out.forEachRemaining(o -> {
                synchronized (s) { // synchronized is important if multiple threads
                    s.onNext(o);
                }
            });
        }
    }

    @Override
    public void onNext(final I in) {
        boolean sync = shouldRunSynchronously(in);
        if (sync) {
            execute(sync, in, subscriber);
        } else {
            CompletableFuture<Boolean> cf = new CompletableFuture();
            futures.add(cf);
            executor.submit(() -> {
                execute(sync, in, subscriber);
                cf.complete(Boolean.TRUE);
            });
        }
    }

    @Override
    public void subscribe(final Subscriber<O> subscriber) {
        this.subscriber = subscriber;
        publisher.forEachRemaining(this::onNext);
        CompletableFuture[] futuresArray = futures.toArray(new CompletableFuture[futures.size()]);
        if (futuresArray.length > 0) {
            executor.submit(() -> { // will not block caller even when waiting for completion
                CompletableFuture.allOf(futuresArray).join();
                subscriber.onComplete();
            });
        } else {
            subscriber.onComplete();
        }
    }

    @Override
    public abstract Iterator<O> process(I in);

    public boolean shouldRunSynchronously(I in) {
        return false;
    }

}

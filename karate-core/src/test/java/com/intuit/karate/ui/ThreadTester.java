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
package com.intuit.karate.ui;

import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.embed.swing.JFXPanel;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author vmchukky
 */
public class ThreadTester {

    private static final Logger logger = LoggerFactory.getLogger(ThreadTester.class);

    @Test
    public void testRunAll() {
        JavaFxRunnable runnable = new JavaFxRunnable();
        new Thread(runnable).start();
        File tempFile = new File("src/test/java/com/intuit/karate/ui/threadtest.feature");
        AppSession session = new AppSession(tempFile, null, false);
        session.runAll();
        assertThreadName(session, RunService.RUN_ALL_THREAD_NAME);
        runnable.stopFx();
    }

    @Test
    public void testRunUpto() {
        JavaFxRunnable runnable = new JavaFxRunnable();
        new Thread(runnable).start();
        File tempFile = new File("src/test/java/com/intuit/karate/ui/threadtest.feature");
        AppSession session = new AppSession(tempFile, null, false);
        StepPanel step10Scenario2 = session.featurePanel.getSectionAtIndex(1).getStepAtIndex(9);
        session.runUpto(step10Scenario2);
        assertThreadName(session, RunService.RUN_UPTO_THREAD_NAME_PREFIX + 10);
    }

    private void assertThreadName(AppSession session, String expectedThreadName) {
        // simplest way to wait for runAll to finish
        while (session.isRunningNow().get()) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        ObservableList<Var> vars = session.getVars();
        String threadName = null;
        for (Var var : vars) {
            if ("threadName".equals(var.getName())) {
                threadName = var.getValue().getAsString();
                break;
            }
        }
        Assert.assertTrue(expectedThreadName.equals(threadName));
    }

    class JavaFxRunnable implements Runnable {

        final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void run() {
            PlatformImpl.startup(() -> {
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public void stopFx() {
            latch.countDown();
            PlatformImpl.exit();
        }
    }

}

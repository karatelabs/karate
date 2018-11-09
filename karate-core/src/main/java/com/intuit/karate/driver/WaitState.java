/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate.driver;

import com.intuit.karate.Logger;
import java.util.function.Predicate;

/**
 *
 * @author pthomas3
 */
public class WaitState {

    private final DriverOptions options;
    private final Logger logger;

    private DevToolsMessage lastSent;
    private Predicate<DevToolsMessage> condition;
    private DevToolsMessage lastReceived;

    private final Predicate<DevToolsMessage> DEFAULT = m -> lastSent.getId().equals(m.getId()) && m.getResult() != null;
    public static final Predicate<DevToolsMessage> CHROME_FRAME_RESIZED = forEvent("Page.frameResized");
    public static final Predicate<DevToolsMessage> CHROME_INSPECTOR_DETACHED = forEvent("Inspector.detached");
    public static final Predicate<DevToolsMessage> CHROME_DIALOG_OPENING = forEvent("Page.javascriptDialogOpening");
    public static final Predicate<DevToolsMessage> CHROME_DOM_CONTENT = forEvent("Page.domContentEventFired");

    public static Predicate<DevToolsMessage> forEvent(String name) {
        return m -> name.equals(m.getMethod());
    }

    public static final Predicate<DevToolsMessage> NO_WAIT = m -> true;

    public WaitState(DriverOptions options) {
        this.options = options;
        logger = options.driverLogger;
    }

    public DevToolsMessage waitAfterSend(DevToolsMessage dtm, Predicate<DevToolsMessage> condition) {
        lastReceived = null;
        lastSent = dtm;
        this.condition = condition == null ? DEFAULT : condition;
        synchronized (this) {
            logger.trace(">> wait: {}", dtm);
            try {
                wait(options.timeout);
            } catch (InterruptedException e) {
                logger.error("interrupted: {} wait: {}", e.getMessage(), dtm);
            }
        }
        if (lastReceived != null) {
            logger.trace("<< notified: {}", dtm);
        } else {
            logger.warn("<< timed out: {}", dtm);
        }
        return lastReceived;
    }

    public void receive(DevToolsMessage dtm) {
        synchronized (this) {
            if (condition.test(dtm)) {
                logger.trace("<< notify: {}", dtm);
                lastReceived = dtm;
                notify();
            } else {
                logger.trace("<< ignore: {}", dtm);
            }
        }
    }

}

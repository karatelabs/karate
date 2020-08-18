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
public class DevToolsWait {

    private final DriverOptions options;
    private final DevToolsDriver driver;

    private DevToolsMessage lastSent;
    private Predicate<DevToolsMessage> condition;
    private DevToolsMessage lastReceived;

    private final Predicate<DevToolsMessage> DEFAULT = m -> lastSent.getId().equals(m.getId());
    public static final Predicate<DevToolsMessage> FRAME_RESIZED = forEvent("Page.frameResized");
    public static final Predicate<DevToolsMessage> INSPECTOR_DETACHED = forEvent("Inspector.detached");
    public static final Predicate<DevToolsMessage> DIALOG_OPENING = forEvent("Page.javascriptDialogOpening");
    public static final Predicate<DevToolsMessage> ALL_FRAMES_LOADED = m -> {
        // page is considered ready only when the dom is ready
        // AND all child frames that STARTED loading BEFORE the dom became ready
        if (m.methodIs("Page.domContentEventFired")) {
            if (m.driver.framesStillLoading.isEmpty()) {
                m.driver.logger.trace("** dom ready, and no frames loading, wait done");
                return true;
            } else {
                m.driver.logger.trace("** dom ready, but frames still loading, will wait: {}", m.driver.framesStillLoading);
                return false;
            }
        }
        if (m.methodIs("Page.frameStoppedLoading")) {
            if (!m.driver.domContentEventFired) {
                m.driver.logger.trace("** dom not ready, will wait, and frames loading: {}", m.driver.framesStillLoading);
                return false;
            }
            if (m.driver.framesStillLoading.isEmpty()) {
                m.driver.logger.trace("** dom ready, and no frames loading, wait done");
                return true;
            } else {
                m.driver.logger.trace("** dom ready, but frames still loading, will wait: {}", m.driver.framesStillLoading);
            }
        }
        return false;
    };

    public static Predicate<DevToolsMessage> forEvent(String name) {
        return m -> name.equals(m.getMethod());
    }

    public DevToolsWait(DevToolsDriver driver, DriverOptions options) {
        this.driver = driver;
        this.options = options;
        logger = options.driverLogger;
    }

    // mutable when driver logger is swapped
    private Logger logger;

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void setCondition(Predicate<DevToolsMessage> condition) {
        this.condition = condition;
    }

    public DevToolsMessage send(DevToolsMessage dtm, Predicate<DevToolsMessage> condition) {
        lastReceived = null;
        lastSent = dtm;
        this.condition = condition == null ? DEFAULT : condition;        
        long timeout = dtm.getTimeout() == null ? options.getTimeout() : dtm.getTimeout();
        synchronized (this) {
            logger.trace(">> wait: {}", dtm);
            try {
                driver.send(dtm);
                wait(timeout);
            } catch (InterruptedException e) {
                logger.error("interrupted: {} wait: {}", e.getMessage(), dtm);
            }
        }
        if (lastReceived != null) {
            logger.trace("<< notified: {}", dtm);
        } else {
            logger.error("<< timed out after milliseconds: {} - {}", timeout, dtm);
            return null;
        }
        return lastReceived;
    }

    public void receive(DevToolsMessage dtm) {
        synchronized (this) {
            if (condition.test(dtm)) {
                if (dtm.isResultError()) {
                    logger.warn("devtools error: {}", dtm);
                } else {
                    logger.trace("<< notify: {}", dtm);
                }                
                lastReceived = dtm;
                notify();
            } else {
                logger.trace("<< ignore: {}", dtm);
            }
        }
    }

}

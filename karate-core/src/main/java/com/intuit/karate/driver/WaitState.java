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

import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class WaitState {

    private static final Logger logger = LoggerFactory.getLogger(WaitState.class);
    
    private final long timeOut;

    private DevToolsMessage lastSent;
    private Predicate<DevToolsMessage> condition;
    private DevToolsMessage lastReceived;

    private final Predicate<DevToolsMessage> DEFAULT = m -> lastSent.getId().equals(m.getId()) && m.getResult() != null;
    
    public static final Predicate<DevToolsMessage> CHROME_FRAME_RESIZED = forEvent("Page.frameResized");
    
    public static final Predicate<DevToolsMessage> CHROME_INSPECTOR_DETACHED = forEvent("Inspector.detached");
    
    public static Predicate<DevToolsMessage> forEvent(String name) {
        return m -> name.equals(m.getMethod());
    }

    public static final Predicate<DevToolsMessage> CHROME_FRAME_NAVIGATED = m -> {
        if ("Page.frameNavigated".equals(m.getMethod())) {
            if (m.getFrameUrl().startsWith("http")) {
                return true;
            }
        }
        return false;
    };
    
    public static final Predicate<DevToolsMessage> NO_WAIT = m -> true;
    
    public WaitState(long timeOut) {
        this.timeOut = timeOut;
    }

    public DevToolsMessage sendAndWait(DevToolsMessage dtm, Predicate<DevToolsMessage> condition) {
        lastReceived = null;
        lastSent = dtm;
        this.condition = condition == null ? DEFAULT : condition;
        synchronized (this) {
            logger.debug(">> wait: {}", dtm);
            try {
                wait(timeOut);
            } catch (InterruptedException e) {
                logger.error("interrupted: {} wait: {}", e.getMessage(), dtm);
            }
        }
        if (lastReceived != null) {
            logger.debug("<< notified: {}", dtm);
        } else {
            logger.warn("<< timed out: {}", dtm);
        }        
        return lastReceived;
    }

    public void receive(DevToolsMessage dtm) {
        synchronized (this) {
            if (condition.test(dtm)) {
                logger.debug("<< notify: {}", dtm);
                lastReceived = dtm;
                notify();
            } else {
                logger.debug("<< ignore: {}", dtm);
            }
        }
    }

}

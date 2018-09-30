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
package com.intuit.karate.web;

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
    private boolean conditionMatched;

    private final Predicate<DevToolsMessage> DEFAULT = cm -> lastSent.getId().equals(cm.getId()) && cm.getResult() != null;

    public static final Predicate<DevToolsMessage> FRAME_NAVIGATED = cm -> {
        if ("Page.frameNavigated".equals(cm.getMethod())) {
            if (cm.getFrameUrl().startsWith("http")) {
                return true;
            }
        }
        return false;
    };
    
    public WaitState(long timeOut) {
        this.timeOut = timeOut;
    }

    public DevToolsMessage sendAndWait(DevToolsMessage cm, Predicate<DevToolsMessage> condition) {
        lastReceived = null;
        lastSent = cm;
        this.condition = condition == null ? DEFAULT : condition;
        while (lastReceived == null) {
            synchronized (this) {
                logger.debug(">> wait: {}", cm);
                try {
                    wait(timeOut);
                } catch (InterruptedException e) {
                    logger.error("interrupted: {} wait: {}", e.getMessage(), cm);
                }
            }
        }
        if (conditionMatched) {
            logger.debug("<< notified: {}", cm);
        } else {
            logger.warn("<< timed out: {}", cm);
        }        
        return lastReceived;
    }

    public void receive(DevToolsMessage cm) {
        lastReceived = cm;
        synchronized (this) {
            if (condition.test(cm)) {
                logger.debug("<< notify: {}", cm);
                conditionMatched = true;
                notify();
            } else {
                conditionMatched = false;
                logger.debug("<< ignore: {}", cm);
            }
        }
    }

}

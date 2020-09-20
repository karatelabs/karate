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
package com.intuit.karate.driver.playwright;

import com.intuit.karate.Logger;
import com.intuit.karate.driver.DriverOptions;
import java.util.function.Predicate;

/**
 *
 * @author pthomas3
 */
public class PlaywrightWait {

    private final DriverOptions options;
    private final PlaywrightDriver driver;

    private PlaywrightMessage lastSent;
    private Predicate<PlaywrightMessage> condition;
    private PlaywrightMessage lastReceived;

    private final Predicate<PlaywrightMessage> DEFAULT = m -> lastSent.getId().equals(m.getId());
    
    public static final Predicate<PlaywrightMessage> DOM_CONTENT_LOADED = m -> m.methodIs("domcontentloaded");

    public PlaywrightWait(PlaywrightDriver driver, DriverOptions options) {
        this.driver = driver;
        this.options = options;
        logger = options.driverLogger;        
    }

    // mutable when driver logger is swapped
    private Logger logger;

    public void setLogger(Logger logger) {
        this.logger = logger;
    }
    
    public PlaywrightMessage send(PlaywrightMessage pwm, Predicate<PlaywrightMessage> condition) {
        lastReceived = null;
        lastSent = pwm;
        this.condition = condition == null ? DEFAULT : condition;        
        long timeout = pwm.getTimeout() == null ? options.getTimeout() : pwm.getTimeout();
        synchronized (this) {
            logger.trace(">> wait: {}", pwm);
            try {
                driver.send(pwm);
                wait(timeout);
            } catch (InterruptedException e) {
                logger.error("interrupted: {} wait: {}", e.getMessage(), pwm);
            }
        }
        if (lastReceived != null) {
            logger.trace("<< notified: {}", pwm);
        } else {
            logger.error("<< timed out after milliseconds: {} - {}", timeout, pwm);
            return null;
        }
        return lastReceived;
    }

    public void receive(PlaywrightMessage pwm) {
        if (condition == null) {
            return;
        }
        synchronized (this) {
            if (condition.test(pwm)) {   
                if (pwm.isError()) {
                    logger.warn("playwright error: {}", pwm);
                } else {
                    logger.trace("<< notify: {}", pwm);
                }
                lastReceived = pwm;
                notify();
            } else {
                logger.trace("<< ignore: {}", pwm);
            }
        }
    }    

}

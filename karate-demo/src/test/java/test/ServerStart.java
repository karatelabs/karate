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
package test;

import com.intuit.karate.demo.Application;
import com.intuit.karate.demo.config.ServerStartedInitializingBean;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author pthomas3
 */
public class ServerStart {

    private static final Logger logger = LoggerFactory.getLogger(ServerStart.class);

    private ConfigurableApplicationContext context;
    private MonitorThread monitor;
    private int port = 0;

    public void start(String[] args, boolean wait) throws Exception {
        if (wait) {
            try {
                logger.info("attempting to stop server if it is already running");
                new ServerStop().stopServer();
            } catch (Exception e) {
                logger.info("failed to stop server (was probably not up): {}", e.getMessage());
            }
        }
        context = Application.run(args);
        ServerStartedInitializingBean ss = context.getBean(ServerStartedInitializingBean.class);
        port = ss.getLocalPort();
        logger.info("started server on port: {}", port);
        if (wait) {
            int stopPort = port + 1;
            logger.info("will use stop port as {}", stopPort);
            monitor = new MonitorThread(stopPort, () -> context.close());
            monitor.start();
            monitor.join();
        }
    }

    public int getPort() {
        return port;
    }

    @Test
    public void startServer() throws Exception {
        start(new String[]{}, true);
    }

}

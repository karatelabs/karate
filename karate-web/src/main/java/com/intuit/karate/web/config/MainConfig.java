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
package com.intuit.karate.web.config;

import com.intuit.karate.web.service.KarateService;
import com.intuit.karate.web.wicket.WicketApplication;
import org.apache.wicket.protocol.ws.javax.WicketServerEndpointConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 *
 * @author pthomas3
 */
@EnableAutoConfiguration
@EnableWebSocket
@Configuration
@Import({WicketServletFilterConfig.class})
public class MainConfig implements WebSocketConfigurer {
    
    private static final Logger logger = LoggerFactory.getLogger(MainConfig.class);
    
    @Bean
    KarateService karateService() {
        return new KarateService();
    }

    @Bean
    WicketApplication wicketApplication() {
        return new WicketApplication();
    }

    @Bean
    ServerStartedInitializingBean serverStartedInitializingBean() {
        return new ServerStartedInitializingBean();
    }    
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        logger.debug("register websocket handlers: {}", registry);
    }

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    @Bean
    public WicketServerEndpointConfig myWicketServerEndpointConfig() {
        return new WicketServerEndpointConfig();
    }    
    
}

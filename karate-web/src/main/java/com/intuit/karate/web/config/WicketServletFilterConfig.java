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

import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import org.apache.wicket.protocol.ws.javax.JavaxWebSocketFilter;
import org.apache.wicket.spring.SpringWebApplicationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author pthomas3
 */
@Configuration
public class WicketServletFilterConfig implements ServletContextInitializer {
    
	private static final Logger logger = LoggerFactory.getLogger(WicketServletFilterConfig.class);
		
	@Value("${wicket.configuration}")
	private String wicketConfiguration;	

	@Override
	public void onStartup(ServletContext sc) throws ServletException {
		logger.info("wicket servlet initializer startup, configuration: {}", wicketConfiguration);
		FilterRegistration filter = sc.addFilter("wicket-filter", JavaxWebSocketFilter.class);
		filter.setInitParameter(JavaxWebSocketFilter.APP_FACT_PARAM, SpringWebApplicationFactory.class.getName());
		filter.setInitParameter("applicationBean", "wicketApplication");
		filter.setInitParameter(JavaxWebSocketFilter.FILTER_MAPPING_PARAM, "/*");
		filter.setInitParameter("configuration", wicketConfiguration);
		filter.addMappingForUrlPatterns(null, false, "/*");
	}    
    
}

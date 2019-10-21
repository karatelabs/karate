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
package demo;

import com.intuit.karate.demo.controller.CatsController;
import com.intuit.karate.demo.controller.DogsController;
import com.intuit.karate.demo.controller.EchoController;
import com.intuit.karate.demo.controller.EncodingController;
import com.intuit.karate.demo.controller.GraphqlController;
import com.intuit.karate.demo.controller.GreetingController;
import com.intuit.karate.demo.controller.HeadersController;
import com.intuit.karate.demo.controller.RedirectController;
import com.intuit.karate.demo.controller.SearchController;
import com.intuit.karate.demo.controller.SignInController;
import com.intuit.karate.demo.controller.SoapController;
import com.intuit.karate.demo.controller.UploadController;
import com.intuit.karate.demo.exception.GlobalExceptionHandler;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 *
 * @author pthomas3
 */
@Configuration
@EnableAutoConfiguration
@PropertySource("classpath:application.properties")
public class MockDemoConfig {
    
    @Bean
    public CatsController catsController() {
        return new CatsController();
    }
    
    @Bean
    public DogsController dogsController() {
        return new DogsController();
    }    
    
    @Bean
    public GreetingController greetingController() {
        return new GreetingController();
    }
    
    @Bean
    public HeadersController headersController() {
        return new HeadersController();
    }
    
    @Bean
    public SearchController searchController() {
        return new SearchController();
    }
    
    @Bean
    public SignInController signInController() {
        return new SignInController();
    }
    
    @Bean
    public UploadController uploadController() throws Exception {
        return new UploadController();
    }
    
    @Bean
    public EncodingController encodingController() {
        return new EncodingController();
    }
    
    @Bean
    public RedirectController redirectController() {
        return new RedirectController();
    } 
    
    @Bean
    public GraphqlController graphqlController() {
        return new GraphqlController();
    } 
    
    @Bean
    public SoapController soapController() {
        return new SoapController();
    }
    
    @Bean
    public EchoController echoController() {
        return new EchoController();
    }    

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
    
}

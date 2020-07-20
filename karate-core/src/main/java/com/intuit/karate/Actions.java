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
package com.intuit.karate;

import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public interface Actions {

    void assertTrue(String expression);

    void call(String line);

    void callonce(String line);
    
    void csv(String name, String expression);

    void json(String name, String expression);

    void string(String name, String expression);

    void xml(String name, String expression);

    void xmlstring(String name, String expression);
    
    void bytes(String name, String expression);

    void configure(String key, String exp);

    void configureDocstring(String key, String exp);

    void cookie(String name, String value);

    void cookies(String expr);

    void copy(String name, String expression);

    void def(String name, String expression);

    void defDocstring(String name, String expression);

    void eval(String exp);

    void evalDocstring(String exp);
    
    void eval(String name, String dotOrParen, String expression);
    
    void evalIf(String expression);
    
    void formField(String name, List<String> values);

    void formFields(String expr);

    void header(String name, List<String> values);

    void headers(String expr);

    void match(String expression, String op1, String op2, String rhs);        

    void method(String method);
    
    void retry(String until);

    void multipartEntity(String value);

    void multipartFiles(String expr);

    void multipartField(String name, String value);

    void multipartFields(String expr);

    void multipartFile(String name, String value);

    void param(String name, List<String> values);

    void params(String expr);

    void path(List<String> paths);

    void print(List<String> exps);

    void remove(String name, String path);

    void replace(String name, List<Map<String, String>> table);

    void replace(String name, String token, String value);

    void request(String body);

    void requestDocstring(String body);

    void set(String name, String path, String value);

    void setDocstring(String name, String path, String value);

    void set(String name, String path, List<Map<String, String>> table);

    void soapAction(String action);

    void status(int status);

    void table(String name, List<Map<String, String>> table);

    void text(String name, String expression);

    void url(String expression);
    
    void yaml(String name, String expression);        
    
    //==========================================================================
    //
    void driver(String expression);
    
    void robot(String expression);

}

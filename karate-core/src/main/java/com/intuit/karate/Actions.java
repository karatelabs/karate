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

    public static final String ASSERT = "^assert (.+)";

    @Action(ASSERT)
    void assertTrue(String expression);

    public static final String CALL = "^call ([^\\s]+)( .*)?";

    @Action(CALL)
    void call(String name, String arg);

    public static final String CALLONCE = "^callonce ([^\\s]+)( .*)?";

    @Action(CALLONCE)
    void callonce(String name, String arg);

    public static final String JSON = "^json (.+) = (.+)";

    @Action(JSON)
    void json(String name, String expression);

    public static final String STRING = "^string (.+) = (.+)";

    @Action(STRING)
    void string(String name, String expression);

    public static final String XML = "^xml (.+) = (.+)";
    
    @Action(XML)
    void xml(String name, String expression);

    public static final String XMLSTRING = "^xmlstring (.+) = (.+)";
    
    @Action(XMLSTRING)
    void xmlstring(String name, String expression);

    public static final String CONFIGURE = "^configure ([^\\s]+) = (.+)";
    
    @Action(CONFIGURE)
    void configure(String key, String exp);

    public static final String CONFIGURE_DOCSTRING = "^configure ([^\\s]+) =$";
    
    @Action(CONFIGURE_DOCSTRING)
    void configureDocstring(String key, String exp);

    public static final String COOKIE = "^cookie ([^\\s]+) = (.+)";
    
    @Action(COOKIE)
    void cookie(String name, String value);

    public static final String COOKIES = "^cookies (.+)";
    
    @Action(COOKIES)
    void cookies(String expr);

    public static final String COPY = "^copy (.+) = (.+)";
    
    @Action(COPY)
    void copy(String name, String expression);

    public static final String DEF = "^def (\\w+) = (.+)";
    
    @Action(DEF)
    void def(String name, String expression);

    public static final String DEF_DOCSTRING = "^def (.+) =$";
    
    @Action(DEF_DOCSTRING)
    void defDocstring(String name, String expression);

    public static final String EVAL = "^eval (.+)";
    
    @Action(EVAL)
    void eval(String exp);

    public static final String EVAL_DOCSTRING = "^eval$";
    
    @Action(EVAL_DOCSTRING)
    void evalDocstring(String exp);
    
    public static final String FORM_FIELD = "^form field ([^\\s]+) = (.+)";

    @Action(FORM_FIELD)
    void formField(String name, List<String> values);

    public static final String FORM_FIELDS = "^form fields (.+)";
    
    @Action(FORM_FIELDS)
    void formFields(String expr);

    public static final String HEADER = "^header ([^\\s]+) = (.+)";
    
    @Action(HEADER)
    void header(String name, List<String> values);

    public static final String HEADERS = "^headers (.+)";
    
    @Action(HEADERS)
    void headers(String expr);

    public static final String MATCH_CONTAINS = "^match (each )?([^\\s]+)( [^\\s]+)? (!)?contains( only| any)?(.+)";
    
    @Action(MATCH_CONTAINS)
    void matchContains(String each, String name, String path, String not, String only, String expected);

    public static final String MATCH_CONTAINS_DOCSTRING = "^match (each )?([^\\s]+)( [^\\s]+)? (!)?contains( only| any)?$";
    
    @Action(MATCH_CONTAINS_DOCSTRING)
    void matchContainsDocstring(String each, String name, String path, String not, String only, String expected);

    public static final String MATCH_EQUALS = "^match (each )?([^\\s]+)( [^\\s]+)? (==?|!=) (.+)";
    
    @Action(MATCH_EQUALS)
    void matchEquals(String each, String name, String path, String eqSymbol, String expected);

    public static final String MATCH_EQUALS_DOCSTRING = "^match (each )?([^\\s]+)( [^\\s]+)? (==?|!=)$";
    
    @Action(MATCH_EQUALS_DOCSTRING)
    void matchEqualsDocstring(String each, String name, String path, String eqSymbol, String expected);

    public static final String METHOD = "^method (\\w+)";
    
    @Action(METHOD)
    void method(String method);

    public static final String MULTIPART_ENTITY = "^multipart entity (.+)";
    
    @Action(MULTIPART_ENTITY)
    void multipartEntity(String value);

    public static final String MULTIPART_FILES = "^multipart files (.+)";
    
    @Action(MULTIPART_FILES)
    void multipartFiles(String expr);

    public static final String MULTIPART_FIELD = "^multipart field (.+) = (.+)";
    
    @Action(MULTIPART_FIELD)
    void multipartField(String name, String value);

    public static final String MULTIPART_FIELDS = "^multipart fields (.+)";
    
    @Action(MULTIPART_FIELDS)
    void multipartFields(String expr);

    public static final String MULTIPART_FILE = "^multipart file (.+) = (.+)";
    
    @Action(MULTIPART_FILE)
    void multipartFile(String name, String value);

    public static final String PARAM = "^param ([^\\s]+) = (.+)";
    
    @Action(PARAM)
    void param(String name, List<String> values);

    public static final String PARAMS = "^params (.+)";
    
    @Action(PARAMS)
    void params(String expr);

    public static final String PATH = "^path (.+)";
    
    @Action(PATH)
    void path(List<String> paths);

    public static final String PRINT = "^print (.+)";
    
    @Action(PRINT)
    void print(List<String> exps);

    public static final String REMOVE = "^remove ([^\\s]+)( .+)?";
    
    @Action(REMOVE)
    void remove(String name, String path);

    public static final String REPLACE_TABLE = "^replace (\\w+)$";
    
    @Action(REPLACE_TABLE)
    void replace(String name, List<Map<String, String>> table);

    public static final String REPLACE = "^replace (\\w+).([^\\s]+) = (.+)";
    
    @Action(REPLACE)
    void replace(String name, String token, String value);

    public static final String REQUEST = "^request (.+)";
    
    @Action(REQUEST)
    void request(String body);

    public static final String REQUEST_DOCSTRING = "^request$";
    
    @Action(REQUEST_DOCSTRING)
    void requestDocstring(String body);

    public static final String SET = "^set ([^\\s]+)( .+)? = (.+)";
    
    @Action(SET)
    void set(String name, String path, String value);

    public static final String SET_DOCSTRING = "^set ([^\\s]+)( .+)? =$";
    
    @Action(SET_DOCSTRING)
    void setDocstring(String name, String path, String value);

    public static final String SET_TABLE = "^set ([^\\s]+)( [^=]+)?$";
    
    @Action(SET_TABLE)
    void set(String name, String path, List<Map<String, String>> table);

    public static final String SOAP_ACTION = "^soap action( .+)?";
    
    @Action(SOAP_ACTION)
    void soapAction(String action);

    public static final String STATUS = "^status (\\d+)";
    
    @Action(STATUS)
    void status(int status);

    public static final String TABLE = "^table (.+)";
    
    @Action(TABLE)
    void table(String name, List<Map<String, String>> table);

    public static final String TEXT = "^text (.+) =$";
    
    @Action(TEXT)
    void text(String name, String expression);

    public static final String URL = "^url (.+)";
    
    @Action(URL)
    void url(String expression);

    public static final String YAML = "^yaml (.+) =$";
    
    @Action(YAML)
    void yaml(String name, String expression);

}

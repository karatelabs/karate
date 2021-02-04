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
package com.intuit.karate;

/**
 *
 * @author pthomas3
 */
public class Constants {

    private Constants() {
        // only static methods
    }

    public static final String KARATE_ENV = "karate.env";
    public static final String KARATE_CONFIG_DIR = "karate.config.dir";
    public static final String KARATE_CONFIG_INCL_RESULT_METHOD = "karate.config.result.result-method.include";
    public static final String KARATE_OUTPUT_DIR = "karate.output.dir";
    public static final String KARATE_OPTIONS = "karate.options";
    public static final String KARATE_REPORTS = "karate-reports";
    public static final String KARATE_JSON_SUFFIX = ".karate-json.txt";
    
    public static final byte[] ZERO_BYTES = new byte[0];

}

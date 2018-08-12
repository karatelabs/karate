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
package com.intuit.karate.core;

import java.util.BitSet;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ParserErrorListener implements ANTLRErrorListener {

    private static final Logger logger = LoggerFactory.getLogger(FeatureParser.class);

    private String message;
    private int line = -1;
    private int position = -1;
    private Object offendingSymbol;

    public boolean isFail() {
        return message != null;
    }

    public String getMessage() {
        return message;
    }

    public int getLine() {
        return line;
    }

    public int getPosition() {
        return position;
    }

    public Object offendingSymbol() {
        return offendingSymbol;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int position, String message, RecognitionException e) {
        logger.error("syntax error: {}", message);
        this.message = message;
        this.line = line;
        this.position = position;
        this.offendingSymbol = offendingSymbol;
    }

    @Override
    public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact, BitSet ambigAlts, ATNConfigSet configs) {
//        if (logger.isTraceEnabled()) {
//            logger.trace("reportAmbiguity: {} {} {} {} {} {} {}", recognizer, dfa, startIndex, stopIndex, exact, ambigAlts, configs);
//        }
    }

    @Override
    public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) {
//        if (logger.isTraceEnabled()) {
//            logger.trace("reportAttemptingFullContext: {} {} {} {} {} {}", recognizer, dfa, startIndex, stopIndex, conflictingAlts, configs);
//        }
    }

    @Override
    public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) {
//        if (logger.isTraceEnabled()) {
//            logger.trace("reportContextSensitivity: {} {} {} {} {} {}", recognizer, dfa, startIndex, stopIndex, prediction, configs);
//        }
    }

}

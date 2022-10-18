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
lexer grammar KarateLexer;

FEATURE_COMMENT: WSLF* '#' CHAR* NEWLINE -> channel(HIDDEN) ;
FEATURE_TAGS: WSLF* '@' CHAR+ NEWLINE ;
FEATURE: WSLF* 'Feature:' WS* -> pushMode(MAIN) ; // we never popMode !

fragment WSLF: [\r\n \t] ;     // White Space or Line Feed
fragment BOL: [\r\n]+ [ \t]* ; // Beginning Of Line
fragment WS: [ \t] ;           // White Space

mode MAIN; // ==================================================================

BACKGROUND: NEWLINE 'Background:' WS* ;
SCENARIO: NEWLINE 'Scenario:' WS* ;
SCENARIO_OUTLINE: NEWLINE 'Scenario Outline:' WS* ;
EXAMPLES: NEWLINE 'Examples:' WS* ;

STAR: NEWLINE '*' WS+ ;
GIVEN: NEWLINE 'Given' WS+ ;
WHEN: NEWLINE 'When' WS+ ;
THEN: NEWLINE 'Then' WS+ ;
AND: NEWLINE 'And' WS+ ;
BUT: NEWLINE 'But' WS+ ;

COMMENT: NEWLINE '#' CHAR* -> channel(HIDDEN) ;
TAGS: NEWLINE '@' CHAR+ ;
TABLE_ROW: NEWLINE '|' CHAR+ ;
DOC_STRING: NEWLINE '"""' .*? '"""' CHAR* ;

CHAR: ~[\r\n] ;
NEWLINE: BOL+ ;

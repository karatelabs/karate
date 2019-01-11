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

FEATURE_COMMENT: WSLF* '#' ~[\r\n]* BOL+ -> channel(HIDDEN) ;
FEATURE_TAGS: WSLF* '@' ~[\r\n]+ BOL+ ;
FEATURE: WSLF* 'Feature:' WS* -> pushMode(MAIN) ; // we never popMode !

fragment WSLF: [\r\n \t] ;     // White Space or Line Feed
fragment BOL: [\r\n]+ [ \t]* ; // Beginning Of Line
fragment WS: [ \t] ;           // White Space

mode MAIN; // ==================================================================

BACKGROUND: BOL+ 'Background:' WS* ;
SCENARIO: BOL+ 'Scenario:' WS* ;
SCENARIO_OUTLINE: BOL+ 'Scenario Outline:' WS* ;
EXAMPLES: BOL+ 'Examples:' WS* ;

STAR: BOL+ '*' WS+ ;
GIVEN: BOL+ 'Given' WS+ ;
WHEN: BOL+ 'When' WS+ ;
THEN: BOL+ 'Then' WS+ ;
AND: BOL+ 'And' WS+ ;
BUT: BOL+ 'But' WS+ ;

COMMENT: BOL+ '#' ~[\r\n]* -> channel(HIDDEN) ;
TAGS: BOL+ '@' ~[\r\n]+ ;
TABLE_ROW: BOL+ '|' ~[\r\n]+ ;
DOC_STRING: BOL+ '"""' .*? '"""' ~[\r\n]* ;

CHAR: ~[\r\n] ;
NEWLINE: BOL+ ;

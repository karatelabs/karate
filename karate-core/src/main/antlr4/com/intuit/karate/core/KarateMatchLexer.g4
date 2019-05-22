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
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR tANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
lexer grammar KarateMatchLexer;

EQUALS
    : '=='                  -> pushMode(EXPECTED)
    ;

EQUALS_NOT
    : '!='                  -> pushMode(EXPECTED)
    ;

CONTAINS
    : 'contains'            -> pushMode(EXPECTED)
    ;

CONTAINS_NOT
    : '!contains'           -> pushMode(EXPECTED)
    ;

CONTAINS_ANY
    : 'contains' WS+ 'any'  -> pushMode(EXPECTED)
    ;

CONTAINS_ONLY
    : 'contains' WS+ 'only' -> pushMode(EXPECTED)
    ;

EACH
    : 'each'
    ;

LPAREN
    : '('                   -> pushMode(METHOD_PARAMETER)
    ;

LBRACK
    : '['                   -> pushMode(ARRAY_ADDRESS)
    ;

DOT
    : '.'
    ;

IDENTIFIER
    : Letter LetterOrDigit*
    ;

WS
    : [ \t\r?\n] -> skip
    ;

ANY
    : .
    ;

fragment LetterOrDigit
    : Letter
    | [0-9]
    ;

fragment Letter
    : [a-zA-Z$_]
    ;

// We need a different mode for method parameters, because arbitrary code can be use to calculate a method parameter.
mode METHOD_PARAMETER;
CLOSE_METHOD
    : ')'         -> popMode
    ;

METHOD_PARAMETER_CONTENT
    : .
    ;

// We need a different mode for the array address because arbitrary code can be used to calculate an array address
mode ARRAY_ADDRESS;
CLOSE_ARRAY
    : ']'         -> popMode
    ;

ARRAY_ADDRESS_CONTENT
    : .
    ;

// Everything on the right side of the comparision operation is treated as the expected part. No need to pop the mode.
mode EXPECTED;
EXPECT
    : .
    ;

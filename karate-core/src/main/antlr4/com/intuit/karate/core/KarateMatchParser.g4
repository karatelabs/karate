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
parser grammar KarateMatchParser;

options { tokenVocab=KarateMatchLexer; }

match
    : each?  actual  match_operator expected?
    ;

each
    :EACH
    ;

actual
    : name (path)?
    ;

match_operator
    : CONTAINS_ONLY   # ContainsOnly
    | CONTAINS_ANY    # ContainsAny
    | CONTAINS        # Contains
    | CONTAINS_NOT    # ContainsNot
    | EQUALS          # Equals
    | EQUALS_NOT      # EqualsNot
    ;

expected
    : EXPECT+?
    ;

name
    : matchExpression
    ;

path
    : matchExpression
    ;

matchExpression
    : qualifiedName
    | methodCall
    | arrayAddress
    ;

methodCall
    : qualifiedName '(' METHOD_PARAMETER_CONTENT* ')'
    ;

arrayAddress
    : qualifiedName ('[' ARRAY_ADDRESS_CONTENT* ']')+ ( '.' matchExpression )?
    ;

qualifiedName
    : IDENTIFIER ('.' IDENTIFIER)*
    ;
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
parser grammar KarateParser ;

options { tokenVocab=KarateLexer; }

feature: featureHeader background? ( scenario | scenarioOutline )* NEWLINE? EOF ;

featureHeader: featureTags? FEATURE featureDescription ;

featureTags: FEATURE_TAGS+ ;

featureDescription: ~(BACKGROUND | SCENARIO | SCENARIO_OUTLINE | TAGS)* ;

background: BACKGROUND scenarioDescription step* ;

scenario: tags? SCENARIO scenarioDescription step* ;

scenarioDescription: ~(STAR | GIVEN | WHEN | THEN | AND | BUT | SCENARIO | SCENARIO_OUTLINE | TAGS)* ;

scenarioOutline: tags? SCENARIO_OUTLINE scenarioDescription step* examples+ ;

examples: tags? EXAMPLES exampleDescription table ;

exampleDescription: ~(TABLE_ROW)* ;

step: prefix line ( docString | table )? ;

prefix: STAR | GIVEN | WHEN | THEN | AND | BUT ;

line: CHAR+ ;

tags: TAGS+ ;

docString: DOC_STRING ;

table: TABLE_ROW+ ;

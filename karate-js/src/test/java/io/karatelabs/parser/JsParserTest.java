package io.karatelabs.parser;

import io.karatelabs.common.Resource;
import io.karatelabs.js.NodeUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsParserTest {

    private static void equals(String text, String json, NodeType type) {
        JsParser parser = new JsParser(Resource.text(text));
        Node node = parser.parse();
        Node child;
        if (type == null) {
            child = node;
        } else {
            Node found = node.findFirstChild(type);
            child = found.getFirst();
        }
        NodeUtils.assertEquals(text, child, json);
    }

    private static Token firstNumber(String text) {
        JsParser parser = new JsParser(Resource.text(text));
        Node root = parser.parse();
        Node num = root.findFirstChild(TokenType.NUMBER);
        return num.token;
    }

    private static void expr(String text, String json) {
        equals(text, json, NodeType.STATEMENT);
    }

    private static <T> void error(String text, Class<T> type) {
        try {
            JsParser parser = new JsParser(Resource.text(text));
            parser.parse();
            fail("expected exception of type: " + type);
        } catch (Exception e) {
            if (!e.getClass().equals(type)) {
                fail("expected exception of type: " + type + ", but was: " + e.getClass());
            }
        }
    }

    private static void program(String text, String json) {
        equals(text, json, null);
    }

    @Test
    void testDev() {

    }

    @Test
    void testFunctionDeclarationAsi() {
        // ASI: consecutive function declarations without semicolons - valid ES6
        program("function A() {} function B() {}", "{PROGRAM:[[function,$A,['(',')'],['{','}']],[function,$B,['(',')'],['{','}']],EOF]}");
    }

    @Test
    void testBlock() {
        expr("{ a }", "['{',$a,'}']");
        expr("{ 1; 2 }", "['{',[1,';'],2,'}']");
        expr("{ 1; 2; }", "['{',[1,';'],[2,';'],'}']");
        expr("{ 1; 2; };", "['{',[1,';'],[2,';'],'}']");
        expr("{ 1;; 2; }", "['{',[1,';'],';',[2,';'],'}']");
    }

    @Test
    void testProgram() {
        program(";", "{PROGRAM:[';', EOF]}");
        program(";;", "{PROGRAM:[';',';',EOF]}");
        program("1;2", "{PROGRAM:[[1,';'],2,EOF]}");
        program("1\n2", "{PROGRAM:[1,2,EOF]}");
        program("1 \n 2", "{PROGRAM:[1,2,EOF]}");
        program("1 \n 2 ", "{PROGRAM:[1,2,EOF]}");
        program("1;2;", "{PROGRAM:[[1,';'],[2,';'],EOF]}");
        program("1 ;2", "{PROGRAM:[[1,';'],2,EOF]}");
        program("1;2 ", "{PROGRAM:[[1,';'],2,EOF]}");
        program("1;2; ", "{PROGRAM:[[1,';'],[2,';'],EOF]}");
    }

    @Test
    void testAddExpr() {
        expr("1 + 2", "[1,'+',2]");
        expr("2 - 1", "[2,'-',1]");
        expr("1 + 2 + 3", "[[1,'+',2],'+',3]");
        expr("1 - 2 + 3", "[[1,'-',2],'+',3]");
    }

    @Test
    void testMulExpr() {
        expr("2 * 3", "[2,'*',3]");
        expr("6 / 2", "[6,'/',2]");
    }

    @Test
    void testAddMul() {
        expr("1 * 2 + 3", "[[1,'*',2],'+',3]");
        expr("1 + 2 * 3", "[1,'+',[2,'*',3]]");
    }

    @Test
    void testExp() {
        expr("2 ** 3", "[2, '**', 3]");
        expr("1 ** 2 ** 3", "[1,'**',[2,'**',3]]");
        expr("(2 ** 3) ** 2", "[[2,'**',3],'**',2]");
    }

    @Test
    void testPostExpr() {
        expr("a++", "[$a,'++']");
        expr("b--", "[$b,'--']");
        expr("a = b++", "[$a,'=',[$b,'++']]");
    }

    @Test
    void testPreExpr() {
        expr("++a", "['++',$a]");
        expr("--b", "['--',$b]");
        expr("a = --b", "[$a,'=',['--',$b]]");
    }

    @Test
    void testBitwise() {
        expr("1 | 2", "[1,'|',2]");
        expr("5 | 1 | 2", "[[5,'|',1],'|',2]");
    }

    @Test
    void testPrimitives() {
        expr("1", "1");
        expr("null", "null");
    }

    @Test
    void testParen() {
        expr("(1)", "1");
        expr("(1 + 3) * 2", "[[1,'+',3],'*',2]]");
        expr("2 * (1 + 3)", "[2,'*',[1,'+',3]]");
    }

    @Test
    void testStrings() {
        expr("'foo'", "foo");
        expr("\"foo\"", "foo");
        expr("\"\\\"foo\\\"\"", "\\\"foo\\\"");
        expr("'\\'foo\\''", "\\'foo\\'");
        expr("read('fooRbar')", "[$read,'(','fooRbar',')']");
    }

    @Test
    void testRegex() {
        expr("/foo/", "/foo/");
        expr("(/a\\/b/)", "/a\\/b/");
        expr("/foo/i", "/foo/i");
        expr("var re1 = /test/", "[var,$re1,'=','/test/']");
    }

    @Test
    void testPathExpr() {
        expr("a", "$a");
        expr("a.b", "[$a,'.',$b]");
        expr("a.b.c", "[[$a,'.',$b],'.',$c]");
        expr("a.b.c.d", "[[[$a,'.',$b],'.',$c],'.',$d]");
        expr("a.b[c]", "[[$a,'.',$b],'[',$c,']']");
        expr("a.b[c].d", "[[[$a,'.',$b],'[',$c,']'],'.',$d]");
        expr("a[b].c[d]", "[[[$a,'[',$b,']'],'.',$c],'[',$d,']']");
        expr("a[b].c", "[[$a,'[',$b,']'],'.',$c]");
        expr("a['b']", "[$a,'[',b,']']");
        expr("a[b]", "[$a,'[',$b,']']");
        expr("a['b']['c']", "[[$a,'[',b,']'],'[',c,']']");
        expr("a[b][c]", "[[$a,'[',$b,']'],'[',$c,']']");
    }

    @Test
    void testPathExprReservedWords() {
        expr("a.null", "[$a,'.',null]");
    }

    @Test
    void testPathMix() {
        expr("(a)", "$a");
        expr("(a).b", "[$a,'.',$b]");
        expr("a[(b)]", "[$a,'[',$b,']']");
        expr("a[b + 'c']", "[$a,'[',[$b,'+',c],']']");
    }

    @Test
    void testObject() {
        expr("{}", "['{','}']");
        expr("{ a: 1 }", "['{',[$a:,1],'}']");
        expr("{ a: 'b' }", "['{',[$a:,b],'}']");
    }

    @Test
    void testArray() {
        expr("[]", "['[',']']");
        expr("[1]", "['[',1,']']");
        expr("[1,]", "['[',1,']']");
        expr("[a]", "['[',$a,']']");
        expr("['a']", "['[',a,']']");
        expr("[1,2]", "['[',1,2,']']");
        expr("[1,2,3]", "['[',1,2,3,']']");
    }

    @Test
    void testFnExpr() {
        expr("function(){}", "[function,['(',')'],['{','}']]");
        expr("function(){ return true }", "[function,['(',')'],['{',['return',true],'}']]");
        expr("function(a){ return a }", "[function,['(',$a,')'],['{',['return',$a],'}']]");
        expr("function(a){ return { a } }", "[function,['(',$a,')'],['{',['return',['{',$a,'}']],'}']]");
        expr("function(a){ return { a, b } }", "[function,['(',$a,')'],['{',['return',['{',$a,$b,'}']],'}']]");
    }

    @Test
    void testFnExprDefaultParams() {
        expr("function(a = 1){ return a }", "[function,['(',[$a,'=',1],')'],['{',['return',$a],'}']]");
    }

    @Test
    void testFnCall() {
        expr("a.b()", "[[$a,'.',$b],'(',[],')']");
        expr("foo()", "[$foo,'(',[],')']");
        expr("foo.bar()", "[[$foo,'.','$bar'],'(',[],')']");
    }

    @Test
    void testFnExprArrow() {
        expr("() => true", "[['(',')'],'=>',true]");
        expr("() => {}", "[['(',')'],'=>',['{','}']]");
        expr("a => true", "[$a,'=>',true]");
        expr("(a) => true", "[['(',$a,')'],'=>',true]");
        expr("(a, b) => true", "[['(',[$a,','],$b,')'],'=>',true]");
        expr("a => { return true }", "[$a,'=>',['{',['return',true],'}']]");
        // arrow function with regex body - regex must be recognized after =>
        expr("s => /test/.test(s)", "[$s,'=>',[['/test/','.',$test],'(',$s,')']]");
        expr("s => /^1.*/.test(s)", "[$s,'=>',[['/^1.*/','.',$test],'(',$s,')']]");
    }

    @Test
    void testVarStatement() {
        expr("var foo", "[var,$foo]");
        expr("var foo, bar", "[var,[$foo,','$bar]]");
        expr("var foo = 1", "[var,$foo,'=',1]");
        expr("var foo, bar = 1", "[var,[$foo,','$bar],'=',1]");
        expr("var a, b = 1 + 2", "[var,[$a,','$b],'=',[1,'+',2]]");
    }

    @Test
    void testConstMissingInitializer() {
        error("const a", ParserException.class);
    }

    @Test
    void testAssignStatement() {
        expr("a = 1", "[$a,'=',1]");
        expr("a.b = 1", "[[$a,'.',$b],'=',1]");
        expr("a.b.c = 1", "[[[$a,'.',$b],'.',$c],'=',1]");
        expr("a = 1 + 2", "[$a,'=',[1,'+',2]]");
        expr("a = 2 * 3", "[$a,'=',[2,'*',3]]");
        expr("a = function(){ return true }", "[$a,'=',[function,['(',')'],['{',['return',true],'}']]]");
    }

    @Test
    void testCommaExpression() {
        expr("a, b, c", "[$a,',',$b,','$c]");
    }

    @Test
    void testAssignBitShift() {
        expr("n >>>= 0", "[$n,'>>>=',0]");
        expr("n >>= 0", "[$n,'>>=',0]");
    }

    @Test
    void testIfStatement() {
        expr("if (true) a = 1", "['if','(',true,')',[$a,'=',1]]");
        expr("if (true) a = 1; else a = 2", "['if','(',true,')',[[$a,'=',1]';'],'else',[$a,'=',2]]");
    }

    @Test
    void testForStatement() {
        expr("for(;;){}", "['for','(',';',';',')',['{','}']]");
    }

    @Test
    void testUnary() {
        expr("!foo || bar", "[['!','$foo'], '||', '$bar']");
    }

    @Test
    void testTernary() {
        expr("true ? 'foo' : bar", "[true,'?','foo',':',$bar]");
        expr("0 && 1 ? 'foo' : bar", "[[0,'&&',1],'?','foo',':',$bar]");
    }

    @Test
    void testLogicalExpr() {
        expr("a < b", "[$a,'<',$b]");
        expr("x = a >= b", "[$x,'=',[$a,'>=',$b]]");
    }

    @Test
    void testTryStmt() {
        expr("try {} catch (e) {}", "['try',['{','}'],'catch','(',$e,')',['{','}']]");
        expr("try {} finally {}", "['try',['{','}'],'finally',['{','}']]");
        expr("try {} catch (e) {} finally {}", "['try',['{','}'],'catch','(',$e,')',['{','}'],'finally',['{','}']]");
        expr("try {} catch {}", "['try',['{','}'],'catch',['{','}']]");
    }

    @Test
    void testTypeOf() {
        expr("typeof 'foo' === 'string'", "[['typeof','foo'],'===','string']");
    }

    @Test
    void testInstanceOf() {
        expr("foo instanceof Foo", "[$foo, 'instanceof', $Foo]");
    }

    @Test
    void testNewExprPrecedence() {
        // new binds to constructor call only
        expr("new Foo()", "[new,[$Foo,'(',[],')']]]");
        // .bar is OUTSIDE the new expression
        expr("new Foo().bar", "[[new,[$Foo,'(',[],')']],'.',$bar]");
        // .bar() call is OUTSIDE the new expression
        expr("new Foo().bar()", "[[[new,[$Foo,'(',[],')']],'.',$bar],'(',[],')']");
        // chained member access
        expr("new Foo().bar.baz", "[[[new,[$Foo,'(',[],')']],'.',$bar],'.',$baz]");
        // new without parens followed by member
        expr("new Foo.bar()", "[new,[[$Foo,'.',$bar],'(',[],')']]]");
    }

    @Test
    void testSyntaxError() {
        error("function", ParserException.class);
    }

    @Test
    void testTemplate() {
        expr("``", "['`','`']");
        expr("`foo`", "['`','foo','`']");
        expr("`${}`", "['`','${', '}','`']");
        expr("`${foo}`", "['`','${', '$foo', '}','`']");
        expr("`${1 + 2}`", "['`','${',[1,'+',2],'}','`']");
        expr("`[${}]`", "['`','[','${','}',']','`']");
    }

    @Test
    void testWhiteSpaceCounting() {
        Token token = firstNumber("/* */  1");
        assertEquals(0, token.line);
        assertEquals(7, token.col);
        assertEquals(7, token.pos);
        token = firstNumber("/* \n* \n*/\n 1");
        assertEquals(3, token.line);
        assertEquals(1, token.col);
        assertEquals(11, token.pos);
        token = firstNumber("// foo \n // bar \n1");
        assertEquals(2, token.line);
        assertEquals(0, token.col);
        token = firstNumber("\n  \n  1");
        assertEquals(2, token.line);
        assertEquals(2, token.col);
        assertEquals(6, token.pos);
    }

    @Test
    void testBacktickEdgeCases() {
        error("`", ParserException.class);
    }

    @Test
    void testRegexEofEdgeCases() {
        error("<x>x</", ParserException.class);
        error("<foo>foo</foo>\n", ParserException.class);
    }

    // ========== Error Recovery Tests ==========

    @Test
    void testErrorRecoveryEnabled() {
        JsParser parser = new JsParser(Resource.text("let x = 1"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertFalse(parser.hasErrors());
        assertNotNull(parser.getAst());
    }

    @Test
    void testIncompleteExpression() {
        // User is typing: let x = 1 +
        JsParser parser = new JsParser(Resource.text("let x = 1 +"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertTrue(parser.hasErrors());
        List<SyntaxError> errors = parser.getErrors();
        assertFalse(errors.isEmpty());
        // Should have partial AST
        Node varStmt = ast.findFirstChild(NodeType.VAR_STMT);
        assertNotNull(varStmt);
    }

    @Test
    void testIncompleteBlock() {
        // Unclosed block
        JsParser parser = new JsParser(Resource.text("function foo() { let x = 1"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertTrue(parser.hasErrors());
        // Should have partial AST
        Node fnExpr = ast.findFirstChild(NodeType.FN_EXPR);
        assertNotNull(fnExpr);
    }

    @Test
    void testIncompleteIfStatement() {
        // Missing closing paren
        JsParser parser = new JsParser(Resource.text("if (true { x = 1 }"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertTrue(parser.hasErrors());
        Node ifStmt = ast.findFirstChild(NodeType.IF_STMT);
        assertNotNull(ifStmt);
    }

    @Test
    void testIncompleteForLoop() {
        // Incomplete for loop
        JsParser parser = new JsParser(Resource.text("for (let i = 0; i < 10"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertTrue(parser.hasErrors());
        Node forStmt = ast.findFirstChild(NodeType.FOR_STMT);
        assertNotNull(forStmt);
    }

    @Test
    void testIncompleteFunctionCall() {
        // Unclosed function call
        JsParser parser = new JsParser(Resource.text("foo(1, 2"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertTrue(parser.hasErrors());
        // FN_CALL_EXPR modifies REF_EXPR, so we check for FN_CALL_ARGS which is inside it
        Node fnCallArgs = ast.findFirstChild(NodeType.FN_CALL_ARGS);
        assertNotNull(fnCallArgs);
    }

    @Test
    void testIncompleteArray() {
        // Unclosed array
        JsParser parser = new JsParser(Resource.text("[1, 2, 3"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertTrue(parser.hasErrors());
        Node arr = ast.findFirstChild(NodeType.LIT_ARRAY);
        assertNotNull(arr);
    }

    @Test
    void testIncompleteTemplate() {
        // Unclosed template literal
        JsParser parser = new JsParser(Resource.text("`hello ${name"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertTrue(parser.hasErrors());
        Node template = ast.findFirstChild(NodeType.LIT_TEMPLATE);
        assertNotNull(template);
    }

    @Test
    void testMultipleStatements() {
        // First statement incomplete, second complete
        JsParser parser = new JsParser(Resource.text("let x = 1 +\nlet y = 2"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertTrue(parser.hasErrors());
        // Should still parse both statements
        List<Node> varStmts = ast.findAll(NodeType.VAR_STMT);
        assertEquals(2, varStmts.size());
    }

    @Test
    void testGetAstReturnsCorrectNode() {
        JsParser parser = new JsParser(Resource.text("let x = 1"), true);
        parser.parse();
        Node ast = parser.getAst();
        assertNotNull(ast);
        assertEquals(NodeType.PROGRAM, ast.type);
    }

    @Test
    void testErrorRecoveryPreservesAstStructure() {
        // Complete code should have same structure with or without error recovery
        String code = "let x = 1 + 2";
        JsParser parser1 = new JsParser(Resource.text(code), false);
        JsParser parser2 = new JsParser(Resource.text(code), true);
        Node ast1 = parser1.parse();
        Node ast2 = parser2.parse();
        assertFalse(parser2.hasErrors());
        // Structure should be equivalent
        assertEquals(ast1.size(), ast2.size());
    }

    @Test
    void testErrorPositionTracking() {
        JsParser parser = new JsParser(Resource.text("let x = "), true);
        parser.parse();
        assertTrue(parser.hasErrors());
        SyntaxError error = parser.getErrors().get(0);
        assertNotNull(error);
        assertTrue(error.getLine() > 0);
        assertTrue(error.getColumn() > 0);
    }

    @Test
    void testIncompleteTernary() {
        // Missing colon branch
        JsParser parser = new JsParser(Resource.text("x ? y"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertTrue(parser.hasErrors());
    }

    @Test
    void testDotPropertyAccessIncomplete() {
        // Missing property after dot
        JsParser parser = new JsParser(Resource.text("foo."), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertTrue(parser.hasErrors());
    }

    @Test
    void testIncompleteSwitchStatement() {
        // Unclosed switch
        JsParser parser = new JsParser(Resource.text("switch (x) { case 1: a = 1"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertTrue(parser.hasErrors());
        Node switchStmt = ast.findFirstChild(NodeType.SWITCH_STMT);
        assertNotNull(switchStmt);
    }

    @Test
    void testIncompleteObjectProperty() {
        // Identifier without colon followed by another property
        String code = "const x = {\n    f\n    bar: 1\n}";
        JsParser parser = new JsParser(Resource.text(code), true);
        Node ast = parser.parse();
        assertTrue(parser.hasErrors());
        assertNotNull(ast);
    }

    @Test
    void testIncompleteArrayElement() {
        // Array with malformed element (identifier without comma)
        String code = "[1, a b, 3]";
        JsParser parser = new JsParser(Resource.text(code), true);
        Node ast = parser.parse();
        assertTrue(parser.hasErrors());
        assertNotNull(ast);
        Node arr = ast.findFirstChild(NodeType.LIT_ARRAY);
        assertNotNull(arr);
    }

    @Test
    void testIncompleteFunctionCallArg() {
        // Function call with malformed argument
        String code = "foo(1, a b, 3)";
        JsParser parser = new JsParser(Resource.text(code), true);
        Node ast = parser.parse();
        assertTrue(parser.hasErrors());
        assertNotNull(ast);
    }

    @Test
    void testIncompleteFunctionDeclArg() {
        // Function declaration with malformed parameter
        String code = "function foo(a, b c, d) { return 1 }";
        JsParser parser = new JsParser(Resource.text(code), true);
        Node ast = parser.parse();
        assertTrue(parser.hasErrors());
        assertNotNull(ast);
        Node fn = ast.findFirstChild(NodeType.FN_EXPR);
        assertNotNull(fn);
    }

    @Test
    void testIncompleteSwitchCase() {
        // Switch where case_block loop might get stuck - invalid token after case content
        // Using 'else' which can't start a statement in this context
        String code = "switch (x) { case 1: a; else case 2: b }";
        JsParser parser = new JsParser(Resource.text(code), true);
        Node ast = parser.parse();
        // Should recover and parse, key is it doesn't hang
        assertNotNull(ast);
        Node switchStmt = ast.findFirstChild(NodeType.SWITCH_STMT);
        assertNotNull(switchStmt);
    }

    @Test
    void testIncompleteSwitchDefault() {
        // Switch where default_block loop might get stuck
        String code = "switch (x) { default: a; else }";
        JsParser parser = new JsParser(Resource.text(code), true);
        Node ast = parser.parse();
        // Should recover and parse, key is it doesn't hang
        assertNotNull(ast);
        Node switchStmt = ast.findFirstChild(NodeType.SWITCH_STMT);
        assertNotNull(switchStmt);
    }

    @Test
    void testMultipleBareIdentifiersInObject() {
        // Multiple identifiers without punctuation (e.g., uncommented comment)
        String code = "const x = {\n    Base rates for coverages\n    bar: 1\n}";
        JsParser parser = new JsParser(Resource.text(code), true);
        Node ast = parser.parse();
        assertTrue(parser.hasErrors());
        assertNotNull(ast);
    }

    @Test
    void testBlockWithExpressionStatementNoError() {
        // Block containing expression statement should not be flagged as error
        // { console.log() } is valid JS - a block with an expression statement
        JsParser parser = new JsParser(Resource.text("{ console.log() }"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertFalse(parser.hasErrors(), "Expected no errors for valid block statement, but got: " + parser.getErrors());
        // Should have a BLOCK node, not LIT_OBJECT
        Node block = ast.findFirstChild(NodeType.BLOCK);
        assertNotNull(block, "Expected BLOCK node but didn't find one");
    }

    @Test
    void testBlockWithMultipleStatements() {
        // Block with multiple statements
        JsParser parser = new JsParser(Resource.text("{ let x = 1; console.log(x) }"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertFalse(parser.hasErrors(), "Expected no errors for valid block, but got: " + parser.getErrors());
        Node block = ast.findFirstChild(NodeType.BLOCK);
        assertNotNull(block, "Expected BLOCK node but didn't find one");
    }

    @Test
    void testObjectLiteralStillWorks() {
        // Valid object literal should still work
        JsParser parser = new JsParser(Resource.text("let x = { a: 1, b: 2 }"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertFalse(parser.hasErrors(), "Expected no errors for valid object, but got: " + parser.getErrors());
        Node obj = ast.findFirstChild(NodeType.LIT_OBJECT);
        assertNotNull(obj, "Expected LIT_OBJECT node but didn't find one");
    }

    @Test
    void testObjectWithInvalidSecondElement() {
        // Object where second element is invalid should still error
        JsParser parser = new JsParser(Resource.text("let x = { a: 1, b c }"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertTrue(parser.hasErrors(), "Expected error for invalid object element");
    }

    @Test
    void testParenthesizedExpressionNoError() {
        // Parenthesized expression should not be flagged as error
        // (places + 5) is valid JS - not arrow function params
        JsParser parser = new JsParser(Resource.text("(places + 5)"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertFalse(parser.hasErrors(), "Expected no errors for valid paren expression, but got: " + parser.getErrors());
        Node paren = ast.findFirstChild(NodeType.PAREN_EXPR);
        assertNotNull(paren, "Expected PAREN_EXPR node but didn't find one");
    }

    @Test
    void testArrowFunctionStillWorks() {
        // Valid arrow function should still work
        JsParser parser = new JsParser(Resource.text("(a, b) => a + b"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertFalse(parser.hasErrors(), "Expected no errors for valid arrow fn, but got: " + parser.getErrors());
        Node arrow = ast.findFirstChild(NodeType.FN_ARROW_EXPR);
        assertNotNull(arrow, "Expected FN_ARROW_EXPR node but didn't find one");
    }

    @Test
    void testArrowFunctionWithInvalidSecondParam() {
        // Arrow function where second param is invalid should still error
        JsParser parser = new JsParser(Resource.text("(a, b c) => a + b"), true);
        Node ast = parser.parse();
        assertNotNull(ast);
        assertTrue(parser.hasErrors(), "Expected error for invalid function parameter");
    }

}

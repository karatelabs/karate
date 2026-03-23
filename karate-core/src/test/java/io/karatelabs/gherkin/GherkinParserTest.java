package io.karatelabs.gherkin;

import io.karatelabs.common.Resource;
import io.karatelabs.parser.Node;
import io.karatelabs.parser.NodeType;
import io.karatelabs.parser.SyntaxError;
import io.karatelabs.parser.Token;
import io.karatelabs.parser.TokenType;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GherkinParserTest {

    static final Logger logger = LoggerFactory.getLogger(GherkinParserTest.class);

    Feature feature;
    Scenario scenario;
    ScenarioOutline outline;

    private void feature(String text) {
        Resource resource = Resource.text(text);
        GherkinParser parser = new GherkinParser(resource);
        feature = parser.parse();
        scenario = null;
        if (!feature.getSections().isEmpty()) {
            FeatureSection section = feature.getSections().getFirst();
            scenario = section.getScenario();
            outline = section.getScenarioOutline();
        }
    }

    @Test
    void testFeatureBasics() {
        feature("""
                Feature:
                """);
        assertNull(feature.getName());

        feature("""
                Feature: foo
                """);
        assertEquals("foo", feature.getName());

        feature("""
                Feature: foo
                    bar
                """);
        assertEquals("foo", feature.getName());
        assertEquals("bar", feature.getDescription());
        assertTrue(feature.getTags().isEmpty());

        feature("""
                @tag1 @tag2
                Feature: foo
                """);
        List<Tag> tags = feature.getTags();
        assertEquals(2, tags.size());
        assertEquals("tag1", tags.get(0).getName());
        assertEquals("tag2", tags.get(1).getName());

    }

    @Test
    void testScenarioBasics() {
        feature("""
                Feature:
                Scenario: foo
                  bar
                  * print 'hello world'
                """);
        assertEquals("foo", scenario.getName());
        assertEquals("bar", scenario.getDescription());
        assertEquals(1, scenario.getSteps().size());
        Step step = scenario.getSteps().getFirst();
        assertEquals("*", step.getPrefix());
        assertEquals("print", step.getKeyword());
        assertEquals("'hello world'", step.getText());
    }

    @Test
    void testSimpleHttp() {
        feature("""
                Feature:
                Scenario:
                  * url 'http://httpbin.org/get'
                  * method get
                """);
        Step step1 = scenario.getSteps().getFirst();
        assertEquals("url", step1.getKeyword());
        assertEquals("'http://httpbin.org/get'", step1.getText());
        Step step2 = scenario.getSteps().get(1);
        assertEquals("method", step2.getKeyword());
        assertEquals("get", step2.getText());
    }

    // ========== AST Structure Tests ==========

    private GherkinParser parser;

    private void parseWithAst(String text) {
        parseWithAst(text, false);
    }

    private void parseWithAst(String text, boolean errorRecovery) {
        Resource resource = Resource.text(text);
        parser = new GherkinParser(resource, errorRecovery);
        feature = parser.parse();
        scenario = null;
        outline = null;
        if (!feature.getSections().isEmpty()) {
            FeatureSection section = feature.getSections().getFirst();
            scenario = section.getScenario();
            outline = section.getScenarioOutline();
        }
    }

    @Test
    void testAstStructure() {
        parseWithAst("""
                @tag1
                Feature: name
                  description
                Scenario: test
                  * def x = 1
                """);

        Node ast = parser.getAst();
        assertNotNull(ast);
        assertEquals(NodeType.G_FEATURE, ast.type);

        // Verify tags node exists
        Node tags = ast.findFirstChild(NodeType.G_TAGS);
        assertNotNull(tags);

        // Verify scenario node exists
        Node scenarioNode = ast.findFirstChild(NodeType.G_SCENARIO);
        assertNotNull(scenarioNode);

        // Verify step node exists
        Node step = ast.findFirstChild(NodeType.G_STEP);
        assertNotNull(step);
    }

    @Test
    void testAstAvailable() {
        parseWithAst("""
                Feature: test
                Scenario: first
                  * print 'hello'
                """);

        Node ast = parser.getAst();
        assertNotNull(ast);
        assertEquals(NodeType.G_FEATURE, ast.type);
    }

    // ========== Error Recovery Tests ==========

    @Test
    void testMissingFeatureKeyword() {
        parseWithAst("""
                @tag
                Scenario: orphan
                  * print 'hello'
                """, true);
        // Should recover and parse scenario
        assertNotNull(scenario);
        assertEquals("orphan", scenario.getName());
        assertEquals(1, scenario.getSteps().size());

        // Should have recorded an error
        assertTrue(parser.hasErrors());
        List<SyntaxError> errors = parser.getErrors();
        assertFalse(errors.isEmpty());
    }

    @Test
    void testIncompleteStep() {
        parseWithAst("""
                Feature: test
                Scenario: incomplete
                  * def
                """, true);
        // Should parse without throwing
        assertNotNull(scenario);
        assertEquals(1, scenario.getSteps().size());
        Step step = scenario.getSteps().getFirst();
        assertEquals("def", step.getKeyword());
        // Missing RHS - text should be null
        assertNull(step.getText());
    }

    @Test
    void testMultipleScenarios() {
        parseWithAst("""
                Feature: multi
                Scenario: first
                  * print 'one'
                Scenario: second
                  * print 'two'
                """);
        assertEquals(2, feature.getSections().size());
        assertEquals("first", feature.getSections().get(0).getScenario().getName());
        assertEquals("second", feature.getSections().get(1).getScenario().getName());
    }

    @Test
    void testScenarioWithTags() {
        parseWithAst("""
                Feature: test
                @smoke @regression
                Scenario: tagged
                  * print 'hello'
                """);
        assertNotNull(scenario);
        assertNotNull(scenario.getTags());
        assertEquals(2, scenario.getTags().size());
        assertEquals("smoke", scenario.getTags().get(0).getName());
        assertEquals("regression", scenario.getTags().get(1).getName());
    }

    @Test
    void testBackground() {
        parseWithAst("""
                Feature: with background
                Background:
                  * def base = 'http://localhost'
                Scenario: test
                  * print base
                """);
        assertNotNull(feature.getBackground());
        assertEquals(1, feature.getBackground().getSteps().size());
        Step bgStep = feature.getBackground().getSteps().getFirst();
        assertEquals("def", bgStep.getKeyword());
    }

    @Test
    void testTable() {
        parseWithAst("""
                Feature: test
                Scenario: with table
                  * def data =
                    | name | age |
                    | John | 30  |
                    | Jane | 25  |
                """);
        assertNotNull(scenario);
        assertEquals(1, scenario.getSteps().size());
        Step step = scenario.getSteps().getFirst();
        assertNotNull(step.getTable());
        assertEquals(3, step.getTable().getRows().size()); // header + 2 data rows
    }

    @Test
    void testScenarioOutlineWithExamples() {
        parseWithAst("""
                Feature: outline test
                Scenario Outline: parameterized
                  * print '<name>'
                Examples:
                  | name  |
                  | Alice |
                  | Bob   |
                """);
        assertNotNull(outline);
        assertEquals("parameterized", outline.getName());
        assertEquals(1, outline.getSteps().size());
        assertEquals(1, outline.getExamplesTables().size());
        assertNotNull(outline.getExamplesTables().get(0).getTable());
    }

    @Test
    void testNoErrors() {
        parseWithAst("""
                Feature: valid
                Scenario: test
                  * def x = 1
                  * print x
                """);
        assertFalse(parser.hasErrors());
        assertTrue(parser.getErrors().isEmpty());
    }

    // ========== MatchExpression Parsing Tests ==========

    @Test
    void testParseMatchExpressionEquals() {
        MatchExpression me = GherkinParser.parseMatchExpression("foo == bar");
        assertFalse(me.isEach());
        assertEquals("foo", me.getActualExpr());
        assertEquals("==", me.getOperator());
        assertEquals("bar", me.getExpectedExpr());
        assertEquals("EQUALS", me.getMatchTypeName());
    }

    @Test
    void testParseMatchExpressionContains() {
        MatchExpression me = GherkinParser.parseMatchExpression("foo contains { a: '#number' }");
        assertFalse(me.isEach());
        assertEquals("foo", me.getActualExpr());
        assertEquals("contains", me.getOperator());
        assertEquals("{ a: '#number' }", me.getExpectedExpr());
        assertEquals("CONTAINS", me.getMatchTypeName());
    }

    @Test
    void testParseMatchExpressionEach() {
        MatchExpression me = GherkinParser.parseMatchExpression("each foo == '#number'");
        assertTrue(me.isEach());
        assertEquals("foo", me.getActualExpr());
        assertEquals("==", me.getOperator());
        assertEquals("'#number'", me.getExpectedExpr());
        assertEquals("EACH_EQUALS", me.getMatchTypeName());
    }

    @Test
    void testParseMatchExpressionContainsDeep() {
        MatchExpression me = GherkinParser.parseMatchExpression("foo contains deep { a: 1 }");
        assertFalse(me.isEach());
        assertEquals("foo", me.getActualExpr());
        assertEquals("contains deep", me.getOperator());
        assertEquals("{ a: 1 }", me.getExpectedExpr());
        assertEquals("CONTAINS_DEEP", me.getMatchTypeName());
    }

    @Test
    void testParseMatchExpressionContainsDeepWithNewline() {
        // This simulates "* match message contains deep" followed by content on next line
        // The operator should be "contains deep", not just "contains" with "deep" as expected
        // Note: Using docstring syntax to match actual Gherkin usage
        MatchExpression me = GherkinParser.parseMatchExpression("message contains deep\n\"\"\"\n{ order_id: 5 }\n\"\"\"");
        assertFalse(me.isEach());
        assertEquals("message", me.getActualExpr());
        assertEquals("contains deep", me.getOperator());
        assertTrue(me.getExpectedExpr().contains("order_id"));
        assertEquals("CONTAINS_DEEP", me.getMatchTypeName());
    }

    @Test
    void testParseMatchExpressionContainsDeepWithIndentedDocstring() {
        // Match actual failing test: "* match message contains deep" with indented docstring
        MatchExpression me = GherkinParser.parseMatchExpression("message contains deep\n  \"\"\"\n  { order_id: 5 }\n  \"\"\"");
        assertFalse(me.isEach());
        assertEquals("message", me.getActualExpr());
        assertEquals("contains deep", me.getOperator());
        assertTrue(me.getExpectedExpr().contains("order_id"));
        assertEquals("CONTAINS_DEEP", me.getMatchTypeName());
    }

    @Test
    void testTokenizeContainsDeepWithNewline() {
        // Verify "contains deep" is tokenized as single G_KEYWORD when followed by docstring
        Resource resource = Resource.text("* match message contains deep\n  \"\"\"\n  { order_id: 5 }\n  \"\"\"");
        GherkinLexer lexer = new GherkinLexer(resource);
        Token token;
        boolean foundContainsDeep = false;
        do {
            token = lexer.nextToken();
            if (token.type == TokenType.G_KEYWORD && "contains deep".equals(token.getText())) {
                foundContainsDeep = true;
            }
        } while (token.type != TokenType.EOF);
        assertTrue(foundContainsDeep, "Expected 'contains deep' as single G_KEYWORD token");
    }

    @Test
    void testParseMatchExpressionContainsDeepNoExpected() {
        // When step.getText() is "message contains deep" (no expected value on same line),
        // the docstring provides the expected value separately.
        // The operator should be "contains deep", not just "contains" with "deep" as expected.
        MatchExpression me = GherkinParser.parseMatchExpression("message contains deep");
        assertEquals("message", me.getActualExpr());
        assertEquals("contains deep", me.getOperator());
        // Expected should be null or empty since docstring will provide it
        assertTrue(me.getExpectedExpr() == null || me.getExpectedExpr().isEmpty(),
                "Expected expr should be null/empty, but was: '" + me.getExpectedExpr() + "'");
        assertEquals("CONTAINS_DEEP", me.getMatchTypeName());
    }

    @Test
    void testParseMatchExpressionNotContains() {
        MatchExpression me = GherkinParser.parseMatchExpression("foo !contains bar");
        assertFalse(me.isEach());
        assertEquals("foo", me.getActualExpr());
        assertEquals("!contains", me.getOperator());
        assertEquals("bar", me.getExpectedExpr());
        assertEquals("NOT_CONTAINS", me.getMatchTypeName());
    }

    @Test
    void testParseMatchExpressionNotEquals() {
        MatchExpression me = GherkinParser.parseMatchExpression("foo != bar");
        assertFalse(me.isEach());
        assertEquals("foo", me.getActualExpr());
        assertEquals("!=", me.getOperator());
        assertEquals("bar", me.getExpectedExpr());
        assertEquals("NOT_EQUALS", me.getMatchTypeName());
    }

    @Test
    void testParseMatchExpressionBracketAccess() {
        MatchExpression me = GherkinParser.parseMatchExpression("json['hy-phen'] == 'bar'");
        assertFalse(me.isEach());
        assertEquals("json['hy-phen']", me.getActualExpr());
        assertEquals("==", me.getOperator());
        assertEquals("'bar'", me.getExpectedExpr());
    }

    @Test
    void testParseMatchExpressionWithMethodCall() {
        MatchExpression me = GherkinParser.parseMatchExpression("query == read('file.txt').replaceAll(\"\\r\", \"\")");
        assertFalse(me.isEach());
        assertEquals("query", me.getActualExpr());
        assertEquals("==", me.getOperator());
        assertEquals("read('file.txt').replaceAll(\"\\r\", \"\")", me.getExpectedExpr());
    }

    @Test
    void testParseMatchExpressionWithin() {
        MatchExpression me = GherkinParser.parseMatchExpression("subset within superset");
        assertFalse(me.isEach());
        assertEquals("subset", me.getActualExpr());
        assertEquals("within", me.getOperator());
        assertEquals("superset", me.getExpectedExpr());
        assertEquals("WITHIN", me.getMatchTypeName());
    }

    @Test
    void testParseMatchExpressionNotWithin() {
        MatchExpression me = GherkinParser.parseMatchExpression("foo !within bar");
        assertFalse(me.isEach());
        assertEquals("foo", me.getActualExpr());
        assertEquals("!within", me.getOperator());
        assertEquals("bar", me.getExpectedExpr());
        assertEquals("NOT_WITHIN", me.getMatchTypeName());
    }

    @Test
    void testParseMatchExpressionBareXPath() {
        // V1 SOAP syntax: match /Envelope/Body/AddResponse/AddResult == '5'
        MatchExpression me = GherkinParser.parseMatchExpression("/Envelope/Body/AddResponse/AddResult == '5'");
        assertFalse(me.isEach());
        assertEquals("/Envelope/Body/AddResponse/AddResult", me.getActualExpr());
        assertEquals("==", me.getOperator());
        assertEquals("'5'", me.getExpectedExpr());
    }

    @Test
    void testDocStringWithJsonArray() {
        feature("""
                Feature: docstring test
                Scenario: with json array
                  * def expected =
                  \"\"\"
                  [
                      { name: '#notnull' },
                      { name: '#notnull' }
                  ]
                  \"\"\"
                  * print expected
                """);
        assertNotNull(scenario);
        assertEquals(2, scenario.getSteps().size());
        Step step = scenario.getSteps().getFirst();
        assertEquals("def", step.getKeyword());
        assertNotNull(step.getDocString());
        assertTrue(step.getDocString().contains("["));
    }

    // ========== Call Syntax Tests ==========

    @Test
    void testCallWithInlineJsonArg() {
        // V1 syntax: call fun { key: 'value' } - inline JSON argument
        feature("""
                Feature: call test
                Scenario: call with inline json
                  * def fun = function(arg){ return arg.first }
                  * def result = call fun { first: 'dummy', second: 'other' }
                  * match result == 'dummy'
                """);
        assertNotNull(scenario);
        assertEquals(3, scenario.getSteps().size());
        Step step = scenario.getSteps().get(1);
        assertEquals("def", step.getKeyword());
        assertEquals("result = call fun { first: 'dummy', second: 'other' }", step.getText());
    }

    @Test
    void testStepComments() {
        // Test that comments preceding a step are captured
        feature("""
                Feature: test
                Scenario: with comment
                  * def foo = 1
                  # this is a comment label
                  * match foo == 1
                """);
        assertNotNull(scenario);
        assertEquals(2, scenario.getSteps().size());
        Step matchStep = scenario.getSteps().get(1);
        assertEquals("match", matchStep.getKeyword());
        List<String> comments = matchStep.getComments();
        assertNotNull(comments, "Step should have comments");
        assertFalse(comments.isEmpty(), "Comments should not be empty");
        assertTrue(comments.get(0).contains("this is a comment label"));
    }

    @Test
    void testDocStringWithTrailingSpaces() {
        // Test that closing """ with trailing spaces is parsed correctly
        // This was a bug where {NOT_LF}+ would match """ plus trailing chars
        feature("""
                Feature: docstring test
                Scenario Outline: with trailing spaces
                  * def expected =
                  \"\"\"
                  [
                      { name: '#notnull' }
                  ]
                  \"\"\"
                  Given path 'search'
                Examples:
                  | name |
                  | foo  |
                """);
        assertNotNull(outline);
        assertEquals(2, outline.getSteps().size());
        Step step1 = outline.getSteps().get(0);
        assertEquals("def", step1.getKeyword());
        assertNotNull(step1.getDocString());
        assertTrue(step1.getDocString().contains("["));
    }

    // ========== Windows CRLF Compatibility Tests ==========

    @Test
    void testWindowsCrlfLineEndings() {
        // Simulate Windows CRLF line endings - tags should not include \r
        String windowsFeature = "@ignore\r\n" +
                "Feature: windows test\r\n" +
                "\r\n" +
                "@setup\r\n" +
                "Scenario: test scenario\r\n" +
                "  * def x = 1\r\n";
        feature(windowsFeature);

        // Feature @ignore tag should be parsed correctly (no \r)
        assertEquals(1, feature.getTags().size());
        assertEquals("ignore", feature.getTags().get(0).getName());
        assertFalse(feature.getTags().get(0).getName().contains("\r"),
                "Tag name should not contain carriage return");

        // Scenario @setup tag should be parsed correctly (no \r)
        assertNotNull(scenario);
        assertEquals(1, scenario.getTags().size());
        assertEquals("setup", scenario.getTags().get(0).getName());
        assertFalse(scenario.getTags().get(0).getName().contains("\r"),
                "Tag name should not contain carriage return");
    }

    @Test
    void testWindowsCrlfWithTagValues() {
        // Test tags with values using CRLF
        String windowsFeature = "@env=dev,qa\r\n" +
                "Feature: env test\r\n" +
                "Scenario: test\r\n" +
                "  * print 'hello'\r\n";
        feature(windowsFeature);

        assertEquals(1, feature.getTags().size());
        Tag envTag = feature.getTags().get(0);
        assertEquals("env", envTag.getName());
        assertEquals(2, envTag.getValues().size());
        assertEquals("dev", envTag.getValues().get(0));
        assertEquals("qa", envTag.getValues().get(1));
    }

    // ========== Keyword vs JS Expression Tests ==========

    @Test
    void testCookieKeywordVsJsExpression() {
        // Test that "cookie foo = 'bar'" is parsed with cookie as keyword
        // but "cookie({...})" is parsed as JS expression (no keyword)
        feature("""
                Feature: cookie parsing
                Scenario: keyword form
                  * cookie foo = 'bar'
                Scenario: js expression form
                  * cookie({ name: 'test', value: 'hello' })
                """);

        // First scenario: cookie as keyword
        Scenario keywordScenario = feature.getSections().get(0).getScenario();
        Step keywordStep = keywordScenario.getSteps().getFirst();
        assertEquals("cookie", keywordStep.getKeyword());
        assertEquals("foo = 'bar'", keywordStep.getText());

        // Second scenario: cookie as JS expression (no keyword)
        Scenario exprScenario = feature.getSections().get(1).getScenario();
        Step exprStep = exprScenario.getSteps().getFirst();
        assertNull(exprStep.getKeyword(), "cookie({...}) should not have a keyword");
        assertEquals("cookie({ name: 'test', value: 'hello' })", exprStep.getText());
    }

    @Test
    void testHeaderKeywordVsJsExpression() {
        // Same pattern applies to other assign keywords like header
        feature("""
                Feature: header parsing
                Scenario: keyword form
                  * header Authorization = 'Bearer token'
                Scenario: js expression form
                  * header({ name: 'Authorization', value: 'Bearer token' })
                """);

        // First scenario: header as keyword
        Scenario keywordScenario = feature.getSections().get(0).getScenario();
        Step keywordStep = keywordScenario.getSteps().getFirst();
        assertEquals("header", keywordStep.getKeyword());
        assertEquals("Authorization = 'Bearer token'", keywordStep.getText());

        // Second scenario: header as JS expression (no keyword)
        Scenario exprScenario = feature.getSections().get(1).getScenario();
        Step exprStep = exprScenario.getSteps().getFirst();
        assertNull(exprStep.getKeyword(), "header({...}) should not have a keyword");
        assertEquals("header({ name: 'Authorization', value: 'Bearer token' })", exprStep.getText());
    }

    @Test
    void testDefKeywordVsArrayAccess() {
        // Test that "def foo = bar" is keyword but "def[0]" is JS expression
        feature("""
                Feature: def parsing
                Scenario: keyword form
                  * def foo = 'bar'
                Scenario: array access form
                  * def[0].name
                """);

        // First scenario: def as keyword
        Scenario keywordScenario = feature.getSections().get(0).getScenario();
        Step keywordStep = keywordScenario.getSteps().getFirst();
        assertEquals("def", keywordStep.getKeyword());
        assertEquals("foo = 'bar'", keywordStep.getText());

        // Second scenario: def[0] as JS expression (no keyword)
        Scenario exprScenario = feature.getSections().get(1).getScenario();
        Step exprStep = exprScenario.getSteps().getFirst();
        assertNull(exprStep.getKeyword(), "def[0] should not have a keyword");
        assertEquals("def[0].name", exprStep.getText());
    }

    // ========== DocString Line Offset Tests ==========

    @Test
    void testDocStringLineOffset() {
        // Line numbers are 1-indexed for step.getLine()
        // docStringLine is 0-indexed (for the lexer)
        feature("""
                Feature: test
                Scenario: eval docstring
                  * eval
                    \"\"\"
                    var x = 1;
                    var y = 2;
                    \"\"\"
                """);
        Step step = scenario.getSteps().getFirst();
        assertEquals("eval", step.getKeyword());
        assertNotNull(step.getDocString());
        assertTrue(step.getDocString().contains("var x = 1"));
        // step is on line 3 (1-indexed), """ is on line 4, content starts on line 5
        // docStringLine is 0-indexed, so line 5 → index 4
        assertTrue(step.getDocStringLine() >= 0, "docStringLine should be set");
        assertEquals(4, step.getDocStringLine());
    }

    @Test
    void testDocStringLineOffsetWithDef() {
        feature("""
                Feature: test
                Scenario: def docstring
                  * def foo =
                    \"\"\"
                    function() { return 1 }
                    \"\"\"
                """);
        Step step = scenario.getSteps().getFirst();
        assertEquals("def", step.getKeyword());
        assertNotNull(step.getDocString());
        assertTrue(step.getDocStringLine() >= 0);
        // """ is on line 4 (1-indexed → 0-indexed = 3), content on line 5 (0-indexed = 4)
        assertEquals(4, step.getDocStringLine());
    }

    @Test
    void testDocStringLineNotSetWithoutDocString() {
        feature("""
                Feature: test
                Scenario: no docstring
                  * eval 1 + 2
                """);
        Step step = scenario.getSteps().getFirst();
        assertNull(step.getDocString());
        assertEquals(-1, step.getDocStringLine());
    }

    // ========== Empty File Handling Tests ==========

    @Test
    void testEmptyFile() {
        // Empty file should return an empty feature, not throw an exception
        feature("");
        assertNotNull(feature);
        assertNull(feature.getName());
        assertTrue(feature.getSections().isEmpty());
    }

    @Test
    void testWhitespaceOnlyFile() {
        // File with only whitespace should be handled gracefully
        feature("   \n\n   \n");
        assertNotNull(feature);
        assertNull(feature.getName());
        assertTrue(feature.getSections().isEmpty());
    }

    @Test
    void testCommentOnlyFile() {
        // File with only comments should be handled gracefully
        feature("""
                # this is just a comment
                # and another one
                """);
        assertNotNull(feature);
        assertNull(feature.getName());
        assertTrue(feature.getSections().isEmpty());
    }

}

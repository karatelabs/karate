# Parser Infrastructure & IDE Support

This document describes the parser infrastructure for error-tolerant parsing and IDE features. The approach uses AST (Abstract Syntax Tree) nodes with error recovery, enabling syntax coloring, code completion, and formatting even when source code is incomplete or invalid.

> See also: [ROADMAP.md](./ROADMAP.md) for pending work | [RUNTIME.md](./RUNTIME.md) | [JS_ENGINE.md](./JS_ENGINE.md)

---

## Status Summary

| Component | Status |
|-----------|--------|
| Parser Infrastructure (Error Recovery) | Complete |
| Gherkin AST Building | Complete |
| JavaScript Error Recovery | Complete |
| Code Formatting | Planned |
| Source Reconstitution | Planned |
| Embedded Language Support | Planned |

---

## Parser API

### Gherkin Parser

```java
// Error recovery mode (for IDE) - default
GherkinParser parser = new GherkinParser(Resource.text(source));
Feature feature = parser.parse();           // Domain object
Node ast = parser.getAst();                 // AST for IDE features
List<SyntaxError> errors = parser.getErrors();
boolean hasErrors = parser.hasErrors();
```

### JavaScript Parser

```java
// Normal mode (throws on errors)
JsParser parser = new JsParser(Resource.text(source));

// Error recovery mode (for IDE)
JsParser parser = new JsParser(Resource.text(source), true);
Node ast = parser.parse();                  // Returns AST even with errors
List<SyntaxError> errors = parser.getErrors();
boolean hasErrors = parser.hasErrors();
```

### Token Information

```java
Token token = node.getFirstToken();
int line = token.line;           // 0-indexed
int col = token.col;             // 0-indexed
long pos = token.pos;            // character offset
String text = token.text;
TokenType type = token.type;

// Whitespace access (for formatting)
Token prev = token.prev;         // includes whitespace tokens
Token next = token.next;
```

### AST Traversal

```java
Node child = ast.findFirstChild(NodeType.G_STEP);
Node atPos = ast.findNodeAt(offset);
String source = node.getTextIncludingWhitespace();
List<Node> all = ast.findAll(NodeType.G_SCENARIO);
```

---

## SyntaxError Class

```java
public class SyntaxError {
    public final Token token;
    public final String message;
    public final NodeType expected;

    public int getLine() { return token.line + 1; }
    public int getColumn() { return token.col + 1; }
    public long getOffset() { return token.pos; }
}
```

---

## Gherkin Node Types

```java
G_FEATURE,          // Feature root node
G_TAGS,             // Tags container
G_NAME_DESC,        // Name and description block
G_BACKGROUND,       // Background section
G_SCENARIO,         // Scenario section
G_SCENARIO_OUTLINE, // Scenario Outline section
G_EXAMPLES,         // Examples section
G_STEP,             // Step node
G_STEP_LINE,        // Step text content (RHS)
G_DOC_STRING,       // Doc string (triple quoted)
G_TABLE,            // Table node
G_TABLE_ROW         // Single table row
```

---

## Error Recovery Patterns

### Recovery Points

| Level | Recovery Tokens | Description |
|-------|-----------------|-------------|
| Statement | `IF`, `FOR`, `WHILE`, `DO`, `SWITCH`, `TRY`, `RETURN`, `THROW`, `BREAK`, `CONTINUE`, `VAR`, `LET`, `CONST`, `FUNCTION`, `L_CURLY`, `R_CURLY`, `SEMI`, `EOF` | Statement starts and ends |

### Element-Parsing Loop Pattern

Loops that parse multiple elements need error recovery to prevent infinite loops:

```java
while (true) {
    if (peekIf(CLOSING_TOKEN) || peekIf(EOF)) {
        break;
    }
    if (!parseElement()) {
        if (errorRecoveryEnabled) {
            error("invalid element");
            recoverTo(CLOSING_TOKEN, COMMA, EOF);
            continue;
        }
        break;
    }
}
```

| Context | Recovery Tokens |
|---------|-----------------|
| Switch cases | `R_CURLY`, `CASE`, `DEFAULT`, `EOF` |
| Function params | `R_PAREN`, `COMMA`, `EOF` |
| Object properties | `R_CURLY`, `COMMA`, `EOF` |
| Array elements | `R_BRACKET`, `COMMA`, `EOF` |

### Infinite Loop Safeguard

The `recoverTo()` method prevents infinite loops by tracking position:

```java
private int lastRecoveryPosition = -1;

protected boolean recoverTo(TokenType... recoveryTokens) {
    if (position == lastRecoveryPosition) {
        if (peek() != EOF) {
            consumeNext();  // Force skip at least one token
        }
    }
    lastRecoveryPosition = position;
    // ... normal recovery logic
}
```

---

## Position Tracking

Token has all necessary position information:

| Field | Type | Description |
|-------|------|-------------|
| `pos` | `long` | Character offset from start of file |
| `line` | `int` | 0-indexed line number |
| `col` | `int` | 0-indexed column number |
| `text` | `String` | Token text (for length: `text.length()`) |

Node position derived from tokens:
- Start: `node.getFirstToken().pos`, `.line`, `.col`
- End: `node.getLastToken().pos + node.getLastToken().text.length()`

---

## IDE Integration

### Syntax Highlighting

Map `TokenType` and `NodeType` to CSS classes:

```java
parser.enableErrorRecovery();
Node node = parser.parse();           // Never throws
applyHighlighting(node);              // Always works - even partial code
```

### Error Display

```java
for (SyntaxError error : parser.getErrors()) {
    int start = (int) error.token.pos;
    int end = start + error.token.text.length();
    addErrorUnderline(start, end, error.message);
}
```

### CSS for Errors (RichTextFX)

```css
.syntax-error {
    -rtfx-underline-color: red;
    -rtfx-underline-width: 1px;
    -rtfx-underline-dash-array: 2 2;
}
```

---

## Planned: Code Formatting

> See [ROADMAP.md](./ROADMAP.md#parser--ide-support) for status.

### FormatOptions (JSON-based)

```java
public class FormatOptions {
    private final Json options;

    // Namespaced keys
    public static final String JS_INDENT_SIZE = "js.indentSize";
    public static final String JS_USE_TABS = "js.useTabs";
    public static final String JS_SPACE_BEFORE_BRACE = "js.spaceBeforeBlockBrace";
    public static final String GHERKIN_INDENT_SIZE = "gherkin.indentSize";
    public static final String GHERKIN_ALIGN_TABLES = "gherkin.alignTableColumns";
}
```

### Settings File (`format.json`)

```json
{
  "js.indentSize": 2,
  "js.useTabs": false,
  "js.spaceBeforeBlockBrace": true,
  "gherkin.indentSize": 2,
  "gherkin.alignTableColumns": true
}
```

### Formatting Strategies

| Strategy | Description |
|----------|-------------|
| Token-based | Adjust whitespace between tokens - minimal diff |
| AST-based | Reprint from AST - canonical output |
| Hybrid | Combine both approaches |

---

## Planned: Embedded Language Support

> See [ROADMAP.md](./ROADMAP.md#parser--ide-support) for status.

Gherkin files contain embedded JavaScript in step definitions:

```
* def msg = `Hello ${user.name}!`
  ^^^       ^^^^^^^^^^^^^^^^^^^^^
  Gherkin   JS (embedded in Gherkin)
```

### Approach

1. Add `embeddedAst` and `embeddedOffset` to `Node`
2. Post-process steps to parse embedded JS
3. Layer highlighting (Gherkin first, then JS overlay)
4. Adjust error positions for embedded code

---

## File References

| Purpose | File |
|---------|------|
| Token types | `karate-js/src/main/java/io/karatelabs/js/TokenType.java` |
| Node types | `karate-js/src/main/java/io/karatelabs/js/NodeType.java` |
| Error class | `karate-js/src/main/java/io/karatelabs/js/SyntaxError.java` |
| Parser base | `karate-js/src/main/java/io/karatelabs/js/Parser.java` |
| JS Parser | `karate-js/src/main/java/io/karatelabs/js/JsParser.java` |
| Gherkin Parser | `karate-js/src/main/java/io/karatelabs/js/GherkinParser.java` |
| Tests | `karate-js/src/test/java/io/karatelabs/gherkin/GherkinParserTest.java` |

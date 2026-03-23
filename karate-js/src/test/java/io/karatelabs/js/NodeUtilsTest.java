package io.karatelabs.js;

import io.karatelabs.common.Resource;
import io.karatelabs.parser.Node;
import io.karatelabs.parser.NodeType;
import io.karatelabs.parser.Token;
import io.karatelabs.parser.TokenBuffer;
import io.karatelabs.parser.TokenType;
import org.junit.jupiter.api.Test;

class NodeUtilsTest {

    @Test
    void testConversion() {
        Node node = new Node(NodeType.EXPR);
        Node c1 = new Node(NodeType.LIT_EXPR);
        node.add(c1);
        String text = "1";
        TokenBuffer buffer = new TokenBuffer(Resource.text(text));
        Token token = new Token(buffer, TokenType.NUMBER, 0, 0, 0, text.length());
        Node c2 = new Node(token);
        c1.add(c2);
        NodeUtils.assertEquals(text, node, "1");
    }

}

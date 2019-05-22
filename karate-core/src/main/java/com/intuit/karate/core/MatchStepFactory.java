package com.intuit.karate.core;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchStepFactory {

    private static final Logger logger = LoggerFactory.getLogger(FeatureParser.class);

    public static MatchStep parseFromString(String matchExpression) {

        KarateMatchParseListener listener = new KarateMatchParseListener();
        new ParseTreeWalker().walk(listener, createParseTree(matchExpression));
        return listener.build();
    }

    private static RuleContext createParseTree(String matchStepExpression) {
        KarateMatchParser parser = createMatchStepParserFor(matchStepExpression);
        ParserErrorListener errorListener = new ParserErrorListener();

        parser.addErrorListener(errorListener);
        RuleContext tree = parser.match();

        if (logger.isTraceEnabled()) {
            logger.debug(tree.toStringTree(parser));
        }

        if (errorListener.isFail()) {
            String errorMessage = errorListener.getMessage();
            logger.error("not a valid match expression: {}\n\tError message: {}", matchStepExpression, errorMessage);
            throw new RuntimeException(errorMessage);
        }
        return tree;
    }

    private static KarateMatchParser createMatchStepParserFor(String matchStepExpression) {
        return new KarateMatchParser(
                new CommonTokenStream(
                        new KarateMatchLexer(CharStreams.fromString(matchStepExpression))
                )
        );
    }

    static class KarateMatchParseListener extends KarateMatchParserBaseListener {

        private String name;
        private String path;
        private MatchType type;
        private String expected;
        private boolean isEach = false;

        MatchStep build() {
            return new MatchStep(name, path, type, expected);
        }

        @Override
        public void enterEach(KarateMatchParser.EachContext ctx) {
            this.isEach = true;
        }

        @Override
        public void enterContainsOnly(KarateMatchParser.ContainsOnlyContext ctx) {
            this.type = isEach ? MatchType.EACH_CONTAINS_ONLY : MatchType.CONTAINS_ONLY;
        }

        @Override
        public void enterContainsAny(KarateMatchParser.ContainsAnyContext ctx) {
            this.type = isEach ? MatchType.EACH_CONTAINS_ANY : MatchType.CONTAINS_ANY;
        }

        @Override
        public void enterContains(KarateMatchParser.ContainsContext ctx) {
            this.type = isEach ? MatchType.EACH_CONTAINS : MatchType.CONTAINS;
        }

        @Override
        public void enterContainsNot(KarateMatchParser.ContainsNotContext ctx) {
            this.type = isEach ? MatchType.EACH_NOT_CONTAINS : MatchType.NOT_CONTAINS;
        }

        @Override
        public void enterEqualsNot(KarateMatchParser.EqualsNotContext ctx) {
            this.type = isEach ? MatchType.EACH_NOT_EQUALS : MatchType.NOT_EQUALS;
        }

        @Override
        public void enterEquals(KarateMatchParser.EqualsContext ctx) {
            this.type = isEach ? MatchType.EACH_EQUALS : MatchType.EQUALS;
        }

        @Override
        public void enterName(KarateMatchParser.NameContext ctx) {
            this.name = ctx.getText().trim();
        }

        @Override
        public void enterPath(KarateMatchParser.PathContext ctx) {
            this.path = ctx.getText().trim();
        }

        @Override
        public void enterExpected(KarateMatchParser.ExpectedContext ctx) {
            this.expected = ctx.getText().trim();
        }
    }

}

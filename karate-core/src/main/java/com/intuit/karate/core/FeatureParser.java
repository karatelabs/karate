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
package com.intuit.karate.core;

import com.intuit.karate.StringUtils;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class FeatureParser extends KarateParserBaseListener {
    
    private static final Logger logger = LoggerFactory.getLogger(FeatureParser.class);
    
    private final ParserErrorListener errorListener = new ParserErrorListener();
    private final Feature feature;
    private final CommonTokenStream tokenStream;
    
    private FeatureParser(Feature feature, InputStream is) {
        this.feature = feature;
        CharStream stream;
        try {
            stream = CharStreams.fromStream(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        KarateLexer lexer = new KarateLexer(stream);
        tokenStream = new CommonTokenStream(lexer);
        KarateParser parser = new KarateParser(tokenStream);
        parser.addErrorListener(errorListener);
        RuleContext tree = parser.feature();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(this, tree);
    }
    
    protected static void parse(Feature feature) {
        FeatureParser fp = new FeatureParser(feature, feature.getResource().getStream());
        if (fp.errorListener.isFail()) {
            String errorMessage = fp.errorListener.getMessage();
            logger.error("not a valid feature file: {} - {}", feature.getResource().getRelativePath(), errorMessage);
            throw new RuntimeException(errorMessage);
        }
    }
    
    private static int getActualLine(TerminalNode node) {
        int count = 0;
        for (char c : node.getText().toCharArray()) {
            if (c == '\n') {
                count++;
            } else if (!Character.isWhitespace(c)) {
                break;
            }
        }
        return node.getSymbol().getLine() + count;
    }
    
    private static List<Tag> toTags(int line, List<TerminalNode> nodes) {
        List<Tag> tags = new ArrayList();
        for (TerminalNode node : nodes) {
            String text = node.getText();
            if (line == -1) {
                line = getActualLine(node);
            }
            String[] tokens = text.trim().split("\\s+"); // handles spaces and tabs also        
            for (String t : tokens) {
                tags.add(new Tag(line, t));
            }
        }
        return tags;
    }
    
    private static Table toTable(KarateParser.TableContext ctx) {
        List<TerminalNode> nodes = ctx.TABLE_ROW();
        int rowCount = nodes.size();
        if (rowCount < 1) {
            // if scenario outline found without examples
            return null;
        }
        List<List<String>> rows = new ArrayList(rowCount);
        List<Integer> lineNumbers = new ArrayList(rowCount);
        for (TerminalNode node : nodes) {
            List<String> tokens = StringUtils.split(node.getText().trim(), '|', true);
            int count = tokens.size();
            for (int i = 0; i < count; i++) {
                tokens.set(i, tokens.get(i).trim());
            }
            rows.add(tokens);
            lineNumbers.add(getActualLine(node));
        }
        return new Table(rows, lineNumbers);
    }
    
    public static final String TRIPLE_QUOTES = "\"\"\"";
    
    private static int indexOfFirstText(String s) {
        int pos = 0;
        for (char c : s.toCharArray()) {
            if (!Character.isWhitespace(c)) {
                return pos;
            }
            pos++;
        }
        return 0; // defensive coding
    }
    
    private static String fixDocString(String temp) {
        int quotePos = temp.indexOf(TRIPLE_QUOTES);
        int endPos = temp.lastIndexOf(TRIPLE_QUOTES);
        String raw = temp.substring(quotePos + 3, endPos).replaceAll("\r", "");
        List<String> lines = StringUtils.split(raw, '\n', false);
        StringBuilder sb = new StringBuilder();
        int marginPos = -1;
        Iterator<String> iterator = lines.iterator();
        while (iterator.hasNext()) {
            String line = iterator.next();
            int firstTextPos = indexOfFirstText(line);
            if (marginPos == -1) {
                marginPos = firstTextPos;
            }
            if (marginPos < line.length() && marginPos <= firstTextPos) {
                line = line.substring(marginPos);
            }
            if (iterator.hasNext()) {
                sb.append(line).append('\n');
            } else {
                sb.append(line);
            }
        }
        return sb.toString().trim();
    }
    
    private List<String> collectComments(ParserRuleContext prc) {
        List<Token> tokens = tokenStream.getHiddenTokensToLeft(prc.start.getTokenIndex());
        if (tokens == null) {
            return null;
        }
        List<String> comments = new ArrayList(tokens.size());
        for (Token t : tokens) {
            comments.add(StringUtils.trimToNull(t.getText()));
        }
        return comments;
    }
    
    private List<Step> toSteps(Scenario scenario, List<KarateParser.StepContext> list) {
        List<Step> steps = new ArrayList(list.size());
        int index = 0;
        for (KarateParser.StepContext sc : list) {
            Step step = scenario == null ? new Step(feature, index++) : new Step(scenario, index++);
            step.setComments(collectComments(sc));
            steps.add(step);
            int stepLine = sc.line().getStart().getLine();
            step.setLine(stepLine);
            step.setPrefix(sc.prefix().getText().trim());
            step.setText(sc.line().getText().trim());
            if (sc.docString() != null) {
                String raw = sc.docString().getText();
                step.setDocString(fixDocString(raw));
                step.setEndLine(stepLine + StringUtils.countLineFeeds(raw));
            } else if (sc.table() != null) {
                Table table = toTable(sc.table());
                step.setTable(table);
                step.setEndLine(stepLine + StringUtils.countLineFeeds(sc.table().getText()));
            } else {
                step.setEndLine(stepLine);
            }
        }
        return steps;
    }
    
    @Override
    public void enterFeatureHeader(KarateParser.FeatureHeaderContext ctx) {
        if (ctx.featureTags() != null) {
            feature.setTags(toTags(ctx.featureTags().getStart().getLine(), ctx.featureTags().FEATURE_TAGS()));
        }
        if (ctx.FEATURE() != null) {
            feature.setLine(ctx.FEATURE().getSymbol().getLine());
            if (ctx.featureDescription() != null) {
                StringUtils.Pair pair = StringUtils.splitByFirstLineFeed(ctx.featureDescription().getText());
                feature.setName(pair.left);
                feature.setDescription(pair.right);
            }
        }
    }
    
    @Override
    public void enterBackground(KarateParser.BackgroundContext ctx) {
        Background background = new Background();
        feature.setBackground(background);
        background.setLine(getActualLine(ctx.BACKGROUND()));
        List<Step> steps = toSteps(null, ctx.step());
        if (!steps.isEmpty()) {
            background.setSteps(steps);
        }
        if (logger.isTraceEnabled()) {
            logger.trace("background steps: {}", steps);
        }
    }
    
    @Override
    public void enterScenario(KarateParser.ScenarioContext ctx) {
        FeatureSection section = new FeatureSection();
        Scenario scenario = new Scenario(feature, section, -1);
        scenario.setLine(getActualLine(ctx.SCENARIO()));
        section.setScenario(scenario);
        feature.addSection(section);
        if (ctx.tags() != null) {
            scenario.setTags(toTags(-1, ctx.tags().TAGS()));
        }
        if (ctx.scenarioDescription() != null) {
            StringUtils.Pair pair = StringUtils.splitByFirstLineFeed(ctx.scenarioDescription().getText());
            scenario.setName(pair.left);
            scenario.setDescription(pair.right);
        }
        List<Step> steps = toSteps(scenario, ctx.step());
        scenario.setSteps(steps);
    }
    
    @Override
    public void enterScenarioOutline(KarateParser.ScenarioOutlineContext ctx) {
        FeatureSection section = new FeatureSection();
        ScenarioOutline outline = new ScenarioOutline(feature, section);
        outline.setLine(getActualLine(ctx.SCENARIO_OUTLINE()));
        section.setScenarioOutline(outline);
        feature.addSection(section);
        if (ctx.tags() != null) {
            outline.setTags(toTags(-1, ctx.tags().TAGS()));
        }
        if (ctx.scenarioDescription() != null) {
            outline.setDescription(ctx.scenarioDescription().getText());
            StringUtils.Pair pair = StringUtils.splitByFirstLineFeed(ctx.scenarioDescription().getText());
            outline.setName(pair.left);
            outline.setDescription(pair.right);
        }
        List<Step> steps = toSteps(null, ctx.step());
        outline.setSteps(steps);
        if (logger.isTraceEnabled()) {
            logger.trace("outline steps: {}", steps);
        }
        List<ExamplesTable> examples = new ArrayList(ctx.examples().size());
        outline.setExamplesTables(examples);
        for (KarateParser.ExamplesContext ec : ctx.examples()) {
            Table table = toTable(ec.table());
            ExamplesTable example = new ExamplesTable(outline, table);
            examples.add(example);
            if (ec.tags() != null) {
                example.setTags(toTags(-1, ec.tags().TAGS()));
            }
            if (logger.isTraceEnabled()) {
                logger.trace("example rows: {}", table.getRows());
            }
        }
    }
    
}

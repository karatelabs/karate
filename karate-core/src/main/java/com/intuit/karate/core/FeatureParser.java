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

import com.intuit.karate.FileUtils;
import com.intuit.karate.StringUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FeatureParser extends KarateParserBaseListener {

    private static final Logger logger = LoggerFactory.getLogger(FeatureParser.class);

    private final ParserErrorListener errorListener = new ParserErrorListener();

    private final Feature feature;

    public static Feature parse(File file) {
        return new FeatureParser(file).feature;
    }

    public static Feature parse(String path) {
        File file = FileUtils.fromRelativeClassPath(path);
        return parse(file, path);
    }

    public static Feature parse(File file, String relativePath) {
        return new FeatureParser(file, relativePath).feature;
    }

    private FeatureParser(File file) {
        this(file, FileUtils.toRelativeClassPath(file));
    }

    private FeatureParser(File file, String relativePath) {
        feature = new Feature(file, relativePath);
        CharStream stream;
        try {
            FileInputStream fis = new FileInputStream(file);
            stream = CharStreams.fromStream(fis, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        KarateLexer lexer = new KarateLexer(stream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        KarateParser parser = new KarateParser(tokens);
        parser.addErrorListener(errorListener);
        RuleContext tree = parser.feature();
        if (logger.isTraceEnabled()) {
            logger.debug(tree.toStringTree(parser));
        }
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(this, tree);
        if (errorListener.isFail()) {
            throw new RuntimeException(errorListener.getMessage());
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

    private static List<Tag> toTags(int line, TerminalNode node) {
        String text = node.getText();
        if (line == -1) {
            line = getActualLine(node);
        }
        String[] tokens = text.trim().split("\\s+"); // handles spaces and tabs also
        List<Tag> tags = new ArrayList(tokens.length);
        for (String t : tokens) {
            tags.add(new Tag(line, t));
        }
        return tags;
    }

    private static Table toTable(KarateParser.TableContext ctx) {
        List<TerminalNode> nodes = ctx.TABLE_ROW();
        int rowCount = nodes.size();
        List<List<String>> rows = new ArrayList(rowCount);
        List<Integer> lineNumbers = new ArrayList(rowCount);
        for (TerminalNode node : nodes) {
            List<String> tokens = StringUtils.split(node.getText().trim(), '|'); // TODO escaped pipe characters "\|" ?            
            // tokens.remove(0);
            int count = tokens.size();
            for (int i = 0; i < count; i++) {
                tokens.set(i, tokens.get(i).trim());
            }
            rows.add(tokens);
            lineNumbers.add(getActualLine(node));
        }
        return new Table(rows, lineNumbers);
    }

    private static final String TRIPLE_QUOTES = "\"\"\"";

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
        String raw = temp.substring(quotePos + 3, endPos);
        List<String> lines = StringUtils.split(raw, '\n');
        StringBuilder sb = new StringBuilder();
        int marginPos = -1;
        Iterator<String> iterator = lines.iterator();
        while (iterator.hasNext()) {
            String line = iterator.next();
            if (marginPos == -1) {
                marginPos = indexOfFirstText(line);
            }
            if (marginPos < line.length()) {
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

    private static List<Step> toSteps(List<KarateParser.StepContext> list) {
        List<Step> steps = new ArrayList(list.size());
        for (KarateParser.StepContext sc : list) {
            Step step = new Step();
            steps.add(step);
            step.setLine(sc.line().getStart().getLine());
            step.setPrefix(sc.prefix().getText().trim());
            step.setText(sc.line().getText().trim());
            if (sc.docString() != null) {
                step.setDocString(fixDocString(sc.docString().getText()));
            } else if (sc.table() != null) {
                Table table = toTable(sc.table());
                step.setTable(table);
            }
        }
        return steps;
    }

    @Override
    public void enterFeatureHeader(KarateParser.FeatureHeaderContext ctx) {
        if (ctx.FEATURE_TAGS() != null) {
            feature.setTags(toTags(ctx.FEATURE_TAGS().getSymbol().getLine(), ctx.FEATURE_TAGS()));
        }
        feature.setLine(ctx.FEATURE().getSymbol().getLine());
        if (ctx.featureDescription() != null) {
            StringUtils.Pair pair = StringUtils.splitByFirstLineFeed(ctx.featureDescription().getText());
            feature.setName(pair.left);
            feature.setDescription(pair.right);
        }
    }

    @Override
    public void enterBackground(KarateParser.BackgroundContext ctx) {
        Background background = new Background();
        feature.setBackground(background);
        background.setLine(getActualLine(ctx.BACKGROUND()));
        List<Step> steps = toSteps(ctx.step());
        background.setSteps(steps);
        if (logger.isTraceEnabled()) {
            logger.trace("background steps: {}", steps);
        }
    }

    @Override
    public void enterScenario(KarateParser.ScenarioContext ctx) {
        FeatureSection section = new FeatureSection();
        Scenario scenario = new Scenario(feature);
        section.setScenario(scenario);
        feature.addSection(section);
        scenario.setLine(getActualLine(ctx.SCENARIO()));
        if (ctx.tags() != null) {
            scenario.setTags(toTags(-1, ctx.tags().TAGS()));
        }
        if (ctx.scenarioDescription() != null) {
            StringUtils.Pair pair = StringUtils.splitByFirstLineFeed(ctx.scenarioDescription().getText());
            scenario.setName(pair.left);
            scenario.setDescription(pair.right);
        }
        List<Step> steps = toSteps(ctx.step());
        scenario.setSteps(steps);
        if (logger.isTraceEnabled()) {
            logger.trace("scenario steps: {}", steps);
        }
    }

    @Override
    public void enterScenarioOutline(KarateParser.ScenarioOutlineContext ctx) {
        FeatureSection section = new FeatureSection();
        ScenarioOutline outline = new ScenarioOutline(feature);
        section.setScenarioOutline(outline);
        feature.addSection(section);
        outline.setLine(getActualLine(ctx.SCENARIO_OUTLINE()));
        if (ctx.tags() != null) {
            outline.setTags(toTags(-1, ctx.tags().TAGS()));
        }
        if (ctx.scenarioDescription() != null) {
            outline.setDescription(ctx.scenarioDescription().getText());
            StringUtils.Pair pair = StringUtils.splitByFirstLineFeed(ctx.scenarioDescription().getText());
            outline.setName(pair.left);
            outline.setDescription(pair.right);
        }
        List<Step> steps = toSteps(ctx.step());
        outline.setSteps(steps);
        if (logger.isTraceEnabled()) {
            logger.trace("outline steps: {}", steps);
        }
        List<ExampleTable> examples = new ArrayList(ctx.examples().size());
        outline.setExampleTables(examples);
        for (KarateParser.ExamplesContext ec : ctx.examples()) {
            ExampleTable example = new ExampleTable(outline);
            examples.add(example);
            if (ec.tags() != null) {
                example.setTags(toTags(-1, ec.tags().TAGS()));
            }
            Table table = toTable(ec.table());
            example.setTable(table);
            if (logger.isTraceEnabled()) {
                logger.trace("example rows: {}", table.getRows());
            }
        }
    }

}

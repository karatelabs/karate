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
import com.intuit.karate.StepDefs;
import com.intuit.karate.StringUtils;
import cucumber.api.DataTable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class Feature extends KarateParserBaseListener {

    private static final Logger logger = LoggerFactory.getLogger(Feature.class);

    private final ParserErrorListener errorListener = new ParserErrorListener();

    private final File file;
    private final String featurePath;
    private int line;
    private List<Tag> tags;
    private String description;
    private Background background;
    private final List<FeatureSection> sections = new ArrayList();

    public File getFile() {
        return file;
    }

    public String getFeaturePath() {
        return featurePath;
    }    
    
    public List<Tag> getTags() {
        return tags;
    }

    public List<FeatureSection> getSections() {
        return sections;
    }

    public void execute(StepDefs stepDefs) {
        for (FeatureSection section : sections) {
            if (section.isOutline()) {
                List<Scenario> scenarios = section.getScenarioOutline().getScenarios();
                for (Scenario scenario : scenarios) {
                    execute(scenario, stepDefs);
                }
            } else {
                Scenario scenario = section.getScenario();
                execute(scenario, stepDefs);
            }
        }
    }

    private void execute(Scenario scenario, StepDefs stepDefs) {
        if (background != null) {
            execute(background.getSteps(), stepDefs);
        }
        execute(scenario.getSteps(), stepDefs);
    }

    private void execute(List<Step> steps, StepDefs stepDefs) {
        for (Step step : steps) {
            String text = step.getText();
            List<MethodMatch> matches = MethodUtils.findMethodsMatching(text);
            if (matches.isEmpty()) {
                String message = "no method match found for: " + text;
                logger.error(message);
                throw new RuntimeException(message);
            } else if (matches.size() > 1) {
                String message = "more than one method matched: " + text + " - " + matches;
                logger.error(message);
                throw new RuntimeException(message);
            }
            MethodMatch match = matches.get(0);
            Object last;
            if (step.getDocString() != null) {
                last = step.getDocString();
            } else if (step.getTable() != null) {
                last = DataTable.create(step.getTable().getRows());
            } else {
                last = null;
            }
            Object[] args = match.convertArgs(last);
            if (logger.isTraceEnabled()) {
                logger.debug("MATCH: {}, {}, {}", text, match, Arrays.asList(args));
            }
            try {
                match.method.invoke(stepDefs, args);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    public Map<String, Object> toMap() {
        String name = new File(featurePath).getName();
        Map<String, Object> map = new HashMap();
        map.put("line", line);
        map.put("id", name);
        map.put("name", featurePath);
        map.put("uri", file.getPath());
        map.put("description", description);
        map.put("keyword", "Feature");
        List<Map<String, Object>> list = new ArrayList();
        map.put("elements", list);
        if (background != null) {
            list.add(background.toMap());
        }
        for (FeatureSection section : sections) {
            if (section.isOutline()) {
                list.add(section.getScenarioOutline().toMap());
            } else {
                list.add(section.getScenario().toMap());
            }
        }
        return map;
    }

    public Feature(String featurePath) { 
        this.featurePath = featurePath;
        file = FileUtils.resolveIfClassPath(featurePath, Thread.currentThread().getContextClassLoader());        
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

    private static List<Tag> toTags(String text) {
        String[] tokens = text.split("\\s+"); // handles spaces and tabs also
        List<Tag> tags = new ArrayList(tokens.length);
        for (String t : tokens) {
            tags.add(new Tag(t));
        }
        return tags;
    }

    private static Table toTable(KarateParser.TableContext ctx) {
        List<TerminalNode> nodes = ctx.TABLE_ROW();
        List<List<String>> rows = new ArrayList(nodes.size());
        for (TerminalNode node : nodes) {
            List<String> tokens = StringUtils.split(node.getText(), '|'); // TODO escaped pipe characters "\|" ?            
            tokens.remove(0);
            int count = tokens.size();
            for (int i = 0; i < count; i++) {
                tokens.set(i, tokens.get(i).trim());
            }
            rows.add(tokens);
        }
        return new Table(rows);
    }

    private static List<Step> toSteps(List<KarateParser.StepContext> list) {
        List<Step> steps = new ArrayList(list.size());
        for (KarateParser.StepContext sc : list) {
            Step step = new Step();
            steps.add(step);
            step.setLine(sc.prefix().getStart().getLine());
            step.setPrefix(sc.prefix().getText().trim());
            step.setText(sc.line().getText());
            if (sc.docString() != null) {
                String temp = sc.docString().getText().trim();
                step.setDocString(temp.substring(3, temp.length() - 3));
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
            tags = toTags(ctx.FEATURE_TAGS().getText());
        }
        line = ctx.FEATURE().getSymbol().getLine();
        if (ctx.featureDescription() != null) {
            description = ctx.featureDescription().getText();
        }        
    }    

    @Override
    public void enterBackground(KarateParser.BackgroundContext ctx) {
        background = new Background();
        background.setLine(ctx.BACKGROUND().getSymbol().getLine());
        List<Step> steps = toSteps(ctx.step());
        background.setSteps(steps);
        if (logger.isTraceEnabled()) {
            logger.trace("background steps: {}", steps);
        }
    }

    @Override
    public void enterScenario(KarateParser.ScenarioContext ctx) {
        FeatureSection section = new FeatureSection();
        Scenario scenario = new Scenario();        
        section.setScenario(scenario);
        sections.add(section);
        scenario.setLine(ctx.SCENARIO().getSymbol().getLine());
        if (ctx.tags() != null) {
            scenario.setTags(toTags(ctx.tags().getText()));
        }
        if (ctx.scenarioDescription() != null) {
            scenario.setDescription(ctx.scenarioDescription().getText());
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
        ScenarioOutline outline = new ScenarioOutline();        
        section.setScenarioOutline(outline);
        sections.add(section);
        outline.setLine(ctx.SCENARIO_OUTLINE().getSymbol().getLine());
        if (ctx.tags() != null) {
            outline.setTags(toTags(ctx.tags().getText()));
        }
        if (ctx.scenarioDescription() != null) {
            outline.setDescription(ctx.scenarioDescription().getText());
        }
        List<Step> steps = toSteps(ctx.step());
        outline.setSteps(steps);
        if (logger.isTraceEnabled()) {
            logger.trace("outline steps: {}", steps);
        }
        List<ExampleTable> examples = new ArrayList(ctx.examples().size());
        outline.setExampleTables(examples);
        for (KarateParser.ExamplesContext ec : ctx.examples()) {
            ExampleTable example = new ExampleTable();
            examples.add(example);
            if (ec.tags() != null) {
                example.setTags(toTags(ec.tags().getText()));
            }
            Table table = toTable(ec.table());
            example.setTable(table);
            if (logger.isTraceEnabled()) {
                logger.trace("example rows: {}", table.getRows());
            }
        }
    }

}

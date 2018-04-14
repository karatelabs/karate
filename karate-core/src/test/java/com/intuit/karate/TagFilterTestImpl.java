package com.intuit.karate;

import com.intuit.karate.filter.TagFilter;
import com.intuit.karate.filter.TagFilterException;
import cucumber.runtime.model.CucumberExamples;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.model.CucumberScenarioOutline;
import cucumber.runtime.model.CucumberTagStatement;
import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import gherkin.formatter.model.Tag;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TagFilterTestImpl implements TagFilter {

    private static final String TAG_FILTER_FEATURE = "tag-filter.feature";
    private static final String TAG_FILTER_MULTISCENARIO_FEATURE = "tag-filter-multiscenario.feature";

    @Override
    public boolean filter(CucumberFeature feature, CucumberTagStatement cucumberTagStatement) throws TagFilterException {
        String path = feature.getPath();
        if(path.endsWith(TAG_FILTER_FEATURE)) {
            throw new TagFilterException("Feature: "+TAG_FILTER_FEATURE+" failed due to tag filtering");
        }

        if(path.endsWith(TAG_FILTER_MULTISCENARIO_FEATURE)) {
            validateTags(feature, cucumberTagStatement);
        }
        return false;
    }

    private void validateTags (CucumberFeature cucumberFeature, CucumberTagStatement cucumberTagStatement) throws TagFilterException {

        Set<Tag> effectiveTags = null;
        if (cucumberTagStatement.getGherkinModel() instanceof ScenarioOutline) {
            final List<CucumberExamples> cucumberExamplesList = ((CucumberScenarioOutline) cucumberTagStatement).getCucumberExamplesList();
            for (CucumberExamples cucumberExamples : cucumberExamplesList) {
                effectiveTags = getAllTagsForScenarioOutline(cucumberExamples.getExamples(), cucumberFeature, cucumberTagStatement);
            }

        } else if (cucumberTagStatement.getGherkinModel() instanceof Scenario) {
            effectiveTags = getAllTagsForScenario(cucumberFeature, cucumberTagStatement);
        }

        if (null == effectiveTags) {
            throw new TagFilterException("Required tags missing for feature "+ cucumberFeature.getPath());
        }
    }

    private Set<Tag> getAllTagsForScenario(CucumberFeature cucumberFeature, CucumberTagStatement cucumberTagStatement) {
        Set<Tag> tags = new HashSet<>();
        tags.addAll(cucumberFeature.getGherkinFeature().getTags());
        tags.addAll(cucumberTagStatement.getGherkinModel().getTags());
        return tags;
    }

    private Set<Tag> getAllTagsForScenarioOutline(Examples examples, CucumberFeature cucumberFeature, CucumberTagStatement cucumberTagStatement) {
        Set<Tag> tags = new HashSet<>();
        tags.addAll(getAllTagsForScenario(cucumberFeature, cucumberTagStatement));
        tags.addAll(examples.getTags());
        return tags;
    }
}

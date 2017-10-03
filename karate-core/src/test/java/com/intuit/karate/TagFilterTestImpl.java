package com.intuit.karate;

import com.intuit.karate.filter.TagFilter;
import com.intuit.karate.filter.TagFilterException;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.model.CucumberTagStatement;

public class TagFilterTestImpl implements TagFilter {

    private static final String TAG_FILTER_FEATURE = "tag-filter.feature";

    @Override
    public boolean filter(CucumberFeature feature, CucumberTagStatement cucumberTagStatement) throws TagFilterException {
        String path = feature.getPath();
        if(path.endsWith(TAG_FILTER_FEATURE)) {
            throw new TagFilterException("Feature: "+TAG_FILTER_FEATURE+" failed due to tag filtering");
        }
        return false;
    }
}

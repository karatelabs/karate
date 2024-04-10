package com.intuit.karate.core;

import java.util.List;

public class TestScenario{
    private Feature feature;

    public Feature getFeature(){
        return feature;
    }

    public void setFeature(Feature feature){
        this.feature=feature;
    }

    private FeatureSection section;

    public FeatureSection getSection(){
        return section;
    }

    public void setSection(FeatureSection section){
        this.section=section;
    }

    private int line;

    public int getLine(){
        return line;
    }

    public void setLine(int line){
        this.line=line;
    }

    private List<Tag> tags;

    public List<Tag> getTags(){
        return tags;
    }

    public void setTags(List<Tag> tags){
        this.tags=tags;
    }

    private String name;

    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name=name;
    }

    private String description;

    public String getDescription(){
        return description;
    }

    public void setDescription(String description){
        this.description=description;
    }

    private List<Step> steps;

    public List<Step> getSteps(){
        return steps;
    }

    public void setSteps(List<Step> steps){
        this.steps=steps;
    }

    public TestScenario(Feature feature,FeatureSection section,int line,List<Tag> tags,String name,String description,List<Step> steps){
        this.feature=feature;
        this.section=section;
        this.line=line;
        this.tags=tags;
        this.name=name;
        this.description=description;
        this.steps=steps;
    }
}

